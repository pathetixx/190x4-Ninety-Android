package pw.x4.ninety.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Options
import pw.x4.ninety.data.Prefs
import pw.x4.ninety.ui.components.SurfaceCard
import pw.x4.ninety.ui.components.ToggleRow
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

@Composable
internal fun DesktopGeneralSettings() {
    val context = LocalContext.current
    val prefs = Prefs.get(context)
    val options = Options.data
    var autoConnect by remember { mutableStateOf(prefs.autoConnect) }
    var autoUpdate by remember { mutableStateOf(prefs.autoUpdateCheck) }

    DesktopSettingsCallout(
        "PING · NATIVE URLTEST",
        "Auto управляется штатным selector/urltest sing-box. Quality Engine не подменяет ноду и не вызывает reload.",
    )
    DesktopSettingsGap()
    SurfaceCard {
        ToggleRow("Автоподключение", autoConnect, "Запускать последний готовый режим") {
            autoConnect = it
            prefs.autoConnect = it
        }
        ToggleRow("Проверять обновления", autoUpdate, "Тихая проверка GitHub Releases") {
            autoUpdate = it
            prefs.autoUpdateCheck = it
        }
    }
    DesktopSettingsGap()
    SurfaceCard {
        DesktopTextSetting("URL проверки", "Endpoint для штатной группы urltest", options.testUrl) {
            Options.update(context) { current -> current.copy(testUrl = it) }
        }
        DesktopFieldDivider()
        DesktopNumberSetting("Интервал проверки", "30–3600 секунд", options.testIntervalSec, 30, 3600) {
            Options.update(context) { current -> current.copy(testIntervalSec = it) }
        }
        DesktopFieldDivider()
        DesktopSelectSetting(
            "Уровень логов",
            "Подробность журнала sing-box",
            options.logLevel,
            listOf("trace", "debug", "info", "warn", "error"),
        ) { Options.update(context) { current -> current.copy(logLevel = it) } }
        DesktopFieldDivider()
        ToggleRow("Отключить логи", options.logDisabled, "Runtime-диагностика станет недоступна") {
            Options.update(context) { current -> current.copy(logDisabled = it) }
        }
    }
}

@Composable
internal fun DesktopAppearanceSettings() {
    val context = LocalContext.current
    val prefs = Prefs.get(context)
    Text(
        "Каждая тема переносит не только accent, но и материал карточек, Hero-диск, второй цвет HUD, sidebar и фоновые слои desktop Ninety.",
        style = NinetyTypography.bodyMedium,
        color = Ink.TextMid,
    )
    Spacer(Modifier.height(14.dp))
    DesktopThemeGallery(NinetyState.pack.id) { pack ->
        NinetyState.pack = pack
        prefs.themePack = pack.id
    }
}

@Composable
internal fun DesktopRoutingSettings() {
    val context = LocalContext.current
    val options = Options.data
    DesktopReloadHint()
    DesktopSettingsGap()
    SurfaceCard {
        DesktopSelectSetting(
            "Регион",
            "Локальные geosite/geoip идут напрямую",
            options.region,
            listOf("other", "ru", "cn", "ir", "tr", "by"),
            DesktopRegionLabels,
        ) { Options.update(context) { current -> current.copy(region = it) } }
        DesktopFieldDivider()
        ToggleRow("Блокировать рекламу", options.blockAds, "Rule sets рекламы, malware и phishing") {
            Options.update(context) { current -> current.copy(blockAds = it) }
        }
        ToggleRow("Обход LAN", options.bypassLan, "Приватные адреса идут напрямую") {
            Options.update(context) { current -> current.copy(bypassLan = it) }
        }
        DesktopFieldDivider()
        DesktopSelectSetting(
            "Маршрут IPv6",
            "Стратегия выбора IP",
            options.ipv6Mode,
            listOf("disable", "enable", "prefer", "only"),
            DesktopIpv6Labels,
        ) { Options.update(context) { current -> current.copy(ipv6Mode = it) } }
    }
    DesktopSettingsGap()
    RoutingRulesEditor(options.customRules) { rules ->
        Options.update(context) { current -> current.copy(customRules = rules) }
    }
}

@Composable
internal fun DesktopDnsSettings() {
    val context = LocalContext.current
    val options = Options.data
    DesktopReloadHint()
    DesktopSettingsGap()
    SurfaceCard {
        DesktopTextSetting("Remote DNS", "DNS защищённого маршрута", options.dnsRemote) {
            Options.update(context) { current -> current.copy(dnsRemote = it) }
        }
        DesktopFieldDivider()
        DesktopTextSetting("Direct DNS", "DNS прямого маршрута", options.dnsDirect) {
            Options.update(context) { current -> current.copy(dnsDirect = it) }
        }
        DesktopFieldDivider()
        ToggleRow("Независимый DNS-кэш", options.independentCache, "Раздельный кэш remote/direct") {
            Options.update(context) { current -> current.copy(independentCache = it) }
        }
        ToggleRow("Fake-DNS", options.fakeDns, "Внутренний mapping доменов в TUN") {
            Options.update(context) { current -> current.copy(fakeDns = it) }
        }
    }
}
