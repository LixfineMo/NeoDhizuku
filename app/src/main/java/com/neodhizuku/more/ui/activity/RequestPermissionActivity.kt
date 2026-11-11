package com.neodhizuku.more.ui.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R
import com.rosan.dhizuku.aidl.IDhizukuRequestPermissionListener
import com.neodhizuku.more.data.common.util.getPackageInfoForUid
import com.neodhizuku.more.data.common.util.signature
import com.neodhizuku.more.data.settings.model.room.entity.AppEntity
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.data.settings.repo.PermissionStrikes
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.neodhizuku.more.server.PermissionCache
import com.neodhizuku.more.server.ClientApiVersion
import com.neodhizuku.more.server.ServiceStatus
import com.neodhizuku.more.server.RequestPermissionToken
import com.neodhizuku.more.ui.component.ClientApiBadge
import com.rosan.dhizuku.shared.DhizukuVariables
import com.neodhizuku.more.ui.theme.DhizukuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin

class RequestPermissionActivity : ComponentActivity(), KoinComponent {
    companion object {
        const val UID_ERR = -1
        const val AUTO_DENY_SECONDS = 15

        /** 抽屉高度上限占屏幕的比例，内容少时抽屉按内容收窄 */
        private const val SHEET_MAX_HEIGHT_FRACTION = 0.72f

        /** 抽屉入场动画时长 */
        private const val SHEET_ENTER_MS = 280

        /** 向下拖动超过这个距离松手，即判定为「收起并拒绝」 */
        private const val DISMISS_DRAG_DP = 120

        /** 遮罩透明度 */
        private const val SCRIM_ALPHA = 0.32f

        /** 底部按钮距屏幕底边的距离（不含导航栏高度，由 navigationBarsPadding 另加） */
        private const val BOTTOM_BUTTON_MARGIN_DP = 24

        private const val KEY_UID = "neo_uid"
        private const val KEY_TOKEN = "neo_token"
        private const val KEY_TIME_LEFT = "neo_time_left"
        private const val KEY_TRUSTED = "neo_trusted"
        private const val KEY_BLOCKLIST_COUNTDOWN = "neo_blocklist_countdown"
    }

    data class ViewState(
        val uid: Int = UID_ERR,
        val allowApi: Boolean = false,
        val signature: String = "",
        val listener: IDhizukuRequestPermissionListener? = null,
        val timeLeft: Int = AUTO_DENY_SECONDS,
        val timedOut: Boolean = false,
        /** 本次是否为「黑名单倒计时」：连击达到上限后，倒计时结束直接拉黑而非仅拒绝 */
        val blacklistCountdown: Boolean = false,
        val shouldShowDialog: Boolean = true,
        /** 服务端下发的一次性令牌，非空表示本次请求由服务端发起、uid 可信 */
        val serverToken: String? = null
    )

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val appRepo by inject<AppRepo>()
    private val settingsRepo by inject<SettingsRepo>()
    /** 「超时未处理」连击计数，用于判断是否升级为黑名单倒计时 */
    private val strikes by lazy { PermissionStrikes(filesDir) }
    private var state by mutableStateOf(ViewState())

    /** 保证授权结果只回调一次 */
    private val resultSent = AtomicBoolean(false)

    /** 是否已做过明确选择（用于区分"超时/取消"与"用户点了同意"） */
    @Volatile
    private var userDecided: Boolean = false

    /** 用户是否点了「加入黑名单」（拉黑后不处理该应用的授权请求） */
    @Volatile
    private var blockCaller: Boolean = false

    private val neoPermissionWrite get() = NeoDhizuku.on(NeoDhizuku.FixPermissionResultWrite)
    private val neoOnPause get() = NeoDhizuku.on(NeoDhizuku.FixPermissionOnPause)
    private val neoFinishInCompose get() = NeoDhizuku.on(NeoDhizuku.FixPermissionFinishInCompose)
    private val neoHardenedEntry get() = NeoDhizuku.on(NeoDhizuku.HardenPermissionEntry)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 重建（旋转 / 深色模式 / 语言切换）时恢复倒计时与身份，避免 state 归零
        if (NeoDhizuku.on(NeoDhizuku.FixPermissionSavedState) && savedInstanceState != null) {
            val savedUid = savedInstanceState.getInt(KEY_UID, UID_ERR)
            val savedToken = savedInstanceState.getString(KEY_TOKEN)
            val savedTime = savedInstanceState.getInt(KEY_TIME_LEFT, AUTO_DENY_SECONDS)
            if (savedUid != UID_ERR) {
                state = state.copy(
                    uid = savedUid,
                    serverToken = savedToken,
                    timeLeft = savedTime,
                    blacklistCountdown = savedInstanceState.getBoolean(
                        KEY_BLOCKLIST_COUNTDOWN, false
                    ),
                    signature = packageManager.getPackageInfoForUid(savedUid)?.signature ?: ""
                )
            }
        }

        NeoLog.i(NeoLog.PERM) {
            "activity: onCreate action=${intent?.action} " +
                "uid=${intent?.extras?.getInt(DhizukuVariables.PARAM_CLIENT_UID, -1)}"
        }

        if (!registerAppEntity(intent)) {
            NeoLog.w(NeoLog.PERM) { "activity: 身份登记失败，不弹窗" }
            finish()
            return
        }

        NeoLog.i(NeoLog.PERM) { "activity: 身份登记成功 uid=${state.uid}" }

        // Check if dhizuku is enabled and app is not blocked
        coroutineScope.launch {
            if (!settingsRepo.isDhizukuEnabled) {
                NeoLog.w(NeoLog.PERM) { "activity: 不弹窗（API 已停用）uid=${state.uid}" }
                state = state.copy(allowApi = false, timedOut = true, shouldShowDialog = false)
                finish()
                return@launch
            }

            val entity = appRepo.findByUID(state.uid)

            if (settingsRepo.isWhitelistMode && (entity == null || !entity.allowApi)) {
                NeoLog.w(NeoLog.PERM) { "activity: 不弹窗（白名单模式未授权）uid=${state.uid}" }
                state = state.copy(allowApi = false, timedOut = true, shouldShowDialog = false)
                finish()
                return@launch
            }

            if (entity?.blocked == true) {
                NeoLog.w(NeoLog.PERM) { "activity: 不弹窗（已拉黑）uid=${state.uid}" }
                state = state.copy(allowApi = false, timedOut = true, shouldShowDialog = false)
                finish()
                return@launch
            }

            // 连击判定：该应用此前已连续 LIMIT 次「弹了但没人管」，
            // 本次倒计时结束时直接拉黑。
            val pkgName = runCatching {
                packageManager.getPackageInfoForUid(state.uid)?.packageName
            }.getOrNull()
            if (!pkgName.isNullOrEmpty() && strikes.count(pkgName) >= PermissionStrikes.LIMIT) {
                NeoLog.w(NeoLog.PERM) {
                    "activity: 已连续 ${PermissionStrikes.LIMIT} 次超时未处理，" +
                        "本次升级为黑名单倒计时 uid=${state.uid} ($pkgName)"
                }
                state = state.copy(blacklistCountdown = true)
            }

            if (entity?.allowApi == true && entity.signature == state.signature) {
                NeoLog.i(NeoLog.PERM) { "activity: 已授权且签名一致，直接放行 uid=${state.uid}" }
                state = state.copy(allowApi = true, timedOut = false, shouldShowDialog = false)
                finish()
                return@launch
            }

            NeoLog.i(NeoLog.PERM) { "activity: 弹窗 uid=${state.uid} showDialog=${state.shouldShowDialog}" }
        }

        setContent {
            DhizukuTheme {
                if (state.shouldShowDialog) {
                    LaunchedEffect(Unit) {
                        repeat(AUTO_DENY_SECONDS) { second ->
                            delay(1000)
                            state = state.copy(timeLeft = AUTO_DENY_SECONDS - second - 1)
                        }
                        if (!state.timedOut) {
                            // 黑名单倒计时：结束时不是「拒绝」，而是直接拉黑
                            if (state.blacklistCountdown) blockCaller = true
                            state = state.copy(allowApi = false, timedOut = true)
                            finish()
                        }
                    }
                }

                if (neoFinishInCompose) {
                    // 不在组合阶段直接 finish()，改为副作用
                    LaunchedEffect(state.shouldShowDialog, state.uid) {
                        if (!state.shouldShowDialog) finish()
                        else if (packageManager.getPackageInfoForUid(state.uid) == null) finish()
                    }
                    if (state.shouldShowDialog) {
                        if (!showDialog()) LaunchedEffect(Unit) { finish() }
                    }
                } else {
                    if (state.shouldShowDialog && !showDialog()) finish()
                    if (!state.shouldShowDialog) finish()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!NeoDhizuku.on(NeoDhizuku.FixPermissionSavedState)) return
        outState.putInt(KEY_UID, state.uid)
        outState.putString(KEY_TOKEN, state.serverToken)
        outState.putInt(KEY_TIME_LEFT, state.timeLeft)
        outState.putBoolean(KEY_BLOCKLIST_COUNTDOWN, state.blacklistCountdown)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (registerAppEntity(intent)) return
        finish()
    }

    override fun onDestroy() {
        // 兜底回收：正常路径下 token 已在 dispatchResult 中移除，
        // 但进程被杀 / 异常退出等路径走不到那里，残留记录会一直持有跨进程
        // listener 引用。这里无条件再清一次，重复清理无害。
        runCatching { RequestPermissionToken.revoke(state.serverToken) }
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        if (neoOnPause) {
            // 切后台 / 来电 / 通知栏下拉 / 其它弹窗覆盖都不应当被判为"拒绝"。
            // 只有真正结束（用户取消、超时、已做出选择）时才在 finish() 中处理。
            return
        }
        finish()
    }

    override fun finish() {
        if (neoPermissionWrite) {
            // 同步落库 + 结果只回调一次。
            // 异步写库在进程被回收时会丢失，用户点了"同意"下次仍弹窗；
            // 且 finish() 有多处调用点，listener 会被回调多次（先 GRANTED 后 DENIED）。
            if (!resultSent.compareAndSet(false, true)) {
                super.finish()
                return
            }
            val snapshot = state
            runCatching {
                runBlocking(Dispatchers.IO) {
                    persistResult(snapshot.uid, snapshot.allowApi, blockCaller)
                }
            }
            super.finish()
            dispatchResult(snapshot)
            return
        }

        super.finish()

        coroutineScope.launch {
            val uid = state.uid
            val allowApi = state.allowApi
            val packageInfo = packageManager.getPackageInfoForUid(uid) ?: return@launch
            val signature = packageInfo.signature ?: return@launch

            val entity = appRepo.findByUID(uid)
            if (entity == null)
                appRepo.insert(AppEntity(uid = uid, signature = signature, allowApi = allowApi))
            else
                appRepo.update(entity.copy(uid = uid, signature = signature, allowApi = allowApi))
        }.invokeOnCompletion {
            val result = if (state.allowApi) PackageManager.PERMISSION_GRANTED
            else PackageManager.PERMISSION_DENIED
            state.listener?.onRequestPermission(result)
        }
    }

    /**
     * 本次请求是否来自服务端签发的一次性 token。
     * 只有这种请求的 uid 才是 Binder 侧真实调用方；兼容路径与通知拉起都算不可信，
     * 涉及"惩罚性"的操作（连击拉黑）一律不生效。
     */
    private val trustedRequest: Boolean
        get() = !state.serverToken.isNullOrEmpty()

    /** 落库。超时/取消场景不覆盖已有的 allowApi=true 记录（避免被"轰炸"撤销授权）。 */
    private suspend fun persistResult(uid: Int, allowApi: Boolean, blocked: Boolean = false) {
        val packageInfo = packageManager.getPackageInfoForUid(uid) ?: return
        val signature = packageInfo.signature ?: return
        val entity = appRepo.findByUID(uid)

        if (!userDecided && !allowApi && entity?.allowApi == true && !blocked) {
            // 未做出明确选择（超时 / 被动 finish）：保留原授权，仅回调拒绝本次请求
            PermissionCache.put(entity)
            return
        }

        // 连击计数：只有「弹了但没人管」才累加；用户明确表态或已拉黑就清零。
        // 【只在可信路径累加】授权 Activity 是 exported 的，第三方可以不经服务端
        // 直接 startActivity 并带一个伪造的 uid。若这种请求也计入连击，
        // 攻击者就能靠反复拉起弹窗把受害者应用刷进黑名单（DoS）。
        // 可信的判据是服务端签发的一次性 token —— 它由 Binder.getCallingUid()
        // 签发，伪造不了。兼容路径（老版本客户端）没有 token，一律不计数。
        runCatching {
            val pkg = packageManager.getPackageInfoForUid(uid)?.packageName
            if (!pkg.isNullOrEmpty()) {
                when {
                    userDecided -> strikes.reset(pkg)
                    blocked -> strikes.reset(pkg)
                    !allowApi && trustedRequest -> {
                        val n = strikes.increment(pkg)
                        NeoLog.i(NeoLog.PERM) { "activity: 超时未处理计数 $pkg -> $n" }
                    }
                    !allowApi -> NeoLog.w(NeoLog.PERM) {
                        "activity: 非可信路径，跳过连击计数 pkg=$pkg"
                    }
                }
            }
        }

        if (entity == null) {
            appRepo.insert(
                AppEntity(
                    uid = uid,
                    signature = signature,
                    allowApi = allowApi,
                    blocked = blocked
                )
            )
        } else {
            appRepo.update(
                entity.copy(
                    uid = uid,
                    signature = signature,
                    allowApi = if (blocked) false else allowApi,
                    blocked = blocked || entity.blocked
                )
            )
        }
        runCatching { PermissionCache.refreshOne(uid) }
        NeoLog.i(NeoLog.PERM) {
            "result: 落库 uid=$uid allowApi=${if (blocked) false else allowApi} " +
                "blocked=$blocked 原blocked=${entity?.blocked} decided=$userDecided"
        }
        // 授权变更后刷新首页「授权应用」计数
        if (NeoDhizuku.on(NeoDhizuku.UseHomeStatusCard)) {
            runCatching {
                ServiceStatus.updateAuthorizedCount(
                    appRepo.all().count { it.allowApi && !it.blocked }
                )
            }
        }
    }

    private fun dispatchResult(snapshot: ViewState) {
        val result = if (snapshot.allowApi) PackageManager.PERMISSION_GRANTED
        else PackageManager.PERMISSION_DENIED
        val token = snapshot.serverToken
        if (neoHardenedEntry && !token.isNullOrEmpty()) {
            // 结果通过服务端持有的 listener 回传
            RequestPermissionToken.dispatchResult(token, result)
            return
        }
        val listener = snapshot.listener
        if (listener == null) {
            NeoLog.w(NeoLog.PERM) {
                "result: 无 listener 可回调，结果仅落库 uid=${snapshot.uid} result=$result"
            }
            return
        }
        runCatching { listener.onRequestPermission(result) }
            .onFailure { NeoLog.e(NeoLog.PERM, { "result: 回调失败 uid=${snapshot.uid}" }, it) }
    }

    private fun registerAppEntity(intent: Intent?): Boolean {
        if (intent == null) return false

        if (neoHardenedEntry) {
            // 优先校验服务端下发的一次性 token。
            // token 有效 → uid 来自 Binder.getCallingUid()，客户端无法伪造。
            val token = intent.getStringExtra(RequestPermissionToken.PARAM_SERVER_TOKEN)
            val trustedUid = RequestPermissionToken.consume(token)
            NeoLog.d(NeoLog.PERM) {
                "request: token=${token?.take(8)}… -> trustedUid=$trustedUid"
            }
            if (trustedUid >= 0) {
                state = state.copy(
                    uid = trustedUid,
                    signature = packageManager.getPackageInfoForUid(trustedUid)?.signature ?: "",
                    serverToken = token,
                    listener = null
                )
                return true
            }
            // 无有效 token（旧版本客户端）：继续走原有逻辑，保持兼容。
            // 超时与取消不会覆盖已有授权，因此伪造请求无法撤销他人授权。
        }

        // 由本应用的通知拉起：没有 listener 可回调（老客户端从未发起过请求），
        // 只按 uid 展示并写库。授权与否仍需用户在弹窗里确认。
        if (neoHardenedEntry
            && intent.getBooleanExtra(RequestPermissionToken.PARAM_SELF_LAUNCH, false)
        ) {
            val uid = intent.getIntExtra(DhizukuVariables.PARAM_CLIENT_UID, -1)
            NeoLog.i(NeoLog.PERM) { "request: 通知拉起 uid=$uid（无回调 listener）" }
            if (uid == -1) return false
            state = state.copy(
                uid = uid,
                signature = packageManager.getPackageInfoForUid(uid)?.signature ?: "",
                listener = null
            )
            return true
        }

        val bundle = listOfNotNull(
            intent.extras,
            intent.getBundleExtra("bundle")
        ).find { it.containsKey(DhizukuVariables.PARAM_CLIENT_UID) }
        if (bundle == null) {
            NeoLog.w(NeoLog.PERM) {
                "request: 无 uid 参数，兼容路径失败 " +
                    "extras=${intent.extras?.keySet()}"
            }
            return false
        }

        val uid = bundle.getInt(DhizukuVariables.PARAM_CLIENT_UID, -1)
        NeoLog.i(NeoLog.PERM) { "request: 兼容路径 uid=$uid" }
        if (uid == -1) {
            NeoLog.w(NeoLog.PERM) { "request: uid missing, reject" }
            return false
        }

        val binder = bundle.getBinder(DhizukuVariables.PARAM_CLIENT_REQUEST_PERMISSION_BINDER)
        if (binder == null) {
            // 拿不到回调 binder 也要弹窗：结果只落库、不回调。
            // 直接 return false 会让这类客户端永远拿不到授权 ——
            // 弹窗没有、回调也没有，下一次 isPermissionGranted() 依旧是 false，
            // 用户看到的就是"已授权却始终提示无权限"。
            NeoLog.w(NeoLog.PERM) { "request: 无回调 binder，仅弹窗写库 uid=$uid" }
            state = state.copy(
                uid = uid,
                signature = packageManager.getPackageInfoForUid(uid)?.signature ?: "",
                listener = null
            )
            return true
        }

        val listener = kotlin.runCatching {
            IDhizukuRequestPermissionListener.Stub.asInterface(binder)
        }.getOrElse {
            NeoLog.e(NeoLog.PERM, { "request: listener 解析失败 uid=$uid" }, it)
            return false
        }

        state = state.copy(
            uid = uid,
            signature = packageManager.getPackageInfoForUid(uid)?.signature ?: "",
            listener = listener
        )
        return true
    }

    // 授权弹窗（底部抽屉）
    // 从底部升起、占半个屏幕；上方空白为半透明灰遮罩，点击等同于拒绝。
    // 顶部的小横条既是"可拖动"的暗示，也是真正的把手：向下拖过阈值松手即收起，
    // 收起按「拒绝」处理。
    // 布局自上而下：图标 → 应用名 → 包名（灰）→ 倒计时进度条 → 两个等宽按钮。
    // 「加入黑名单」降级为右上角灰色文字入口 —— 低频且后果重，
    // 与日常两个按钮平级摆在一起误触代价太高。
    // 包名单独列出是刻意的安全设计：图标和应用名都能伪造，包名不能，
    // 它是判断"到底是谁在申请"的唯一可靠信息。
    @Composable
    private fun showDialog(): Boolean {
        val packageInfo = packageManager.getPackageInfoForUid(state.uid) ?: return false
        val packageName = packageInfo.packageName
        val applicationInfo = packageInfo.applicationInfo
        val icon = applicationInfo?.loadIcon(packageManager)
            ?: packageManager.defaultActivityIcon
        val label = applicationInfo?.loadLabel(packageManager) ?: packageName

        val progress by animateFloatAsState(
            targetValue = (state.timeLeft.toFloat() / AUTO_DENY_SECONDS).coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
            label = "countdown_progress"
        )

        // 申请方信息：把原来中间那段空白用掉，让用户看清楚在给谁授权
        val versionName = packageInfo.versionName ?: "-"
        // versionCode 在 API 28 才升级为 long，走 compat 取，避免低版本崩
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        val targetSdk = applicationInfo?.targetSdkVersion ?: -1
        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
        val installedText = remember(packageName) {
            dateFormat.format(Date(packageInfo.firstInstallTime))
        }
        val updatedText = remember(packageName) {
            dateFormat.format(Date(packageInfo.lastUpdateTime))
        }
        val signatureText = remember(packageName) {
            val raw = packageInfo.signature
            if (raw.isNullOrEmpty()) "-" else raw.take(16)
        }

        // 灰阶随日/夜切换：深色背景用浅灰，浅色背景用深灰。
        // 用 surface 的感知亮度判断，不依赖具体主题实现。
        val surface = MaterialTheme.colorScheme.surfaceContainerHigh
        val darkBackground =
            surface.red * 0.299f + surface.green * 0.587f + surface.blue * 0.114f < 0.5f
        val gray = if (darkBackground) Color(0xFFB5B5B5) else Color(0xFF6E6E6E)
        val handleColor = if (darkBackground) Color(0xFF8A8A8A) else Color(0xFFBDBDBD)

        // 拖动位移用普通状态存：拖动中直接跟随手指，松手后交给 animateFloatAsState 回弹。
        // 刻意不用 Animatable —— 它需要额外的类型转换器参数，且此处没必要在协程里驱动。
        val density = LocalDensity.current
        var dragDp by remember { mutableStateOf(0f) }
        var settling by remember { mutableStateOf(false) }
        val settleAnim by animateFloatAsState(
            targetValue = if (settling) 0f else dragDp,
            animationSpec = tween(durationMillis = 220),
            label = "sheet_settle"
        )
        val offsetDp = if (settling) settleAnim else dragDp
        val dismissDistanceDp = DISMISS_DRAG_DP.toFloat()

        // 入场：整块从屏幕下方滑上来。状态只翻一次，动画也只跑一次。
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val enterOffsetDp by animateFloatAsState(
            targetValue = if (entered) 0f else screenHeightDp.toFloat(),
            animationSpec = tween(durationMillis = SHEET_ENTER_MS, easing = FastOutSlowInEasing),
            label = "sheet_enter"
        )
        val sheetOffsetDp = offsetDp + enterOffsetDp

        fun deny() {
            userDecided = true
            state = state.copy(allowApi = false, timedOut = true)
            finish()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                    .clickable { deny() }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 按内容收窄，最多占 SHEET_MAX_HEIGHT_FRACTION 屏高：
                    // 信息填进来后不会被顶到屏幕外，内容少时抽屉也不会空一大块。
                    .heightIn(max = (screenHeightDp * SHEET_MAX_HEIGHT_FRACTION).dp)
                    .offset(y = sheetOffsetDp.dp)
                    .draggable(
                        state = rememberDraggableState { delta ->
                            settling = false
                            dragDp = (dragDp + delta / density.density).coerceAtLeast(0f)
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = {
                            if (dragDp >= dismissDistanceDp) deny() else settling = true
                        }
                    )
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
            ) {
                // 拖动把手 + 右上角「加入黑名单」
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(handleColor)
                    )
                    TextButton(
                        onClick = {
                            userDecided = true
                            blockCaller = true
                            state = state.copy(allowApi = false, timedOut = true)
                            finish()
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                        colors = ButtonDefaults.textButtonColors(contentColor = gray)
                    ) {
                        Text(
                            text = stringResource(R.string.add_to_blocklist),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Image(
                    painter = rememberDrawablePainter(drawable = icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 应用名与版本药丸同一行：左右各留等宽占位，
                // 使应用名保持居中，药丸贴在行的最右侧。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = label.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        ClientApiBadge(version = ClientApiVersion.of(state.uid))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 倒计时进度条：走完即默认拒绝（或默认拉黑）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (state.blacklistCountdown) {
                        if (state.timeLeft > 0) {
                            stringResource(R.string.auto_block_in_seconds, state.timeLeft)
                        } else {
                            stringResource(R.string.adding_to_blocklist)
                        }
                    } else {
                        if (state.timeLeft > 0) {
                            stringResource(R.string.auto_deny_in_seconds, state.timeLeft)
                        } else {
                            stringResource(R.string.denying_access)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoRow(
                        label = stringResource(R.string.perm_info_version),
                        value = "$versionName ($versionCode)"
                    )
                    InfoRow(
                        label = stringResource(R.string.perm_info_target_sdk),
                        value = if (targetSdk > 0) targetSdk.toString() else "-"
                    )
                    InfoRow(
                        label = stringResource(R.string.perm_info_installed),
                        value = installedText
                    )
                    InfoRow(
                        label = stringResource(R.string.perm_info_updated),
                        value = updatedText
                    )
                    InfoRow(
                        label = stringResource(R.string.perm_info_signature),
                        value = signatureText
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = BOTTOM_BUTTON_MARGIN_DP.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            userDecided = true
                            state = state.copy(allowApi = true, timedOut = true)
                            finish()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = stringResource(R.string.agree))
                    }

                    Button(
                        onClick = {
                            userDecided = true
                            state = state.copy(allowApi = false, timedOut = true)
                            finish()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = stringResource(R.string.refuse))
                    }
                }
            }
        }
        return true
    }

    /** 授权弹窗里的一行「标签 : 值」。值右对齐，过长时省略。 */
    @Composable
    private fun InfoRow(label: String, value: String) {
        val gray = MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = gray,
                modifier = Modifier.width(84.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
