package com.banglu.keyboard

import android.content.Context
import com.banglu.keyboard.account.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class BackendSyncResult(
    val success: Boolean,
    val message: String,
    val syncedCustomConversions: Int = 0
)

class BackendSyncClient(context: Context) {
    private val appContext = context.applicationContext

    init {
        BangluProcessGuards.requireUiProcess(appContext, "BackendSyncClient")
    }

    private val prefs = appContext.getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE)
    private val storage = AndroidStorage(appContext)
    private val sessionStore = AuthSessionStore(appContext)
    private val authClient = MobileAuthClient(appContext)

    suspend fun syncNow(): BackendSyncResult = withContext(Dispatchers.IO) {
        val session = sessionStore.current()
            ?: return@withContext BackendSyncResult(false, "Login করলে sync চালু হবে")

        runCatching {
            val customConversions = storage.getCustomConversions()
            val payload = JSONObject()
                .put("deviceId", sessionStore.deviceId())
                .put(
                    "entitlement",
                    JSONObject()
                        .put("plan", prefs.getString("subscription_plan", "free"))
                        .put("source", prefs.getString("subscription_source", "none"))
                        .put("productId", prefs.getString("subscription_product_id", null))
                        .put("purchaseToken", prefs.getString("subscription_purchase_token", null))
                        .put("checkedAt", prefs.getLong("subscription_checked_at", 0L))
                )
                .put(
                    "customConversions",
                    JSONArray().apply {
                        customConversions.forEach { item ->
                            put(
                                JSONObject()
                                    .put("phonetic", item.phonetic)
                                    .put("bengali", item.bengali)
                                    .put("createdAt", item.createdAt)
                            )
                        }
                    }
                )

            var response = postSync(payload, session.accessToken)
            if (response.code == 401) {
                val refreshResult = authClient.refresh()
                val refreshedToken = refreshResult.session?.accessToken ?: sessionStore.current()?.accessToken
                if (refreshResult.success && !refreshedToken.isNullOrBlank()) {
                    response = postSync(payload, refreshedToken)
                }
            }

            if (response.code !in 200..299) {
                prefs.edit().putString("last_backend_sync_error", response.body.ifBlank { "HTTP ${response.code}" }).apply()
                return@withContext BackendSyncResult(false, "Sync failed: HTTP ${response.code}")
            }

            val json = JSONObject(response.body.ifBlank { "{}" })
            val data = json.optJSONObject("data")
            val synced = data?.optInt("syncedCustomConversions", customConversions.size) ?: customConversions.size
            prefs.edit()
                .putLong("last_backend_sync_at", System.currentTimeMillis())
                .remove("last_backend_sync_error")
                .apply()
            BackendSyncResult(true, "Backend sync complete", synced)
        }.getOrElse { error ->
            prefs.edit().putString("last_backend_sync_error", error.message ?: error::class.java.simpleName).apply()
            BackendSyncResult(false, error.message ?: "Backend sync failed")
        }
    }

    private fun postSync(payload: JSONObject, accessToken: String): HttpResponse {
        val endpoint = BuildConfig.BANGLU_API_BASE_URL.trimEnd('/') + "/api/mobile/sync"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Banglu-Client", "android-keyboard")
            setRequestProperty("Authorization", "Bearer $accessToken")
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
