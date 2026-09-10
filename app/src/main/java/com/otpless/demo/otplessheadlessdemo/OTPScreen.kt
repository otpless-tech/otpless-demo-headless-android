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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.otpless.v2.android.sdk.dto.OtplessRequest
import com.otpless.v2.android.sdk.dto.OtplessResponse
import com.otpless.v2.android.sdk.dto.ResponseTypes
import com.otpless.v2.android.sdk.main.OtplessSDK
import kotlinx.coroutines.launch


private const val ARG_PHONE_NUMBER = "phone_number"
private const val ARG_DELIVERY_CHANNEL = "delivery_channel"

class OTPScreen : Fragment() {
    private var phoneNumber: String? = null
    private var deliveryChannel: String? = null

    private lateinit var otpEditText: EditText
    private lateinit var submitButton: Button
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
        boxLoader = view.findViewById(R.id.otp_box_loader)
        statusText = view.findViewById(R.id.otp_status_text)
        phoneDisplay = view.findViewById(R.id.phone_display)
        OtplessSDK.setResponseCallback(this::onOtplessResponse)

        phoneDisplay.text = "Sending... OTP \uD83D\uDCAC to +91 $phoneNumber on $deliveryChannel"


        submitButton.setOnClickListener {
           verifyOTP()
        }
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
                        hideLoader()
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
    fun handleVerifyError(response: OtplessResponse) {
        val errorCode = response.response?.get("errorCode") as? String
        val errorMessage = response.response?.get("errorMessage") as? String
        Toast.makeText(context, "⚠ $errorMessage", Toast.LENGTH_SHORT).show()
        when (errorCode) {
            "7112" -> {
                // Handle request error: Empty OTP
                println("OTPless Error: $errorMessage")
            }

            "7115" -> {
                // Handle request error: OTP is already verified
                println("OTPless Error: $errorMessage")
            }

            "7118" -> {
                // Handle request error: Incorrect OTP
                println("OTPless Error: $errorMessage")
            }

            "7303" -> {
                // Handle request error: OTP expired
                println("OTPless Error: $errorMessage")
            }

            "4000" -> {
                // Handle invalid request
                println("OTPless Error: $errorMessage")
            }

            "9100", "9104", "9103" -> {
                // Handle network error:
                println("OTPless Error: $errorMessage")
            }

            else -> {
                // Handle unknown error
                println("OTPless Error: $errorMessage")
            }
        }
    }

    private fun showLoader(message: String) {
        boxLoader.visibility = View.VISIBLE
        statusText.text = message
        statusText.visibility = View.VISIBLE
        submitButton.isEnabled = false
        otpEditText.isEnabled = false
    }

    private fun hideLoader() {
        boxLoader.visibility = View.GONE
        statusText.text = ""
        statusText.visibility = View.GONE
        submitButton.isEnabled = true
        otpEditText.isEnabled = true
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