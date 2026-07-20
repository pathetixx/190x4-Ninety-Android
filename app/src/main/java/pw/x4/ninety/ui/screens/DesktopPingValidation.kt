package pw.x4.ninety.ui.screens

/** Shared validity rule for desktop-style node latency readouts. */
internal fun desktopValidPing(ms: Int?): Boolean = ms != null && ms in 1 until 65_000
