package com.banglu.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.banglu.keyboard.account.BuildConfig
import com.banglu.engine.SmartEngineAdapter
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private var AccountPrimary = Color(0xFF64D2FF)
private var AccountSuccess = Color(0xFF30D158)
private var AccountCoral = Color(0xFFFF453A)
private var AccountBg = Color(0xFF080D16)
private var AccountBg2 = Color(0xFF111827)
private var AccountSurface = Color(0xFF182235)
private var AccountBorder = Color(0xFF27364F)
private var AccountMuted = Color(0xFFA8B3C7)
private var AccountText = Color(0xFFF8FAFC)

private enum class AccountDashboardPage(
    val bengali: String,
    val english: String,
    val icon: String
) {
    Home("হোমপেজ", "Home", "⌂"),
    Dashboard("ড্যাশবোর্ড", "Dashboard", "▦"),
    Dictionary("আমার অভিধান", "My Dictionary", "▭"),
    AiWriter("AI Writer", "Writing tools", "⚡"),
    Editor("এডিটর", "Editor", "✎"),
    Documents("আমার নথি", "Documents", "▤"),
    Saved("সংরক্ষিত", "Saved", "☆"),
    Stats("পরিসংখ্যান", "Stats", "▥"),
    Tutorial("টিউটোরিয়াল", "Tutorial", "?"),
    Subscription("সাবস্ক্রিপশন", "Subscription", "P"),
    Profile("প্রোফাইল", "Profile", "♙"),
    Settings("সেটিংস", "Settings", "⚙")
}

private fun applyAccountPalette(dark: Boolean) {
    if (dark) {
        AccountPrimary = Color(0xFF64D2FF)
        AccountSuccess = Color(0xFF30D158)
        AccountCoral = Color(0xFFFF453A)
        AccountBg = Color(0xFF080D16)
        AccountBg2 = Color(0xFF111827)
        AccountSurface = Color(0xFF182235)
        AccountBorder = Color(0xFF27364F)
        AccountMuted = Color(0xFFA8B3C7)
        AccountText = Color(0xFFF8FAFC)
    } else {
        AccountPrimary = Color(0xFF0A84FF)
        AccountSuccess = Color(0xFF34C759)
        AccountCoral = Color(0xFFFF3B30)
        AccountBg = Color(0xFFF8FAFF)
        AccountBg2 = Color(0xFFEFF3F8)
        AccountSurface = Color(0xFFFFFFFF)
        AccountBorder = Color(0xFFE4E8F0)
        AccountMuted = Color(0xFF6B7280)
        AccountText = Color(0xFF111827)
    }
}

class AccountActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setBangluContent { BangluAccountScreen(onBack = { finish() }) }
    }
}

@Composable
fun BangluAccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE) }
    val storage = remember { AndroidStorage(context.applicationContext) }
    var billingManager by remember { mutableStateOf<BillingEntitlementManager?>(null) }
    val backendSyncClient = remember { lazy(LazyThreadSafetyMode.NONE) { BackendSyncClient(context.applicationContext) } }
    val mobileAuthClient = remember { lazy(LazyThreadSafetyMode.NONE) { MobileAuthClient(context.applicationContext) } }
    val coroutineScope = rememberCoroutineScope()

    val themeMode = prefs.getString("theme", "dark") ?: "dark"
    val darkTheme = themeMode == "dark" || themeMode == "amoled" || (themeMode == "auto" && isSystemInDarkTheme())
    applyAccountPalette(darkTheme)
    SideEffect {
        val activity = context as? Activity ?: return@SideEffect
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = if (darkTheme) android.graphics.Color.rgb(8, 13, 22) else android.graphics.Color.rgb(248, 250, 255)
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = if (darkTheme) android.graphics.Color.rgb(8, 13, 22) else android.graphics.Color.rgb(246, 247, 251)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = if (darkTheme) {
            0
        } else {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(if (darkTheme) 0 else mask, mask)
        }
    }

    var activeTab by remember { mutableStateOf("login") }
    var authName by remember { mutableStateOf(prefs.getString("auth_name", "") ?: "") }
    var authEmail by remember { mutableStateOf(prefs.getString("auth_email", "") ?: "") }
    var authUserId by remember { mutableStateOf(prefs.getString("auth_user_id", "") ?: "") }
    var subscriptionPlan by remember { mutableStateOf(prefs.getString("subscription_plan", "free") ?: "free") }
    var billingState by remember {
        mutableStateOf(
            BillingEntitlementState(
                proActive = subscriptionPlan == "pro",
                message = if (subscriptionPlan == "pro") "Pro entitlement cached" else "Free plan"
            )
        )
    }
    var syncMessage by remember { mutableStateOf(prefs.getString("last_backend_sync_error", "") ?: "") }
    var lastSyncAt by remember { mutableStateOf(prefs.getLong("last_backend_sync_at", 0L)) }
    var loginEmail by remember { mutableStateOf(authEmail) }
    var loginPassword by remember { mutableStateOf("") }
    var registerName by remember { mutableStateOf(authName) }
    var registerEmail by remember { mutableStateOf(authEmail) }
    var registerPassword by remember { mutableStateOf("") }
    var registerConfirm by remember { mutableStateOf("") }
    var authMessage by remember { mutableStateOf("") }
    var customPhonetic by remember { mutableStateOf("") }
    var customBengali by remember { mutableStateOf("") }
    var customConversions by remember { mutableStateOf(storage.getCustomConversions()) }

    val isSignedIn = authUserId.isNotBlank() && authEmail.isNotBlank()
    val isSubscribed = subscriptionPlan == "pro"
    val openSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
    val openTutorial = { context.startActivity(Intent(context, TutorialActivity::class.java)) }

    fun applyAuthSession(session: AuthSession) {
        authUserId = session.userId
        authEmail = session.email
        authName = session.name
        customConversions = storage.getCustomConversions()
    }

    val googleWebClientId = BuildConfig.BANGLU_GOOGLE_WEB_CLIENT_ID

    fun ensureBillingManager(): BillingEntitlementManager {
        return billingManager ?: BillingEntitlementManager(context.applicationContext).also { manager ->
            billingManager = manager
        }
    }

    fun startGoogleSignIn() {
        if (googleWebClientId.isBlank()) {
            authMessage = "Google Client ID সেট করা নেই"
            return
        }
        authMessage = "Google খুলছে..."
        coroutineScope.launch {
            runCatching {
                val googleOption = GetSignInWithGoogleOption.Builder(googleWebClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()
                val credentialManager = CredentialManager.create(context)
                val response = credentialManager.getCredential(context, request)
                val credential = response.credential
                if (credential is CustomCredential &&
                    (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
                ) {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                } else {
                    throw IllegalStateException("Unsupported Google credential type")
                }
            }.onSuccess { idToken ->
                if (idToken.isBlank()) {
                    authMessage = "Google token পাওয়া যায়নি"
                    return@onSuccess
                }
                authMessage = "Google login হচ্ছে..."
                val result = mobileAuthClient.value.loginWithGoogle(idToken)
                authMessage = result.message
                result.session?.let { session ->
                    applyAuthSession(session)
                    syncMessage = backendSyncClient.value.syncNow().message
                    lastSyncAt = prefs.getLong("last_backend_sync_at", System.currentTimeMillis())
                }
            }.onFailure { error ->
                authMessage = when (error) {
                    is GetCredentialCancellationException -> "Google login বাতিল হয়েছে"
                    is GoogleIdTokenParsingException -> "Google token পড়তে সমস্যা হয়েছে"
                    is GetCredentialException -> error.message ?: "Google login failed"
                    else -> error.message ?: "Google login failed"
                }
            }
        }
    }

    DisposableEffect(billingManager) {
        val manager = billingManager
        if (manager == null) {
            onDispose { }
        } else {
            manager.setListener { state ->
                billingState = state
                subscriptionPlan = if (state.proActive) "pro" else "free"
                if (!state.loading && isSignedIn) {
                    coroutineScope.launch {
                        syncMessage = backendSyncClient.value.syncNow().message
                        lastSyncAt = prefs.getLong("last_backend_sync_at", System.currentTimeMillis())
                    }
                }
            }
            onDispose { manager.close() }
        }
    }

    var activeDashboardPage by remember { mutableStateOf(AccountDashboardPage.Dashboard) }
    var sidebarOpen by remember { mutableStateOf(false) }
    var userMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AccountBg, AccountBg2)))
    ) {
        if (isSignedIn) {
            AccountDashboardShell(
                activePage = activeDashboardPage,
                authName = authName,
                authEmail = authEmail,
                subscriptionPlan = subscriptionPlan,
                syncMessage = syncMessage,
                lastSyncAt = lastSyncAt,
                customWordCount = customConversions.size,
                proActive = billingState.proActive,
                sidebarOpen = sidebarOpen,
                userMenuOpen = userMenuOpen,
                customPhonetic = customPhonetic,
                customBengali = customBengali,
                customConversions = customConversions,
                billingState = billingState,
                onBack = onBack,
                onToggleSidebar = { sidebarOpen = !sidebarOpen },
                onToggleUserMenu = { userMenuOpen = !userMenuOpen },
                onCloseUserMenu = { userMenuOpen = false },
                onSelectPage = { page ->
                    activeDashboardPage = page
                    sidebarOpen = false
                    userMenuOpen = false
                },
                onSync = {
                    coroutineScope.launch {
                        syncMessage = backendSyncClient.value.syncNow().message
                        lastSyncAt = prefs.getLong("last_backend_sync_at", System.currentTimeMillis())
                    }
                },
                onOpenSettings = openSettings,
                onOpenTutorial = openTutorial,
                onCustomPhoneticChange = { customPhonetic = it.lowercase().filter { ch -> ch in 'a'..'z' } },
                onCustomBengaliChange = { customBengali = it },
                onSaveCustomConversion = {
                    val key = customPhonetic.trim()
                    val value = customBengali.trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        storage.saveCustomConversion(key, value)
                        SmartEngineAdapter.addCustomConversion(key, value)
                        customConversions = storage.getCustomConversions()
                        customPhonetic = ""
                        customBengali = ""
                        coroutineScope.launch {
                            syncMessage = backendSyncClient.value.syncNow().message
                            lastSyncAt = prefs.getLong("last_backend_sync_at", System.currentTimeMillis())
                        }
                    }
                },
                onSaveCustomPair = { keyRaw, valueRaw ->
                    val key = keyRaw.trim().lowercase().filter { ch -> ch in 'a'..'z' || ch == ',' }
                    val value = valueRaw.trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        key.split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { variant ->
                                storage.saveCustomConversion(variant, value)
                                SmartEngineAdapter.addCustomConversion(variant, value)
                        }
                        customConversions = storage.getCustomConversions()
                        coroutineScope.launch {
                            syncMessage = backendSyncClient.value.syncNow().message
                            lastSyncAt = prefs.getLong("last_backend_sync_at", System.currentTimeMillis())
                        }
                    }
                },
                onDeleteCustomConversion = { item ->
                    storage.deleteCustomConversion(item.phonetic, item.bengali)
                    customConversions = storage.getCustomConversions()
                    coroutineScope.launch {
                        syncMessage = backendSyncClient.value.syncNow().message
                        lastSyncAt = prefs.getLong("last_backend_sync_at", System.currentTimeMillis())
                    }
                },
                onUpgrade = {
                    val activity = context as? Activity ?: return@AccountDashboardShell
                    val manager = ensureBillingManager()
                    manager.loadProductDetails { details ->
                        manager.launchPurchase(activity, details)
                    }
                },
                onRestore = { ensureBillingManager().refreshEntitlement() },
                onSignOut = {
                    coroutineScope.launch {
                        runCatching {
                            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                        }
                        val result = mobileAuthClient.value.logout()
                        authUserId = ""
                        authName = ""
                        authEmail = ""
                        subscriptionPlan = "free"
                        lastSyncAt = 0L
                        customConversions = storage.getCustomConversions()
                        authMessage = result.message
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item { AccountHeader(onBack) }
                item {
                    AccountCard {
                    Text(
                        "অ্যাকাউন্ট",
                        color = AccountText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Login বা registration করুন",
                        color = AccountMuted,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    AccountTabRow(activeTab) { activeTab = it }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (activeTab == "login") {
                        OutlinedButton(
                            onClick = { startGoogleSignIn() },
                            enabled = googleWebClientId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("G", color = AccountPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Continue with Google", color = AccountText, fontSize = 16.sp)
                        }
                        if (googleWebClientId.isBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("local.properties এ BANGLU_GOOGLE_WEB_CLIENT_ID দিন", color = AccountCoral, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        AccountField(loginEmail, { loginEmail = it }, "ইমেইল")
                        Spacer(modifier = Modifier.height(10.dp))
                        AccountField(loginPassword, { loginPassword = it }, "পাসওয়ার্ড", password = true)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                authMessage = "Login হচ্ছে..."
                                coroutineScope.launch {
                                    val result = mobileAuthClient.value.login(loginEmail, loginPassword)
                                    authMessage = result.message
                                    result.session?.let { session ->
                                        applyAuthSession(session)
                                        syncMessage = backendSyncClient.value.syncNow().message
                                        lastSyncAt = prefs.getLong("last_backend_sync_at", System.currentTimeMillis())
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccountPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Login", color = Color.White, fontSize = 16.sp)
                        }
                        TextButton(
                            onClick = {
                                if (loginEmail.isBlank()) {
                                    authMessage = "Reset link পাঠাতে ইমেইল দিন"
                                } else {
                                    authMessage = "Reset link পাঠানো হচ্ছে..."
                                    coroutineScope.launch {
                                        val result = mobileAuthClient.value.requestPasswordReset(loginEmail)
                                        authMessage = result.message
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("পাসওয়ার্ড ভুলে গেছেন?", color = AccountPrimary, fontSize = 15.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { startGoogleSignIn() },
                            enabled = googleWebClientId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("G", color = AccountPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Register with Google", color = AccountText, fontSize = 16.sp)
                        }
                        if (googleWebClientId.isBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("local.properties এ BANGLU_GOOGLE_WEB_CLIENT_ID দিন", color = AccountCoral, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        AccountField(registerName, { registerName = it }, "নাম")
                        Spacer(modifier = Modifier.height(10.dp))
                        AccountField(registerEmail, { registerEmail = it }, "ইমেইল")
                        Spacer(modifier = Modifier.height(10.dp))
                        AccountField(registerPassword, { registerPassword = it }, "পাসওয়ার্ড", password = true)
                        Spacer(modifier = Modifier.height(10.dp))
                        AccountField(registerConfirm, { registerConfirm = it }, "পাসওয়ার্ড নিশ্চিত করুন", password = true)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (registerPassword != registerConfirm) {
                                    authMessage = "পাসওয়ার্ড মিলছে না"
                                } else if (registerPassword.length < 8) {
                                    authMessage = "পাসওয়ার্ড কমপক্ষে ৮ অক্ষর দিন"
                                } else {
                                    authMessage = "Register হচ্ছে..."
                                    coroutineScope.launch {
                                        val result = mobileAuthClient.value.register(registerName, registerEmail, registerPassword)
                                        authMessage = result.message
                                        result.session?.let { session ->
                                            applyAuthSession(session)
                                            syncMessage = backendSyncClient.value.syncNow().message
                                            lastSyncAt = prefs.getLong("last_backend_sync_at", System.currentTimeMillis())
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccountPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Register", color = Color.White, fontSize = 16.sp)
                        }
                    }
                    if (authMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            authMessage,
                            color = if (authMessage.contains("failed", ignoreCase = true)) AccountCoral else AccountMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun AccountDashboardShell(
    activePage: AccountDashboardPage,
    authName: String,
    authEmail: String,
    subscriptionPlan: String,
    syncMessage: String,
    lastSyncAt: Long,
    customWordCount: Int,
    proActive: Boolean,
    sidebarOpen: Boolean,
    userMenuOpen: Boolean,
    customPhonetic: String,
    customBengali: String,
    customConversions: List<CustomConversion>,
    billingState: BillingEntitlementState,
    onBack: () -> Unit,
    onToggleSidebar: () -> Unit,
    onToggleUserMenu: () -> Unit,
    onCloseUserMenu: () -> Unit,
    onSelectPage: (AccountDashboardPage) -> Unit,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTutorial: () -> Unit,
    onCustomPhoneticChange: (String) -> Unit,
    onCustomBengaliChange: (String) -> Unit,
    onSaveCustomConversion: () -> Unit,
    onSaveCustomPair: (String, String) -> Unit,
    onDeleteCustomConversion: (CustomConversion) -> Unit,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AccountDashboardTopBar(
                activePage = activePage,
                authName = authName,
                onBack = onBack,
                onMenuClick = onToggleSidebar,
                onUserClick = onToggleUserMenu
            )
            AccountDashboardPageContent(
                page = activePage,
                authName = authName,
                authEmail = authEmail,
                subscriptionPlan = subscriptionPlan,
                syncMessage = syncMessage,
                lastSyncAt = lastSyncAt,
                customWordCount = customWordCount,
                proActive = proActive,
                customPhonetic = customPhonetic,
                customBengali = customBengali,
                customConversions = customConversions,
                billingState = billingState,
                onSelectPage = onSelectPage,
                onSync = onSync,
                onOpenSettings = onOpenSettings,
                onOpenTutorial = onOpenTutorial,
                onCustomPhoneticChange = onCustomPhoneticChange,
                onCustomBengaliChange = onCustomBengaliChange,
                onSaveCustomConversion = onSaveCustomConversion,
                onSaveCustomPair = onSaveCustomPair,
                onDeleteCustomConversion = onDeleteCustomConversion,
                onUpgrade = onUpgrade,
                onRestore = onRestore,
                onSignOut = onSignOut
            )
        }
        if (userMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onCloseUserMenu() }
            )
            AccountUserPopover(
                authName = authName,
                authEmail = authEmail,
                subscriptionPlan = subscriptionPlan,
                onProfile = {
                    onCloseUserMenu()
                    onSelectPage(AccountDashboardPage.Profile)
                },
                onSignOut = onSignOut,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 78.dp, end = 16.dp)
            )
        }
        if (sidebarOpen) {
            Row(modifier = Modifier.fillMaxSize()) {
                AccountSidebar(
                    activePage = activePage,
                    authName = authName,
                    authEmail = authEmail,
                    subscriptionPlan = subscriptionPlan,
                    onSelectPage = onSelectPage,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(308.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.42f))
                        .clickable { onToggleSidebar() }
                )
            }
        }
    }
}

@Composable
private fun AccountDashboardTopBar(
    activePage: AccountDashboardPage,
    authName: String,
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    onUserClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccountBg.copy(alpha = 0.96f))
            .border(1.dp, AccountBorder.copy(alpha = 0.55f), RoundedCornerShape(0.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AccountSurface)
                .border(1.dp, AccountBorder, RoundedCornerShape(14.dp))
                .clickable { onMenuClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("☰", color = AccountPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(AccountPrimary.copy(alpha = 0.16f))
                .border(1.dp, AccountPrimary.copy(alpha = 0.32f), RoundedCornerShape(13.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = AccountPrimary, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Image(
            painter = painterResource(id = R.drawable.banglu_logo),
            contentDescription = "Banglu logo",
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("বাংলু", color = AccountText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(activePage.bengali, color = AccountMuted, fontSize = 13.sp, lineHeight = 16.sp)
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(AccountSurface)
                .border(1.dp, AccountPrimary.copy(alpha = 0.36f), CircleShape)
                .clickable { onUserClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(userInitial(authName), color = AccountPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccountSidebar(
    activePage: AccountDashboardPage,
    authName: String,
    authEmail: String,
    subscriptionPlan: String,
    onSelectPage: (AccountDashboardPage) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .background(AccountSurface)
            .border(1.dp, AccountBorder, RoundedCornerShape(0.dp)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.banglu_logo),
                    contentDescription = "Banglu logo",
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("বাংলু", color = AccountText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Keyboard dashboard", color = AccountMuted, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AccountBg2)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccountPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(userInitial(authName), color = AccountPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(authName.ifBlank { "Banglu user" }, color = AccountText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(subscriptionPlan.replaceFirstChar { it.uppercase() }, color = AccountMuted, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        AccountDashboardPage.values().forEach { page ->
            item {
                AccountSidebarItem(
                    page = page,
                    selected = page == activePage,
                    badge = if (page == AccountDashboardPage.AiWriter) "NEW" else null,
                    onClick = { onSelectPage(page) }
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(authEmail, color = AccountMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun AccountSidebarItem(
    page: AccountDashboardPage,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AccountPrimary.copy(alpha = 0.24f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) AccountPrimary.copy(alpha = 0.42f) else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(page.icon, color = if (selected) AccountPrimary else AccountMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(page.bengali, color = AccountText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(page.english, color = AccountMuted, fontSize = 11.sp)
        }
        if (badge != null) {
            DashboardPill(badge, AccountSuccess)
        }
    }
}

@Composable
private fun AccountUserPopover(
    authName: String,
    authEmail: String,
    subscriptionPlan: String,
    onProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(286.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(AccountSurface)
            .border(1.dp, AccountPrimary.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccountPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(userInitial(authName), color = AccountPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(authName.ifBlank { "Banglu user" }, color = AccountText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(authEmail, color = AccountMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        DashboardPill(subscriptionPlan.replaceFirstChar { it.uppercase() }, AccountPrimary)
        Button(
            onClick = onProfile,
            colors = ButtonDefaults.buttonColors(containerColor = AccountPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("প্রোফাইল দেখুন", color = Color.White)
        }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out", color = AccountCoral)
        }
    }
}

@Composable
private fun AccountDashboardPageContent(
    page: AccountDashboardPage,
    authName: String,
    authEmail: String,
    subscriptionPlan: String,
    syncMessage: String,
    lastSyncAt: Long,
    customWordCount: Int,
    proActive: Boolean,
    customPhonetic: String,
    customBengali: String,
    customConversions: List<CustomConversion>,
    billingState: BillingEntitlementState,
    onSelectPage: (AccountDashboardPage) -> Unit,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTutorial: () -> Unit,
    onCustomPhoneticChange: (String) -> Unit,
    onCustomBengaliChange: (String) -> Unit,
    onSaveCustomConversion: () -> Unit,
    onSaveCustomPair: (String, String) -> Unit,
    onDeleteCustomConversion: (CustomConversion) -> Unit,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AccountPageTitle(page.bengali, page.english) }
        when (page) {
            AccountDashboardPage.Home -> item {
                AccountHomePage(
                    authName = authName,
                    customWordCount = customWordCount,
                    onSelectPage = onSelectPage,
                    onSync = onSync
                )
            }
            AccountDashboardPage.Dashboard -> item {
                AccountDashboardOverviewPage(
                    authName = authName,
                    authEmail = authEmail,
                    subscriptionPlan = subscriptionPlan,
                    syncMessage = syncMessage,
                    lastSyncAt = lastSyncAt,
                    customWordCount = customWordCount,
                    proActive = proActive,
                    onSelectPage = onSelectPage,
                    onSync = onSync
                )
            }
            AccountDashboardPage.Dictionary -> item {
                AccountDictionaryPage(
                    customPhonetic = customPhonetic,
                    customBengali = customBengali,
                    customConversions = customConversions,
                    proActive = proActive,
                    onCustomPhoneticChange = onCustomPhoneticChange,
                    onCustomBengaliChange = onCustomBengaliChange,
                    onSaveCustomConversion = onSaveCustomConversion,
                    onSaveCustomPair = onSaveCustomPair,
                    onDeleteCustomConversion = onDeleteCustomConversion
                )
            }
            AccountDashboardPage.AiWriter -> item {
                AccountAIWritingPage(
                    authName = authName,
                    customWordCount = customWordCount,
                    onOpenEditor = { onSelectPage(AccountDashboardPage.Editor) }
                )
            }
            AccountDashboardPage.Editor -> item { AccountFeaturePage("এডিটর", "Open the Banglu editor from the main app to test lowercase typing and voice output.", "Available in web now") }
            AccountDashboardPage.Documents -> item { AccountFeaturePage("আমার নথি", "Saved documents from web and Android will appear here after document sync is enabled.", "Sync planned") }
            AccountDashboardPage.Saved -> item { AccountFeaturePage("সংরক্ষিত", "Favorite words, snippets, and correction choices will be collected here.", "Local first") }
            AccountDashboardPage.Stats -> item {
                AccountStatsPage(customWordCount, lastSyncAt, syncMessage)
            }
            AccountDashboardPage.Tutorial -> item {
                AccountFeaturePage("Banglu Guide", "Full phonetic mapping, voice typing, punctuation, and shortcut tutorial.", "Open guide", onAction = onOpenTutorial)
            }
            AccountDashboardPage.Subscription -> item {
                AccountSubscriptionCard(
                    isSubscribed = proActive,
                    billingState = billingState,
                    syncMessage = syncMessage,
                    onUpgrade = onUpgrade,
                    onRestore = onRestore,
                    onSync = onSync
                )
            }
            AccountDashboardPage.Profile -> item {
                AccountProfilePage(authName, authEmail, subscriptionPlan, onSync, onSignOut)
            }
            AccountDashboardPage.Settings -> item {
                AccountFeaturePage("সেটিংস", "Keyboard theme, font size, suggestions, personal dictionary, and typing behavior.", "Open settings", onAction = onOpenSettings)
            }
        }
    }
}

@Composable
private fun AccountPageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = AccountPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
        Text(subtitle, color = AccountMuted, fontSize = 15.sp)
    }
}

@Composable
private fun AccountHomePage(
    authName: String,
    customWordCount: Int,
    onSelectPage: (AccountDashboardPage) -> Unit,
    onSync: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AccountCard {
            Text("${accountGreeting()}, ${authName.ifBlank { "Banglu user" }}", color = AccountText, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
            Text("Lowercase typing, voice, dictionary, and subscription tools are organized in the sidebar.", color = AccountMuted, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardHeroButton("Dictionary", "+", AccountPrimary, Modifier.weight(1f)) { onSelectPage(AccountDashboardPage.Dictionary) }
                DashboardHeroButton("Sync", "↻", AccountSuccess, Modifier.weight(1f), onSync)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("Custom", customWordCount.toString(), "saved words", AccountPrimary, Modifier.weight(1f))
            DashboardMetric("Mode", "Smart", "lowercase engine", AccountSuccess, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AccountDashboardOverviewPage(
    authName: String,
    authEmail: String,
    subscriptionPlan: String,
    syncMessage: String,
    lastSyncAt: Long,
    customWordCount: Int,
    proActive: Boolean,
    onSelectPage: (AccountDashboardPage) -> Unit,
    onSync: () -> Unit
) {
    val planLabel = if (proActive) "Pro" else "Free"
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AccountCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(AccountPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(userInitial(authName), color = AccountPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(authName.ifBlank { "Banglu user" }, color = AccountText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(authEmail, color = AccountMuted, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardPill("Signed in", AccountSuccess)
                        DashboardPill(planLabel, AccountPrimary)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("অভিধান", customWordCount.toString(), "নিজের শব্দ", AccountPrimary, Modifier.weight(1f))
            DashboardMetric("Plan", subscriptionPlan.uppercase(), if (proActive) "Premium tools" else "Basic access", AccountSuccess, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("Sync", if (lastSyncAt > 0L) "Ready" else "New", formatSyncTime(lastSyncAt), AccountPrimary, Modifier.weight(1f))
            DashboardMetric("Typing", "Smart", "Preference learning", AccountSuccess, Modifier.weight(1f))
        }
        AccountCard {
            Text("Quick actions", color = AccountText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardHeroButton("Add word", "+", AccountPrimary, Modifier.weight(1f)) { onSelectPage(AccountDashboardPage.Dictionary) }
                DashboardHeroButton("AI Writer", "⚡", AccountSuccess, Modifier.weight(1f)) { onSelectPage(AccountDashboardPage.AiWriter) }
            }
            Spacer(modifier = Modifier.height(10.dp))
            DashboardHeroButton("Sync now", "↻", AccountPrimary, Modifier.fillMaxWidth(), onSync)
        }
        AccountCard {
            Text("সাম্প্রতিক কার্যক্রম", color = AccountText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            DashboardActivityLine("Sync", syncMessage.ifBlank { "Ready for backend sync" }, AccountSuccess)
            DashboardActivityLine("Dictionary", "$customWordCount custom conversion saved", AccountPrimary)
            DashboardActivityLine("Account", "$planLabel plan active", AccountPrimary)
        }
    }
}

@Composable
private fun AccountDictionaryPage(
    customPhonetic: String,
    customBengali: String,
    customConversions: List<CustomConversion>,
    proActive: Boolean,
    onCustomPhoneticChange: (String) -> Unit,
    onCustomBengaliChange: (String) -> Unit,
    onSaveCustomConversion: () -> Unit,
    onSaveCustomPair: (String, String) -> Unit,
    onDeleteCustomConversion: (CustomConversion) -> Unit
) {
    var activeTab by remember { mutableStateOf("add") }
    var bulkInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var bulkMessage by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("created") }

    val filtered = customConversions
        .filter {
            searchQuery.isBlank() ||
                it.phonetic.contains(searchQuery, ignoreCase = true) ||
                it.bengali.contains(searchQuery)
        }
        .let { list ->
            when (sortBy) {
                "bengali" -> list.sortedBy { it.bengali }
                "phonetic" -> list.sortedBy { it.phonetic }
                else -> list.sortedByDescending { it.createdAt }
            }
        }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AccountDictionaryHero(customConversions.size)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardMetric("মোট সংগ্রহ", customConversions.size.toString(), "total", AccountPrimary, Modifier.weight(1f))
            DashboardMetric("সক্রিয়", customConversions.size.toString(), "active", AccountSuccess, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardMetric("শেয়ারড", "0", "community", AccountPrimary, Modifier.weight(1f))
            DashboardMetric("পর্যালোচনা", "0", "pending", AccountSuccess, Modifier.weight(1f))
        }

        AccountDictionaryTabs(
            activeTab = activeTab,
            manageCount = customConversions.size,
            onSelect = { activeTab = it }
        )

        when (activeTab) {
            "add" -> AccountDictionaryAddTab(
                customPhonetic = customPhonetic,
                customBengali = customBengali,
                proActive = proActive,
                bulkInput = bulkInput,
                bulkMessage = bulkMessage,
                onCustomPhoneticChange = onCustomPhoneticChange,
                onCustomBengaliChange = onCustomBengaliChange,
                onBulkInputChange = { bulkInput = it },
                onSaveCustomConversion = onSaveCustomConversion,
                onSaveBulk = {
                    val parsed = parseBulkDictionaryInput(bulkInput)
                    if (parsed.isEmpty()) {
                        bulkMessage = "বাল্ক ইনপুট পার্স করা যায়নি। ফরম্যাট: dhaka -> ঢাকা"
                    } else if (!proActive) {
                        bulkMessage = "Pro চালু করলে বাল্ক আমদানি সংরক্ষণ হবে।"
                    } else {
                        parsed.forEach { pair -> onSaveCustomPair(pair.first, pair.second) }
                        bulkInput = ""
                        bulkMessage = "${parsed.size}টি এন্ট্রি সংরক্ষণ হয়েছে।"
                    }
                },
                onClear = {
                    onCustomPhoneticChange("")
                    onCustomBengaliChange("")
                    bulkInput = ""
                    bulkMessage = ""
                }
            )
            "manage" -> AccountDictionaryManageTab(
                searchQuery = searchQuery,
                sortBy = sortBy,
                entries = filtered,
                totalCount = customConversions.size,
                onSearchChange = { searchQuery = it },
                onSortChange = { sortBy = it },
                onDelete = onDeleteCustomConversion,
                onAddFirst = { activeTab = "add" }
            )
            else -> AccountDictionaryStatsTab(customConversions)
        }
    }
}

@Composable
private fun AccountDictionaryHero(total: Int) {
    AccountCard {
        DashboardPill("ব্যক্তিগত সংগ্রহ", AccountPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Text("আমার শব্দভাণ্ডার", color = AccountText, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
        Text(
            "আপনার নিজস্ব বাংলা-ইংরেজি শব্দ ম্যাপিং তৈরি করুন। প্রতিটি শব্দ engine suggestion এর আগে প্রাধান্য পাবে।",
            color = AccountMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardPill("তাৎক্ষণিক সক্রিয়", AccountSuccess)
            DashboardPill("${total} শব্দ", AccountPrimary)
        }
    }
}

@Composable
private fun AccountDictionaryTabs(
    activeTab: String,
    manageCount: Int,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AccountSurface)
            .border(1.dp, AccountBorder, RoundedCornerShape(18.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        listOf(
            "add" to "সংগ্রহ",
            "manage" to "শব্দ ($manageCount)",
            "stats" to "অন্তর্দৃষ্টি"
        ).forEach { (key, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (activeTab == key) AccountPrimary else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (activeTab == key) Color.White else AccountMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AccountDictionaryAddTab(
    customPhonetic: String,
    customBengali: String,
    proActive: Boolean,
    bulkInput: String,
    bulkMessage: String,
    onCustomPhoneticChange: (String) -> Unit,
    onCustomBengaliChange: (String) -> Unit,
    onBulkInputChange: (String) -> Unit,
    onSaveCustomConversion: () -> Unit,
    onSaveBulk: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AccountCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(AccountPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = AccountPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("সংগ্রহ নির্দেশিকা", color = AccountText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("একাধিক variant কমা দিয়ে লিখুন", color = AccountMuted, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("dhaka, dhaaka", color = AccountPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(10.dp))
                Text("→", color = AccountSuccess, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(10.dp))
                Text("ঢাকা", color = AccountText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("আপনার শব্দগুলি সিস্টেম অভিধানের আগে প্রাধান্য পাবে।", color = AccountMuted, fontSize = 13.sp)
        }

        AccountCard {
            Text("দ্রুত সংগ্রহ", color = AccountText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("English variants → বাংলা শব্দ", color = AccountMuted, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(14.dp))
            AccountField(customPhonetic, onCustomPhoneticChange, "english variants, comma separated")
            Spacer(modifier = Modifier.height(10.dp))
            AccountField(customBengali, onCustomBengaliChange, "বাংলা শব্দ")
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSaveCustomConversion,
                enabled = proActive && customPhonetic.isNotBlank() && customBengali.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccountPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (proActive) "সংগ্রহে সংরক্ষণ করুন" else "Pro চালু করে সংরক্ষণ করুন", color = Color.White, fontSize = 16.sp)
            }
        }

        AccountCard {
            Text("বাল্ক আমদানি", color = AccountText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("প্রতি লাইনে একটি mapping দিন।", color = AccountMuted, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = bulkInput,
                onValueChange = onBulkInputChange,
                minLines = 5,
                label = { Text("dhaka -> ঢাকা\nchittagong -> চট্টগ্রাম\nrajshahi -> রাজশাহী") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccountPrimary,
                    unfocusedBorderColor = AccountBorder,
                    focusedTextColor = AccountText,
                    unfocusedTextColor = AccountText,
                    focusedLabelColor = AccountPrimary,
                    unfocusedLabelColor = AccountMuted,
                    cursorColor = AccountPrimary
                )
            )
            if (bulkMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(bulkMessage, color = if (bulkMessage.contains("পার্স") || bulkMessage.contains("Pro")) AccountCoral else AccountSuccess, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSaveBulk,
                    enabled = bulkInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccountSuccess),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("প্রক্রিয়া", color = Color.White)
                }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text("সব মুছুন", color = AccountText)
                }
            }
        }
    }
}

@Composable
private fun AccountDictionaryManageTab(
    searchQuery: String,
    sortBy: String,
    entries: List<CustomConversion>,
    totalCount: Int,
    onSearchChange: (String) -> Unit,
    onSortChange: (String) -> Unit,
    onDelete: (CustomConversion) -> Unit,
    onAddFirst: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AccountCard {
            Text("শব্দভাণ্ডার পরিচালনা", color = AccountText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("${totalCount}টি শব্দ, ${entries.size}টি দেখানো হচ্ছে", color = AccountMuted, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            AccountField(searchQuery, onSearchChange, "শব্দ অনুসন্ধান করুন")
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("created" to "তারিখ", "phonetic" to "English", "bengali" to "বাংলা").forEach { (key, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sortBy == key) AccountPrimary.copy(alpha = 0.22f) else AccountBg2)
                            .border(1.dp, if (sortBy == key) AccountPrimary else AccountBorder, RoundedCornerShape(12.dp))
                            .clickable { onSortChange(key) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (sortBy == key) AccountPrimary else AccountMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            AccountCard {
                Text(if (searchQuery.isBlank()) "আপনার সংগ্রহ খালি" else "কোনো শব্দ পাওয়া যায়নি", color = AccountText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (searchQuery.isBlank()) "আপনার প্রথম শব্দ যোগ করে সংগ্রহ শুরু করুন।" else "অন্য শব্দ দিয়ে অনুসন্ধান করুন।", color = AccountMuted, fontSize = 15.sp)
                if (searchQuery.isBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DashboardHeroButton("প্রথম শব্দ যোগ করুন", "+", AccountPrimary, Modifier.fillMaxWidth(), onAddFirst)
                }
            }
        } else {
            entries.take(30).forEach { item ->
                AccountCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(AccountPrimary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ব", color = AccountPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.bengali, color = AccountText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(item.phonetic, color = AccountPrimary, fontSize = 15.sp)
                            Text("সক্রিয় · লোকাল", color = AccountMuted, fontSize = 12.sp)
                        }
                        Text(
                            "মুছুন",
                            color = AccountCoral,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDelete(item) }
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDictionaryStatsTab(entries: List<CustomConversion>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardMetric("মোট", entries.size.toString(), "সংগ্রহ", AccountPrimary, Modifier.weight(1f))
            DashboardMetric("সক্রিয়", entries.size.toString(), "engine-ready", AccountSuccess, Modifier.weight(1f))
        }
        AccountCard {
            Text("সবচেয়ে নতুন শব্দ", color = AccountText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            if (entries.isEmpty()) {
                Text("এখনও কোনো শব্দ নেই।", color = AccountMuted, fontSize = 15.sp)
            } else {
                entries.sortedByDescending { it.createdAt }.take(8).forEach { item ->
                    DashboardActivityLine(item.bengali, item.phonetic, AccountPrimary)
                }
            }
        }
        AccountCard {
            Text("কমিউনিটি শেয়ার", color = AccountText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("ওয়েবের মতো shared/review workflow backend sync এর সাথে যুক্ত হবে। Android এখন local-first dictionary ব্যবহার করছে।", color = AccountMuted, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

private fun parseBulkDictionaryInput(input: String): List<Pair<String, String>> {
    return input.lines().mapNotNull { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@mapNotNull null
        val separators = listOf("->", "=>", ":", "|", "\t")
        for (separator in separators) {
            if (line.contains(separator)) {
                val parts = line.split(separator, limit = 2).map { it.trim() }
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    return@mapNotNull parts[0] to parts[1]
                }
            }
        }
        null
    }
}

@Composable
private fun AccountStatsPage(customWordCount: Int, lastSyncAt: Long, syncMessage: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("Words", customWordCount.toString(), "custom mappings", AccountPrimary, Modifier.weight(1f))
            DashboardMetric("Sync", if (lastSyncAt > 0L) "Ready" else "New", formatSyncTime(lastSyncAt), AccountSuccess, Modifier.weight(1f))
        }
        AccountCard {
            Text("Engine health", color = AccountText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(syncMessage.ifBlank { "No sync errors found." }, color = AccountMuted, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

private data class AIWritingTemplate(
    val id: String,
    val title: String,
    val english: String,
    val description: String,
    val category: String,
    val marker: String,
    val accent: Color
)

private val AI_WRITING_TEMPLATES = listOf(
    AIWritingTemplate("essay", "প্রবন্ধ", "Essay", "গভীর বিশ্লেষণধর্মী ও তথ্যসমৃদ্ধ রচনা তৈরি করুন", "একাডেমিক", "প", Color(0xFFFFB340)),
    AIWritingTemplate("story", "ছোটগল্প", "Short Story", "সৃজনশীল কল্পকাহিনী ও আকর্ষণীয় বর্ণনা", "সৃজনশীল", "গ", Color(0xFFFF6B8A)),
    AIWritingTemplate("poem", "কবিতা", "Poetry", "ছন্দময় কাব্যিক প্রকাশ ও শব্দের শিল্প", "সৃজনশীল", "ক", Color(0xFFBF8CFF)),
    AIWritingTemplate("email", "পেশাদার ইমেইল", "Professional Email", "ব্যবসায়িক ও পেশাদার যোগাযোগ রচনা", "ব্যবসায়িক", "@", Color(0xFF30D158)),
    AIWritingTemplate("article", "ব্লগ আর্টিকেল", "Blog Article", "আকর্ষণীয় ও পাঠকপ্রিয় কন্টেন্ট তৈরি", "কন্টেন্ট", "আ", Color(0xFF64D2FF)),
    AIWritingTemplate("speech", "বক্তৃতা", "Speech", "প্রভাবশালী ও হৃদয়গ্রাহী বক্তব্য রচনা", "পেশাদার", "ব", Color(0xFFFF7AD9))
)

@Composable
private fun AccountAIWritingPage(
    authName: String,
    customWordCount: Int,
    onOpenEditor: () -> Unit
) {
    var activeView by remember { mutableStateOf("overview") }
    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf("grid") }
    val firstName = authName.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "লেখক"

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AccountAIHero(
            firstName = firstName,
            activeView = activeView,
            onViewChange = { activeView = it },
            onOpenEditor = onOpenEditor
        )

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardMetric("মোট নথি", "0", "documents", AccountPrimary, Modifier.weight(1f))
            DashboardMetric("মোট শব্দ", "0", "words", Color(0xFFFFB340), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardMetric("আজকের শব্দ", "0", "today", AccountSuccess, Modifier.weight(1f))
            DashboardMetric("অভিধান", customWordCount.toString(), "custom words", Color(0xFFBF8CFF), Modifier.weight(1f))
        }

        if (activeView == "overview") {
            AccountAIRecentDocuments(onHistory = { activeView = "history" })
            DashboardSectionTitle("টেমপ্লেট বেছে নিন", "Templates")
            AI_WRITING_TEMPLATES.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { template ->
                        AccountAITemplateCard(
                            template = template,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenEditor
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            AccountAIBlankPageCTA(onOpenEditor)
            AccountAICapabilitiesPanel()
        } else {
            AccountAIHistoryView(
                searchQuery = searchQuery,
                viewMode = viewMode,
                onSearchChange = { searchQuery = it },
                onViewModeChange = { viewMode = it },
                onBack = { activeView = "overview" },
                onOpenEditor = onOpenEditor
            )
        }
    }
}

@Composable
private fun AccountAIHero(
    firstName: String,
    activeView: String,
    onViewChange: (String) -> Unit,
    onOpenEditor: () -> Unit
) {
    AccountCard {
        DashboardPill(accountGreeting(), AccountPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "$firstName, আজ কী লিখবেন?",
            color = AccountText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 33.sp
        )
        Text(
            "Claude AI এর সাথে বাংলায় পেশাদার মানের কন্টেন্ট তৈরি করুন।",
            color = AccountMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardHeroButton("নতুন লেখা", "+", AccountPrimary, Modifier.weight(1f), onOpenEditor)
            DashboardHeroButton(
                if (activeView == "overview") "সব নথি" else "টেমপ্লেট",
                if (activeView == "overview") "→" else "←",
                AccountSuccess,
                Modifier.weight(1f)
            ) {
                onViewChange(if (activeView == "overview") "history" else "overview")
            }
        }
    }
}

@Composable
private fun AccountAIRecentDocuments(onHistory: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DashboardSectionTitle("সাম্প্রতিক লেখা", "Recent")
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "সব দেখুন",
                color = AccountPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onHistory() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        AccountCard {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(AccountPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("ন", color = AccountPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("এখনো কোনো নথি নেই", color = AccountText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("টেমপ্লেট বেছে নিয়ে প্রথম লেখা শুরু করুন।", color = AccountMuted, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun AccountAITemplateCard(
    template: AIWritingTemplate,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(template.accent.copy(alpha = 0.10f))
            .border(1.dp, template.accent.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(template.accent.copy(alpha = 0.16f))
                .border(1.dp, template.accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(template.marker, color = template.accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Text(template.title, color = template.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
        Text(template.description, color = AccountMuted, fontSize = 12.sp, lineHeight = 18.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(template.category, color = AccountMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Text("→", color = template.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccountAIBlankPageCTA(onOpenEditor: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(AccountBorder))
        Text(
            "+ খালি পাতা দিয়ে শুরু করুন",
            color = AccountPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onOpenEditor() }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(AccountBorder))
    }
}

@Composable
private fun AccountAICapabilitiesPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1D2438), Color(0xFF101827))))
            .border(1.dp, AccountPrimary.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFFFFB340).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", color = Color(0xFFFFB340), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text("AI লেখা সহায়ক কী কী পারে?", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        listOf(
            Triple("স্মার্ট রচনা", "আপনার আইডিয়া দিন, AI তৈরি করে দেবে সম্পূর্ণ লেখা", "✦"),
            Triple("লেখা সম্পাদনা", "যেকোনো লেখা উন্নত, সংক্ষিপ্ত বা বিস্তারিত করুন", "↻"),
            Triple("ভাষান্তর", "বাংলা ও ইংরেজির মধ্যে সাবলীল অনুবাদ", "অ"),
            Triple("ব্যাকরণ সংশোধন", "স্বয়ংক্রিয় বানান ও ব্যাকরণ পরীক্ষা", "✓")
        ).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    AccountAICapabilityCard(item.first, item.second, item.third, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AccountAICapabilityCard(title: String, description: String, marker: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(marker, color = Color(0xFFFFB340), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun AccountAIHistoryView(
    searchQuery: String,
    viewMode: String,
    onSearchChange: (String) -> Unit,
    onViewModeChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "←",
                color = AccountPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            DashboardSectionTitle("সকল নথি", "History")
            Spacer(modifier = Modifier.weight(1f))
            DashboardPill("0", AccountPrimary)
        }
        AccountCard {
            AccountField(searchQuery, onSearchChange, "নথি অনুসন্ধান করুন")
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("grid" to "Grid", "list" to "List").forEach { (key, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (viewMode == key) AccountPrimary.copy(alpha = 0.22f) else AccountBg2)
                            .border(1.dp, if (viewMode == key) AccountPrimary else AccountBorder, RoundedCornerShape(13.dp))
                            .clickable { onViewModeChange(key) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (viewMode == key) AccountPrimary else AccountMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                DashboardHeroButton("নতুন", "+", AccountSuccess, Modifier.weight(1f), onOpenEditor)
            }
        }
        AccountCard {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AccountPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text("ন", color = AccountPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                if (searchQuery.isBlank()) "এখনো কোনো নথি নেই" else "কোনো নথি পাওয়া যায়নি",
                color = AccountText,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (searchQuery.isBlank()) "টেমপ্লেট বেছে নিয়ে প্রথম লেখা শুরু করুন।" else "অন্য শব্দ দিয়ে অনুসন্ধান করুন।",
                color = AccountMuted,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            if (searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                DashboardHeroButton("টেমপ্লেট দেখুন", "→", AccountPrimary, Modifier.fillMaxWidth(), onBack)
            }
        }
    }
}

@Composable
private fun AccountProfilePage(
    authName: String,
    authEmail: String,
    subscriptionPlan: String,
    onSync: () -> Unit,
    onSignOut: () -> Unit
) {
    AccountCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AccountPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(userInitial(authName), color = AccountPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(authName.ifBlank { "Banglu user" }, color = AccountText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(authEmail, color = AccountMuted, fontSize = 14.sp, lineHeight = 19.sp)
                Text("Plan: ${subscriptionPlan.replaceFirstChar { it.uppercase() }}", color = AccountPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        DashboardHeroButton("Sync profile", "↻", AccountPrimary, Modifier.fillMaxWidth(), onSync)
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out", color = AccountCoral)
        }
    }
}

@Composable
private fun AccountFeaturePage(
    title: String,
    description: String,
    action: String,
    onAction: (() -> Unit)? = null
) {
    AccountCard {
        DashboardPill(action, AccountPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, color = AccountText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(description, color = AccountMuted, fontSize = 15.sp, lineHeight = 22.sp)
        if (onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            DashboardHeroButton(action, "→", AccountPrimary, Modifier.fillMaxWidth(), onAction)
        }
    }
}

private fun userInitial(name: String): String {
    return name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "বা"
}

@Composable
private fun AccountHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AccountSurface)
                .border(1.dp, AccountBorder, CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = AccountPrimary, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Image(
            painter = painterResource(id = R.drawable.banglu_logo),
            contentDescription = "Banglu logo",
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("প্রোফাইল", color = AccountPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Login, Pro, Sync", color = AccountMuted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun AccountCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AccountSurface)
            .border(1.dp, AccountBorder, RoundedCornerShape(22.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun AccountTabRow(activeTab: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AccountBg2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("login" to "Login", "register" to "Register").forEach { (key, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (activeTab == key) AccountPrimary else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (activeTab == key) Color.White else AccountMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SignedInAccountDashboard(
    authName: String,
    authEmail: String,
    subscriptionPlan: String,
    syncMessage: String,
    lastSyncAt: Long,
    customWordCount: Int,
    proActive: Boolean,
    onSync: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenDictionary: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTutorial: () -> Unit,
    onSignOut: () -> Unit
) {
    val displayName = authName.ifBlank { "Banglu user" }
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "বা"
    val greeting = accountGreeting()
    val syncText = if (syncMessage.isBlank()) {
        "সবকিছু প্রস্তুত। এখন আপনার dictionary এবং typing preference sync করা যাবে।"
    } else {
        syncMessage
    }
    val planLabel = if (proActive) "Pro" else "Free"

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            AccountSurface.copy(alpha = 0.96f),
                            AccountPrimary.copy(alpha = 0.18f),
                            AccountSuccess.copy(alpha = 0.13f)
                        )
                    )
                )
                .border(1.dp, AccountPrimary.copy(alpha = 0.28f), RoundedCornerShape(28.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(AccountBg.copy(alpha = 0.72f))
                        .border(1.dp, AccountPrimary.copy(alpha = 0.36f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initial, color = AccountPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(greeting, color = AccountMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(displayName, color = AccountText, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                    Text(authEmail, color = AccountMuted, fontSize = 14.sp, lineHeight = 19.sp)
                    Spacer(modifier = Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardPill("Signed in", AccountSuccess)
                        DashboardPill(planLabel, AccountPrimary)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardHeroButton("নতুন শব্দ", "+", AccountPrimary, Modifier.weight(1f), onOpenDictionary)
                DashboardHeroButton("Sync now", "↻", AccountSuccess, Modifier.weight(1f), onSync)
            }
        }

        DashboardSectionTitle("ড্যাশবোর্ড", "Keyboard workspace")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("অভিধান", customWordCount.toString(), "নিজের শব্দ", AccountPrimary, Modifier.weight(1f))
            DashboardMetric("Plan", subscriptionPlan.uppercase(), if (proActive) "Premium tools" else "Basic access", AccountSuccess, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("Sync", if (lastSyncAt > 0L) "Ready" else "New", formatSyncTime(lastSyncAt), AccountPrimary, Modifier.weight(1f))
            DashboardMetric("Typing", "Smart", "Preference learning", AccountSuccess, Modifier.weight(1f))
        }

        DashboardSectionTitle("মেনু", "Sidebar")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardActionCard("Dashboard", "Overview", "⌂", AccountPrimary, Modifier.weight(1f), onOpenDashboard)
            DashboardActionCard("My Dictionary", "Custom mapping", "ব", AccountPrimary, Modifier.weight(1f), onOpenDictionary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardActionCard("Voice Typing", "Mic settings", "◉", AccountSuccess, Modifier.weight(1f), onOpenSettings)
            DashboardActionCard("Tutorial", "How to type", "?", AccountPrimary, Modifier.weight(1f), onOpenTutorial)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardActionCard(if (proActive) "Pro tools" else "Subscription", planLabel, "P", AccountPrimary, Modifier.weight(1f), onOpenSubscription)
            DashboardActionCard("Settings", "Theme and keyboard", "⚙", AccountSuccess, Modifier.weight(1f), onOpenSettings)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AccountBg2.copy(alpha = 0.74f))
                .border(1.dp, AccountBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccountSuccess)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("সাম্প্রতিক কার্যক্রম", color = AccountText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            DashboardActivityLine("Sync", syncText, AccountSuccess)
            DashboardActivityLine("Dictionary", "$customWordCount custom conversion saved", AccountPrimary)
            DashboardActivityLine("Account", "$planLabel plan active", AccountPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(AccountPrimary.copy(alpha = 0.22f), AccountBg2.copy(alpha = 0.9f))))
                .border(1.dp, AccountPrimary.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardPill("AI next", AccountPrimary)
            Text("AI লেখা সহায়ক", color = AccountText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Web dashboard এর AI writer পরে Android keyboard preference এবং voice correction এর সাথে sync হবে।",
                color = AccountMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out", color = AccountCoral, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardHeroButton(
    label: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DashboardSectionTitle(bengali: String, english: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(AccountPrimary, AccountSuccess)))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(bengali, color = AccountText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(7.dp))
        Text(english, color = AccountMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DashboardPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DashboardMetric(
    title: String,
    value: String,
    helper: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AccountBg2.copy(alpha = 0.7f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, color = AccountMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = AccountText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(helper, color = AccountMuted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun DashboardMiniStatus(value: String, helper: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AccountBg2.copy(alpha = 0.62f))
            .border(1.dp, AccountBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, color = AccountText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(helper, color = AccountMuted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun DashboardActionCard(
    title: String,
    helper: String,
    icon: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AccountBg2.copy(alpha = 0.72f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(title, color = AccountText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(helper, color = AccountMuted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun DashboardActivityLine(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AccountSurface.copy(alpha = 0.36f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AccountText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(value, color = AccountMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

private fun accountGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 6 -> "শুভ রাত্রি"
        hour < 12 -> "শুভ সকাল"
        hour < 17 -> "শুভ অপরাহ্ন"
        hour < 20 -> "শুভ সন্ধ্যা"
        else -> "শুভ রাত্রি"
    }
}

private fun formatSyncTime(lastSyncAt: Long): String {
    if (lastSyncAt <= 0L) return "Not synced yet"
    return runCatching {
        "Last ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastSyncAt))}"
    }.getOrDefault("Recently synced")
}

@Composable
private fun AccountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccountPrimary,
            unfocusedBorderColor = AccountBorder,
            focusedTextColor = AccountText,
            unfocusedTextColor = AccountText,
            focusedLabelColor = AccountPrimary,
            unfocusedLabelColor = AccountMuted,
            cursorColor = AccountPrimary
        )
    )
}

@Composable
private fun AccountSectionHeader(bengali: String, english: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AccountPrimary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(bengali, color = AccountPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(english, color = AccountMuted, fontSize = 16.sp)
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun AccountSubscriptionCard(
    isSubscribed: Boolean,
    billingState: BillingEntitlementState,
    syncMessage: String,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onSync: () -> Unit
) {
    AccountCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isSubscribed) AccountSuccess else AccountPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isSubscribed) "✓" else "P", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isSubscribed) "Banglu Pro active" else "Banglu Free plan",
                    color = AccountText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(billingState.message, color = AccountMuted, fontSize = 15.sp, lineHeight = 20.sp)
            }
            if (billingState.loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AccountPrimary)
            }
        }
        if (syncMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Sync: $syncMessage", color = AccountMuted, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = if (isSubscribed) onRestore else onUpgrade,
            enabled = !billingState.loading,
            colors = ButtonDefaults.buttonColors(containerColor = if (isSubscribed) AccountSuccess else AccountPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSubscribed) "Restore Pro" else "Pro কিনুন", color = Color.White, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRestore,
                enabled = !billingState.loading,
                colors = ButtonDefaults.buttonColors(containerColor = AccountPrimary.copy(alpha = 0.85f)),
                modifier = Modifier.weight(1f)
            ) {
                Text("Check", color = Color.White, fontSize = 15.sp)
            }
            OutlinedButton(
                onClick = onSync,
                modifier = Modifier.weight(1f)
            ) {
                Text("Sync", color = AccountText, fontSize = 15.sp)
            }
        }
    }
}
