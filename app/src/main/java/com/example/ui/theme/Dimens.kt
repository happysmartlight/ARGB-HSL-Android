package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width-based size buckets. Used to pick paddings/spacings/font scale so the UI
 * adapts from small 5" phones (Compact) up to tablets (Expanded).
 */
enum class WindowSizeClass { Compact, Medium, Expanded }

/**
 * Central, screen-aware dimension set. Read it anywhere via [LocalAppDimens] (or the
 * `dimens` shortcut). Everything that used to be a hard-coded dp/sp should pull from
 * here so one place controls how the app scales across devices.
 */
data class AppDimens(
    val sizeClass: WindowSizeClass,
    val screenWidthDp: Int,
    val fontScale: Float,
    val screenPadding: Dp,
    val cardPadding: Dp,
    val sectionSpacing: Dp,
    val itemSpacing: Dp,
    val iconSize: Dp,
    val buttonHeight: Dp,
    val colorWheelSize: Dp,
    /** Bề rộng cột danh sách thiết bị ở layout master-detail (màn ≥ 720dp). */
    val deviceListPaneWidth: Dp,
    val isCompact: Boolean
)

val LocalAppDimens = staticCompositionLocalOf {
    // Sensible default (medium phone) until a real provider wraps the tree.
    AppDimens(
        sizeClass = WindowSizeClass.Medium,
        screenWidthDp = 393,
        fontScale = 1f,
        screenPadding = 16.dp,
        cardPadding = 16.dp,
        sectionSpacing = 20.dp,
        itemSpacing = 12.dp,
        iconSize = 18.dp,
        buttonHeight = 50.dp,
        colorWheelSize = 260.dp,
        deviceListPaneWidth = 340.dp,
        isCompact = false
    )
}

/** Compute the dimension set from the current screen width. */
@Composable
fun rememberAppDimens(): AppDimens {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val sizeClass = when {
        widthDp < 360 -> WindowSizeClass.Compact
        widthDp < 720 -> WindowSizeClass.Medium
        else -> WindowSizeClass.Expanded
    }
    val fontScale = when (sizeClass) {
        WindowSizeClass.Compact -> 0.88f
        WindowSizeClass.Medium -> 1f
        WindowSizeClass.Expanded -> 1.06f
    }
    return AppDimens(
        sizeClass = sizeClass,
        screenWidthDp = widthDp,
        fontScale = fontScale,
        screenPadding = when (sizeClass) {
            WindowSizeClass.Compact -> 10.dp
            WindowSizeClass.Medium -> 16.dp
            WindowSizeClass.Expanded -> 20.dp
        },
        cardPadding = when (sizeClass) {
            WindowSizeClass.Compact -> 12.dp
            WindowSizeClass.Medium -> 16.dp
            WindowSizeClass.Expanded -> 18.dp
        },
        sectionSpacing = when (sizeClass) {
            WindowSizeClass.Compact -> 14.dp
            WindowSizeClass.Medium -> 20.dp
            WindowSizeClass.Expanded -> 22.dp
        },
        itemSpacing = when (sizeClass) {
            WindowSizeClass.Compact -> 8.dp
            WindowSizeClass.Medium -> 12.dp
            WindowSizeClass.Expanded -> 14.dp
        },
        iconSize = when (sizeClass) {
            WindowSizeClass.Compact -> 16.dp
            WindowSizeClass.Medium -> 18.dp
            WindowSizeClass.Expanded -> 20.dp
        },
        buttonHeight = when (sizeClass) {
            WindowSizeClass.Compact -> 46.dp
            WindowSizeClass.Medium -> 50.dp
            WindowSizeClass.Expanded -> 54.dp
        },
        // Interactive color wheel: a share of the screen width, clamped to sane bounds.
        colorWheelSize = (widthDp * 0.66f).dp.coerceIn(180.dp, 300.dp),
        // Cột danh sách thiết bị (master-detail): theo tỷ lệ màn hình, kẹp biên hợp lý
        // để tablet rất rộng không bị cột quá to, màn 720dp không bị cột quá hẹp.
        deviceListPaneWidth = (widthDp * 0.28f).dp.coerceIn(300.dp, 420.dp),
        isCompact = sizeClass == WindowSizeClass.Compact
    )
}
