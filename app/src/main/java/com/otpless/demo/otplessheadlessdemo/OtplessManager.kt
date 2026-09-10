package com.otpless.demo.otplessheadlessdemo

import android.app.Activity
import com.otpless.v2.android.sdk.dto.OtplessResponse
import com.otpless.v2.android.sdk.main.OtplessSDK

/**
 * Single place that owns the SDK's app id and the init/logout lifecycle so every
 * screen re-initializes the SDK the same way after a logout.
 */
object OtplessManager {

    // Replace with your OTPless App ID (from the OTPless dashboard) before running the demo.
    const val APP_ID = "YOUR_APP_ID"

    suspend fun initialize(activity: Activity, callback: (OtplessResponse) -> Unit) {
        OtplessSDK.initialize(APP_ID, activity)
        OtplessSDK.setResponseCallback(callback)
        OtplessLogger.log("SDK initialize() called for appId=$APP_ID")
    }

    /**
     * Tears down the current session via [OtplessSDK.cleanup] and re-initializes the
     * SDK so the demo can go through a fresh login without restarting the app.
     */
    suspend fun logout(activity: Activity, callback: (OtplessResponse) -> Unit) {
        OtplessSDK.cleanup()
        OtplessLogger.log("SDK cleanup() called - session cleared")
        initialize(activity, callback)
    }
}
