package com.otpless.demo.otplessheadlessdemo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.otpless.v2.android.sdk.dto.OtplessResponse
import com.otpless.v2.android.sdk.main.OtplessSDK
import kotlinx.coroutines.launch

private const val ARG_TOKEN = "token"

/**
 * Shown once OTPless delivers the ONETAP token. Makes success unambiguous (the
 * previous flow only toasted+copied silently) and offers a logout path that runs
 * OtplessSDK.cleanup() + re-initialize so the demo can be re-tested without a
 * process restart.
 */
class SuccessScreen : Fragment() {

    private var token: String? = null
    private lateinit var loaderContainer: View
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = arguments?.getString(ARG_TOKEN)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_success_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tokenText = view.findViewById<TextView>(R.id.token_text)
        val copyButton = view.findViewById<View>(R.id.copy_token_button)
        val logoutButton = view.findViewById<View>(R.id.logout_button)
        loaderContainer = view.findViewById(R.id.loader_container)
        progressText = view.findViewById(R.id.progress_text)

        tokenText.text = token

        copyButton.setOnClickListener { copyTokenToClipboard() }
        logoutButton.setOnClickListener { logoutAndReset() }
    }

    private fun copyTokenToClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OTPless Token", token))
        Toast.makeText(requireContext(), "Token copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun logoutAndReset() {
        showLoader()
        lifecycleScope.launch {
            OtplessManager.logout(requireActivity(), this@SuccessScreen::onOtplessResponse)
            if (!isStateSaved) {
                parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, HomeScreen())
                    .commit()
                parentFragmentManager.executePendingTransactions()
            }
        }
    }

    private fun onOtplessResponse(response: OtplessResponse) {
        // HomeScreen attaches its own callback once it's shown; this only exists to
        // capture/log whatever the SDK emits during the re-initialize window.
        OtplessSDK.commit(response)
        OtplessLogger.logResponse("SuccessScreen(logout)", response)
    }

    private fun showLoader() {
        loaderContainer.visibility = View.VISIBLE
        progressText.text = "Logging out..."
    }

    companion object {
        @JvmStatic
        fun newInstance(token: String) = SuccessScreen().apply {
            arguments = Bundle().apply {
                putString(ARG_TOKEN, token)
            }
        }
    }
}
