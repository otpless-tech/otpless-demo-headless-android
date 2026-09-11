package com.otpless.demo.otplessheadlessdemo

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.otpless.v2.android.sdk.dto.OtplessResponse
import com.otpless.v2.android.sdk.dto.ResponseTypes
import com.otpless.v2.android.sdk.main.OtplessSDK
import kotlinx.coroutines.launch
import org.json.JSONObject


class MainActivity : AppCompatActivity() {

    private lateinit var logHeader: TextView
    private lateinit var logClear: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var logText: TextView
    private var isLogExpanded = false

    private val logListener: (LogEntry) -> Unit = { entry ->
        runOnUiThread { appendLogEntry(entry) }
    }
    private val logClearListener: () -> Unit = {
        runOnUiThread { logText.text = "" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        logHeader = findViewById(R.id.log_header)
        logClear = findViewById(R.id.log_clear)
        logScroll = findViewById(R.id.log_scroll)
        logText = findViewById(R.id.log_text)
        OtplessLogger.history().forEach { appendLogEntry(it) }
        logHeader.setOnClickListener { toggleLogConsole() }
        logClear.setOnClickListener { OtplessLogger.clear() }

        lifecycleScope.launch {
            OtplessManager.initialize(this@MainActivity, this@MainActivity::onOtplessResponse)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeScreen())
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        OtplessLogger.addListener(logListener)
        OtplessLogger.addClearListener(logClearListener)
    }

    override fun onStop() {
        super.onStop()
        OtplessLogger.removeListener(logListener)
        OtplessLogger.removeClearListener(logClearListener)
    }

    private fun toggleLogConsole() {
        isLogExpanded = !isLogExpanded
        val visibility = if (isLogExpanded) View.VISIBLE else View.GONE
        logScroll.visibility = visibility
        logClear.visibility = visibility
        logHeader.text = if (isLogExpanded) "Event log  ▾" else "Event log  ▸"
    }

    private fun appendLogEntry(entry: LogEntry) {
        logText.append(formatEntry(entry))
        logText.append("\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    /** Renders one [LogEntry] as: dim "time · source" header, a colored response-type
     * badge (or plain info text), pretty-printed JSON body, then a divider line. */
    private fun formatEntry(entry: LogEntry): CharSequence {
        val sb = SpannableStringBuilder()

        val headerStart = sb.length
        sb.append(if (entry.source != null) "${entry.timestamp}  ·  ${entry.source}" else entry.timestamp)
        sb.setSpan(ForegroundColorSpan(Color.parseColor("#8B98A5")), headerStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("\n")

        if (entry.responseType != null) {
            val badgeStart = sb.length
            val statusSuffix = entry.statusCode?.let { "  ·  $it" } ?: ""
            sb.append("● ${entry.responseType}$statusSuffix")
            val color = colorForResponseType(entry.responseType)
            sb.setSpan(ForegroundColorSpan(color), badgeStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), badgeStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            entry.data?.let { raw ->
                sb.append("\n")
                val dataStart = sb.length
                sb.append(prettyJson(raw))
                sb.setSpan(ForegroundColorSpan(Color.parseColor("#AAB4BD")), dataStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            val start = sb.length
            sb.append("ℹ ${entry.text}")
            sb.setSpan(ForegroundColorSpan(Color.parseColor("#7FE3A0")), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        sb.append("\n")
        val dividerStart = sb.length
        sb.append("─".repeat(36))
        sb.setSpan(ForegroundColorSpan(Color.parseColor("#2A2F36")), dividerStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    private fun colorForResponseType(type: String): Int = when (type) {
        "SDK_READY", "ONETAP" -> Color.parseColor("#5FD98A")
        "FAILED" -> Color.parseColor("#FF6B6B")
        "INITIATE" -> Color.parseColor("#FFB454")
        "VERIFY" -> Color.parseColor("#5AC8FA")
        "DELIVERY_STATUS" -> Color.parseColor("#4FD1C5")
        "FALLBACK_TRIGGERED" -> Color.parseColor("#FFA657")
        "OTP_AUTO_READ" -> Color.parseColor("#C792EA")
        else -> Color.LTGRAY
    }

    private fun prettyJson(raw: String): String = try {
        JSONObject(raw).toString(2)
    } catch (e: Exception) {
        raw
    }

    private fun onOtplessResponse(response: OtplessResponse) {
        OtplessSDK.commit(response)
        OtplessLogger.logResponse("MainActivity", response)
        if (response.responseType == ResponseTypes.SDK_READY) {
            Toast.makeText(this, "SDK is ready 🥳", Toast.LENGTH_LONG).show()
            return
        }
        if (response.responseType == ResponseTypes.FAILED) {
            Toast.makeText(this, "SDK Failed to load check your network or App id provided for init..", Toast.LENGTH_LONG).show()
            return
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch {
            OtplessSDK.onNewIntent(intent)
        }
    }
}
