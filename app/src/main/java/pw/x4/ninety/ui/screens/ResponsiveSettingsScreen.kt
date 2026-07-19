package pw.x4.ninety.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import pw.x4.ninety.BuildConfig
import pw.x4.ninety.data.Diag
import pw.x4.ninety.data.Options
import pw.x4.ninety.data.Prefs
import pw.x4.ninety.data.Updater
import pw.x4.ninety.ui.components.PillButton
import pw.x4.ninety.ui.components.ScreenHeader
import pw.x4.ninety.ui.components.SurfaceCard
import pw.x4.ninety.ui.components.ToggleRow
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.icons.SettingsIcons
import pw.x4.ninety.ui.layout.NinetyLayoutMetrics
import pw.x4.ninety.ui.layout.NinetyPage
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.ui.theme.ThemePack
import pw.x4.ninety.ui.theme.ThemePacks

private data class AdaptiveSettingSection(
    val key: String,
    val title: String,
    val hint: String,
    val icon: ImageVector,
)

private val AdaptiveSettingSections = listOf(
    AdaptiveSettingSection("general", "Общие", "Запуск, обновления и диагностика соединения", SettingsIcons.General),
    AdaptiveSettingSection("appearance", "Оформление", "Полные палитры desktop-Ninety", SettingsIcons.Theme),
    AdaptiveSettingSection("routing", "Маршрутизация", "Регион, LAN, реклама и пользовательские правила", SettingsIcons.Routing),
    AdaptiveSettingSection("dns", "DNS", "Remote/Direct DNS и Fake-DNS", SettingsIcons.Dns),
    AdaptiveSettingSection("inbound", "Локальный доступ", "MTU, TUN stack и strict route", SettingsIcons.Inbound),
    AdaptiveSettingSection("tls", "TLS-фрагментация", "ClientHello, padding и SNI", SettingsIcons.Tls),
    AdaptiveSettingSection("mux", "Мультиплексор", "Параллельные потоки транспорта", SettingsIcons.Mux),
    AdaptiveSettingSection("logs", "Логи", "Отчёты ядра и аварий", NinetyIcons.Logs),
    AdaptiveSettingSection("about", "О программе", "Версия, обновления и репозиторий", SettingsIcons.Info),
)

@Composable
fun SettingsScreen(metrics: NinetyLayoutMetrics) {
    val context = LocalContext.current
    Options.load(context)
    var selected by rememberSaveable { mutableStateOf(if (metrics.isExpanded) "general" else null) }

    NinetyPage(metrics) {
        if (metrics.isExpanded) {
            Row(
                Modifier.fillMaxSize().padding(top = 22.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AdaptiveSettingsNavigation(
                    selected = selected ?: "general",
                    onSelect = { selected = it },
                    modifier = Modifier.width(270.dp).fillMaxHeight(),
                )
                AdaptiveSettingsSectionPane(
                    sectionKey = selected ?: "general",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else if (selected == null) {
            AdaptiveSettingsMenu(onOpen = { selected = it })
        } else {
            AdaptiveSettingsMobileSection(
                sectionKey = selected!!,
                onBack = { selected = null },
            )
        }
    }
}

@Composable
private fun AdaptiveSettingsMenu(onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 22.dp, bottom = 28.dp),
    ) {
        ScreenHeader(
            kicker = "System · Preferences",
            title = "Настройки",
            sub = "Параметры Android-клиента и sing-box. Изменения ядра применяются при reload.",
        )
        Spacer(Modifier.height(18.dp))
        AdaptiveSettingSections.forEach { section ->
            AdaptiveSettingsNavCard(section, selected = false) { onOpen(section.key) }
            Spacer(Modifier.height(9.dp))
        }
    }
}

@Composable
private fun AdaptiveSettingsNavigation(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Ink1)
            .border(1.dp, Ink.Line1, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("SYSTEM · SETTINGS", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(6.dp))
        Text("Параметры", style = NinetyTypography.titleLarge, color = Ink.TextHi)
        Spacer(Modifier.height(14.dp))
        AdaptiveSettingSections.forEach { section ->
            AdaptiveSettingsNavCard(section, selected = section.key == selected) { onSelect(section.key) }
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun AdaptiveSettingsNavCard(
    section: AdaptiveSettingSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) pack.accentSoft else Ink.Ink2)
            .border(1.dp, if (selected) pack.accentSoft else Ink.Line1, RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(if (selected) pack.accentSoft else Ink.Ink3, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(section.icon, null, tint = if (selected) pack.accentBright else Ink.TextMid, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(section.title, style = NinetyTypography.titleMedium, color = Ink.TextHi)
            Text(section.hint, style = MonoStyle, color = Ink.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!selected) Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun AdaptiveSettingsMobileSection(sectionKey: String, onBack: () -> Unit) {
    val section = AdaptiveSettingSections.first { it.key == sectionKey }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SettingsIcons.ArrowLeft, "Назад", tint = Ink.TextMid, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(6.dp))
            Column {
                Text(section.title, style = NinetyTypography.headlineMedium, color = Ink.TextHi)
                Text(section.hint, style = MonoStyle, color = Ink.TextLo)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
        AdaptiveSettingsContent(
            sectionKey = sectionKey,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun AdaptiveSettingsSectionPane(sectionKey: String, modifier: Modifier = Modifier) {
    val section = AdaptiveSettingSections.first { it.key == sectionKey }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Ink0)
            .border(1.dp, Ink.Line1, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        ScreenHeader(kicker = "Settings · ${section.key}", title = section.title, sub = section.hint)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
        AdaptiveSettingsContent(
            sectionKey = sectionKey,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun AdaptiveSettingsContent(sectionKey: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        when (sectionKey) {
            "general" -> AdaptiveGeneralSettings()
            "appearance" -> AdaptiveAppearanceSettings()
            "routing" -> AdaptiveRoutingSettings()
            "dns" -> AdaptiveDnsSettings()
            "inbound" -> AdaptiveInboundSettings()
            "tls" -> AdaptiveTlsSettings()
            "mux" -> AdaptiveMuxSettings()
            "logs" -> AdaptiveLogsSettings()
            "about" -> AdaptiveAboutSettings()
        }
    }
}

@Composable
private fun AdaptiveGeneralSettings() {
    val context = LocalContext.current
    val prefs = Prefs.get(context)
    val options = Options.data
    var autoConnect by remember { mutableStateOf(prefs.autoConnect) }
    var autoUpdate by remember { mutableStateOf(prefs.autoUpdateCheck) }

    SurfaceCard {
        ToggleRow("Автоподключение", autoConnect, "К последнему выбору при запуске") {
            autoConnect = it
            prefs.autoConnect = it
        }
        ToggleRow("Проверять обновления", autoUpdate, "Тихая проверка GitHub Releases") {
            autoUpdate = it
            prefs.autoUpdateCheck = it
        }
    }
    SettingsGap()
    ApplyHint()
    SettingsGap()
    SurfaceCard {
        AdaptiveTextSetting("URL проверки", "Endpoint urltest", options.testUrl) {
            Options.update(context) { current -> current.copy(testUrl = it) }
        }
        AdaptiveNumberSetting("Интервал проверки", "30–3600 секунд", options.testIntervalSec, 30, 3600) {
            Options.update(context) { current -> current.copy(testIntervalSec = it) }
        }
        AdaptiveSelectSetting("Уровень логов", "Подробность sing-box", options.logLevel, listOf("trace", "debug", "info", "warn", "error")) {
            Options.update(context) { current -> current.copy(logLevel = it) }
        }
        ToggleRow("Отключить логи", options.logDisabled, "Диагностика ядра станет недоступна") {
            Options.update(context) { current -> current.copy(logDisabled = it) }
        }
    }
}

@Composable
private fun AdaptiveAppearanceSettings() {
    val context = LocalContext.current
    val prefs = Prefs.get(context)
    Text(
        "Тема меняет материал поверхности, линии, текст и акцент. Выбор сохраняется автоматически.",
        style = NinetyTypography.bodyMedium,
        color = Ink.TextMid,
    )
    SettingsGap()
    ThemePacks.forEach { pack ->
        AdaptiveThemeCard(pack, selected = pack.id == NinetyState.pack.id) {
            NinetyState.pack = pack
            prefs.themePack = pack.id
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun AdaptiveThemeCard(pack: ThemePack, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) pack.accentSoft else Ink.Ink2)
            .border(1.dp, if (selected) pack.accent else Ink.Line2, shape)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(pack.palette.ink0, pack.palette.ink2, pack.accent, pack.accentBright).forEach { color ->
                Box(Modifier.size(22.dp).background(color, CircleShape).border(1.dp, Ink.Line2, CircleShape))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(pack.label, style = NinetyTypography.titleMedium, color = Ink.TextHi)
            Text(pack.kicker, style = KickerStyle, color = if (selected) pack.accentBright else Ink.TextLo)
        }
        if (selected) Text("ACTIVE", style = KickerStyle, color = pack.accentBright)
    }
}

@Composable
private fun AdaptiveRoutingSettings() {
    val context = LocalContext.current
    val options = Options.data
    ApplyHint()
    SettingsGap()
    SurfaceCard {
        AdaptiveSelectSetting("Регион", "Локальные geosite/geoip идут напрямую", options.region, listOf("other", "ru", "cn", "ir", "tr", "by"), REGION_LABELS_ADAPTIVE) {
            Options.update(context) { current -> current.copy(region = it) }
        }
        ToggleRow("Блокировать рекламу", options.blockAds, "Rule sets рекламы и malware") {
            Options.update(context) { current -> current.copy(blockAds = it) }
        }
        ToggleRow("Обход LAN", options.bypassLan, "Приватные адреса идут напрямую") {
            Options.update(context) { current -> current.copy(bypassLan = it) }
        }
        AdaptiveSelectSetting("Маршрут IPv6", "Стратегия выбора IP", options.ipv6Mode, listOf("disable", "enable", "prefer", "only"), IPV6_LABELS_ADAPTIVE) {
            Options.update(context) { current -> current.copy(ipv6Mode = it) }
        }
    }
    SettingsGap()
    RoutingRulesEditor(options.customRules) { rules ->
        Options.update(context) { current -> current.copy(customRules = rules) }
    }
}

@Composable
private fun AdaptiveDnsSettings() {
    val context = LocalContext.current
    val options = Options.data
    ApplyHint()
    SettingsGap()
    SurfaceCard {
        AdaptiveTextSetting("Remote DNS", "DNS для трафика через прокси", options.dnsRemote) {
            Options.update(context) { current -> current.copy(dnsRemote = it) }
        }
        AdaptiveTextSetting("Direct DNS", "DNS для прямого трафика", options.dnsDirect) {
            Options.update(context) { current -> current.copy(dnsDirect = it) }
        }
        ToggleRow("Независимый DNS-кэш", options.independentCache, "Раздельный кэш remote/direct") {
            Options.update(context) { current -> current.copy(independentCache = it) }
        }
        ToggleRow("Fake-DNS", options.fakeDns, "Внутренний маппинг доменов в TUN") {
            Options.update(context) { current -> current.copy(fakeDns = it) }
        }
    }
}

@Composable
private fun AdaptiveInboundSettings() {
    val context = LocalContext.current
    val options = Options.data
    ApplyHint()
    SettingsGap()
    SurfaceCard {
        AdaptiveNumberSetting("MTU TUN", "576–9000", options.mtu, 576, 9000) {
            Options.update(context) { current -> current.copy(mtu = it) }
        }
        AdaptiveSelectSetting("TUN-стек", "Реализация сетевого стека", options.tunStack, listOf("mixed", "gvisor", "system"), TUN_LABELS_ADAPTIVE) {
            Options.update(context) { current -> current.copy(tunStack = it) }
        }
        ToggleRow("Строгая маршрутизация", options.strictRoute, "Перехватывать весь подходящий трафик") {
            Options.update(context) { current -> current.copy(strictRoute = it) }
        }
    }
}

@Composable
private fun AdaptiveTlsSettings() {
    val context = LocalContext.current
    val options = Options.data
    ApplyHint()
    SettingsGap()
    SurfaceCard {
        ToggleRow("TLS-фрагментация", options.tlsFragment, "Фрагментация ClientHello") {
            Options.update(context) { current -> current.copy(tlsFragment = it) }
        }
        AdaptiveSelectSetting("Режим фрагментации", "TLS record или TCP segment", options.fragmentMode, listOf("record", "tcp")) {
            Options.update(context) { current -> current.copy(fragmentMode = it) }
        }
        ToggleRow("Смешанный регистр SNI", options.mixedSniCase, "Изменять регистр имени сервера") {
            Options.update(context) { current -> current.copy(mixedSniCase = it) }
        }
        ToggleRow("TLS padding", options.tlsPadding, "Добавлять случайный padding") {
            Options.update(context) { current -> current.copy(tlsPadding = it) }
        }
        AdaptiveNumberSetting("Padding от", "Минимум байт", options.paddingFrom, 0, 4096) {
            Options.update(context) { current -> current.copy(paddingFrom = it) }
        }
        AdaptiveNumberSetting("Padding до", "Максимум байт", options.paddingTo, 0, 4096) {
            Options.update(context) { current -> current.copy(paddingTo = it) }
        }
    }
}

@Composable
private fun AdaptiveMuxSettings() {
    val context = LocalContext.current
    val options = Options.data
    ApplyHint()
    SettingsGap()
    SurfaceCard {
        ToggleRow("Включить multiplex", options.muxEnable, "Несколько потоков через одно соединение") {
            Options.update(context) { current -> current.copy(muxEnable = it) }
        }
        AdaptiveSelectSetting("Протокол", "Multiplex transport", options.muxProtocol, listOf("h2mux", "smux", "yamux")) {
            Options.update(context) { current -> current.copy(muxProtocol = it) }
        }
        AdaptiveNumberSetting("Максимум потоков", "1–64", options.muxMaxStreams, 1, 64) {
            Options.update(context) { current -> current.copy(muxMaxStreams = it) }
        }
        ToggleRow("Padding multiplex", options.muxPadding, "Добавлять padding к фреймам") {
            Options.update(context) { current -> current.copy(muxPadding = it) }
        }
    }
}

@Composable
private fun AdaptiveLogsSettings() {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    val crash = remember(refreshKey) { Diag.lastCrash(context) }
    val stderr = remember(refreshKey) { Diag.boxStderr(context) }
    val run = remember(refreshKey) { Diag.boxRun(context) }
    val logcat = remember(refreshKey) { Diag.logcat(context) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Обновить") { refreshKey++ }
        PillButton("Скопировать") {
            copyText(context, Diag.fullReport(context))
            toastAdaptive(context, "Диагностика скопирована")
        }
        PillButton("Поделиться") { shareDiagnostics(context) }
        PillButton("Очистить") {
            Diag.clear(context)
            refreshKey++
            toastAdaptive(context, "Логи очищены")
        }
    }
    SettingsGap()
    DiagnosticCard("ПОСЛЕДНИЙ КРАШ", crash)
    SettingsGap()
    DiagnosticCard("STDERR ЯДРА", stderr)
    SettingsGap()
    DiagnosticCard("ЛОГ SING-BOX", run)
    SettingsGap()
    DiagnosticCard("LOGCAT SNAPSHOT", logcat)
}

@Composable
private fun DiagnosticCard(title: String, value: String?) {
    SurfaceCard {
        Text(title, style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(8.dp))
        Text(
            value ?: "Нет данных",
            style = MonoStyle,
            color = if (value == null) Ink.TextLo else Ink.TextMid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AdaptiveAboutSettings() {
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.dp).background(NinetyState.pack.accentSoft, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("九", style = NinetyTypography.headlineMedium, color = NinetyState.pack.accentBright)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Ninety Android", style = NinetyTypography.titleLarge, color = Ink.TextHi)
                Text("v${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}", style = MonoStyle, color = Ink.TextLo)
            }
        }
        SettingsGap()
        Text(
            "Нативный Android VPN-клиент 190x4 на VpnService и libbox. Общие parser/config contracts синхронизированы с desktop-Ninety.",
            style = NinetyTypography.bodyMedium,
            color = Ink.TextMid,
        )
    }
    SettingsGap()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(if (checking) "Проверяю…" else "Проверить обновление", enabled = !checking) {
            Updater.check(
                onLoading = { checking = true },
                onDone = { release, error ->
                    checking = false
                    result = error ?: release?.let { "Доступна версия ${it.version}" } ?: "Установлена актуальная версия"
                    if (release != null) Updater.Available.release = release
                },
            )
        }
        PillButton("GitHub") { openUriAdaptive(context, "https://github.com/pathetixx/190x4-Ninety-Android") }
    }
    result?.let {
        SettingsGap()
        Text(it, style = MonoStyle, color = Ink.TextMid)
    }
}

@Composable
private fun ApplyHint() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(NinetyState.pack.accentSoft)
            .border(1.dp, NinetyState.pack.accentSoft, RoundedCornerShape(11.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(NinetyState.pack.accent, CircleShape))
        Spacer(Modifier.width(9.dp))
        Text("Изменения ядра применятся при следующем подключении или reload.", style = MonoStyle, color = Ink.TextMid)
    }
}

@Composable
private fun AdaptiveTextSetting(label: String, hint: String, value: String, onValue: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    AdaptiveSettingRow(label, hint) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it; onValue(it) },
            singleLine = true,
            textStyle = MonoStyle.copy(color = Ink.TextHi),
            cursorBrush = SolidColor(NinetyState.pack.accent),
            modifier = Modifier
                .widthIn(min = 150.dp, max = 290.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Ink.Ink3)
                .border(1.dp, Ink.Line2, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AdaptiveNumberSetting(
    label: String,
    hint: String,
    value: Int,
    min: Int,
    max: Int,
    onValue: (Int) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    AdaptiveSettingRow(label, hint) {
        BasicTextField(
            value = draft,
            onValueChange = { raw ->
                val filtered = raw.filter(Char::isDigit)
                draft = filtered
                filtered.toIntOrNull()?.coerceIn(min, max)?.let(onValue)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MonoStyle.copy(color = Ink.TextHi),
            cursorBrush = SolidColor(NinetyState.pack.accent),
            modifier = Modifier
                .width(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Ink.Ink3)
                .border(1.dp, Ink.Line2, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AdaptiveSelectSetting(
    label: String,
    hint: String,
    value: String,
    values: List<String>,
    labels: Map<String, String> = emptyMap(),
    onValue: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    AdaptiveSettingRow(label, hint) {
        Box {
            Text(
                labels[value] ?: value.uppercase(),
                style = MonoStyle,
                color = NinetyState.pack.accentBright,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.Ink3)
                    .border(1.dp, Ink.Line2, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 11.dp, vertical = 8.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(labels[option] ?: option, style = MonoStyle) },
                        onClick = { expanded = false; onValue(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveSettingRow(
    label: String,
    hint: String,
    control: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = NinetyTypography.titleMedium, color = Ink.TextHi)
            Spacer(Modifier.height(2.dp))
            Text(hint, style = MonoStyle, color = Ink.TextLo)
        }
        Spacer(Modifier.width(16.dp))
        control()
    }
}

@Composable
private fun SettingsGap() = Spacer(Modifier.height(14.dp))

private val REGION_LABELS_ADAPTIVE = mapOf(
    "other" to "Другой",
    "ru" to "Россия",
    "cn" to "Китай",
    "ir" to "Иран",
    "tr" to "Турция",
    "by" to "Беларусь",
)
private val IPV6_LABELS_ADAPTIVE = mapOf(
    "disable" to "Отключить",
    "enable" to "Включить",
    "prefer" to "Предпочитать",
    "only" to "Только IPv6",
)
private val TUN_LABELS_ADAPTIVE = mapOf(
    "mixed" to "Mixed",
    "gvisor" to "gVisor",
    "system" to "System",
)

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Ninety diagnostics", text))
}

private fun shareDiagnostics(context: Context) {
    runCatching {
        val file = Diag.writeReportFile(context)
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Поделиться диагностикой",
            ),
        )
    }.onFailure { toastAdaptive(context, it.message ?: "Не удалось поделиться") }
}

private fun openUriAdaptive(context: Context, value: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }
        .onFailure { toastAdaptive(context, "Не удалось открыть ссылку") }
}

private fun toastAdaptive(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
