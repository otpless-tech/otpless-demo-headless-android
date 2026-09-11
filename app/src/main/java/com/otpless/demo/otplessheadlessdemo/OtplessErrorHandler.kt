package com.otpless.demo.otplessheadlessdemo

import com.otpless.v2.android.sdk.dto.OtplessResponse

/**
 * Groups the ~20 individual OTPless error codes (see the SDK's error-codes reference:
 * https://otpless.com/docs/frontend-sdks/app-sdks/android/new/references/error-codes)
 * into a handful of user-facing categories, so a screen reacts the same way to every
 * code in a category instead of switching on each one individually.
 */
enum class OtplessErrorCategory {
    VALIDATION,
    UNAUTHORIZED,
    RATE_LIMITED,
    NETWORK,
    OTP_INCORRECT,
    OTP_EXPIRED,
    OTP_ALREADY_VERIFIED,
    SERVER,
    UNKNOWN,
}

data class OtplessError(
    val category: OtplessErrorCategory,
    val code: String?,
    val message: String,
)

object OtplessErrorHandler {

    // Bad/missing request params - INITIATE only.
    private val VALIDATION_CODES = setOf(
        "7101", "7102", "7103", "7104", "7105", "7106", "7113", "7116", "7121", "4000", "4001", "4003"
    )
    private val UNAUTHORIZED_CODES = setOf("401", "7025")
    private val RATE_LIMIT_CODES = setOf("7020", "7022", "7023", "7024") // HTTP 429
    private val NETWORK_CODES = setOf("9100", "9103", "9104") // socket timeout / IO / unknown host
    private val SERVER_CODES = setOf("500", "5003")

    fun classify(response: OtplessResponse): OtplessError {
        val errorCode = response.response?.optString("errorCode")?.takeIf { it.isNotBlank() }
        val serverMessage = response.response?.optString("errorMessage")?.takeIf { it.isNotBlank() }

        val category = when (errorCode) {
            in VALIDATION_CODES -> OtplessErrorCategory.VALIDATION
            in UNAUTHORIZED_CODES -> OtplessErrorCategory.UNAUTHORIZED
            in RATE_LIMIT_CODES -> OtplessErrorCategory.RATE_LIMITED
            in NETWORK_CODES -> OtplessErrorCategory.NETWORK
            "7112", "7118" -> OtplessErrorCategory.OTP_INCORRECT // empty / incorrect OTP
            "7303" -> OtplessErrorCategory.OTP_EXPIRED
            "7115" -> OtplessErrorCategory.OTP_ALREADY_VERIFIED
            in SERVER_CODES -> OtplessErrorCategory.SERVER
            else -> OtplessErrorCategory.UNKNOWN
        }

        val message = when (category) {
            OtplessErrorCategory.VALIDATION -> "That request wasn't valid. Double-check the number and try again."
            OtplessErrorCategory.UNAUTHORIZED -> "Not authorized - check your App ID or this channel's setup for the number."
            OtplessErrorCategory.RATE_LIMITED -> "Too many attempts. Please wait a bit before trying again."
            OtplessErrorCategory.NETWORK -> "Network issue reaching OTPless. Check your connection and try again."
            OtplessErrorCategory.OTP_INCORRECT -> "That OTP doesn't look right. Please re-check and try again."
            OtplessErrorCategory.OTP_EXPIRED -> "This OTP has expired. Resend a new one."
            OtplessErrorCategory.OTP_ALREADY_VERIFIED -> "This OTP was already used."
            OtplessErrorCategory.SERVER -> "Something went wrong on our end. Please try again."
            OtplessErrorCategory.UNKNOWN -> serverMessage ?: "Something went wrong."
        }

        return OtplessError(category, errorCode, message)
    }
}
