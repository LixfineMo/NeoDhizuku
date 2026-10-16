package com.neodhizuku.more.ui.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.neodhizuku.more.server.PermissionCache
import com.neodhizuku.more.server.ServiceStatus
import com.neodhizuku.more.server.RequestPermissionToken
import com.rosan.dhizuku.shared.DhizukuVariables
import com.neodhizuku.more.ui.theme.DhizukuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin

class RequestPermissionActivity : ComponentActivity(), KoinComponent {
    companion object {
        const val UID_ERR = -1
        const val AUTO_DENY_SECONDS = 15

        private const val KEY_UID = "neo_uid"
        private const val KEY_TOKEN = "neo_token"
        private const val KEY_TIME_LEFT = "neo_time_left"
        private const val KEY_TRUSTED = "neo_trusted"
    }

    data class ViewState(
        val uid: Int = UID_ERR,
        val allowApi: Boolean = false,
        val signature: String = "",
        val listener: IDhizukuRequestPermissionListener? = null,
        val timeLeft: Int = AUTO_DENY_SECONDS,
        val timedOut: Boolean = false,
        val shouldShowDialog: Boolean = true,
        /** 服务端下发的一次性令牌，非空表示本次请求由服务端发起、uid 可信 */
        val serverToken: String? = null
    )

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val appRepo by inject<AppRepo>()
    private val settingsRepo by inject<SettingsRepo>()
    private var state by mutableStateOf(ViewState())

    /** 保证授权结果只回调一次 */
    private val resultSent = AtomicBoolean(false)

    /** 是否已做过明确选择（用于区分"超时/取消"与"用户点了同意"） */
    @Volatile
    private var userDecided: Boolean = false

    /** 用户是否点了「加入黑名单」（拉黑后不再处理该应用的授权请求） */
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (registerAppEntity(intent)) return
        finish()
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
            // 原实现在 finish() 之后异步写库，进程随时可能被回收 → 用户点了"同意"下次仍弹窗；
            // 且 finish() 有 5 处调用点，listener 会被回调多次（先 GRANTED 后 DENIED）。
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
        runCatching { snapshot.listener?.onRequestPermission(result) }
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
            NeoLog.w(NeoLog.PERM) { "request: 无回调 binder，uid=$uid" }
            return false
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

    @Composable
    private fun CountdownPieChart(
        progress: Float,
        modifier: Modifier = Modifier
    ) {
        val color = MaterialTheme.colorScheme.outline

        Canvas(modifier = modifier) {
            val radius = size.minDimension / 2f
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val strokeWidth = 2.dp.toPx() // Made thicker
            val lineRadius = radius - strokeWidth / 2f

            // Draw the remaining arc (hollow circle outline that gets eaten away counter-clockwise)
            if (progress > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f, // Start from top (12 o'clock)
                    sweepAngle = 360f * progress, // Sweep clockwise for remaining time
                    useCenter = false, // This makes it hollow!
                    topLeft = androidx.compose.ui.geometry.Offset(
                        center.x - lineRadius,
                        center.y - lineRadius
                    ),
                    size = androidx.compose.ui.geometry.Size(lineRadius * 2, lineRadius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Always draw a line from center to top (12 o'clock position)
            drawLine(
                color = color,
                start = center,
                end = androidx.compose.ui.geometry.Offset(center.x, center.y - lineRadius),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Draw current time position line (moves clockwise as arc gets eaten counter-clockwise)
            if (progress > 0f && progress < 1f) {
                val angle = -90f + (360f * progress)
                val angleRad = Math.toRadians(angle.toDouble())
                val endX = center.x + lineRadius * cos(angleRad).toFloat()
                val endY = center.y + lineRadius * sin(angleRad).toFloat()

                drawLine(
                    color = color,
                    start = center,
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    @Composable
    private fun showDialog(): Boolean {
        val uid = state.uid
        val packageInfo = packageManager.getPackageInfoForUid(uid) ?: return false
        val packageName = packageInfo.packageName
        val applicationInfo = packageInfo.applicationInfo
        val icon = applicationInfo?.loadIcon(packageManager)
            ?: packageManager.defaultActivityIcon
        val label = applicationInfo?.loadLabel(packageManager)
            ?: packageName

        val progress by animateFloatAsState(
            targetValue = state.timeLeft.toFloat() / AUTO_DENY_SECONDS,
            animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
            label = "countdown_progress"
        )

        AlertDialog(onDismissRequest = {
            userDecided = true
            state = state.copy(allowApi = false, timedOut = true)
            finish()
        }, icon = {
            Image(
                painter = rememberDrawablePainter(drawable = icon),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        }, title = {
            Text(
                AnnotatedString.fromHtml(stringResource(R.string.request_permission_text, label)),
                textAlign = TextAlign.Center
            )
        }, text = {
            if (NeoDhizuku.on(NeoDhizuku.UsePermissionDialogV2)) {
                // 去掉进度条，正文只保留请求说明
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.request_permission_scope_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CountdownPieChart(
                        progress = progress,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (state.timeLeft > 0) {
                            stringResource(R.string.auto_deny_in_seconds, state.timeLeft)
                        } else {
                            stringResource(R.string.denying_access)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }
        }, confirmButton = {
            if (NeoDhizuku.on(NeoDhizuku.UsePermissionDialogV2)) {
                // 三个按钮都不要背景框，只保留文字。
                // 授权=绿色，拒绝=红色，加入黑名单=黑色。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 授权：绿色
                    androidx.compose.material3.TextButton(
                        onClick = {
                            userDecided = true
                            state = state.copy(allowApi = true, timedOut = true)
                            finish()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = androidx.compose.ui.graphics.Color(0xFF2E7D32)
                        )
                    ) {
                        Text(
                            stringResource(R.string.agree),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // 加入黑名单：黑色
                    androidx.compose.material3.TextButton(
                        onClick = {
                            userDecided = true
                            blockCaller = true
                            state = state.copy(allowApi = false, timedOut = true)
                            finish()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = androidx.compose.ui.graphics.Color(0xFF000000)
                        )
                    ) {
                        Text(
                            stringResource(R.string.add_to_blocklist),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // 拒绝：红色
                    androidx.compose.material3.TextButton(
                        onClick = {
                            userDecided = true
                            state = state.copy(allowApi = false, timedOut = true)
                            finish()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                        )
                    ) {
                        Text(
                            stringResource(R.string.refuse),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                    // 倒计时放在拒绝按钮下方
                    Text(
                        text = if (state.timeLeft > 0) {
                            stringResource(R.string.auto_deny_in_seconds, state.timeLeft)
                        } else {
                            stringResource(R.string.denying_access)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
            } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                @Composable
                fun MyTextButton(
                    onClick: () -> Unit,
                    @StringRes textResId: Int,
                    isPrimary: Boolean = false
                ) {
                    TextButton(
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(16.dp),
                        colors = if (isPrimary) ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) else ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(
                            stringResource(textResId),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                MyTextButton(onClick = {
                    userDecided = true
                    state = state.copy(allowApi = true, timedOut = true)
                    finish()
                }, textResId = R.string.agree, isPrimary = true)
                MyTextButton(onClick = {
                    userDecided = true
                    state = state.copy(allowApi = false, timedOut = true)
                    finish()
                }, textResId = R.string.refuse)
            }
            }
        })
        return true
    }
}
