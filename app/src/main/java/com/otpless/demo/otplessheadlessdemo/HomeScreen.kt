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

        submit.setOnClickListener {
            this.showLoader("Initiating request...\uD83D\uDD10")
            val phone = etPhoneNumber.text.toString().trim()
            val otplessRequest = OtplessRequest()
            otplessRequest.setPhoneNumber(phone, "+91")
            lifecycleScope.launch {
                OtplessSDK.start(request = otplessRequest, callback = this@HomeScreen::onOtplessResponse)
            }
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
                        hideLoader()
                        if (response.statusCode == 200) {
                            Toast.makeText(context, " Request initiated✅ for $authType", Toast.LENGTH_LONG).show()
                        } else
                        {
                          handleInitiateError(response)
                        }

                    }
                }
            }

            ResponseTypes.VERIFY -> {
                if (authType == "SILENT_AUTH") {
                    if (response.statusCode == 9106){
                        hideLoader()
                        Toast.makeText(context, "Silent auth session failed", Toast.LENGTH_LONG).show()
                    } else {
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
        val errorCode = response.response?.get("errorCode") as? String
        val errorMessage = response.response?.get("errorMessage") as? String
        Toast.makeText(context,"⚠ $errorMessage", Toast.LENGTH_SHORT).show()
        when (errorCode) {
            "7101" -> {
                // Handle request error: Invalid parameters values or missing parameters
                println("OTPless Error: $errorMessage")
            }

            "7102" -> {
                // Handle request error: Invalid phone number
                println("OTPless Error: $errorMessage")
            }

            "7103" -> {
                // Handle request error: Invalid phone number delivery channel
                println("OTPless Error: $errorMessage")
            }

            "7104" -> {
                // Handle request error: Invalid email
                println("OTPless Error: $errorMessage")
            }

            "7105" -> {
                // Handle request error: Invalid email channel
                println("OTPless Error: $errorMessage")
            }

            "7106" -> {
                // Handle request error: Invalid phone number or email
                println("OTPless Error: $errorMessage")
            }

            "7113" -> {
                // Handle request error: Invalid expiry
                println("OTPless Error: $errorMessage")
            }

            "7116" -> {
                // Handle request error: OTP Length is invalid (4 or 6 only allowed)
                println("OTPless Error: $errorMessage")
            }

            "7121" -> {
                // Handle request error: Invalid app hash
                println("OTPless Error: $errorMessage")
            }

            "4000" -> {
                // Handle invalid request values
                println("OTPless Error: $errorMessage")
            }

            "4003" -> {
                // Handle incorrect request channel
                println("OTPless Error: $errorMessage")
            }

            "401", "7025" -> {
                // Handle unauthorized request or country not enabled
                println("OTPless Error: $errorMessage")
            }

            "7020", "7022", "7023", "7024" -> {
                // Handle rate limiting errors (Too many requests)
                println("OTPless Error: $errorMessage")
            }

            "9100", "9104", "9103" -> {
                // Handle network connectivity errors
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
        submit.isEnabled = false
        etPhoneNumber.isEnabled = false
    }

    private fun hideLoader() {
        boxLoader.visibility = View.GONE
        statusText.text = ""
        statusText.visibility = View.GONE
        submit.isEnabled = true
        etPhoneNumber.isEnabled = true
    }

}