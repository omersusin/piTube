package com.omersusin.pitube

import android.app.Application
import com.omersusin.pitube.data.CookieDownloader
import com.omersusin.pitube.data.InstanceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

class PiTubeApplication : Application() {
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        lateinit var instance: PiTubeApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        super.onCreate()
        NewPipe.init(CookieDownloader(emptyMap()))
        NewPipe.setupLocalization(Localization("en"))
        appScope.launch { InstanceManager.refreshInstances() }
    }
}
