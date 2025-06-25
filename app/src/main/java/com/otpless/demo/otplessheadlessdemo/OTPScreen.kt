package com.otpless.demo.otplessheadlessdemo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.otpless.v2.android.sdk.dto.OtplessRequest
import com.otpless.v2.android.sdk.dto.OtplessResponse
import com.otpless.v2.android.sdk.dto.ResponseTypes
import com.otpless.v2.android.sdk.main.OtplessSDK
import com.otpless.v2.android.sdk.main.OtplessSDK.startAsync
import kotlinx.coroutines.launch


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val ARG_PARAM3 = "param3"

/**
 * A simple [Fragment] subclass.
 * Use the [OTPScreen.newInstance] factory method to
 * create an instance of this fragment.
 */
class OTPScreen : Fragment() {
    private var phoneNumber: String? = null
    private var deliveryChannel: String? = null
    private var responseJson: String? = null

    private lateinit var otpEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var loaderContainer: View
    private lateinit var progressText: TextView
    private lateinit var phoneDisplay: TextView
    private lateinit var channelText: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            phoneNumber = it.getString(ARG_PARAM1)
            responseJson = it.getString(ARG_PARAM2)
            deliveryChannel = it.getString(ARG_PARAM3)
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
        loaderContainer = view.findViewById(R.id.loader_container)
        progressText = view.findViewById(R.id.progress_text)
        phoneDisplay = view.findViewById(R.id.phone_display)
        channelText = view.findViewById(R.id.otp_channel_text)
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
        Log.d("OTPLESS", response.toString())

        val context = requireContext()
        val authType = response.response?.optString("authType")

        when (response.responseType) {

            ResponseTypes.VERIFY -> {
                if (authType == "OTP") {
                    if (response.statusCode != 200){
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
                        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("OTPless Token", token)
                        clipboard.setPrimaryClip(clip)

                        // Single toast message
                        Toast.makeText(context, "Token received \uD83D\uDC4D and copied to clipboard:\n$token", Toast.LENGTH_LONG).show()
                        // Clear the entire back stack
                        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

                        // Navigate to HomeScreen (fresh)
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, HomeScreen())
                            .commit()
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
        loaderContainer.visibility = View.VISIBLE
        progressText.text = message
        submitButton.isEnabled = false
    }

    private fun hideLoader() {
        loaderContainer.visibility = View.GONE
        progressText.text = ""
        submitButton.isEnabled = true
    }
    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment OTPScreen.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String,param3: String) =
            OTPScreen().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                    putString(ARG_PARAM3, param3)
                }
            }
    }
}