package com.autophone.callforward

import android.app.Application
import com.autophone.callforward.notify.NotificationHelper

/**
 * App 入口：初始化通知渠道等全局资源。
 */
class CallForwardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).createChannels()
    }
}
