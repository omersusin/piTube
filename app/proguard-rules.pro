# ProGuard rules for piTube

# Keep NewPipe Extractor
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**

# Keep Media3/ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data models for Gson
-keep class com.omersusin.pitube.data.** { *; }

# Keep Compose
-dontwarn androidx.compose.**

# Keep Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Security Crypto
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Keep Paging
-keep class androidx.paging.** { *; }
-dontwarn androidx.paging.**

# General Android
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
