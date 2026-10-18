// Modul: :app
package com.codeforge.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CodeForgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TermuxApplication.initTermuxApplication(this)
        com.codeforge.feature.composepreview.PreviewActivityHolder.register(this)
    }
}
