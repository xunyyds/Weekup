package com.xu.wake.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.selection.SelectionContainer
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.webkit.*
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xu.wake.data.SchoolImport
import com.xu.wake.data.SchoolParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONTokener



private val LOGIN_HOSTS = listOf("open.weixin.qq.com", "login.weixin.qq.com", "weixin.qq.com")

private fun loginHost(value: String): Boolean = runCatching {
    val host = Uri.parse(value).host.orEmpty().lowercase()
    LOGIN_HOSTS.any { host == it || host.endsWith("." + it) }
}.getOrDefault(false)

private fun schoolWebUrl(value: String): Boolean = runCatching {
    val uri = Uri.parse(value)
    (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
        !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

                                                                                                     
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
fun BrowserImport(url: String, onBack: () -> Unit, existingSchool: String = "", onImport: (SchoolImport) -> Unit) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current.browserActivity()
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation
    val compactToolbar = configuration.screenWidthDp < 380 || configuration.fontScale > 1.2f
    fun toggleOrientation() {
        activity?.requestedOrientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    val originalOrientation = remember(activity) { activity?.requestedOrientation }
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var showAddress by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    
    var chromeVisible by remember(url) { mutableStateOf(true) }
    


    var desktopMode by remember(url) { mutableStateOf(true) }
    
    val desktopModeState = rememberUpdatedState(desktopMode)
    


    val latestImport by rememberUpdatedState(onImport)
    var webView by remember(url) { mutableStateOf<WebView?>(null) }
    var loading by remember(url) { mutableStateOf(false) }
    var busy by remember(url) { mutableStateOf(false) }
    var error by remember(url) { mutableStateOf<String?>(if (schoolWebUrl(url)) null else "地址无效：仅允许不含账号密码的 HTTP / HTTPS 网站。") }
    var address by remember(url) { mutableStateOf(url) }
    var recognized by remember(url) { mutableStateOf<SchoolImport?>(null) }
    
    val lifetime = remember(url) { BrowserLifetime() }
    fun back() {
        val browser = webView
        if (browser != null && browser.canGoBack()) browser.goBack() else onBack()
    }
    BackHandler { if (recognized != null) recognized = null else back() }


    LaunchedEffect(configuration.screenWidthDp, configuration.screenHeightDp, configuration.orientation, desktopMode) {
        val browser = webView ?: return@LaunchedEffect
        if (!schoolWebUrl(browser.url.orEmpty())) return@LaunchedEffect


        browser.settings.apply {
            userAgentString = if (desktopMode) DESKTOP_CHROME_UA else null
            useWideViewPort = false
            loadWithOverviewMode = needsOverviewScale(browser.url.orEmpty())
        }
        browser.setInitialScale(0)
    }
    LaunchedEffect(error) {
        error?.let { message ->
            snackbar.showSnackbar(message, actionLabel = "关闭", duration = SnackbarDuration.Long)
            if (error == message) error = null
        }
    }
    DisposableEffect(activity) {
        onDispose {
            if (originalOrientation != null) activity?.requestedOrientation = originalOrientation
        }
    }
    DisposableEffect(lifetime) {
        onDispose {
            CookieManager.getInstance().flush()
            lifetime.alive = false
            lifetime.generation++
            lifetime.view?.let { browser ->
                CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true)
                browser.stopLoading()
                browser.webChromeClient = null
                browser.webViewClient = WebViewClient()
                browser.removeAllViews()
                browser.destroy()
            }
            lifetime.view = null
        }
    }
                                                                                          
    fun applyViewportScript(browser: WebView, desktop: Boolean) {
        val page = browser.url.orEmpty()
        if (!schoolWebUrl(page)) return


        if (desktop) browser.evaluateJavascript(DESKTOP_VIEWPORT_SCRIPT, null)
        browser.evaluateJavascript(CAS_FIT_SCRIPT, null)


        browser.evaluateJavascript(VH_HEIGHT_REPAIR_SCRIPT, null)
    }
    
    fun applyMode(browser: WebView, desktop: Boolean) {
        desktopMode = desktop
        
        browser.settings.apply {
            userAgentString = if (desktop) DESKTOP_CHROME_UA else null


            useWideViewPort = false


            loadWithOverviewMode = needsOverviewScale(browser.url.orEmpty())
        }
        browser.setInitialScale(0)
        applyViewportScript(browser, desktop)
    }
    
    fun switchViewMode() {
        val target = !desktopMode
        desktopMode = target
        
        lifetime.generation++
        recognized = null
        error = if (target) "已切换为桌面模式（桌面版 UA + 宽布局视口），正在重新加载。"
        else "已切换为手机模式（移动版 UA + 页面原始 viewport），正在重新加载。"
        val browser = webView ?: return
        applyMode(browser, target)
        val current = browser.url?.takeIf { schoolWebUrl(it) } ?: url
        browser.loadUrl(current)
    }
    fun extract() {
        val browser = webView ?: return
        if (busy || loading || !schoolWebUrl(browser.url.orEmpty())) return
        busy = true
        error = null
        val generation = lifetime.generation
        scope.launch {
            try {


                val imported = withTimeout(45_000) {
                    var failure: Exception? = null
                    var observed = ""
                    var attempt = 0
                    var result: SchoolImport? = null
                    while (result == null && attempt < 8) {
                        try {
                            val raw = kotlinx.coroutines.suspendCancellableCoroutine<String> { continuation ->
                                browser.evaluateJavascript(TABLE_EXTRACTION) { value ->
                                    if (continuation.isActive) continuation.resumeWith(Result.success(value ?: "null"))
                                }
                            }
                            result = withContext(Dispatchers.Default) {
                                require(raw.length <= 12_000_000) { "课表页面过大，请打开单独的课表页后重试" }
                                val decoded = JSONTokener(raw).nextValue()
                                require(decoded is String && decoded.isNotBlank()) { "当前页面没有可读取的课表 table；请先登录并打开课表页面" }
                                require(decoded != "XWAKE_TABLE_TOO_LARGE") { "课表页面过大" }
                                SchoolParser.parse(decoded, existingSchool)
                            }
                        } catch (e: Exception) {
                            failure = e
                            observed += "#" + (attempt + 1) + " " + (e.message ?: "?").take(36) + " | "
                            if (attempt == 0) browser.evaluateJavascript(OPEN_TIMETABLE_SCRIPT, null)
                            delay(1500)
                        }
                        attempt += 1
                    }
                    result ?: throw IllegalStateException((failure?.message ?: "未识别到课程") + " 尝试记录：" + observed)
                }
                if (lifetime.alive && lifetime.generation == generation) recognized = imported
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (lifetime.alive && lifetime.generation == generation) error = "读取超时，请等待网页加载完成后重试。"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (lifetime.alive && lifetime.generation == generation) error = "未导入：${e.message ?: "课表结构无法识别，请打开标准课表 table 后重试"}"
            } finally {
                if (lifetime.alive) busy = false
            }
        }
    }
    Box(Modifier.fillMaxSize().imePadding()) {
    Column(Modifier.fillMaxSize()) {
        
        
        
        Column(Modifier.fillMaxWidth().animateContentSize()) {
        if (chromeVisible) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::back, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("返回", maxLines = 1) }
            if (!compactToolbar) {
                
                TextButton(onClick = ::switchViewMode, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if (desktopMode) "桌面" else "手机", maxLines = 1)
                }
                TextButton(onClick = { webView?.reload() }, enabled = webView != null,
                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text("刷新", maxLines = 1) }
                TextButton(onClick = ::toggleOrientation, enabled = activity != null,
                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text("横/竖屏", maxLines = 1) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = ::extract, enabled = webView != null && !busy && !loading,
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (busy) "识别中" else "读取课表", maxLines = 1)
            }
            Box {
                TextButton(onClick = { menuOpen = true }, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(40.dp)) { Text("⋮") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (compactToolbar) {
                        DropdownMenuItem(text = { Text(if (desktopMode) "手机模式" else "桌面模式") },
                            onClick = { menuOpen = false; switchViewMode() })
                        DropdownMenuItem(text = { Text("刷新") }, enabled = webView != null,
                            onClick = { menuOpen = false; webView?.reload() })
                        DropdownMenuItem(text = { Text("横/竖屏") }, enabled = activity != null,
                            onClick = { menuOpen = false; toggleOrientation() })
                    }
                    DropdownMenuItem(text = { Text("清除登录会话") }, onClick = { menuOpen = false; confirmClear = true })
                    DropdownMenuItem(text = { Text("隐藏工具栏") }, onClick = {
                        menuOpen = false
                        chromeVisible = false
                        error = "已隐藏工具栏与地址栏；在页面上向下滑或点按屏幕顶部即可恢复。"
                    })
                }
            }
        }
        Text(address, Modifier.fillMaxWidth().clickable { showAddress = true }.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loading && chromeVisible) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (schoolWebUrl(url)) key(url) { AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                DesktopSchoolWebView(context, onSwipe = { upward ->
                    
                    chromeVisible = !upward
                }).apply {
                    
                    var lastPageHeightPx = -1


                    if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }
                    lifetime.view = this
                    webView = this
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    isHorizontalScrollBarEnabled = true
                    isVerticalScrollBarEnabled = true
                    scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                    
                    val desktop = desktopModeState.value
                    setInitialScale(0)
                    settings.apply {
                        userAgentString = if (desktop) DESKTOP_CHROME_UA else null


                        useWideViewPort = false
                        loadWithOverviewMode = needsOverviewScale(url)
                        javaScriptEnabled = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        domStorageEnabled = true
                        databaseEnabled = false
                        saveFormData = false
                        savePassword = false
                        allowFileAccess = false
                        allowContentAccess = false

                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)


                        mediaPlaybackRequiresUserGesture = true
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    setDownloadListener { _, _, _, _, _ -> error = "不支持下载或外部跳转，请直接打开网页课表。" }


                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message,
                        ): Boolean {
                            val popup = WebView(view.context)
                            popup.settings.javaScriptEnabled = true
                            popup.settings.domStorageEnabled = true
                            popup.settings.userAgentString = view.settings.userAgentString
                            CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true)
                            popup.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    v: WebView,
                                    request: WebResourceRequest,
                                ): Boolean = false
                            }


                            popup.webChromeClient = object : WebChromeClient() {
                                override fun onCloseWindow(window: WebView) {
                                    runCatching { (window.parent as? android.view.ViewGroup)?.removeView(window) }
                                    runCatching { window.destroy() }
                                }
                            }
                            val container = android.widget.FrameLayout(view.context)
                            container.addView(popup)
                            (view.parent as? android.view.ViewGroup)?.addView(container)
                            val transport = resultMsg.obj as WebView.WebViewTransport
                            transport.webView = popup
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = block(request.url.toString())
                        override fun shouldOverrideUrlLoading(view: WebView, target: String): Boolean = block(target)
                        

                        private fun block(target: String): Boolean {
                            if (schoolWebUrl(target)) return false
                            if (lifetime.alive) error = "已阻止非 HTTP / HTTPS 跳转或含账号密码的地址。"
                            return true
                        }
                        override fun onPageStarted(view: WebView, target: String?, favicon: Bitmap?) {
                            if (!lifetime.alive) return


                            applyViewportScript(view, desktopModeState.value)
                            lifetime.generation++
                            recognized = null
                            
                            lastPageHeightPx = -1


                            if (!schoolWebUrl(target.orEmpty()) && !loginHost(target.orEmpty())) {
                                view.stopLoading()
                                loading = false
                                error = "已阻止不安全的页面地址。"
                                return
                            }
                            address = target.orEmpty()
                            loading = true
                            error = null
                        }
                        override fun onPageFinished(view: WebView, target: String?) {
                            if (!lifetime.alive) return
                            loading = false
                            if (schoolWebUrl(target.orEmpty())) {
                                address = target.orEmpty()
                                
                                applyViewportScript(view, desktopModeState.value)

                                view.evaluateJavascript(PAGE_HEIGHT_SCRIPT) { height ->
                                    if (!lifetime.alive) return@evaluateJavascript
                                    val pageHeight = (height ?: "null").trim().trim('"').toIntOrNull() ?: -1
                                    val grew = pageHeight > 0 &&
                                        (lastPageHeightPx <= 0 || kotlin.math.abs(pageHeight - lastPageHeightPx) >= 40)
                                    lastPageHeightPx = pageHeight
                                    if (grew) {
                                        view.scrollTo(0, 0)
                                        if (view.scrollY != 0) view.scrollTo(0, 0)
                                    }
                                }
                            }
                            CookieManager.getInstance().flush()
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                            if (lifetime.alive && request.isForMainFrame) {
                                lifetime.generation++
                                recognized = null
                                loading = false
                                error = "网页加载失败（${failure.errorCode}），请检查网络和教务地址后重新加载。"
                            }
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (lifetime.alive && request.isForMainFrame) {
                                lifetime.generation++
                                loading = false
                                error = "教务服务器请求失败（HTTP ${response.statusCode}），请稍后重新加载。"
                            }
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: SslError) {
                            handler.cancel() 
                            if (lifetime.alive) {
                                lifetime.generation++
                                view.stopLoading()
                                loading = false
                                error = "网站安全证书校验失败，已停止加载。请联系学校处理，不能绕过证书错误。"
                            }
                        }
                        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                            handler.cancel()
                            if (lifetime.alive) error = "不支持保存或代填 HTTP 身份凭据，请使用学校网页登录。"
                        }
                        override fun onFormResubmission(view: WebView, dontResend: android.os.Message, resend: android.os.Message) {
                            dontResend.sendToTarget()
                            if (lifetime.alive) error = "已阻止自动重复提交表单，请在网页内重新操作。"
                        }
                    }
                    loadUrl(url)
                }
            },
        ) } else Spacer(Modifier.weight(1f))
    }
    
    
    
    if (!chromeVisible) Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(33.dp).background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    androidx.compose.ui.graphics.Color.Transparent,
                )
            )
        )
    )
    if (!chromeVisible) Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(18.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { chromeVisible = true }
    )
    if (!chromeVisible && loading) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp))
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }
    if (showAddress) AlertDialog(
        onDismissRequest = { showAddress = false },
        title = { Text("当前链接") },
        text = { SelectionContainer { Text(address, Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = { showAddress = false }) { Text("关闭") } },
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清除登录会话？") },
        text = { Text("将清除应用内 WebView 的全部 Cookie、网页本地存储及缓存，可能退出其他学校网站的登录。无法撤销。") },
        confirmButton = { TextButton(onClick = {
            confirmClear = false
            val browser = webView
            browser?.stopLoading()
            lifetime.generation++
            recognized = null
            loading = false
            browser?.let { CookieManager.getInstance().setAcceptThirdPartyCookies(it, true) }
            WebStorage.getInstance().deleteAllData()
            browser?.clearCache(true)
            browser?.clearFormData()
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                if (lifetime.alive) {
                    
                    browser?.loadUrl(url)
                    error = "已清除登录会话、网页存储和缓存。"
                }
            }
        }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
    recognized?.let { imported ->
        var manualSchool by remember(imported) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { recognized = null },
            title = { Text("核对并确认导入") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("识别到 ${imported.courses.size} 条课程。确认后交由本地课表导入处理，可能替换现有课程；请先核对。")
                    if (imported.school != null) {
                        Text(if (existingSchool.isNotBlank()) "学校：${imported.school}（保留已有学校）" else "识别学校：${imported.school}")
                    } else {
                        Text("未可靠识别学校，可在确认时补填；留空不会覆盖已有学校。")
                        OutlinedTextField(value = manualSchool, onValueChange = { manualSchool = it.take(100) },
                            label = { Text("学校名称（选填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Text(imported.weekCount?.let { "学期共 $it 周" } ?: "未识别总周数，不会凭空生成学期数据。")
                    Text(imported.currentWeek?.let { "当前第 $it 周" } ?: "未识别当前周，请核对本地设置。")
                    Text(
                        if (imported.periods.isEmpty()) "未识别作息，请保留并核对本地时间。"
                        else "识别到 " + imported.periods.size + " 节作息：" +
                            imported.periods.joinToString("、") { clock(it.startMinutes) + "–" + clock(it.endMinutes) }
                    )
                    imported.courses.forEach { course ->
                        Text("${course.name} · 星期${"一二三四五六日"[course.weekday - 1]} · ${course.startPeriod}–${course.endPeriod}节\n周次：${course.weeks.joinToString()}\n教师：${course.teacher.ifBlank { "未识别" }}；地点：${course.location.ifBlank { "未识别" }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                recognized = null
                latestImport(imported.copy(school = imported.school ?: manualSchool.trim().takeIf { it.isNotEmpty() }))
            }) { Text("确认导入") } },
            dismissButton = { TextButton(onClick = { recognized = null }) { Text("取消") } },
        )
    }
}

                                                                                                   
@SuppressLint("ViewConstructor")
private class DesktopSchoolWebView(context: Context, private val onSwipe: (upward: Boolean) -> Unit) : WebView(context) {
    private var downX = 0f
    private var downY = 0f
    private var startedAt = 0L
    private var multiTouch = false
    private var swiped = false

    
    
    
    private var gestureLock = GESTURE_UNDECIDED
    private var prevCenterX = 0f
    private var prevCenterY = 0f
    private var prevSpan = 0f
    private var totalDx = 0f
    private var totalDy = 0f
    private var totalPinch = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                startedAt = SystemClock.uptimeMillis()
                multiTouch = false
                swiped = false
                
                
                parent?.requestDisallowInterceptTouchEvent(true)
                beginGesture()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                
                
                requestDisallowInterceptTouchEvent(false)
                
                if (gestureLock == GESTURE_NONE) {
                    beginGesture()
                    seedCenter(event)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                
                
                if (gestureLock == GESTURE_PAN) {
                    val lifted = event.actionIndex
                    val keep = if (lifted == 0) 1 else 0
                    if (keep < event.pointerCount) {
                        prevCenterX = event.getX(keep)
                        prevCenterY = event.getY(keep)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureLock == GESTURE_PAN) {
                    
                    
                    if (event.pointerCount >= 2) {
                        val cx = (event.getX(0) + event.getX(1)) / 2f
                        val cy = (event.getY(0) + event.getY(1)) / 2f
                        scrollBy(-(cx - prevCenterX).toInt(), -(cy - prevCenterY).toInt())
                        prevCenterX = cx
                        prevCenterY = cy
                    } else {
                        
                        scrollBy(-(event.x - prevCenterX).toInt(), -(event.y - prevCenterY).toInt())
                        prevCenterX = event.x
                        prevCenterY = event.y
                    }
                    
                    
                    return true
                }
                if (gestureLock != GESTURE_PINCH && event.pointerCount >= 2) {
                    
                    val cx = (event.getX(0) + event.getX(1)) / 2f
                    val cy = (event.getY(0) + event.getY(1)) / 2f
                    val span = kotlin.math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
                    
                    if (gestureLock == GESTURE_UNDECIDED) {
                        totalDx += cx - prevCenterX
                        totalDy += cy - prevCenterY
                        totalPinch += span - prevSpan
                        
                        
                        if (kotlin.math.hypot(totalDx, totalDy) >= TWO_FINGER_PAN_THRESHOLD_PX ||
                            kotlin.math.abs(totalPinch) >= TWO_FINGER_PAN_THRESHOLD_PX
                        ) {
                            val pan = kotlin.math.abs(totalPinch) <
                                kotlin.math.hypot(totalDx, totalDy) && canPanContent()
                            
                            gestureLock = if (pan) GESTURE_PAN else GESTURE_PINCH
                            
                            swiped = true
                            
                            
                            requestDisallowInterceptTouchEvent(gestureLock == GESTURE_PAN)
                        }
                    }
                    prevCenterX = cx
                    prevCenterY = cy
                    prevSpan = span
                    
                    if (gestureLock == GESTURE_PAN) return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val handledByPan = gestureLock == GESTURE_PAN
                gestureLock = GESTURE_NONE
                val elapsed = SystemClock.uptimeMillis() - startedAt
                val dy = event.y - downY
                val dx = event.x - downX
                
                
                if (multiTouch || handledByPan || elapsed > 1200L || kotlin.math.abs(dy) < 60f || kotlin.math.abs(dy) < kotlin.math.abs(dx)) {
                    swiped = true
                } else {
                    onSwipe(dy < 0f)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureLock = GESTURE_NONE
                swiped = true
            }
        }
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    private fun beginGesture() {
        gestureLock = GESTURE_NONE
        totalDx = 0f
        totalDy = 0f
        totalPinch = 0f
    }

    private fun seedCenter(event: MotionEvent) {
        prevCenterX = (event.getX(0) + event.getX(1)) / 2f
        prevCenterY = (event.getY(0) + event.getY(1)) / 2f
        prevSpan = kotlin.math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
        gestureLock = GESTURE_UNDECIDED
        swiped = true
    }

    
    
    private fun canPanContent(): Boolean =
        canScrollVertically(1) || canScrollVertically(-1) || canScrollHorizontally(1) || canScrollHorizontally(-1)

    private companion object {
        const val GESTURE_NONE = 0
        const val GESTURE_UNDECIDED = 1
        const val GESTURE_PAN = 2
        const val GESTURE_PINCH = 3
        
        
        const val TWO_FINGER_PAN_THRESHOLD_PX = 16f
    }
}

private fun Context.browserActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.browserActivity() else null
    else -> null
}




private val PAGE_HEIGHT_SCRIPT = """
(function() {
  var d = document.documentElement;
  var b = document.body;
  var h = Math.max(d ? d.scrollHeight : 0, b ? b.scrollHeight : 0, window.innerHeight || 0);
  return String(Math.round(h));
})()
""".trimIndent()








private const val DESKTOP_VIEWPORT_WIDTH = 700



private val VH_HEIGHT_REPAIR_SCRIPT = """
(function() {
  function apply() {
    try {
      var H = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0);
      if (!H) return false;
      var host = document.head || document.documentElement;
      if (!host) return false;
      
      var shell = document.querySelector('.edu-container, .edu-container-new, [class*="edu-container"]');
      if (!shell) return true;
      var px = H + 'px';
      var css = ''
        + 'html,body{height:' + px + '!important;min-height:' + px + '!important;margin:0!important;}'
        + '.edu-container,.edu-container-new,[class*="edu-container"]{height:' + px
        + '  !important;min-height:' + px + '!important;display:flex!important;}'
        + '.edu-aside,.edu-main{height:' + px + '!important;display:flex!important;flex-direction:column!important;}'
        + '.edu-center{display:flex!important;flex-direction:column!important;height:100%!important;}'
        + '#edu-cm{flex:1 1 auto!important;min-height:0!important;height:auto!important;display:block!important;overflow:hidden!important;}'
        + '#Frame0{display:block!important;width:100%!important;height:100%!important;border:0!important;}';
      var el = document.getElementById('__xwake_vh_fix');
      if (!el) {
        el = document.createElement('style');
        el.id = '__xwake_vh_fix';
        host.appendChild(el);
      }
      if (el.textContent !== css) el.textContent = css;
      return true;
    } catch (e) { return false; }
  }
  if (apply()) return 'ok';
  var tries = 0;
  var timer = setInterval(function() {
    tries += 1;
    if (apply() || tries > 40) clearInterval(timer);
  }, 50);
  return 'deferred';
})()
""".trimIndent()



private fun needsOverviewScale(url: String): Boolean = runCatching {
    val path = Uri.parse(url).path.orEmpty().lowercase()
    path.contains("/cas/") || path.endsWith("sso.jsp")
}.getOrDefault(false)



private val DESKTOP_VIEWPORT_SCRIPT = """
(function() {
  
  var CONTENT = 'width=@@W@@';

  function isFixed() {
    try {
      if (document.querySelector('.login-card, .login-card-wrapper, [class*="login-card"]')) return true;
      var body = document.body;
      if (!body) return null;
      var s = window.getComputedStyle(body);
      if (!s) return null;
      var pinned = (s.overflow === 'hidden' || s.overflowY === 'hidden');
      var cardish = document.querySelector('[class*="card"], [class*="login"], form');
      return (pinned && !!cardish) ? true : false;
    } catch (e) { return false; }
  }

  function apply() {
    var host = document.head || document.documentElement;
    if (!host) return 'skip';
    if (!isFixed()) {
      
      var mine = document.querySelector('meta[data-xwake-viewport]');
      if (mine && mine.parentNode) mine.parentNode.removeChild(mine);
      return 'skip';
    }
    try {
      var meta = document.querySelector('meta[name="viewport" i]');
      if (!meta) {
        meta = document.createElement('meta');
        meta.setAttribute('name', 'viewport');
        meta.setAttribute('data-xwake-viewport', '1');
        host.appendChild(meta);
      }
      if (meta.getAttribute('content') !== CONTENT) meta.setAttribute('content', CONTENT);
      return 'fixed';
    } catch (e) { return 'skip'; }
  }

  
  var verdict = apply();
  if (verdict === 'fixed') return verdict;
  var tries = 0;
  var timer = setInterval(function() {
    tries += 1;
    if (apply() === 'fixed') { clearInterval(timer); return; }
    if (tries > 40) clearInterval(timer);
  }, 50);
  return verdict;
})()
""".trimIndent().replace("@@W@@", DESKTOP_VIEWPORT_WIDTH.toString())

private val CAS_FIT_SCRIPT = """
(function() {
  
  var CSS = ''
    + 'html,body{height:auto!important;min-height:100%!important;overflow-x:hidden!important;'
    + '  overflow-y:auto!important;}'
    + '.bg,.bg-overlay{position:fixed!important;top:0!important;left:0!important;'
    + '  width:100%!important;height:100%!important;}'
    + 'body{display:flex!important;flex-direction:column!important;align-items:center!important;'
    + '  justify-content:flex-start!important;padding:16px 0 24px!important;box-sizing:border-box!important;}'
    + '.login-card{position:static!important;transform:none!important;margin:0 auto!important;'
    + '  width:min(624px,94vw)!important;box-sizing:border-box!important;}'
    + '.login-form{position:static!important;transform:none!important;left:auto!important;'
    + '  width:min(304px,100%)!important;margin:0 auto!important;}'
    + '.header{position:static!important;margin-bottom:8px!important;}'
    + '.footer{position:static!important;margin-top:16px!important;}';

  function apply() {
    try {
      var card = document.querySelector('.login-card, .login-card-wrapper, [class*="login-card"]');
      if (!card) return false;
      var host = document.head || document.documentElement;
      if (!host) return false;
      var el = document.getElementById('__xwake_sso_fit');
      if (!el) {
        el = document.createElement('style');
        el.id = '__xwake_sso_fit';
        host.appendChild(el);
      }
      if (el.textContent !== CSS) el.textContent = CSS;
      return true;
    } catch (e) { return false; }
  }

  if (apply()) return 'ok';
  var tries = 0;
  var timer = setInterval(function() {
    tries += 1;
    if (apply() || tries > 40) clearInterval(timer);
  }, 50);
  return 'deferred';
})()
""".trimIndent()



private class BrowserLifetime {
    var alive = true
    var generation = 0
    var view: WebView? = null
}

private const val DESKTOP_CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"





private val TABLE_EXTRACTION = """
(function() {
  function escape(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function visible(e) {
    for (var n = e; n && n.nodeType === 1; n = n.parentElement) {
      var w = (n.ownerDocument && n.ownerDocument.defaultView) || window;
      var s = w.getComputedStyle(n);
      if (n.hidden || n.getAttribute('aria-hidden') === 'true' || s.display === 'none' || s.visibility === 'hidden' || s.visibility === 'collapse' || s.opacity === '0') return false;
    }
    return true;
  }
  function clean(e) {
    var clone = e.cloneNode(true);
    clone.querySelectorAll('input,textarea,select,button,form,script,style,iframe,object,embed,svg,img,video,audio,[contenteditable]').forEach(function(n) { n.remove(); });
    return clone;
  }
  function visibleText(e) {
    if (!visible(e) || e.closest('form,input,textarea,select,button,[contenteditable]')) return '';
    var text = '';
    Array.from(e.childNodes).forEach(function(n) {
      if (n.nodeType === 3) text += n.textContent + ' ';
      else if (n.nodeType === 1 && !/^(INPUT|TEXTAREA|SELECT|BUTTON|FORM|SCRIPT|STYLE|IFRAME|OBJECT|EMBED|SVG)$/.test(n.tagName)) text += visibleText(n);
    });
    return text.slice(0, 4096);
  }
  var html = '<title>' + escape(document.title.slice(0, 512)) + '</title>';
  Array.from(document.querySelectorAll('header,h1')).slice(0, 30).forEach(function(e) {
    html += '<h1>' + escape(visibleText(e)) + '</h1>';
  });
  Array.from(document.querySelectorAll('meta[name],meta[property]')).forEach(function(e) {
    var key = (e.getAttribute('name') || e.getAttribute('property') || '').toLowerCase();
    if (['school','school-name','school_name','university','og:site_name'].indexOf(key) >= 0) html += '<meta name="' + escape(key) + '" content="' + escape((e.getAttribute('content') || '').slice(0, 512)) + '">';
  });
  function collectTables(doc, acc) {
    try {
      Array.from(doc.querySelectorAll('table')).forEach(function(t) { acc.push(t) });
      Array.from(doc.querySelectorAll('iframe,frame')).forEach(function(f) {
        var sub = null;
        try { sub = f.contentDocument } catch (e) { sub = null; }
        if (sub) collectTables(sub, acc);
      });
    } catch (e) { /* cross-origin frame: skip */ }
    return acc;
  }
  var tables = collectTables(document, []).filter(function(t) {
    return !(t.parentElement && t.parentElement.closest('table')) && !t.closest('form') && /星期|周[一二三四五六日天]/.test(clean(t).textContent || '');
  });
  for (var i = 0; i < tables.length; i++) {
    Array.from(tables[i].querySelectorAll('[class*="kbcontent1"]')).forEach(function(n) {
      if (n.parentNode) n.parentNode.removeChild(n);
    });
    var table = clean(tables[i]);
    [table].concat(Array.from(table.querySelectorAll('*'))).forEach(function(e) {
      Array.from(e.attributes).forEach(function(a) {
        var key = a.name.toLowerCase();
        if (key === 'class' && e.classList.contains('kbcontent')) e.setAttribute('class', 'kbcontent');
        else if (key === 'title' && ['老师','教师','教室','地点'].indexOf(a.value) >= 0) { /* parser labels only */ }
        else if (key !== 'rowspan' && key !== 'colspan') e.removeAttribute(a.name);
      });
    });
    html += table.outerHTML;
    if (html.length > 2000000) return 'XWAKE_TABLE_TOO_LARGE';
  }
  return html;
})()
""".trimIndent()



private val OPEN_TIMETABLE_SCRIPT = """
(function() {
  function label(e) { return (e.textContent || '').replace(/\s+/g, ' ').trim(); }
  var best = null;
  var nodes = document.querySelectorAll('a,li,span,div');
  for (var i = 0; i < nodes.length; i++) {
    var e = nodes[i];
    var t = label(e);
    if (!t || t.length > 24) continue;
    if (t.indexOf('课表') < 0) continue;
    var href = e.getAttribute('href') || '';
    var oc = e.getAttribute('onclick') || '';
    if (!oc && (!href || href === '#')) continue;
    var score = 0;
    if (t.indexOf('理论课表') >= 0) score += 4;
    if (t.indexOf('学期') >= 0) score += 2;
    if (t.indexOf('我的课表') >= 0) score += 1;
    if (!best || score > best.score) best = { el: e, score: score, text: t };
  }
  if (!best) return 'none';
  best.el.click();
  return 'clicked:' + best.text;
})()
""".trimIndent()
