package com.nonea.ai

import android.app.Application
import com.nonea.ai.ai.CrashRedactor
import com.nonea.ai.automation.AutomationGuard

class NoneaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRedactor.install()   // crash reports must never contain API keys
        AutomationGuard.init(this)   // loads the automation master switch (off by default)
    }
}
