package pw.x4.ninety.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import pw.x4.ninety.data.Options
import pw.x4.ninety.ui.components.SurfaceCard
import pw.x4.ninety.ui.components.ToggleRow

@Composable
internal fun DesktopInboundSettings() {
    val context = LocalContext.current
    val options = Options.data
    DesktopReloadHint()
    DesktopSettingsGap()
    SurfaceCard {
        DesktopNumberSetting("MTU TUN", "576–9000", options.mtu, 576, 9000) {
            Options.update(context) { current -> current.copy(mtu = it) }
        }
        DesktopFieldDivider()
        DesktopSelectSetting(
            "TUN-стек",
            "Реализация сетевого стека",
            options.tunStack,
            listOf("mixed", "gvisor", "system"),
            DesktopTunLabels,
        ) { Options.update(context) { current -> current.copy(tunStack = it) } }
        DesktopFieldDivider()
        ToggleRow("Строгая маршрутизация", options.strictRoute, "Перехватывать весь подходящий трафик") {
            Options.update(context) { current -> current.copy(strictRoute = it) }
        }
    }
}

@Composable
internal fun DesktopTlsSettings() {
    val context = LocalContext.current
    val options = Options.data
    DesktopReloadHint()
    DesktopSettingsGap()
    SurfaceCard {
        ToggleRow("TLS-фрагментация", options.tlsFragment, "Фрагментация ClientHello") {
            Options.update(context) { current -> current.copy(tlsFragment = it) }
        }
        DesktopFieldDivider()
        DesktopSelectSetting(
            "Режим фрагментации",
            "TLS record или TCP segment",
            options.fragmentMode,
            listOf("record", "tcp"),
        ) { Options.update(context) { current -> current.copy(fragmentMode = it) } }
        DesktopFieldDivider()
        ToggleRow("Смешанный регистр SNI", options.mixedSniCase, "Изменять регистр имени сервера") {
            Options.update(context) { current -> current.copy(mixedSniCase = it) }
        }
        ToggleRow("TLS padding", options.tlsPadding, "Добавлять случайный padding") {
            Options.update(context) { current -> current.copy(tlsPadding = it) }
        }
        DesktopFieldDivider()
        DesktopNumberSetting("Padding от", "Минимум байт", options.paddingFrom, 0, 4096) {
            Options.update(context) { current -> current.copy(paddingFrom = it) }
        }
        DesktopFieldDivider()
        DesktopNumberSetting("Padding до", "Максимум байт", options.paddingTo, 0, 4096) {
            Options.update(context) { current -> current.copy(paddingTo = it) }
        }
    }
}

@Composable
internal fun DesktopMuxSettings() {
    val context = LocalContext.current
    val options = Options.data
    DesktopReloadHint()
    DesktopSettingsGap()
    SurfaceCard {
        ToggleRow("Включить multiplex", options.muxEnable, "Несколько потоков через одно соединение") {
            Options.update(context) { current -> current.copy(muxEnable = it) }
        }
        DesktopFieldDivider()
        DesktopSelectSetting(
            "Протокол",
            "Multiplex transport",
            options.muxProtocol,
            listOf("h2mux", "smux", "yamux"),
        ) { Options.update(context) { current -> current.copy(muxProtocol = it) } }
        DesktopFieldDivider()
        DesktopNumberSetting("Максимум потоков", "1–64", options.muxMaxStreams, 1, 64) {
            Options.update(context) { current -> current.copy(muxMaxStreams = it) }
        }
        DesktopFieldDivider()
        ToggleRow("Padding multiplex", options.muxPadding, "Добавлять padding к фреймам") {
            Options.update(context) { current -> current.copy(muxPadding = it) }
        }
    }
}

@Composable
internal fun DesktopWarpSettings() {
    WarpSettingsPane()
}
