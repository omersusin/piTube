package com.omersusin.pitube

import android.app.Application
import com.omersusin.pitube.data.CookieDownloader
import com.omersusin.pitube.data.VisitorDataManager
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

class PiTubeApplication : Application() {
    companion object {
        lateinit var instance: PiTubeApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NewPipe.init(CookieDownloader(emptyMap()))
        NewPipe.setupLocalization(Localization("en"))
        VisitorDataManager.init(this)
    }
}
