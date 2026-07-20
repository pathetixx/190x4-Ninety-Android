package pw.x4.ninety.ui.components

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas as composeNativeCanvas

/**
 * Keeps Android-native HUD text drawing package-local without leaking the
 * Compose Android canvas extension into every visual component.
 */
internal val Canvas.nativeCanvas: android.graphics.Canvas
    get() = this.composeNativeCanvas
