package com.example.util

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.View
import android.view.ViewGroup

data class RefreshModeSpec(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float
)

/**
 * Selects the best 120Hz-class display mode (avoiding private game-only 144Hz modes that OEM
 * schedulers reject for normal apps) while matching current physical resolution, and configures
 * Android 15+ Adaptive Refresh Rate (ARR) Window/View hints.
 */
object DisplayRefreshHelper {

    fun selectBestMode(
        currentMode: RefreshModeSpec?,
        supportedModes: List<RefreshModeSpec>
    ): RefreshModeSpec? {
        if (supportedModes.isEmpty()) return currentMode
        val sameResolution = if (currentMode != null) {
            supportedModes.filter {
                it.width == currentMode.width && it.height == currentMode.height
            }
        } else {
            emptyList()
        }
        val candidates = if (sameResolution.isNotEmpty()) sameResolution else supportedModes
        // Prefer standard high-refresh modes (90-120Hz) before private game-only >125Hz modes
        // (e.g. Vivo/iQOO 144Hz) so OEM refresh rate adjusters do not fall back to 60Hz.
        val preferred120Band = candidates.filter { it.refreshRate in 65f..125f }
        val pool = if (preferred120Band.isNotEmpty()) preferred120Band else candidates
        return pool.maxWithOrNull(
            compareBy<RefreshModeSpec> { it.refreshRate }
                .thenBy { currentMode != null && it.modeId == currentMode.modeId }
        )
    }

    private fun resolveBestDisplayMode(activity: Activity): RefreshModeSpec? = runCatching {
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
                ?: activity.getSystemService(DisplayManager::class.java)
                    ?.getDisplay(Display.DEFAULT_DISPLAY)
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager?.defaultDisplay
        }
        display ?: return null
        val current = display.mode.let {
            RefreshModeSpec(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
        }
        val supported = display.supportedModes.map {
            RefreshModeSpec(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
        }
        selectBestMode(current, supported)
    }.getOrNull()

    fun applyHighestRefreshRate(activity: Activity) {
        runCatching {
            val best = resolveBestDisplayMode(activity) ?: return
            val window = activity.window
            val lp = window.attributes
            if (lp.preferredDisplayModeId != best.modeId || lp.preferredRefreshRate != best.refreshRate) {
                lp.preferredDisplayModeId = best.modeId
                lp.preferredRefreshRate = best.refreshRate
                window.attributes = lp
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && best.refreshRate > 61f) {
                window.setFrameRateBoostOnTouchEnabled(true)
                window.setFrameRatePowerSavingsBalanced(false)
                window.decorView.setRequestedFrameRate(best.refreshRate)
                val content = window.decorView.findViewById<View?>(android.R.id.content)
                content?.setRequestedFrameRate(best.refreshRate)
                if (content is ViewGroup) {
                    for (i in 0 until content.childCount) {
                        content.getChildAt(i)?.setRequestedFrameRate(best.refreshRate)
                    }
                }
            }
        }
    }
}
