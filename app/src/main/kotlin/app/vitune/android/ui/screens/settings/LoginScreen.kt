package app.vitune.android.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.vitune.android.R
import app.vitune.android.preferences.AccountPreferences
import app.vitune.android.ui.components.themed.CircularProgressIndicator
import app.vitune.android.ui.components.themed.DefaultDialog
import app.vitune.android.ui.components.themed.Header
import app.vitune.android.ui.components.themed.HeaderIconButton
import app.vitune.android.ui.components.themed.Scaffold
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.bold
import app.vitune.android.utils.color
import app.vitune.android.utils.medium
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.compose.routing.RouteHandler
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.surface
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeAccount
import com.metrolist.innertube.utils.parseCookieString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private enum class LoginStage {
    Authenticating,
    CheckingAccounts,
    SelectingAccount,
    SwitchingAccount,
    Completing,
}

private data class AuthData(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
)

private class LoginJsInterface {
    var onVisitorDataReceived: ((String?) -> Unit)? = null
    var onDataSyncIdReceived: ((String?) -> Unit)? = null
    var onAuthUserReceived: ((String?) -> Unit)? = null

    @JavascriptInterface
    fun onRetrieveVisitorData(visitorData: String?) {
        onVisitorDataReceived?.invoke(visitorData)
    }

    @JavascriptInterface
    fun onRetrieveDataSyncId(dataSyncId: String?) {
        onDataSyncIdReceived?.invoke(dataSyncId)
    }

    @JavascriptInterface
    fun onRetrieveAuthUser(authUser: String?) {
        onAuthUserReceived?.invoke(authUser)
    }
}

private data class WebAuthData(
    val visitorData: String?,
    val dataSyncId: String?,
    val authUser: String,
)

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Route
@Composable
fun LoginScreen(
    onDismiss: () -> Unit,
    isSwitchingChannel: Boolean = false,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val jsInterface = remember { LoginJsInterface() }

    var loginStage by remember { mutableStateOf(LoginStage.Authenticating) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var accounts by remember { mutableStateOf<List<YouTubeAccount>>(emptyList()) }
    var selectedAccount by remember { mutableStateOf<YouTubeAccount?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onDismiss()
        }
    }

    suspend fun extractAuthData(webView: WebView?): AuthData? {
        val view = webView ?: return null
        var hasAuthCookie = false
        var hasVisitorData = false

        repeat(20) {
            val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
            val cookieMap = runCatching { parseCookieString(cookie) }.getOrDefault(emptyMap())
            val visitorDataDeferred = CompletableDeferred<String?>()
            val dataSyncIdDeferred = CompletableDeferred<String?>()
            val authUserDeferred = CompletableDeferred<String?>()

            jsInterface.onVisitorDataReceived = { visitorData ->
                if (!visitorDataDeferred.isCompleted) visitorDataDeferred.complete(visitorData)
            }
            jsInterface.onDataSyncIdReceived = { dataSyncId ->
                if (!dataSyncIdDeferred.isCompleted) dataSyncIdDeferred.complete(dataSyncId)
            }
            jsInterface.onAuthUserReceived = { authUser ->
                if (!authUserDeferred.isCompleted) authUserDeferred.complete(authUser)
            }

            view.loadUrl(
                "javascript:Android.onRetrieveVisitorData(" +
                    "window.yt&&window.yt.config_?window.yt.config_.VISITOR_DATA:null)",
            )
            view.loadUrl(
                "javascript:Android.onRetrieveDataSyncId(" +
                    "window.yt&&window.yt.config_?window.yt.config_.DATASYNC_ID:null)",
            )
            view.loadUrl(
                "javascript:Android.onRetrieveAuthUser(" +
                    "window.yt&&window.yt.config_?String(window.yt.config_.SESSION_INDEX||0):'0')",
            )

            val pageAuthData = withTimeoutOrNull(1000) {
                WebAuthData(
                    visitorData = visitorDataDeferred.await(),
                    dataSyncId = dataSyncIdDeferred.await(),
                    authUser = authUserDeferred.await().orEmpty(),
                )
            }
            hasAuthCookie = "SAPISID" in cookieMap
            val visitorData = pageAuthData?.visitorData
            hasVisitorData = !visitorData.isNullOrBlank()

            if (hasAuthCookie && pageAuthData != null && !visitorData.isNullOrBlank()) {
                return AuthData(
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = pageAuthData.dataSyncId.orEmpty().substringBefore("||"),
                    authUser = pageAuthData.authUser.filter(Char::isDigit).ifBlank { "0" },
                )
            }
            delay(500)
        }

        Timber.w("Login: Timed out waiting for YouTube session (authCookie=$hasAuthCookie, visitorData=$hasVisitorData)")
        return null
    }

    suspend fun finalizeLogin(authData: AuthData) {
        loginStage = LoginStage.Completing
        YouTube.cookie = authData.cookie
        YouTube.visitorData = authData.visitorData
        YouTube.dataSyncId = authData.dataSyncId
        YouTube.authUser = authData.authUser

        try {
            val accountInfo = YouTube.accountInfo().getOrThrow()
            withContext(Dispatchers.Main) {
                AccountPreferences.innerTubeCookie = authData.cookie
                AccountPreferences.visitorData = authData.visitorData
                AccountPreferences.dataSyncId = authData.dataSyncId
                AccountPreferences.innerTubeAuthUser = authData.authUser
                AccountPreferences.accountName = accountInfo.name
                AccountPreferences.accountEmail = accountInfo.email.orEmpty()
                AccountPreferences.accountChannelHandle = accountInfo.channelHandle.orEmpty()

                app.vitune.providers.innertube.Innertube.cookie = authData.cookie
                app.vitune.providers.innertube.Innertube.visitorData = authData.visitorData

                CookieManager.getInstance().flush()
                onDismiss()
            }
        } catch (throwable: Exception) {
            Timber.e(throwable, "Login: Failed to validate or save account info")
            errorMessage = throwable.message ?: context.getString(R.string.login_failed)
            loginStage = LoginStage.Authenticating
        }
    }

    suspend fun checkAvailableAccounts(authData: AuthData) {
        loginStage = LoginStage.CheckingAccounts
        YouTube.cookie = authData.cookie
        YouTube.visitorData = authData.visitorData
        YouTube.dataSyncId = authData.dataSyncId
        YouTube.authUser = authData.authUser

        val availableAccounts = YouTube.accountsList().getOrElse { throwable ->
            Timber.e(throwable, "Login: Failed to retrieve YouTube channels")
            emptyList()
        }

        if (availableAccounts.size > 1) {
            val currentAccount = availableAccounts.firstOrNull { account ->
                account.pageId == authData.dataSyncId ||
                    account.dataSyncId?.substringBefore("||") == authData.dataSyncId
            } ?: availableAccounts.firstOrNull { it.isSelected }

            accounts = availableAccounts.map { it.copy(isSelected = it == currentAccount) }
            selectedAccount = accounts.firstOrNull { it.isSelected } ?: accounts.first()
            loginStage = LoginStage.SelectingAccount
        } else {
            finalizeLogin(authData)
        }
    }

    fun handleAuthenticatedPage() {
        if (loginStage == LoginStage.Authenticating || loginStage == LoginStage.SwitchingAccount) {
            val stage = loginStage
            loginStage = LoginStage.CheckingAccounts
            coroutineScope.launch {
                val authData = extractAuthData(webViewRef) ?: run {
                    errorMessage = context.getString(R.string.login_failed)
                    loginStage = stage
                    return@launch
                }
                if (stage == LoginStage.SwitchingAccount) {
                    val selectedDataSyncId = selectedAccount?.dataSyncId?.substringBefore("||")
                    finalizeLogin(
                        authData.copy(
                            dataSyncId = selectedDataSyncId?.takeIf(String::isNotBlank) ?: authData.dataSyncId,
                        ),
                    )
                } else {
                    checkAvailableAccounts(authData)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorPalette.background0)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                title = stringResource(R.string.sign_in_youtube),
                actionsContent = {
                    HeaderIconButton(
                        icon = R.drawable.chevron_back,
                        color = colorPalette.text,
                        onClick = onDismiss
                    )
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { webViewContext ->
                        WebView(webViewContext).apply {
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    val scheme = request.url.scheme
                                    if (scheme == "http" || scheme == "https") return false
                                    if (scheme == "intent") {
                                        runCatching {
                                            Intent.parseUri(request.url.toString(), Intent.URI_INTENT_SCHEME)
                                                .apply {
                                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                                    component = null
                                                    selector = null
                                                }.let(context::startActivity)
                                        }
                                    }
                                    return true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    val pageUri = url?.let(Uri::parse)
                                    if (pageUri?.scheme == "https" && pageUri.host == "music.youtube.com") {
                                        handleAuthenticatedPage()
                                    }
                                }
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            addJavascriptInterface(jsInterface, "Android")
                            webViewRef = this
                            loadUrl("https://music.youtube.com")
                        }
                    }
                )

                if (loginStage != LoginStage.Authenticating) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorPalette.background0.copy(alpha = 0.85f))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            BasicText(
                                text = when (loginStage) {
                                    LoginStage.CheckingAccounts -> stringResource(R.string.login_stage_channels)
                                    LoginStage.SelectingAccount -> stringResource(R.string.select_channel)
                                    LoginStage.SwitchingAccount -> stringResource(R.string.login_stage_auth)
                                    LoginStage.Completing -> stringResource(R.string.login_stage_finishing)
                                    else -> stringResource(R.string.logging_in)
                                },
                                style = typography.s.medium.color(colorPalette.text)
                            )
                        }
                    }
                }
            }
        }

        if (loginStage == LoginStage.SelectingAccount && accounts.isNotEmpty()) {
            DefaultDialog(
                onDismiss = { loginStage = LoginStage.Authenticating }
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    BasicText(
                        text = stringResource(R.string.select_channel),
                        style = typography.m.bold.color(colorPalette.text)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    accounts.forEach { account ->
                        val isSelected = account == selectedAccount
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colorPalette.surface else colorPalette.background0)
                                .clickable {
                                    selectedAccount = account
                                    val authEndpoint = account.endpoint
                                    if (authEndpoint != null) {
                                        loginStage = LoginStage.SwitchingAccount
                                        webViewRef?.loadUrl(authEndpoint)
                                    } else {
                                        coroutineScope.launch {
                                            extractAuthData(webViewRef)?.let { authData ->
                                                val selDataSyncId = account.dataSyncId?.substringBefore("||")
                                                finalizeLogin(
                                                    authData.copy(
                                                        dataSyncId = selDataSyncId?.takeIf(String::isNotBlank) ?: authData.dataSyncId
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            account.thumbnailUrl?.let { thumb ->
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column {
                                BasicText(
                                    text = account.name,
                                    style = typography.s.semiBold.color(colorPalette.text),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                account.channelHandle?.let { handle ->
                                    BasicText(
                                        text = handle,
                                        style = typography.xs.secondary.color(colorPalette.textSecondary),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
