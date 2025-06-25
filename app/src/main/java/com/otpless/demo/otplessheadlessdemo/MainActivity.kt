package com.otpless.demo.otplessheadlessdemo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.otpless.v2.android.sdk.dto.OtplessRequest
import com.otpless.v2.android.sdk.dto.OtplessResponse
import com.otpless.v2.android.sdk.dto.ResponseTypes
import com.otpless.v2.android.sdk.main.OtplessSDK
import com.otpless.v2.android.sdk.main.OtplessSDK.startAsync
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        OtplessSDK.initialize("YOUR_APP_ID", this)
        OtplessSDK.setResponseCallback(this::onOtplessResponse)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeScreen())
                .commit()
        }
    }
    private fun onOtplessResponse(response: OtplessResponse) {
        OtplessSDK.commit(response)
        if (response.responseType == ResponseTypes.SDK_READY) {
            Toast.makeText(this, "SDK is ready \uD83E\uDD73", Toast.LENGTH_LONG).show()
            return
        }
        if (response.responseType == ResponseTypes.FAILED) {
            Toast.makeText(this, "SDK Failed to load check your network or App id provided for init..", Toast.LENGTH_LONG).show()
            return
        }

    }


//    override fun onNewIntent(intent: Intent?) {
//        super.onNewIntent(intent)
//
//        lifecycleScope.launch {
//            intent?.let { OtplessSDK.onNewIntent(it) }
//        }
//    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
                lifecycleScope.launch {
            intent?.let {
                OtplessSDK.onNewIntent(it)
            }
        }
    }




    private fun replaceFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}
