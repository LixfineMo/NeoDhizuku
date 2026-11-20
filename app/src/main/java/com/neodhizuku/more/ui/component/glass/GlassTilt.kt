package com.neodhizuku.more.ui.component.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 设备姿态：重力在屏幕平面上的方向。
 * 玻璃高光跟着它走，手机往哪边倾，高光就往哪边偏 —— 这是液态玻璃
 * 「贴着环境光」的关键，固定位置的高光一眼假。
 */
internal data class DeviceTilt(
    val gravityX: Float,
    val gravityY: Float
) {
    companion object {
        val Zero = DeviceTilt(0f, -1f)
    }
}

/** 归一化后的重力方向；取不到传感器时返回 [DeviceTilt.Zero]。 */
internal val DeviceTilt.directionX: Float
    get() {
        val magSq = gravityX * gravityX + gravityY * gravityY
        return if (magSq > 0.01f) {
            val inv = 1f / sqrt(magSq)
            (gravityX * inv).coerceIn(-1f, 1f)
        } else {
            0f
        }
    }

internal val DeviceTilt.directionY: Float
    get() {
        val magSq = gravityX * gravityX + gravityY * gravityY
        return if (magSq > 0.01f) {
            val inv = 1f / sqrt(magSq)
            (gravityY * inv).coerceIn(-1f, 1f)
        } else {
            -1f
        }
    }

/**
 * rememberDeviceTilt
 * (enabled: Boolean = true)
 * 作用：监听加速度计得到重力方向。 两点与「每帧刷新」的写法不同： 一是低通滤波只保留重力分量，手抖不会让高光抖； 二是变化小于阈值就不更新状态，避免高频重组（传感器默认是 60Hz）。
 * 备注：Compose 组件
 */
@Composable
internal fun rememberDeviceTilt(enabled: Boolean = true): DeviceTilt {
    val context = LocalContext.current
    if (!enabled) return DeviceTilt.Zero

    var tilt by remember { mutableStateOf(DeviceTilt.Zero) }

    DisposableEffect(context) {
        val manager = runCatching {
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        }.getOrNull()
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            return@DisposableEffect onDispose { }
        }

        var gx = 0f
        var gy = 0f
        val listener = object : SensorEventListener {
            /**
             * onSensorChanged
             * (event: SensorEvent)
             * 作用：处理 SensorChanged 回调
             * 备注：重写父类 / 接口方法
             */
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 2) return
                gx += (event.values[0] - gx) * 0.12f
                gy += (event.values[1] - gy) * 0.12f
                val current = tilt
                if (abs(gx - current.gravityX) > 0.05f || abs(gy - current.gravityY) > 0.05f) {
                    tilt = DeviceTilt(gx, gy)
                }
            }

            /**
             * onAccuracyChanged
             * (sensor: Sensor?, accuracy: Int)
             * 作用：处理 AccuracyChanged 回调
             * 备注：重写父类 / 接口方法
             */
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { runCatching { manager.unregisterListener(listener) } }
    }

    return tilt
}
