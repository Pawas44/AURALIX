package com.videoguard.app.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class AuthUser(
    val username: String,
    val expiryDate: String,
    val packageType: String
)

object AuthManager {
    private const val PREFS_NAME = "auralix_auth_prefs"
    private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
    private const val KEY_USERNAME = "key_username"
    private const val KEY_EXPIRY = "key_expiry"
    private const val KEY_PACKAGE = "key_package"

    private const val AUTH_URL = "https://backend-alpha-topaz-68.vercel.app/api/auth/"

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    /**
     * Initializes AuthManager from persistent SharedPreferences on app launch.
     * If user previously logged in, isLoggedIn will immediately be true.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (savedLoggedIn) {
            val username = prefs.getString(KEY_USERNAME, "") ?: ""
            val expiry = prefs.getString(KEY_EXPIRY, "") ?: ""
            val pkg = prefs.getString(KEY_PACKAGE, "Basic") ?: "Basic"
            _currentUser.value = AuthUser(username, expiry, pkg)
            _isLoggedIn.value = true
        } else {
            _isLoggedIn.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Encrypts plain device ID using Vigenere cipher matching backend decryptClientString
     */
    private fun encryptClientString(plain: String, randomKey: String): String {
        val password = randomKey + "45783944810"
        val sb = StringBuilder()
        for (i in plain.indices) {
            val charCode = plain[i].code
            if (charCode in 33..126) {
                val base = 32
                val pCode = password[i % password.length].code
                val encVal = ((charCode - base + pCode) % 95 + 95) % 95
                sb.append((encVal + base).toChar())
            } else {
                sb.append(plain[i])
            }
        }
        return sb.toString()
    }

    /**
     * Authenticates user against the hosted aimguardauth backend.
     */
    fun login(
        context: Context,
        usernameInput: String,
        passwordInput: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val username = usernameInput.trim()
        val password = passwordInput.trim()

        if (username.isBlank() || password.isBlank()) {
            val err = "Please enter both username and password"
            _errorMessage.value = err
            onResult(false, err)
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val androidHwid = try {
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                        ?: "AURALIX_DEV_${Build.SERIAL}"
                } catch (_: Exception) {
                    "AURALIX_DEV_${Build.MODEL}"
                }

                val randomKey = UUID.randomUUID().toString().replace("-", "").take(8)
                val encryptedDevId = encryptClientString(androidHwid, randomKey)
                val token = UUID.randomUUID().toString().replace("-", "").take(16)

                val postData = JSONObject().apply {
                    put("id", "login")
                    put("username", username)
                    put("password", password)
                    put("version", "2.0.0")
                    put("deviceid", encryptedDevId)
                    put("entryhash", randomKey)
                    put("token", token)
                    put("devicename", "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("deviceinfo", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                }

                val url = URL(AUTH_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("User-Agent", "AURALIX-Android")
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use {
                    it.write(postData.toString())
                    it.flush()
                }

                val responseCode = conn.responseCode
                val responseText = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                val json = JSONObject(responseText)
                val status = json.optBoolean("status", false)
                val message = json.optString("message", "Unknown authentication response")

                if (status) {
                    val dataObj = json.optJSONObject("data")
                    val expiry = dataObj?.optString("expiry_date", "") ?: ""
                    val pkg = dataObj?.optString("package", "Basic") ?: "Basic"

                    // Persist session locally so user NEVER has to log in again on future launches
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean(KEY_IS_LOGGED_IN, true)
                        .putString(KEY_USERNAME, username)
                        .putString(KEY_EXPIRY, expiry)
                        .putString(KEY_PACKAGE, pkg)
                        .apply()

                    val user = AuthUser(username, expiry, pkg)
                    _currentUser.value = user
                    _isLoggedIn.value = true
                    _isLoading.value = false
                    _errorMessage.value = null

                    // Start background services post-login
                    CoroutineScope(Dispatchers.IO).launch {
                        com.videoguard.app.data.GoogleDriveBackupEngine.checkAndAutoRestoreOnInstall(context)
                        com.videoguard.app.data.YouTubeSyncEngine.syncAllUserData()
                        com.videoguard.app.data.AppUpdateManager.checkForUpdates(context)
                        com.videoguard.app.data.AppUpdateManager.startPeriodicChecks(context)
                    }

                    withContext(Dispatchers.Main) {
                        onResult(true, null)
                    }
                } else {
                    _isLoading.value = false
                    val cleanMessage = when (message) {
                        "found_update" -> "Please update AURALIX to continue."
                        "Invalid username or password" -> "Invalid username or password. Please try again."
                        "Account banned" -> "Your account has been banned."
                        "Account disabled" -> "Your account is currently disabled."
                        "Subscription expired" -> "Your subscription has expired."
                        "Device not authorized" -> "This account is bound to another device. Reset HWID in control panel."
                        else -> message
                    }
                    _errorMessage.value = cleanMessage

                    withContext(Dispatchers.Main) {
                        onResult(false, cleanMessage)
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                val err = "Connection error: ${e.localizedMessage ?: "Could not connect to authentication server"}"
                _errorMessage.value = err
                withContext(Dispatchers.Main) {
                    onResult(false, err)
                }
            }
        }
    }

    /**
     * Clears local authentication state and returns to LoginScreen.
     */
    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        _currentUser.value = null
        _isLoggedIn.value = false
        _errorMessage.value = null
    }
}
