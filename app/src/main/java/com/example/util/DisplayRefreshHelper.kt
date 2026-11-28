package com.example.util

import android.app.Activity
import android.os.Build
import android.view.Display

data class RefreshModeSpec(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float
)

/**
 * Selects the highest refresh-rate display mode (90Hz / 120Hz / 144Hz+) that matches the current
 * physical screen resolution so OEM Android phones do not cap the app to 60Hz.
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
        return candidates.maxWithOrNull(
            compareBy<RefreshModeSpec> { it.refreshRate }
                .thenBy { currentMode != null && it.modeId == currentMode.modeId }
        )
    }

    fun applyHighestRefreshRate(activity: Activity) {
        runCatching {
            val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager?.defaultDisplay
            }
            display ?: return

            val current = display.mode.let {
                RefreshModeSpec(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
            }
            val supported = display.supportedModes.map {
                RefreshModeSpec(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
            }
            val best = selectBestMode(current, supported) ?: return
            val lp = activity.window.attributes
            if (lp.preferredDisplayModeId != best.modeId || lp.preferredRefreshRate != best.refreshRate) {
                lp.preferredDisplayModeId = best.modeId
                lp.preferredRefreshRate = best.refreshRate
                activity.window.attributes = lp
            }
        }
    }
}
