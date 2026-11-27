package com.nexus.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexus.mobile.security.BiometricHelper
import com.nexus.mobile.ui.auth.AuthScreen
import com.nexus.mobile.ui.auth.AuthViewModel
import com.nexus.mobile.ui.auth.BiometricOptInScreen
import com.nexus.mobile.ui.auth.LockScreen
import com.nexus.mobile.ui.auth.PageAuthGate
import com.nexus.mobile.ui.auth.ReAuthScreen
import com.nexus.mobile.ui.components.DockStatusBanner
import com.nexus.mobile.ui.components.FzPortalSplash
import com.nexus.mobile.ui.components.NexusFloatingDock
import com.nexus.mobile.ui.components.NexusTab
import com.nexus.mobile.ui.placeholders.AssistantPlaceholderScreen
import com.nexus.mobile.ui.placeholders.HomePlaceholderScreen
import com.nexus.mobile.ui.placeholders.ServerPlaceholderScreen
import com.nexus.mobile.ui.profile.ProfileScreen
import com.nexus.mobile.ui.settings.SettingsScreen
import com.nexus.mobile.ui.state.AppViewModel
import com.nexus.mobile.ui.theme.NexusTheme
import com.nexus.mobile.updater.ApkInstaller
import com.nexus.mobile.updater.UpdateScreen
import com.nexus.mobile.updater.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.io.File

private enum class ModalOverlay {
    NONE,
    UPDATES,
    SET_PIN,
    REAUTH_CHANGE_PIN,
    REAUTH_REMOVE_PIN,
    AUTH,
    PROFILE,
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val deepLinkTokenState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractDeepLinkToken(intent)
        setContent {
            NexusRootApp(
                activity = this,
                deepLinkToken = deepLinkTokenState.value,
                onDeepLinkConsumed = { deepLinkTokenState.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractDeepLinkToken(intent)
    }

    private fun extractDeepLinkToken(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "nexus-mobile" && data.host == "auth-callback") {
            data.getQueryParameter("token")?.takeIf { it.isNotBlank() }?.let {
                deepLinkTokenState.value = it
            }
        }
    }
}

@Composable
private fun NexusRootApp(
    activity: FragmentActivity,
    deepLinkToken: String?,
    onDeepLinkConsumed: () -> Unit,
    appViewModel: AppViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val settings by appViewModel.settings.collectAsState()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val isUnlocked by appViewModel.lockManager.isAppUnlocked.collectAsState()
    val lockoutUntilMs by appViewModel.lockManager.lockoutUntilMs.collectAsState()
    val pinError by appViewModel.pinError.collectAsState()
    val hasUpdate by updateViewModel.hasUpdateAvailable.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> appViewModel.lockManager.onAppBackgrounded()
                Lifecycle.Event.ON_START -> appViewModel.lockManager.onAppForegrounded()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(deepLinkToken) {
        if (!deepLinkToken.isNullOrBlank()) {
            authViewModel.handleDeepLinkToken(deepLinkToken) {
                appViewModel.reloadFromStorage()
            }
            onDeepLinkConsumed()
        }
    }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            authViewModel.refreshProfile { appViewModel.reloadFromStorage() }
        }
    }

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showBiometricOptIn by rememberSaveable { mutableStateOf(false) }
    var biometricPromptedThisSession by rememberSaveable { mutableStateOf(false) }
    val bioAvailable = remember { BiometricHelper.canAuthenticate(activity) }
    val bioHardwarePresent = remember { BiometricHelper.isBiometricHardwarePresent(activity) }

    LaunchedEffect(isAuthenticated, isUnlocked) {
        if (!isAuthenticated) {
            showBiometricOptIn = false
            biometricPromptedThisSession = false
        } else if (!isUnlocked) {
            // Reset per-unlock guard so "Ask me next time" or manual biometric disable
            // re-prompts cleanly on the next PIN entry.
            biometricPromptedThisSession = false
        }
    }

    LaunchedEffect(isAuthenticated, settings.hasPin, isUnlocked, settings.biometricPromptMode) {
        if (isAuthenticated &&
            settings.hasPin &&
            isUnlocked &&
            !settings.isBiometricEnabled &&
            settings.biometricPromptMode == "ask_next_time" &&
            !biometricPromptedThisSession
        ) {
            showBiometricOptIn = true
            biometricPromptedThisSession = true
        }
    }

    NexusTheme(
        themePreference = settings.themePreference,
        dynamicColor = settings.useDynamicColor,
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!isAuthenticated) {
                    AuthScreen(
                        state = authState,
                        onLoginWithPassword = { id, pw ->
                            authViewModel.loginWithPassword(id, pw) {
                                appViewModel.reloadFromStorage()
                            }
                        },
                        onSendOtp = authViewModel::sendOtp,
                        onVerifyOtp = { email, code ->
                            authViewModel.verifyOtp(email, code) {
                                appViewModel.reloadFromStorage()
                            }
                        },
                        onSendMagicLink = authViewModel::sendMagicLink,
                        onRequestPasswordReset = authViewModel::requestPasswordReset,
                        onRegisterAccount = { email, fullName, pw, username, qKey, qAns ->
                            authViewModel.registerAccount(email, fullName, pw, username, qKey, qAns) {
                                appViewModel.reloadFromStorage()
                            }
                        },
                        onSocialTokenLogin = { provider, token ->
                            authViewModel.loginWithSocialToken(provider, token) {
                                appViewModel.reloadFromStorage()
                            }
                        },
                        onStartDevicePairing = {
                            authViewModel.startDevicePairing {
                                appViewModel.reloadFromStorage()
                            }
                        },
                        onCancelPairing = authViewModel::cancelPairing,
                        onClearMessage = authViewModel::clearMessage,
                    )
                } else if (!settings.hasPin && settings.hasCentralPin) {
                    var centralErr by remember { mutableStateOf<String?>(null) }
                    LockScreen(
                        title = "Verify Central PIN",
                        subtitle = "Enter your synced Nexus 4-digit PIN for ${settings.displayName}",
                        errorMessage = centralErr ?: pinError,
                        isBiometricAvailable = false,
                        onBiometricClick = {},
                        onPinSubmitted = { entered ->
                            appViewModel.verifyCentralPinAndCache(entered) { ok, err ->
                                centralErr = if (ok) null else err
                                if (ok &&
                                    !settings.isBiometricEnabled &&
                                    settings.biometricPromptMode == "ask_next_time"
                                ) {
                                    showBiometricOptIn = true
                                    biometricPromptedThisSession = true
                                }
                            }
                        },
                        onSignOutClick = {
                            appViewModel.removePin()
                            authViewModel.logout { appViewModel.reloadFromStorage() }
                        },
                        userBadgeName = settings.displayName,
                    )
                } else if (!settings.hasPin) {
                    var firstPin by remember { mutableStateOf<String?>(null) }
                    var mismatchError by remember { mutableStateOf<String?>(null) }
                    LockScreen(
                        title = if (firstPin != null) "Confirm 4-Digit PIN" else "Create 4-Digit PIN",
                        subtitle = if (firstPin != null) {
                            "Re-enter your PIN to sync across Nexus"
                        } else {
                            "Mandatory security PIN for your Nexus account"
                        },
                        errorMessage = mismatchError,
                        isBiometricAvailable = false,
                        onBiometricClick = {},
                        onPinSubmitted = { entered ->
                            val initial = firstPin
                            if (initial == null) {
                                firstPin = entered
                                mismatchError = null
                            } else if (entered == initial) {
                                appViewModel.saveNewPin(entered)
                                if (!settings.isBiometricEnabled &&
                                    settings.biometricPromptMode == "ask_next_time"
                                ) {
                                    showBiometricOptIn = true
                                    biometricPromptedThisSession = true
                                }
                            } else {
                                firstPin = null
                                mismatchError = "PINs didn't match. Try again."
                            }
                        },
                        onSignOutClick = {
                            appViewModel.removePin()
                            authViewModel.logout { appViewModel.reloadFromStorage() }
                        },
                        userBadgeName = settings.displayName,
                    )
                } else if (!isUnlocked) {
                    LaunchedEffect(isUnlocked, settings.isBiometricEnabled, showSplash) {
                        if (!showSplash && settings.isBiometricEnabled && bioAvailable) {
                            BiometricHelper.prompt(
                                activity = activity,
                                onSuccess = { appViewModel.lockManager.markUnlocked() },
                                onError = {},
                            )
                        }
                    }

                    LockScreen(
                        title = "Unlock Nexus",
                        subtitle = "Signed in as ${settings.displayName}",
                        errorMessage = pinError,
                        lockoutUntilMs = lockoutUntilMs,
                        isBiometricAvailable = settings.isBiometricEnabled && bioAvailable,
                        onBiometricClick = {
                            BiometricHelper.prompt(
                                activity = activity,
                                onSuccess = { appViewModel.lockManager.markUnlocked() },
                                onError = {},
                            )
                        },
                        onPinSubmitted = { pin -> appViewModel.verifyPin(pin) },
                        onSignOutClick = {
                            appViewModel.removePin()
                            authViewModel.logout { appViewModel.reloadFromStorage() }
                        },
                        userBadgeName = settings.displayName,
                    )
                } else if (showBiometricOptIn) {
                    BiometricOptInScreen(
                        displayName = settings.displayName,
                        onEnableBiometric = {
                            appViewModel.setBiometricPromptMode("enabled")
                            showBiometricOptIn = false
                        },
                        onAskNextTime = {
                            appViewModel.setBiometricPromptMode("ask_next_time")
                            showBiometricOptIn = false
                        },
                        onDontAskAgain = {
                            appViewModel.setBiometricPromptMode("dont_ask_again")
                            showBiometricOptIn = false
                        },
                    )
                } else {
                    MainShell(
                        activity = activity,
                        appViewModel = appViewModel,
                        authViewModel = authViewModel,
                        updateViewModel = updateViewModel,
                        hasUpdateAvailable = hasUpdate,
                        bioAvailable = bioAvailable,
                    )
                }

                if (showSplash) {
                    FzPortalSplash(onFinished = { showSplash = false })
                }
            }
        }
    }
}

@Composable
private fun MainShell(
    activity: FragmentActivity,
    appViewModel: AppViewModel,
    authViewModel: AuthViewModel,
    updateViewModel: UpdateViewModel,
    hasUpdateAvailable: Boolean,
    bioAvailable: Boolean,
) {
    val context = LocalContext.current
    val settings by appViewModel.settings.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val updateState by updateViewModel.uiState.collectAsState()
    var currentTab by rememberSaveable { mutableStateOf(NexusTab.HOME) }
    var activeModal by rememberSaveable { mutableStateOf(ModalOverlay.NONE) }
    var pendingReAuthProof by remember { mutableStateOf<Triple<String?, String?, String?>?>(null) }
    var isDockExpanded by remember { mutableStateOf(true) }

    val hazeState = remember { HazeState() }

    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -14f) {
                    isDockExpanded = false
                } else if (available.y > 14f) {
                    isDockExpanded = true
                }
                return Offset.Zero
            }
        }
    }

    BackHandler(enabled = activeModal != ModalOverlay.NONE) {
        activeModal = ModalOverlay.NONE
    }

    when (activeModal) {
        ModalOverlay.UPDATES -> {
            PageAuthGate(
                requireAuth = settings.hasPin && settings.isPageAuthEnabled,
                activity = activity,
                verifyPin = appViewModel::verifyPin,
            ) {
                UpdateScreen(
                    state = updateState,
                    currentVersionName = updateViewModel.currentVersionName,
                    currentVersionCode = updateViewModel.currentVersionCode,
                    onBackClick = { activeModal = ModalOverlay.NONE },
                    onCheckUpdates = updateViewModel::checkForUpdates,
                    onDownloadUpdate = updateViewModel::downloadAndVerify,
                    onInstallUpdate = { path ->
                        if (!ApkInstaller.canInstallPackages(context)) {
                            ApkInstaller.openInstallPermissionSettings(context)
                        } else {
                            ApkInstaller.installVerifiedApk(context, File(path))
                        }
                    },
                )
            }
        }

        ModalOverlay.SET_PIN -> {
            var firstPin by remember { mutableStateOf<String?>(null) }
            var mismatchError by remember { mutableStateOf<String?>(null) }

            LockScreen(
                title = when {
                    firstPin != null -> "Confirm 4-Digit PIN"
                    settings.hasPin -> "Set New 4-Digit PIN"
                    else -> "Create 4-Digit PIN"
                },
                subtitle = if (firstPin != null) {
                    "Re-enter your PIN to confirm"
                } else {
                    "Protects app launch and sensitive pages"
                },
                errorMessage = mismatchError,
                isBiometricAvailable = false,
                onBiometricClick = {},
                onPinSubmitted = { entered ->
                    val initial = firstPin
                    if (initial == null) {
                        firstPin = entered
                        mismatchError = null
                    } else if (entered == initial) {
                        val proof = pendingReAuthProof
                        if (proof != null) {
                            appViewModel.updateOrRemovePinWithReAuth(
                                newPin = entered,
                                remove = false,
                                password = proof.first,
                                otpCode = proof.second,
                                securityAnswer = proof.third,
                            ) { ok, err ->
                                if (ok) {
                                    pendingReAuthProof = null
                                    activeModal = ModalOverlay.NONE
                                } else {
                                    firstPin = null
                                    mismatchError = err ?: "Re-authentication failed. Try again."
                                }
                            }
                        } else {
                            appViewModel.saveNewPin(entered)
                            activeModal = ModalOverlay.NONE
                        }
                    } else {
                        firstPin = null
                        mismatchError = "PINs didn't match. Try again."
                    }
                },
                onBackClick = {
                    if (firstPin != null) {
                        firstPin = null
                        mismatchError = null
                    } else {
                        activeModal = ModalOverlay.NONE
                    }
                },
            )
        }

        ModalOverlay.REAUTH_CHANGE_PIN,
        ModalOverlay.REAUTH_REMOVE_PIN -> {
            val isRemoving = activeModal == ModalOverlay.REAUTH_REMOVE_PIN
            var reAuthLoading by remember { mutableStateOf(false) }
            var reAuthError by remember { mutableStateOf<String?>(null) }
            var reAuthInfo by remember { mutableStateOf<String?>(null) }

            ReAuthScreen(
                title = if (isRemoving) "Verify to Remove PIN" else "Verify to Change PIN",
                subtitle = "Confirm your identity before modifying your central Nexus PIN.",
                email = settings.email,
                isLoading = reAuthLoading,
                errorMessage = reAuthError,
                infoMessage = reAuthInfo,
                onSendOtp = {
                    reAuthLoading = true
                    reAuthError = null
                    appViewModel.sendReAuthOtp { ok, msg ->
                        reAuthLoading = false
                        if (ok) reAuthInfo = msg else reAuthError = msg
                    }
                },
                onSubmitProof = { pw, otp, ans ->
                    reAuthLoading = true
                    reAuthError = null
                    if (isRemoving) {
                        appViewModel.updateOrRemovePinWithReAuth(
                            newPin = null,
                            remove = true,
                            password = pw,
                            otpCode = otp,
                            securityAnswer = ans,
                        ) { ok, err ->
                            reAuthLoading = false
                            if (ok) {
                                activeModal = ModalOverlay.NONE
                            } else {
                                reAuthError = err
                            }
                        }
                    } else {
                        if (pw.isNullOrBlank() && otp.isNullOrBlank() && ans.isNullOrBlank()) {
                            reAuthLoading = false
                            reAuthError = "Enter your password, 6-digit OTP, or security answer."
                        } else {
                            pendingReAuthProof = Triple(pw, otp, ans)
                            reAuthLoading = false
                            activeModal = ModalOverlay.SET_PIN
                        }
                    }
                },
                onBackClick = { activeModal = ModalOverlay.NONE },
            )
        }

        ModalOverlay.AUTH -> {
            AuthScreen(
                state = authState,
                onLoginWithPassword = { id, pw ->
                    authViewModel.loginWithPassword(id, pw) {
                        appViewModel.reloadFromStorage()
                        activeModal = ModalOverlay.NONE
                    }
                },
                onSendOtp = authViewModel::sendOtp,
                onVerifyOtp = { email, code ->
                    authViewModel.verifyOtp(email, code) {
                        appViewModel.reloadFromStorage()
                        activeModal = ModalOverlay.NONE
                    }
                },
                onSendMagicLink = authViewModel::sendMagicLink,
                onRequestPasswordReset = authViewModel::requestPasswordReset,
                onRegisterAccount = { email, fullName, pw, username, qKey, qAns ->
                    authViewModel.registerAccount(email, fullName, pw, username, qKey, qAns) {
                        appViewModel.reloadFromStorage()
                        activeModal = ModalOverlay.NONE
                    }
                },
                onSocialTokenLogin = { provider, token ->
                    authViewModel.loginWithSocialToken(provider, token) {
                        appViewModel.reloadFromStorage()
                        activeModal = ModalOverlay.NONE
                    }
                },
                onStartDevicePairing = {
                    authViewModel.startDevicePairing {
                        appViewModel.reloadFromStorage()
                        activeModal = ModalOverlay.NONE
                    }
                },
                onCancelPairing = authViewModel::cancelPairing,
                onClearMessage = authViewModel::clearMessage,
                onBackClick = { activeModal = ModalOverlay.NONE },
            )
        }

        ModalOverlay.PROFILE -> {
            ProfileScreen(
                displayName = settings.displayName,
                username = settings.username,
                email = settings.email,
                role = settings.role,
                avatarUrl = settings.avatarUrl,
                deviceId = settings.deviceId,
                hasPin = settings.hasPin,
                onBackClick = { activeModal = ModalOverlay.NONE },
                onRefreshProfile = {
                    authViewModel.refreshProfile { appViewModel.reloadFromStorage() }
                },
                onOpenSecurityPin = {
                    activeModal = if (settings.hasPin) ModalOverlay.REAUTH_CHANGE_PIN else ModalOverlay.SET_PIN
                },
                onOpenUpdates = { activeModal = ModalOverlay.UPDATES },
                onLogout = {
                    activeModal = ModalOverlay.NONE
                    appViewModel.removePin()
                    authViewModel.logout { appViewModel.reloadFromStorage() }
                },
            )
        }

        ModalOverlay.NONE -> {
            val dynamicBanner = if (hasUpdateAvailable) {
                DockStatusBanner(
                    title = "New Nexus Mobile update ready",
                    badge = "Install",
                    isWarning = true,
                    onClick = { activeModal = ModalOverlay.UPDATES },
                )
            } else {
                null
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollConnection),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                ) {
                    when (currentTab) {
                        NexusTab.HOME -> HomePlaceholderScreen(
                            displayName = settings.displayName,
                            onLockClick = {
                                if (settings.hasPin) {
                                    appViewModel.lockManager.lockNow()
                                } else {
                                    activeModal = ModalOverlay.SET_PIN
                                }
                            },
                            onOpenUpdates = { activeModal = ModalOverlay.UPDATES },
                        )

                        NexusTab.SERVER -> ServerPlaceholderScreen(hazeState = hazeState)

                        NexusTab.ASSISTANT -> AssistantPlaceholderScreen()

                        NexusTab.SETTINGS -> SettingsScreen(
                            deviceId = settings.deviceId,
                            isAuthenticated = settings.isAuthenticated,
                            hasPin = settings.hasPin,
                            isBiometricAvailable = bioAvailable,
                            isBiometricEnabled = settings.isBiometricEnabled,
                            isPageAuthEnabled = settings.isPageAuthEnabled,
                            themePreference = settings.themePreference,
                            useDynamicColor = settings.useDynamicColor,
                            currentVersionName = updateViewModel.currentVersionName,
                            hasUpdateAvailable = hasUpdateAvailable,
                            onSetupOrChangePin = {
                                activeModal = if (settings.hasPin) ModalOverlay.REAUTH_CHANGE_PIN else ModalOverlay.SET_PIN
                            },
                            onRemovePin = { activeModal = ModalOverlay.REAUTH_REMOVE_PIN },
                            onToggleBiometric = appViewModel::setBiometricEnabled,
                            onTogglePageAuth = appViewModel::setPageAuthEnabled,
                            onSelectTheme = appViewModel::setThemePreference,
                            onToggleDynamicColor = appViewModel::setDynamicColor,
                            onLockNow = appViewModel.lockManager::lockNow,
                            onOpenUpdates = { activeModal = ModalOverlay.UPDATES },
                            onOpenPairing = { activeModal = ModalOverlay.AUTH },
                        )
                    }
                }

                NexusFloatingDock(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        currentTab = tab
                        isDockExpanded = true
                    },
                    isExpanded = isDockExpanded,
                    onExpandRequest = { isDockExpanded = true },
                    userName = settings.displayName,
                    userRole = settings.role,
                    avatarUrl = settings.avatarUrl,
                    hasPin = settings.hasPin,
                    onOpenProfile = { activeModal = ModalOverlay.PROFILE },
                    onOpenSettings = { currentTab = NexusTab.SETTINGS },
                    onOpenUpdates = { activeModal = ModalOverlay.UPDATES },
                    onLockNow = appViewModel.lockManager::lockNow,
                    onLogout = {
                        appViewModel.removePin()
                        authViewModel.logout { appViewModel.reloadFromStorage() }
                    },
                    hazeState = hazeState,
                    statusBanner = dynamicBanner,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
