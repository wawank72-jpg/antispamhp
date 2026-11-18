package com.wawan.antisadap

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvSummaryStatus: TextView
    private lateinit var containerResults: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSummaryStatus = findViewById(R.id.tvSummaryStatus)
        containerResults = findViewById(R.id.containerResults)

        val btnScanNow: Button = findViewById(R.id.btnScanNow)
        val btnCheckForward: Button = findViewById(R.id.btnCheckForward)
        val btnKillForward: Button = findViewById(R.id.btnKillForward)

        btnScanNow.setOnClickListener {
            runSecurityAudit()
        }

        btnCheckForward.setOnClickListener {
            dialUssdCode("*#21#")
        }

        btnKillForward.setOnClickListener {
            dialUssdCode("##002#")
        }
    }

    private fun runSecurityAudit() {
        containerResults.removeAllViews()
        val pm = packageManager
        var suspiciousCount = 0

        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledAccessibility = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val activeAccessibilityPackages = enabledAccessibility.mapNotNull { it.resolveInfo?.serviceInfo?.packageName }.toSet()

        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        for (pkgInfo in packages) {
            val appInfo = pkgInfo.applicationInfo ?: continue
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            if (pkgInfo.packageName == packageName) continue

            val appName = pm.getApplicationLabel(appInfo).toString()
            val reasons = mutableListOf<String>()
            var riskScore = 0

            if (activeAccessibilityPackages.contains(pkgInfo.packageName)) {
                reasons.add("BAHAYA TINGGI: Menguasai Aksesibilitas Layar")
                riskScore += 60
            }

            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { pm.getInstallSourceInfo(pkgInfo.packageName).installingPackageName } catch (e: Exception) { null }
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkgInfo.packageName)
            }

            if (installer != "com.android.vending") {
                reasons.add("Diinstal di luar Play Store (Sumber: ${installer ?: "APK Manual"})")
                riskScore += 30
            }

            val perms = pkgInfo.requestedPermissions ?: emptyArray()
            if (perms.contains("android.permission.READ_SMS") || perms.contains("android.permission.RECEIVE_SMS")) {
                reasons.add("Meminta izin membaca SMS / OTP")
                riskScore += 25
            }
            if (perms.contains("android.permission.RECORD_AUDIO")) {
                reasons.add("Meminta izin merekam Mikrofon")
                riskScore += 15
            }
            if (perms.contains("android.permission.SYSTEM_ALERT_WINDOW")) {
                reasons.add("Meminta izin tampil di atas aplikasi lain (Overlay)")
                riskScore += 15
            }

            if (riskScore >= 30) {
                suspiciousCount++
                addResultCard(appName, pkgInfo.packageName, riskScore, reasons)
            }
        }

        if (suspiciousCount == 0) {
            tvSummaryStatus.text = "AMAN: Tidak ditemukan aplikasi spyware atau APK mencurigakan."
            tvSummaryStatus.setTextColor(Color.parseColor("#34D399"))
        } else {
            tvSummaryStatus.text = "PERINGATAN: Ditemukan $suspiciousCount aplikasi berisiko! Ketuk untuk hapus."
            tvSummaryStatus.setTextColor(Color.parseColor("#F87171"))
        }
    }

    private fun addResultCard(appName: String, pkgName: String, score: Int, reasons: List<String>) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(32, 28, 32, 28)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 20)
            layoutParams = params
        }

        val tvTitle = TextView(this).apply {
            text = "$appName (Skor Risiko: $score)"
            textSize = 15f
            setTextColor(if (score >= 60) Color.parseColor("#DC2626") else Color.parseColor("#D97706"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvPkg = TextView(this).apply {
            text = pkgName
            textSize = 11f
            setTextColor(Color.GRAY)
        }

        val tvReasons = TextView(this).apply {
            text = reasons.joinToString("\n• ", prefix = "• ")
            textSize = 12f
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 12, 0, 12)
        }

        val tvAction = TextView(this).apply {
            text = "Ketuk kartu ini untuk membuka Info Aplikasi & Uninstall →"
            textSize = 11f
            setTextColor(Color.parseColor("#4F46E5"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        card.addView(tvTitle)
        card.addView(tvPkg)
        card.addView(tvReasons)
        card.addView(tvAction)

        card.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkgName")
            }
            startActivity(intent)
        }

        containerResults.addView(card)
    }

    private fun dialUssdCode(code: String) {
        val encodedHash = Uri.encode("#")
        val ussd = code.replace("#", encodedHash)
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ussd"))
        startActivity(intent)
    }
}
