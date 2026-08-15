package com.omersusin.pitube.recognition

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.omersusin.pitube.data.local.RecognitionProvider
import com.omersusin.pitube.data.local.RecognitionFailureType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Persists recordings that could not be recognized (per the fallback policy)
 * under `filesDir/recognition_samples/<epochMs>_<provider>.wav`, and retries
 * them automatically once connectivity returns (Audile's fallback behavior).
 */
object RecognitionSamplesStore {
    private const val TAG = "RecognitionSamples"
    private const val DIR_NAME = "recognition_samples"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }

    fun saveSample(
        context: Context,
        provider: RecognitionProvider,
        wavBytes: ByteArray,
    ): File? {
        return runCatching {
            val file =
                File(
                    dir(context),
                    "${System.currentTimeMillis()}_${provider.storedValue}.wav",
                )
            file.writeBytes(wavBytes)
            Log.i(TAG, "Saved unrecognized recording: ${file.name}")
            file
        }.getOrNull()
    }

    fun pendingSamples(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".wav") }?.sortedBy { it.name }
            ?: emptyList()

    fun deleteSample(file: File) {
        runCatching { file.delete() }
    }

    /** Provider captured in the file name (`<epochMs>_<provider>.wav`). */
    fun providerOf(file: File): RecognitionProvider {
        val stored = file.name.substringAfter("_", "").substringBefore(".wav")
        return RecognitionProvider.fromStored(stored.ifBlank { null })
    }

    /**
     * Registers a default-network callback that re-runs recognition for every
     * pending sample on reconnect. Called once from FlowApplication.
     */
    fun startOfflineRetryMonitor(context: Context) {
        val appContext = context.applicationContext
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val hasInternet =
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                if (!hasInternet) return
                appScope.launch {
                    retryPending(appContext)
                }
            }
        })
        Log.i(TAG, "Offline retry monitor registered")
    }

    private suspend fun retryPending(context: Context) {
        val pending = pendingSamples(context)
        if (pending.isEmpty()) return
        Log.i(TAG, "Connectivity back — retrying ${pending.size} saved recording(s)")

        val preferences = com.omersusin.pitube.data.local.RecognitionPreferences(context)
        val resultNotifier = RecognitionNotifier.getInstance(context)

        for (file in pending) {
            val provider = providerOf(file)
            val wav = runCatching { file.readBytes() }.getOrNull()
                ?: run { deleteSample(file); continue }
            val pcm = wavToPcm16(wav)
            if (pcm == null || pcm.size < 128 * 46) {
                deleteSample(file)
                continue
            }
            val durationMs = (pcm.size * 1000L) / 16000
            val match = runCatching {
                when (provider) {
                    RecognitionProvider.SHAZAM -> {
                        val generator = ShazamSignatureGenerator()
                        generator.feedPcm16Mono(pcm)
                        val signature = generator.nextSignatureOrNull()
                        if (signature == null) {
                            throw RecognitionException(RecognitionFailureType.NO_MATCH, "No signature")
                        }
                        ShazamRecognizer.recognize(signature.uri, signature.sampleDurationMs)
                    }
                    RecognitionProvider.AUDD -> AuddRecognizer.recognize(wav)
                    RecognitionProvider.ACRCLOUD -> AcrCloudRecognizer.recognize(wav)
                }
            }.getOrNull()

            if (match != null) {
                deleteSample(file)
                if (preferences.notificationsEnabled.first()) {
                    resultNotifier.showMatchedTrackNotification(match)
                }
                Log.i(TAG, "Offline retry matched: ${match.title} — ${match.artist}")
            }
        }
    }
}

/** Quick connectivity check used to pick the voice-recognition path. */
fun hasInternetConnection(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
    return connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}