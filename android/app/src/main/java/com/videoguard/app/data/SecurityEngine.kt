package com.videoguard.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SecurityEngine {

    // Official AURALIX Release Keystore SHA-256 Fingerprint
    private const val EXPECTED_SIGNATURE_SHA256 =
        "D7FBA75A6D995D7C6971F8C6ED661815CF2AD21E018C9FC746BE082392A1B3E4"

    // Cryptographic Internal Salt for HMAC integrity
    private const val MASTER_INTEGRITY_SALT =
        "AURALIX_SECURE_KERNEL_SALT_2026_9882347102948712398471"

    @Volatile
    var isTampered: Boolean = false
        private set

    /**
     * Verifies that the app package has not been tampered, modified, or re-signed.
     */
    fun verifyAppIntegrity(context: Context): Boolean {
        if (!verifySigningCertificate(context)) {
            isTampered = true
            return false
        }
        if (detectFridaOrInstrumentation()) {
            isTampered = true
            return false
        }
        if (detectActiveDebugger()) {
            isTampered = true
            return false
        }
        return true
    }

    /**
     * Validates that the APK signing certificate matches the authentic AURALIX keystore.
     * Prevents apktool decompilation, smali patching, and re-signing attacks.
     */
    fun verifySigningCertificate(context: Context): Boolean {
        try {
            val pm = context.packageManager
            val pkgName = context.packageName

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else null
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES).signatures
            }

            if (signatures == null || signatures.isEmpty()) {
                return false
            }

            for (sig in signatures) {
                val certBytes = sig.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(certBytes)
                val sha256 = digest.joinToString("") { "%02X".format(it) }

                if (sha256.equals(EXPECTED_SIGNATURE_SHA256, ignoreCase = true)) {
                    return true
                }
            }
            return false
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Detects Frida, Xposed, Substrate, and dynamic memory tampering.
     */
    fun detectFridaOrInstrumentation(): Boolean {
        // 1. Scan memory mappings for injected dynamic libraries
        try {
            val mapsFile = File("/proc/${Process.myPid()}/maps")
            if (mapsFile.exists()) {
                BufferedReader(FileReader(mapsFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val lower = line?.lowercase() ?: continue
                        if (lower.contains("frida") ||
                            lower.contains("gadget") ||
                            lower.contains("xposed") ||
                            lower.contains("substrate") ||
                            lower.contains("gum-js-loop")
                        ) {
                            return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Scan standard Frida server binaries on disk
        val suspiciousPaths = arrayOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/data/local/tmp/frida-agent.so",
            "/data/local/tmp/frida-agent-64.so",
            "/data/local/tmp/frida-agent-32.so"
        )
        for (path in suspiciousPaths) {
            try {
                if (File(path).exists()) return true
            } catch (_: Exception) {}
        }

        // 3. Check for standard Frida loopback server port 27042
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 27042), 40)
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Checks if a live debugger is attached to intercept or bypass auth variables.
     */
    fun detectActiveDebugger(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    /**
     * Generates a tamper-proof HMAC-SHA256 signature for user session state.
     */
    fun computeSessionHmac(
        username: String,
        expiry: String,
        pkg: String,
        deviceHwid: String
    ): String {
        try {
            val data = "$username|$expiry|$pkg|$deviceHwid"
            val keySpec = SecretKeySpec(MASTER_INTEGRITY_SALT.toByteArray(Charsets.UTF_8), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(keySpec)
            val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            return hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }
}
