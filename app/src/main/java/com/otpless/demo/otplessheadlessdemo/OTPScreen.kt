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
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.otpless.v2.android.sdk.dto.OtplessRequest
import com.otpless.v2.android.sdk.dto.OtplessResponse
import com.otpless.v2.android.sdk.dto.ResponseTypes
import com.otpless.v2.android.sdk.main.OtplessSDK
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private const val ARG_PHONE_NUMBER = "phone_number"
private const val ARG_DELIVERY_CHANNEL = "delivery_channel"
private const val RESEND_COOLDOWN_SECONDS = 30

class OTPScreen : Fragment() {
    private var phoneNumber: String? = null
    private var deliveryChannel: String? = null
    private var resendCooldownJob: Job? = null

    private lateinit var otpEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var resendButton: Button
    private lateinit var boxLoader: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var phoneDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            phoneNumber = it.getString(ARG_PHONE_NUMBER)
            deliveryChannel = it.getString(ARG_DELIVERY_CHANNEL)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_o_t_p_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        otpEditText = view.findViewById(R.id.otp_input)
        submitButton = view.findViewById(R.id.otp_submit)
        resendButton = view.findViewById(R.id.resend_otp_button)
        boxLoader = view.findViewById(R.id.otp_box_loader)
        statusText = view.findViewById(R.id.otp_status_text)
        phoneDisplay = view.findViewById(R.id.phone_display)
        OtplessSDK.setResponseCallback(this::onOtplessResponse)

        phoneDisplay.text = "Sending... OTP \uD83D\uDCAC to +91 $phoneNumber on $deliveryChannel"

        submitButton.setOnClickListener {
           verifyOTP()
        }
        resendButton.setOnClickListener { resendOtp() }
        view.findViewById<View>(R.id.change_number_button).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // An OTP was just sent to get here, so the resend cooldown starts immediately.
        startResendCooldown()
    }
    private fun verifyOTP(){
        val otp = otpEditText.text.toString().trim()
        if (otp.isBlank()) {
            Toast.makeText(requireContext(), "Enter OTP", Toast.LENGTH_SHORT).show()
            return
        }

        showLoader("Verifying \uD83D\uDD10 OTP...")

        val otplessRequest = OtplessRequest()
        otplessRequest.setPhoneNumber(phoneNumber.toString(), "91")
        otplessRequest.setOtp(otp)
        lifecycleScope.launch {
            OtplessSDK.start(otplessRequest, this@OTPScreen::onOtplessResponse)
        }
    }

    /** Requests a fresh OTP on the same channel - the natural next step after an
     * OTP_EXPIRED error, but left available at all times like a real OTP screen. */
    private fun resendOtp() {
        otpEditText.text?.clear()
        showLoader("Resending OTP...")
        val otplessRequest = OtplessRequest()
        otplessRequest.setPhoneNumber(phoneNumber.toString(), "91")
        deliveryChannel?.takeIf { it.isNotBlank() }?.let { otplessRequest.setDeliveryChannel(it) }
        lifecycleScope.launch {
            OtplessSDK.start(otplessRequest, this@OTPScreen::onOtplessResponse)
        }
        startResendCooldown()
    }

    /** Disables Resend for [RESEND_COOLDOWN_SECONDS], counting down in the button
     * label, so the demo can't hammer the resend endpoint. */
    private fun startResendCooldown() {
        resendCooldownJob?.cancel()
        resendCooldownJob = lifecycleScope.launch {
            resendButton.isEnabled = false
            for (secondsLeft in RESEND_COOLDOWN_SECONDS downTo 1) {
                resendButton.text = "Resend OTP (${secondsLeft}s)"
                delay(1000)
            }
            resendButton.text = "Resend OTP"
            resendButton.isEnabled = true
        }
    }

    private fun onOtplessResponse(response: OtplessResponse) {
        OtplessSDK.commit(response)
        OtplessLogger.logResponse("OTPScreen", response)

        val context = requireContext()
        val authType = response.response?.optString("authType")

        when (response.responseType) {

            ResponseTypes.VERIFY -> {
                if (authType == "OTP") {
                    if (response.statusCode == 200) {
                        showLoader("OTP verified ✅ Completing login...")
                    } else {
                        handleVerifyError(response)
                    }
                }
            }

            ResponseTypes.OTP_AUTO_READ -> {
                val otp = response.response?.optString("otp")
                if (!otp.isNullOrBlank()) {
                    otpEditText.setText(otp)
                    verifyOTP()
                }

            }
            ResponseTypes.DELIVERY_STATUS -> {
                val deliveredChannel = response.response?.optString("deliveryChannel")
                phoneDisplay.text = "OTP Delivered ✅ to +91 $phoneNumber on $deliveryChannel"
            }

            ResponseTypes.FALLBACK_TRIGGERED -> {
                val deliveredChannel = response.response?.optString("deliveryChannel")
                phoneDisplay.text = "Now Sending... OTP \uD83D\uDCAC to +91 $phoneNumber on $deliveryChannel"
            }

            ResponseTypes.ONETAP -> {
                hideLoader()
                response.response
                    ?.optJSONObject("data")
                    ?.optString("token")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { token ->
                        if (!isStateSaved) {
                            // Clear the entire back stack so "back" from Success can't return to OTP entry
                            parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, SuccessScreen.newInstance(token))
                                .commit()
                            parentFragmentManager.executePendingTransactions()
                        }
                    }
            }

            else -> {
                hideLoader()
            }
        }
    }
    private fun handleVerifyError(response: OtplessResponse) {
        val error = OtplessErrorHandler.classify(response)
        OtplessLogger.log("OTPScreen: [${error.category}] ${error.code} - ${error.message}")
        showError(error.message)
        Toast.makeText(context, "⚠ ${error.message}", Toast.LENGTH_SHORT).show()
        if (error.category == OtplessErrorCategory.OTP_INCORRECT ||
            error.category == OtplessErrorCategory.OTP_EXPIRED
        ) {
            otpEditText.text?.clear()
        }
    }

    private fun showLoader(message: String) {
        boxLoader.visibility = View.VISIBLE
        setStatusText(message, isError = false)
        submitButton.isEnabled = false
        otpEditText.isEnabled = false
    }

    private fun hideLoader() {
        boxLoader.visibility = View.GONE
        setStatusText("", isError = false)
        submitButton.isEnabled = true
        otpEditText.isEnabled = true
    }

    private fun showError(message: String) {
        boxLoader.visibility = View.GONE
        setStatusText(message, isError = true)
        submitButton.isEnabled = true
        otpEditText.isEnabled = true
    }

    private fun setStatusText(message: String, isError: Boolean) {
        statusText.text = message
        statusText.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
        statusText.setTextColor(
            ContextCompat.getColor(requireContext(), if (isError) R.color.custom_error else R.color.status_muted)
        )
    }
    companion object {
        @JvmStatic
        fun newInstance(phoneNumber: String, deliveryChannel: String) =
            OTPScreen().apply {
                arguments = Bundle().apply {
                    putString(ARG_PHONE_NUMBER, phoneNumber)
                    putString(ARG_DELIVERY_CHANNEL, deliveryChannel)
                }
            }
    }
}