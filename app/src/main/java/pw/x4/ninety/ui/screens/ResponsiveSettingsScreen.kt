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

private data class SettingSection(
    val key: String,
    val title: String,
    val hint: String,
    val icon: ImageVector,
)

private val SettingSections = listOf(
    SettingSection("general", "Общие", "Запуск, ping и журнал ядра", SettingsIcons.General),
    SettingSection("appearance", "Оформление", "Палитра и визуальная система", SettingsIcons.Theme),
    SettingSection("routing", "Маршрутизация", "Правила, регион, LAN и WARP", SettingsIcons.Routing),
    SettingSection("dns", "DNS", "Remote, direct и Fake-DNS", SettingsIcons.Dns),
    SettingSection("inbound", "TUN", "MTU, stack и strict route", SettingsIcons.Inbound),
    SettingSection("tls", "TLS", "Фрагментация и padding", SettingsIcons.Tls),
    SettingSection("mux", "Multiplex", "Параллельные transport streams", SettingsIcons.Mux),
    SettingSection("logs", "Логи", "Runtime, stderr, crash и logcat", NinetyIcons.Logs),
    SettingSection("about", "О программе", "Версия, обновления и репозиторий", SettingsIcons.Info),
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
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SettingsNavigation(
                    selected = selected ?: "general",
                    onSelect = { selected = it },
                    modifier = Modifier.width(270.dp).fillMaxHeight(),
                )
                SettingsDesktopPane(
                    sectionKey = selected ?: "general",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else if (selected == null) {
            SettingsMenu(onOpen = { selected = it })
        } else {
            SettingsMobilePane(sectionKey = selected!!, onBack = { selected = null })
        }
    }
}

@Composable
private fun SettingsMenu(onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 28.dp),
    ) {
        SettingsTitle("SYSTEM · SETTINGS", "Настройки", "Все controls адаптируются под ширину экрана.")
        Spacer(Modifier.height(18.dp))
        SettingSections.forEach { section ->
            SettingsNavCard(section, selected = false) { onOpen(section.key) }
            Spacer(Modifier.height(9.dp))
        }
    }
}

@Composable
private fun SettingsNavigation(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Ink1)
            .border(1.dp, Ink.Line1, RoundedCornerShape(18.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("NINETY · SYSTEM", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(6.dp))
        Text("Параметры", style = NinetyTypography.titleLarge, color = Ink.TextHi)
        Spacer(Modifier.height(14.dp))
        SettingSections.forEach { section ->
            SettingsNavCard(section, selected = section.key == selected) { onSelect(section.key) }
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun SettingsNavCard(section: SettingSection, selected: Boolean, onClick: () -> Unit) {
    val pack = NinetyState.pack
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) pack.accentSoft else Ink.Ink2)
            .border(1.dp, if (selected) pack.accent else Ink.Line1, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(if (selected) pack.accentSoft else Ink.Ink3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(section.icon, null, tint = if (selected) pack.accentBright else Ink.TextMid, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(section.title, style = NinetyTypography.titleMedium, color = Ink.TextHi)
            Spacer(Modifier.height(2.dp))
            Text(section.hint, style = MonoStyle, color = Ink.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!selected) Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsDesktopPane(sectionKey: String, modifier: Modifier = Modifier) {
    val section = SettingSections.first { it.key == sectionKey }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Ink0)
            .border(1.dp, Ink.Line1, RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        SettingsTitle("SETTINGS · ${section.key.uppercase()}", section.title, section.hint)
        Spacer(Modifier.height(15.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
        SettingsContent(
            sectionKey,
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun SettingsMobilePane(sectionKey: String, onBack: () -> Unit) {
    val section = SettingSections.first { it.key == sectionKey }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Ink.Ink2).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SettingsIcons.ArrowLeft, "Назад", tint = Ink.TextMid, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(section.title, style = NinetyTypography.headlineMedium, color = Ink.TextHi)
                Text(section.hint, style = MonoStyle, color = Ink.TextLo, maxLines = 2)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
        SettingsContent(
            sectionKey,
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 30.dp),
        )
    }
}

@Composable
private fun SettingsTitle(kicker: String, title: String, hint: String) {
    Text(kicker, style = KickerStyle, color = Ink.TextFaint)
    Spacer(Modifier.height(5.dp))
    Text(title, style = NinetyTypography.headlineMedium, color = Ink.TextHi)
    Spacer(Modifier.height(5.dp))
    Text(hint, style = NinetyTypography.bodyMedium, color = Ink.TextMid)
}

@Composable
private fun SettingsContent(sectionKey: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        when (sectionKey) {
            "general" -> GeneralSettings()
            "appearance" -> AppearanceSettings()
            "routing" -> RoutingSettings()
            "dns" -> DnsSettings()
            "inbound" -> InboundSettings()
            "tls" -> TlsSettings()
            "mux" -> MuxSettings()
            "logs" -> LogsSettings()
            "about" -> AboutSettings()
        }
    }
}

@Composable
private fun GeneralSettings() {
    val context = LocalContext.current
    val prefs = Prefs.get(context)
    val options = Options.data
    var autoConnect by remember { mutableStateOf(prefs.autoConnect) }
    var autoUpdate by remember { mutableStateOf(prefs.autoUpdateCheck) }

    SettingsCallout(
        "PING · NATIVE URLTEST",
        "Auto снова управляется штатным selector/urltest sing-box. Внешний Quality Engine больше не меняет ноду и не вызывает reload.",
    )
    SettingsGap()
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
    SettingsGap()
    SurfaceCard {
        TextSetting("URL проверки", "Endpoint для штатной группы urltest", options.testUrl) {
            Options.update(context) { current -> current.copy(testUrl = it) }
        }
        FieldDivider()
        NumberSetting("Интервал проверки", "30–3600 секунд", options.testIntervalSec, 30, 3600) {
            Options.update(context) { current -> current.copy(testIntervalSec = it) }
        }
        FieldDivider()
        SelectSetting(
            "Уровень логов",
            "Подробность журнала sing-box",
            options.logLevel,
            listOf("trace", "debug", "info", "warn", "error"),
        ) { Options.update(context) { current -> current.copy(logLevel = it) } }
        FieldDivider()
        ToggleRow("Отключить логи", options.logDisabled, "Runtime-диагностика станет недоступна") {
            Options.update(context) { current -> current.copy(logDisabled = it) }
        }
    }
}

@Composable
private fun AppearanceSettings() {
    val context = LocalContext.current
    val prefs = Prefs.get(context)
    Text(
        "Палитры сохранены, но экраны постепенно перестраиваются по структуре desktop-Ninety: control panels, hierarchy и status blocks.",
        style = NinetyTypography.bodyMedium,
        color = Ink.TextMid,
    )
    SettingsGap()
    ThemePacks.forEach { pack ->
        ThemeCard(pack, selected = pack.id == NinetyState.pack.id) {
            NinetyState.pack = pack
            prefs.themePack = pack.id
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ThemeCard(pack: ThemePack, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) pack.accentSoft else Ink.Ink1)
            .border(1.dp, if (selected) pack.accent else Ink.Line1, shape)
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(pack.palette.ink0, pack.palette.ink2, pack.accent, pack.accentBright).forEach { color ->
                Box(Modifier.size(23.dp).background(color, CircleShape).border(1.dp, Ink.Line2, CircleShape))
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
private fun RoutingSettings() {
    val context = LocalContext.current
    val options = Options.data
    ReloadHint()
    SettingsGap()
    SurfaceCard {
        SelectSetting(
            "Регион",
            "Локальные geosite/geoip идут напрямую",
            options.region,
            listOf("other", "ru", "cn", "ir", "tr", "by"),
            REGION_LABELS,
        ) { Options.update(context) { current -> current.copy(region = it) } }
        FieldDivider()
        ToggleRow("Блокировать рекламу", options.blockAds, "Rule sets рекламы, malware и phishing") {
            Options.update(context) { current -> current.copy(blockAds = it) }
        }
        ToggleRow("Обход LAN", options.bypassLan, "Приватные адреса идут напрямую") {
            Options.update(context) { current -> current.copy(bypassLan = it) }
        }
        FieldDivider()
        SelectSetting(
            "Маршрут IPv6",
            "Стратегия выбора IP",
            options.ipv6Mode,
            listOf("disable", "enable", "prefer", "only"),
            IPV6_LABELS,
        ) { Options.update(context) { current -> current.copy(ipv6Mode = it) } }
    }
    SettingsGap()
    RoutingRulesEditor(options.customRules) { rules ->
        Options.update(context) { current -> current.copy(customRules = rules) }
    }
}

@Composable
private fun DnsSettings() {
    val context = LocalContext.current
    val options = Options.data
    ReloadHint()
    SettingsGap()
    SurfaceCard {
        TextSetting("Remote DNS", "DNS защищённого маршрута", options.dnsRemote) {
            Options.update(context) { current -> current.copy(dnsRemote = it) }
        }
        FieldDivider()
        TextSetting("Direct DNS", "DNS прямого маршрута", options.dnsDirect) {
            Options.update(context) { current -> current.copy(dnsDirect = it) }
        }
        FieldDivider()
        ToggleRow("Независимый DNS-кэш", options.independentCache, "Раздельный кэш remote/direct") {
            Options.update(context) { current -> current.copy(independentCache = it) }
        }
        ToggleRow("Fake-DNS", options.fakeDns, "Внутренний mapping доменов в TUN") {
            Options.update(context) { current -> current.copy(fakeDns = it) }
        }
    }
}

@Composable
private fun InboundSettings() {
    val context = LocalContext.current
    val options = Options.data
    ReloadHint()
    SettingsGap()
    SurfaceCard {
        NumberSetting("MTU TUN", "576–9000", options.mtu, 576, 9000) {
            Options.update(context) { current -> current.copy(mtu = it) }
        }
        FieldDivider()
        SelectSetting(
            "TUN-стек",
            "Реализация сетевого стека",
            options.tunStack,
            listOf("mixed", "gvisor", "system"),
            TUN_LABELS,
        ) { Options.update(context) { current -> current.copy(tunStack = it) } }
        FieldDivider()
        ToggleRow("Строгая маршрутизация", options.strictRoute, "Перехватывать весь подходящий трафик") {
            Options.update(context) { current -> current.copy(strictRoute = it) }
        }
    }
}

@Composable
private fun TlsSettings() {
    val context = LocalContext.current
    val options = Options.data
    ReloadHint()
    SettingsGap()
    SurfaceCard {
        ToggleRow("TLS-фрагментация", options.tlsFragment, "Фрагментация ClientHello") {
            Options.update(context) { current -> current.copy(tlsFragment = it) }
        }
        FieldDivider()
        SelectSetting(
            "Режим фрагментации",
            "TLS record или TCP segment",
            options.fragmentMode,
            listOf("record", "tcp"),
        ) { Options.update(context) { current -> current.copy(fragmentMode = it) } }
        FieldDivider()
        ToggleRow("Смешанный регистр SNI", options.mixedSniCase, "Изменять регистр имени сервера") {
            Options.update(context) { current -> current.copy(mixedSniCase = it) }
        }
        ToggleRow("TLS padding", options.tlsPadding, "Добавлять случайный padding") {
            Options.update(context) { current -> current.copy(tlsPadding = it) }
        }
        FieldDivider()
        NumberSetting("Padding от", "Минимум байт", options.paddingFrom, 0, 4096) {
            Options.update(context) { current -> current.copy(paddingFrom = it) }
        }
        FieldDivider()
        NumberSetting("Padding до", "Максимум байт", options.paddingTo, 0, 4096) {
            Options.update(context) { current -> current.copy(paddingTo = it) }
        }
    }
}

@Composable
private fun MuxSettings() {
    val context = LocalContext.current
    val options = Options.data
    ReloadHint()
    SettingsGap()
    SurfaceCard {
        ToggleRow("Включить multiplex", options.muxEnable, "Несколько потоков через одно соединение") {
            Options.update(context) { current -> current.copy(muxEnable = it) }
        }
        FieldDivider()
        SelectSetting(
            "Протокол",
            "Multiplex transport",
            options.muxProtocol,
            listOf("h2mux", "smux", "yamux"),
        ) { Options.update(context) { current -> current.copy(muxProtocol = it) } }
        FieldDivider()
        NumberSetting("Максимум потоков", "1–64", options.muxMaxStreams, 1, 64) {
            Options.update(context) { current -> current.copy(muxMaxStreams = it) }
        }
        FieldDivider()
        ToggleRow("Padding multiplex", options.muxPadding, "Добавлять padding к фреймам") {
            Options.update(context) { current -> current.copy(muxPadding = it) }
        }
    }
}

@Composable
private fun LogsSettings() {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    val crash = remember(refreshKey) { Diag.lastCrash(context) }
    val stderr = remember(refreshKey) { Diag.boxStderr(context) }
    val run = remember(refreshKey) { Diag.boxRun(context) }
    val logcat = remember(refreshKey) { Diag.logcat(context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PillButton("Обновить") { refreshKey++ } }
            Box(Modifier.weight(1f)) {
                PillButton("Скопировать") {
                    copyText(context, Diag.fullReport(context))
                    toast(context, "Диагностика скопирована")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PillButton("Поделиться") { shareDiagnostics(context) } }
            Box(Modifier.weight(1f)) {
                PillButton("Очистить") {
                    Diag.clear(context)
                    refreshKey++
                    toast(context, "Логи очищены")
                }
            }
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
private fun AboutSettings() {
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(60.dp).background(NinetyState.pack.accentSoft, RoundedCornerShape(17.dp)),
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
            "Нативный Android-клиент 190x4 на VpnService и libbox. Runtime подключения и UI сейчас проходят повторную проверку на реальном устройстве.",
            style = NinetyTypography.bodyMedium,
            color = Ink.TextMid,
        )
    }
    SettingsGap()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        PillButton("GitHub") { openUri(context, "https://github.com/pathetixx/190x4-Ninety-Android") }
    }
    result?.let {
        SettingsGap()
        Text(it, style = MonoStyle, color = Ink.TextMid)
    }
}

@Composable
private fun SettingsCallout(kicker: String, text: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(NinetyState.pack.accentSoft)
            .border(1.dp, NinetyState.pack.accentSoft, RoundedCornerShape(13.dp))
            .padding(13.dp),
    ) {
        Text(kicker, style = KickerStyle, color = NinetyState.pack.accentBright)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MonoStyle, color = Ink.TextMid)
    }
}

@Composable
private fun ReloadHint() = SettingsCallout(
    "ENGINE · RELOAD",
    "Изменения ядра применятся при следующем подключении или явном reload.",
)

@Composable
private fun TextSetting(label: String, hint: String, value: String, onValue: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    SettingFieldHeader(label, hint)
    Spacer(Modifier.height(8.dp))
    BasicTextField(
        value = draft,
        onValueChange = {
            draft = it
            onValue(it)
        },
        singleLine = true,
        textStyle = MonoStyle.copy(color = Ink.TextHi),
        cursorBrush = SolidColor(NinetyState.pack.accent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
            .background(Ink.Ink3).border(1.dp, Ink.Line2, RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    )
}

@Composable
private fun NumberSetting(
    label: String,
    hint: String,
    value: Int,
    min: Int,
    max: Int,
    onValue: (Int) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    SettingFieldHeader(label, hint)
    Spacer(Modifier.height(8.dp))
    BasicTextField(
        value = draft,
        onValueChange = { raw ->
            draft = raw.filter(Char::isDigit)
            draft.toIntOrNull()?.coerceIn(min, max)?.let(onValue)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MonoStyle.copy(color = Ink.TextHi),
        cursorBrush = SolidColor(NinetyState.pack.accent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
            .background(Ink.Ink3).border(1.dp, Ink.Line2, RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    )
}

@Composable
private fun SelectSetting(
    label: String,
    hint: String,
    value: String,
    values: List<String>,
    labels: Map<String, String> = emptyMap(),
    onValue: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingFieldHeader(label, hint)
    Spacer(Modifier.height(8.dp))
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                .background(Ink.Ink3).border(1.dp, Ink.Line2, RoundedCornerShape(9.dp))
                .clickable { expanded = true }.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(labels[value] ?: value.uppercase(), style = MonoStyle, color = NinetyState.pack.accentBright, modifier = Modifier.weight(1f))
            Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(15.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labels[option] ?: option, style = MonoStyle) },
                    onClick = {
                        expanded = false
                        onValue(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingFieldHeader(label: String, hint: String) {
    Text(label, style = NinetyTypography.titleMedium, color = Ink.TextHi)
    Spacer(Modifier.height(3.dp))
    Text(hint, style = MonoStyle, color = Ink.TextLo)
}

@Composable
private fun FieldDivider() {
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SettingsGap() = Spacer(Modifier.height(14.dp))

private val REGION_LABELS = mapOf(
    "other" to "Другой",
    "ru" to "Россия",
    "cn" to "Китай",
    "ir" to "Иран",
    "tr" to "Турция",
    "by" to "Беларусь",
)

private val IPV6_LABELS = mapOf(
    "disable" to "Отключить",
    "enable" to "Включить",
    "prefer" to "Предпочитать",
    "only" to "Только IPv6",
)

private val TUN_LABELS = mapOf(
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
    }.onFailure { toast(context, it.message ?: "Не удалось поделиться") }
}

private fun openUri(context: Context, value: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }
        .onFailure { toast(context, "Не удалось открыть ссылку") }
}

private fun toast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
