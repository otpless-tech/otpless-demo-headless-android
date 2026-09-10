package com.otpless.demo.otplessheadlessdemo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.otpless.v2.android.sdk.dto.OtplessRequest
import com.otpless.v2.android.sdk.dto.OtplessResponse
import com.otpless.v2.android.sdk.dto.ResponseTypes
import com.otpless.v2.android.sdk.main.OtplessSDK
import kotlinx.coroutines.launch


class HomeScreen : Fragment() {
    private lateinit var etPhoneNumber: EditText
    private lateinit var submit: Button
    private lateinit var boxLoader: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var snaFallbackRow: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        etPhoneNumber = view.findViewById<EditText>(R.id.phone_input)
        submit = view.findViewById<Button>(R.id.phone_submit)
        OtplessSDK.setResponseCallback(this::onOtplessResponse)
        boxLoader = view.findViewById(R.id.phone_box_loader)
        statusText = view.findViewById(R.id.phone_status_text)
        snaFallbackRow = view.findViewById(R.id.sna_fallback_row)

        submit.setOnClickListener {
            snaFallbackRow.visibility = View.GONE
            this.showLoader("Initiating request...\uD83D\uDD10")
            val phone = etPhoneNumber.text.toString().trim()
            val otplessRequest = OtplessRequest()
            otplessRequest.setPhoneNumber(phone, "+91")
            lifecycleScope.launch {
                OtplessSDK.start(request = otplessRequest, callback = this@HomeScreen::onOtplessResponse)
            }
        }

        view.findViewById<Button>(R.id.retry_sms_button).setOnClickListener { retryWithChannel("SMS") }
        view.findViewById<Button>(R.id.retry_whatsapp_button).setOnClickListener { retryWithChannel("WHATSAPP") }
    }

    /**
     * Manual fallback offered when silent auth is exhausted (see the 9106 branch
     * below). Demonstrates OtplessRequest.setDeliveryChannel("SMS"/"WHATSAPP"/"VIBER")
     * from the custom-headless-request doc.
     */
    private fun retryWithChannel(channel: String) {
        snaFallbackRow.visibility = View.GONE
        showLoader("Sending OTP via $channel...")
        val otplessRequest = OtplessRequest()
        otplessRequest.setPhoneNumber(etPhoneNumber.text.toString().trim(), "+91")
        otplessRequest.setDeliveryChannel(channel)
        lifecycleScope.launch {
            OtplessSDK.start(request = otplessRequest, callback = this@HomeScreen::onOtplessResponse)
        }
    }

    private fun onOtplessResponse(response: OtplessResponse) {
        OtplessSDK.commit(response)
        OtplessLogger.logResponse("HomeScreen", response)

        val context = requireContext()
        val authType = response.response?.optString("authType")

        when (response.responseType) {

            ResponseTypes.SDK_READY -> {
                Toast.makeText(context, "SDK is ready 🎉", Toast.LENGTH_LONG).show()
            }

            ResponseTypes.FAILED -> {
                Toast.makeText(
                    context,
                    "SDK Failed to load. Check your network or App ID.",
                    Toast.LENGTH_LONG
                ).show()
            }

            ResponseTypes.INITIATE -> {
                when {
                    response.statusCode == 200 && authType == "OTP" -> {
                        hideLoader()
                        val deliveryChannel = response.response?.optString("deliveryChannel")
                        if (!isStateSaved) {
                            val fragment = OTPScreen.newInstance(
                                etPhoneNumber.text.toString(),
                                deliveryChannel!!
                            )
                            val fm = requireActivity().supportFragmentManager
                            fm.beginTransaction()
                                .replace(R.id.fragment_container, fragment)
                                .addToBackStack("HomeScreen")
                                .commit()
                            // Force the swap (and OTPScreen's setResponseCallback) to run now,
                            // not on the next main-thread loop iteration - otherwise a
                            // callback that arrives immediately after INITIATE (e.g.
                            // DELIVERY_STATUS) still lands on this screen instead of OTPScreen.
                            fm.executePendingTransactions()
                        }
                    }

                    response.statusCode == 200 && authType == "SILENT_AUTH" -> {
                        showLoader("Verifying \uD83D\uDD10  over network\uD83D\uDCE1 ...")
                    }

                    else -> {
                        if (response.statusCode == 200) {
                            hideLoader()
                            Toast.makeText(context, " Request initiated✅ for $authType", Toast.LENGTH_LONG).show()
                        } else {
                            handleInitiateError(response)
                        }
                    }
                }
            }

            ResponseTypes.VERIFY -> {
                if (authType == "SILENT_AUTH") {
                    if (response.statusCode == 9106) {
                        // Per OTPless docs: silent auth AND every dashboard-configured
                        // fallback method have been exhausted - this is terminal, so
                        // offer a manual channel instead of a dead end.
                        hideLoader()
                        showError("Couldn't verify silently. Continue via:")
                        snaFallbackRow.visibility = View.VISIBLE
                    } else {
                        // SmartAuth is retrying automatically via a fresh INITIATE for
                        // the next configured method - nothing to do here but wait.
                        showLoader("Unable to verify over network ...")
                    }
                }
            }
            ResponseTypes.DELIVERY_STATUS -> {
                val deliveredChannel = response.response?.optString("deliveryChannel")
                Toast.makeText(context, " $authType Request Delivered ✅ to +91 ${etPhoneNumber.text} on $deliveredChannel", Toast.LENGTH_LONG).show()
            }

            ResponseTypes.FALLBACK_TRIGGERED -> {
                val deliveredChannel = response.response?.optString("deliveryChannel")
                Toast.makeText(context, "Now Sending...\uD83D\uDCAC $authType Request to +91 ${etPhoneNumber.text} on $deliveredChannel", Toast.LENGTH_LONG).show()
            }

            ResponseTypes.ONETAP -> {
                hideLoader()
                response.response
                    ?.optJSONObject("data")
                    ?.optString("token")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { token ->
                        if (!isStateSaved) {
                            val fm = requireActivity().supportFragmentManager
                            fm.beginTransaction()
                                .replace(R.id.fragment_container, SuccessScreen.newInstance(token))
                                .commit()
                            fm.executePendingTransactions()
                        }
                    }
            }

            else -> {
                hideLoader()
            }
        }
    }
    private fun handleInitiateError(response: OtplessResponse) {
        val error = OtplessErrorHandler.classify(response)
        OtplessLogger.log("HomeScreen: [${error.category}] ${error.code} - ${error.message}")
        showError(error.message)
        Toast.makeText(context, "⚠ ${error.message}", Toast.LENGTH_SHORT).show()
    }

    private fun showLoader(message: String) {
        boxLoader.visibility = View.VISIBLE
        setStatusText(message, isError = false)
        submit.isEnabled = false
        etPhoneNumber.isEnabled = false
    }

    private fun hideLoader() {
        boxLoader.visibility = View.GONE
        setStatusText("", isError = false)
        submit.isEnabled = true
        etPhoneNumber.isEnabled = true
    }

    private fun showError(message: String) {
        boxLoader.visibility = View.GONE
        setStatusText(message, isError = true)
        submit.isEnabled = true
        etPhoneNumber.isEnabled = true
    }

    private fun setStatusText(message: String, isError: Boolean) {
        statusText.text = message
        statusText.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
        statusText.setTextColor(
            ContextCompat.getColor(requireContext(), if (isError) R.color.custom_error else R.color.status_muted)
        )
    }
}