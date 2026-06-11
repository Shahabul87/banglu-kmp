package com.banglu.keyboard

import android.content.Context
import com.banglu.keyboard.account.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthResult(
    val success: Boolean,
    val message: String,
    val session: AuthSession? = null
)

class MobileAuthClient(context: Context) {
    private val appContext = context.applicationContext

    init {
        BangluProcessGuards.requireUiProcess(appContext, "MobileAuthClient")
    }

    private val sessionStore = AuthSessionStore(appContext)
    private val baseUrl = BuildConfig.BANGLU_API_BASE_URL.trimEnd('/')

    suspend fun login(email: String, password: String): AuthResult = authRequest(
        path = "/api/mobile/auth/login",
        payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("deviceId", sessionStore.deviceId())
            .put("deviceName", sessionStore.deviceName())
    )

    suspend fun register(name: String, email: String, password: String): AuthResult = authRequest(
        path = "/api/mobile/auth/register",
        payload = JSONObject()
            .put("name", name.trim())
            .put("email", email.trim())
            .put("password", password)
            .put("deviceId", sessionStore.deviceId())
            .put("deviceName", sessionStore.deviceName())
    )

    suspend fun loginWithGoogle(idToken: String): AuthResult = authRequest(
        path = "/api/mobile/auth/google",
        payload = JSONObject()
            .put("idToken", idToken)
            .put("deviceId", sessionStore.deviceId())
            .put("deviceName", sessionStore.deviceName())
    )

    suspend fun requestPasswordReset(email: String): AuthResult = withContext(Dispatchers.IO) {
        runCatching {
            val response = postJson(
                path = "/api/auth/forgot-password",
                payload = JSONObject().put("email", email.trim())
            )
            if (response.code !in 200..299) {
                return@withContext AuthResult(false, response.errorMessage())
            }
            AuthResult(true, "Reset link পাঠানো হয়েছে, ইমেইল দেখুন")
        }.getOrElse { error ->
            AuthResult(false, error.message ?: "Reset request failed")
        }
    }

    suspend fun refresh(): AuthResult = withContext(Dispatchers.IO) {
        val current = sessionStore.current() ?: return@withContext AuthResult(false, "Login দরকার")
        authRequest(
            path = "/api/mobile/auth/refresh",
            payload = JSONObject()
                .put("refreshToken", current.refreshToken)
                .put("deviceId", sessionStore.deviceId())
                .put("deviceName", sessionStore.deviceName())
        )
    }

    suspend fun logout(): AuthResult = withContext(Dispatchers.IO) {
        val refreshToken = sessionStore.current()?.refreshToken
        if (!refreshToken.isNullOrBlank()) {
            runCatching {
                postJson(
                    path = "/api/mobile/auth/logout",
                    payload = JSONObject().put("refreshToken", refreshToken)
                )
            }
        }
        sessionStore.clear()
        AuthResult(true, "Signed out")
    }

    suspend fun validAccessToken(): String? {
        val current = sessionStore.current() ?: return null
        return current.accessToken
    }

    private suspend fun authRequest(path: String, payload: JSONObject): AuthResult = withContext(Dispatchers.IO) {
        runCatching {
            val response = postJson(path, payload)
            if (response.code !in 200..299) {
                return@withContext AuthResult(false, response.errorMessage())
            }
            val root = JSONObject(response.body)
            if (!root.optBoolean("success")) {
                return@withContext AuthResult(false, root.optString("error", "Auth failed"))
            }
            val data = root.getJSONObject("data")
            val user = data.getJSONObject("user")
            val session = AuthSession(
                userId = user.getString("id"),
                name = user.optString("name", ""),
                email = user.optString("email", ""),
                accessToken = data.getString("accessToken"),
                refreshToken = data.getString("refreshToken")
            )
            sessionStore.save(session)
            AuthResult(true, "Login complete", session)
        }.getOrElse { error ->
            AuthResult(false, error.message ?: "Auth failed")
        }
    }

    private fun postJson(path: String, payload: JSONObject): HttpResponse {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Banglu-Client", "android-keyboard")
        }
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        connection.outputStream.use { it.write(body) }
        val code = connection.responseCode
        val text = if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()
        return HttpResponse(code, text)
    }
}

data class HttpResponse(val code: Int, val body: String) {
    fun errorMessage(): String {
        if (body.isBlank()) return "HTTP $code"
        return runCatching {
            val json = JSONObject(body)
            json.optString("error", "HTTP $code")
        }.getOrDefault("HTTP $code")
    }
}
