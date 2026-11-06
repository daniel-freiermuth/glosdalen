package com.glosdalen.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.glosdalen.app.R

/**
 * Material Symbols (Outlined, FILL 1, wght 400, GRAD 0, opsz 24) used by the app.
 *
 * The vector drawables in res/drawable/ic_*.xml come from the "Android" export of
 * https://fonts.google.com/icons (mirrored in github.com/google/material-design-icons,
 * symbols/android/<name>/materialsymbolsoutlined/<name>_fill1_24px.xml). Directional
 * icons carry android:autoMirrored="true" so they flip in RTL layouts.
 *
 * To add an icon: download the same style from Google Fonts, save it as
 * res/drawable/ic_<name>.xml, replace the tint with ?android:attr/textColorPrimary
 * (the app has no AppCompat, so ?attr/colorControlNormal does not resolve), and add a getter here.
 */
object AppIcons {
    val ArrowBack: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_back)
    val ArrowDropDown: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_drop_down)
    val ArrowForward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_forward)
    val Check: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_check)
    val Close: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_close)
    val ContentCopy: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_content_copy)
    val Delete: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_delete)
    val Info: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_info)
    val KeyboardArrowDown: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_keyboard_arrow_down)
    val KeyboardArrowRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_keyboard_arrow_right)
    val KeyboardArrowUp: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_keyboard_arrow_up)
    val Lightbulb: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lightbulb)
    val Menu: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_menu)
    val PlayArrow: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_play_arrow)
    val Refresh: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_refresh)
    val Search: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_search)
    val Send: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_send)
    val Settings: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_settings)
    val Star: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_star)
    val Stop: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_stop)
    val Translate: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_translate)
    val Visibility: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_visibility)
    val VisibilityOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_visibility_off)
    val VolumeUp: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_volume_up)
    val Warning: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_warning)
}
