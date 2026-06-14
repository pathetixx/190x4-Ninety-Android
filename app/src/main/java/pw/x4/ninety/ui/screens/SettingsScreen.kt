package pw.x4.ninety.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
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
import pw.x4.ninety.BuildConfig
import pw.x4.ninety.data.Diag
import pw.x4.ninety.data.Options
import pw.x4.ninety.data.Prefs
import pw.x4.ninety.data.Updater
import pw.x4.ninety.ui.components.Kicker
import pw.x4.ninety.ui.components.PillButton
import pw.x4.ninety.ui.components.SectionHeader
import pw.x4.ninety.ui.components.SurfaceCard
import pw.x4.ninety.ui.components.ToggleRow
import pw.x4.ninety.ui.components.topHairline
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.icons.SettingsIcons
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.ui.theme.ThemePack
import pw.x4.ninety.ui.theme.ThemePacks

// ── Описание разделов (как desktop SECTIONS, минус WARP — на Android нереализуем) ──
private data class Section(val key: String, val title: String, val hint: String, val icon: ImageVector)

private val SECTIONS = listOf(
    Section("general", "Общие", "Автоподключение, проверка соединения, логи", SettingsIcons.General),
    Section("appearance", "Оформление", "Темы: Kurogane, Synthwave, Matrix, Mono", SettingsIcons.Theme),
    Section("routing", "Маршрутизация", "Регион, обход LAN, блокировка рекламы, IPv6", SettingsIcons.Routing),
    Section("dns", "DNS", "Remote/Direct DNS, кэш, fake-DNS", SettingsIcons.Dns),
    Section("inbound", "Локальный доступ", "MTU, TUN-стек, строгая маршрутизация", SettingsIcons.Inbound),
    Section("tls", "TLS-фрагментация", "Фрагментация ClientHello, padding, регистр SNI", SettingsIcons.Tls),
    Section("mux", "Мультиплексор", "Несколько соединений через один транспорт", SettingsIcons.Mux),
    Section("logs", "Логи", "Диагностика ядра, краши, копирование", NinetyIcons.Logs),
    Section("about", "О программе", "Версия, репозиторий, лицензия", SettingsIcons.Info),
)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    Options.load(context)
    var section by rememberSaveable { mutableStateOf<String?>(null) }

    if (section == null) {
        SettingsMenu(onOpen = { section = it })
    } else {
        val sec = SECTIONS.first { it.key == section }
        SectionScaffold(sec.title, onBack = { section = null }) {
            when (sec.key) {
                "general" -> GeneralSection(context)
                "appearance" -> AppearanceSection(context)
                "routing" -> RoutingSection(context)
                "dns" -> DnsSection(context)
                "inbound" -> InboundSection(context)
                "tls" -> TlsSection(context)
                "mux" -> MuxSection(context)
                "logs" -> LogsSection(context)
                "about" -> AboutSection(context)
            }
        }
    }
}

// ── Меню ───────────────────────────────────────────────────
@Composable
private fun SettingsMenu(onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Настройки", style = NinetyTypography.headlineMedium, color = Ink.TextHi)
        Spacer(Modifier.height(16.dp))
        SECTIONS.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Ink.Ink1, RoundedCornerShape(10.dp))
                    .border(1.dp, Ink.Line1, RoundedCornerShape(10.dp))
                    .clickable { onOpen(s.key) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).background(Ink.Ink2, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(s.icon, null, tint = Ink.TextMid, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = NinetyTypography.titleMedium, color = Ink.TextHi)
                    Spacer(Modifier.height(2.dp))
                    Text(s.hint, style = MonoStyle, color = Ink.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Каркас подраздела: back-хедер + скролл-контент ─────────
@Composable
private fun SectionScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { Icon(SettingsIcons.ArrowLeft, "Назад", tint = Ink.TextMid, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(4.dp))
            Text(title, style = NinetyTypography.headlineMedium, color = Ink.TextHi)
        }
        // Разделитель под хедером (порт settings-head border-bottom: 1px line-1)
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Ink.Line1))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            content()
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ── Секции ─────────────────────────────────────────────────
@Composable
private fun GeneralSection(context: Context) {
    val prefs = Prefs.get(context)
    val o = Options.data
    SurfaceCard {
        var auto by remember { mutableStateOf(prefs.autoConnect) }
        ToggleRow("Автоподключение", auto, sub = "К последней ноде при запуске") { auto = it; prefs.autoConnect = it }
        var chk by remember { mutableStateOf(prefs.autoUpdateCheck) }
        ToggleRow("Проверять обновления при запуске", chk) { chk = it; prefs.autoUpdateCheck = it }
    }
    Spacer(Modifier.height(14.dp))
    ApplyBanner()
    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        SetText("URL для теста соединения", "HTTP(S)-endpoint проверки доступности нод", o.testUrl) {
            Options.update(context) { d -> d.copy(testUrl = it) }
        }
        SetNumber("Интервал теста (сек)", "Как часто ядро перемеряет задержку нод", o.testIntervalSec, 30, 3600) {
            Options.update(context) { d -> d.copy(testIntervalSec = it) }
        }
        SetSelect("Уровень логов", "Подробность логов sing-box", o.logLevel,
            listOf("trace", "debug", "info", "warn", "error")) {
            Options.update(context) { d -> d.copy(logLevel = it) }
        }
        SetSwitch("Отключить логи", "Ядро не пишет логи — диагностика недоступна", o.logDisabled) {
            Options.update(context) { d -> d.copy(logDisabled = it) }
        }
    }
}

@Composable
private fun AppearanceSection(context: Context) {
    val prefs = Prefs.get(context)
    val current = NinetyState.pack
    SettingsBanner("Палитра неизменна — меняется только акцентный цвет. Выбор сохраняется автоматически.")
    Spacer(Modifier.height(14.dp))
    ThemePacks.forEach { pack ->
        ThemeCard(pack, selected = pack.id == current.id) {
            NinetyState.pack = pack; prefs.themePack = pack.id
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RoutingSection(context: Context) {
    val o = Options.data
    ApplyBanner()
    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        SetSelect("Регион", "Локальный трафик региона идёт напрямую (geosite/geoip)", o.region,
            listOf("other", "ru", "cn", "ir", "tr", "by"), REGION_LABELS) {
            Options.update(context) { d -> d.copy(region = it) }
        }
        SetSwitch("Блокировать рекламу", "Domain/IP списки рекламы и malware", o.blockAds) {
            Options.update(context) { d -> d.copy(blockAds = it) }
        }
        SetSwitch("Обход LAN", "Локальные адреса (10.x, 192.168.x) идут напрямую", o.bypassLan) {
            Options.update(context) { d -> d.copy(bypassLan = it) }
        }
        SetSelect("Маршрут IPv6", "Стратегия выбора IPv4/IPv6", o.ipv6Mode,
            listOf("disable", "enable", "prefer", "only"), IPV6_LABELS) {
            Options.update(context) { d -> d.copy(ipv6Mode = it) }
        }
    }
}

@Composable
private fun DnsSection(context: Context) {
    val o = Options.data
    ApplyBanner()
    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        SetText("Remote DNS", "DNS для трафика через прокси (DoH/DoT/UDP)", o.dnsRemote) {
            Options.update(context) { d -> d.copy(dnsRemote = it) }
        }
        SetText("Direct DNS", "DNS для прямого трафика (region/bypass)", o.dnsDirect) {
            Options.update(context) { d -> d.copy(dnsDirect = it) }
        }
        SetSwitch("Независимый DNS-кэш", "Раздельный кэш для remote и direct", o.independentCache) {
            Options.update(context) { d -> d.copy(independentCache = it) }
        }
        SetSwitch("Fake-DNS", "Поддельный IP, маппинг в памяти. Полезно при TUN", o.fakeDns) {
            Options.update(context) { d -> d.copy(fakeDns = it) }
        }
    }
}

@Composable
private fun InboundSection(context: Context) {
    val o = Options.data
    ApplyBanner()
    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        SetNumber("MTU TUN", "Максимальный размер пакета", o.mtu, 576, 9000) {
            Options.update(context) { d -> d.copy(mtu = it) }
        }
        SetSelect("TUN-стек", "Реализация TUN-стека", o.tunStack,
            listOf("mixed", "gvisor", "system"), TUN_STACK_LABELS) {
            Options.update(context) { d -> d.copy(tunStack = it) }
        }
        SetSwitch("Строгая маршрутизация", "Блокировать утечки трафика мимо TUN", o.strictRoute) {
            Options.update(context) { d -> d.copy(strictRoute = it) }
        }
    }
}

@Composable
private fun TlsSection(context: Context) {
    val o = Options.data
    SettingsBanner("Фрагментация делит TLS-handshake на части — помогает, когда провайдер режет по SNI. Начните с фрагментации; padding и регистр SNI включайте, только если без них не соединяется (могут ломать Reality).")
    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        SetSwitch("Фрагментация ClientHello", "Делит TLS-handshake — обход DPI", o.tlsFragment) {
            Options.update(context) { d -> d.copy(tlsFragment = it) }
        }
        SetSelect("Способ фрагментации", "record — по TLS-записям (рекоменд.); tcp — по сегментам", o.fragmentMode,
            listOf("record", "tcp"), FRAGMENT_LABELS) {
            Options.update(context) { d -> d.copy(fragmentMode = it) }
        }
        SetSwitch("Mixed SNI case", "Перемешивает регистр в SNI (может ломать Reality)", o.mixedSniCase) {
            Options.update(context) { d -> d.copy(mixedSniCase = it) }
        }
        SetSwitch("TLS padding", "Добавляет padding в ClientHello (может ломать Reality)", o.tlsPadding) {
            Options.update(context) { d -> d.copy(tlsPadding = it) }
        }
        if (o.tlsPadding) {
            SetNumber("Padding от (байт)", "Нижняя граница длины", o.paddingFrom, 0, 4096) {
                Options.update(context) { d -> d.copy(paddingFrom = it) }
            }
            SetNumber("Padding до (байт)", "Верхняя граница длины", o.paddingTo, 0, 4096) {
                Options.update(context) { d -> d.copy(paddingTo = it) }
            }
        }
    }
}

@Composable
private fun MuxSection(context: Context) {
    val o = Options.data
    SettingsBanner("Мультиплексор гонит несколько соединений через один транспорт — меньше TLS-рукопожатий. На быстрых каналах может резать скорость. Протокол должен поддерживаться сервером.")
    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        SetSwitch("Включить мультиплексор", "Один транспорт под все соединения к ноде", o.muxEnable) {
            Options.update(context) { d -> d.copy(muxEnable = it) }
        }
        if (o.muxEnable) {
            SetSelect("Протокол", "Схема мультиплексирования — как на сервере", o.muxProtocol,
                listOf("h2mux", "smux", "yamux")) {
                Options.update(context) { d -> d.copy(muxProtocol = it) }
            }
            SetNumber("Макс. потоков", "Сколько соединений на один транспорт", o.muxMaxStreams, 1, 1024) {
                Options.update(context) { d -> d.copy(muxMaxStreams = it) }
            }
            SetSwitch("Padding", "Маскирует размеры mux-кадров", o.muxPadding) {
                Options.update(context) { d -> d.copy(muxPadding = it) }
            }
        }
    }
}

@Composable
private fun LogsSection(context: Context) {
    var refresh by remember { mutableIntStateOf(0) }
    val crash = remember(refresh) { Diag.lastCrash(context) }
    val stderr = remember(refresh) { Diag.boxStderr(context) }
    val runLog = remember(refresh) { Diag.boxRun(context) }
    val logcat = remember(refresh) { Diag.logcat(context) }
    val xray = remember(refresh) { Diag.xrayLog(context) }
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(NinetyIcons.Logs, "Диагностика")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("СКОПИРОВАТЬ", style = MonoStyle, color = NinetyState.pack.accent,
                    modifier = Modifier.clickable { copyDiag(context) })
                Spacer(Modifier.width(16.dp))
                Text("ФАЙЛ", style = MonoStyle, color = NinetyState.pack.accent,
                    modifier = Modifier.clickable { shareDiag(context) })
                Spacer(Modifier.width(16.dp))
                Text("ОЧИСТИТЬ", style = MonoStyle, color = Ink.TextLo,
                    modifier = Modifier.clickable { Diag.clear(context); refresh++ })
            }
        }
        Spacer(Modifier.height(10.dp))
        if (crash == null && stderr == null && runLog == null && logcat == null && xray == null) {
            Text("Логов пока нет — подключись к узлу.", color = Ink.TextMid, style = NinetyTypography.bodyMedium)
        } else {
            crash?.let {
                Text("Последний краш:", color = Ink.Err, style = NinetyTypography.titleMedium)
                Spacer(Modifier.height(4.dp)); Text(it, color = Ink.TextMid, style = MonoStyle); Spacer(Modifier.height(10.dp))
            }
            logcat?.let {
                Text("logcat (нативный краш ядра):", color = Ink.Err, style = NinetyTypography.titleMedium)
                Spacer(Modifier.height(4.dp)); Text(it, color = Ink.TextMid, style = MonoStyle); Spacer(Modifier.height(10.dp))
            }
            stderr?.let {
                Text("stderr ядра (паника):", color = Ink.TextLo, style = NinetyTypography.titleMedium)
                Spacer(Modifier.height(4.dp)); Text(it, color = Ink.TextMid, style = MonoStyle); Spacer(Modifier.height(10.dp))
            }
            runLog?.let {
                Text("лог ядра (хвост):", color = Ink.TextLo, style = NinetyTypography.titleMedium)
                Spacer(Modifier.height(4.dp)); Text(it, color = Ink.TextMid, style = MonoStyle); Spacer(Modifier.height(10.dp))
            }
            xray?.let {
                Text("xray (xhttp-узлы):", color = NinetyState.pack.accentBright, style = NinetyTypography.titleMedium)
                Spacer(Modifier.height(4.dp)); Text(it, color = Ink.TextMid, style = MonoStyle)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutSection(context: Context) {
    val pack = NinetyState.pack
    // Паспорт
    SurfaceCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("190×4", style = KickerStyle, color = pack.accentBright)
            Spacer(Modifier.height(6.dp))
            Text("Ninety", style = NinetyTypography.displayLarge, color = Ink.TextHi)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(pack.accentSoft, RoundedCornerShape(6.dp)).border(1.dp, pack.accent, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("v${BuildConfig.VERSION_NAME}", style = MonoStyle, color = pack.accentBright)
                }
                Spacer(Modifier.width(8.dp))
                Text("EARLY ACCESS", style = KickerStyle, color = Ink.TextLo)
            }
            Spacer(Modifier.height(10.dp))
            Text("VPN-клиент в эстетике 190×4", style = NinetyTypography.bodyMedium, color = Ink.TextMid)
        }
    }
    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        SectionHeader(SettingsIcons.Info, "Технический паспорт")
        Spacer(Modifier.height(10.dp))
        InfoRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        InfoRow("Ядро", "sing-box / libbox")
        InfoRow("Платформа", "Android · VPN · TUN")
        InfoRow("Канал", "Early access")
    }
    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        Kicker("Протоколы")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("VLESS", "VMess", "Trojan", "Shadowsocks", "Hysteria2", "TUIC").forEach { Chip(it) }
        }
    }
    Spacer(Modifier.height(14.dp))
    SurfaceCard {
        LinkRow("Репозиторий", "Исходники, релизы, баг-репорты", "ОТКРЫТЬ") {
            openUrl(context, "https://github.com/pathetixx/190x4-Ninety-Android")
        }
        Spacer(Modifier.height(8.dp))
        LinkRow("Лицензия", "Открытый код — свободно для форка и аудита", "MIT") {
            openUrl(context, "https://github.com/pathetixx/190x4-Ninety-Android/blob/main/LICENSE")
        }
        Spacer(Modifier.height(14.dp))
        UpdateInline(context)
    }
}

@Composable
private fun UpdateInline(context: Context) {
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Updater.Release?>(null) }
    var progress by remember { mutableIntStateOf(-1) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Обновления", style = NinetyTypography.titleMedium, color = Ink.TextHi)
            Text(status ?: "Версия ${BuildConfig.VERSION_NAME}", style = MonoStyle, color = Ink.TextLo)
        }
        PillButton(if (busy) "…" else "проверить", enabled = !busy) {
            status = null; found = null
            Updater.check(onLoading = { busy = true }, onDone = { newer, err ->
                busy = false
                status = when {
                    err != null -> err
                    newer == null -> "Установлена последняя версия"
                    else -> { found = newer; "Доступна ${newer.version}" }
                }
            })
        }
    }
    found?.let { rel ->
        Spacer(Modifier.height(10.dp))
        PillButton(if (progress in 0..100) "загрузка $progress%" else "установить ${rel.version}", enabled = progress < 0) {
            Updater.downloadAndInstall(context, rel, onProgress = { progress = it }, onDone = { err ->
                progress = -1
                if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            })
        }
    }
}

// ── Переиспользуемые контролы ──────────────────────────────
@Composable
private fun ApplyBanner() = SettingsBanner("Изменения применятся при следующем подключении (или переподключитесь).")

@Composable
private fun SettingsBanner(text: String) {
    Box(
        Modifier.fillMaxWidth()
            .background(Ink.Ink2, RoundedCornerShape(12.dp))
            .border(1.dp, Ink.Line2, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) { Text(text, style = NinetyTypography.bodyMedium, color = Ink.TextMid) }
}

@Composable
private fun SetRow(label: String, hint: String?, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().topHairline().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = NinetyTypography.titleMedium, color = Ink.TextHi)
            hint?.let { Spacer(Modifier.height(2.dp)); Text(it, style = MonoStyle, color = Ink.TextLo) }
        }
        control()
    }
}

@Composable
private fun SetSwitch(label: String, hint: String?, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val pack = NinetyState.pack
    SetRow(label, hint) {
        Box(
            Modifier.width(46.dp).height(26.dp)
                .background(if (checked) pack.accent else Ink.Line2, RoundedCornerShape(13.dp))
                .clickable { onToggle(!checked) }
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.size(20.dp).background(Ink.TextHi, CircleShape)) }
    }
}

@Composable
private fun SetSelect(
    label: String, hint: String?, value: String,
    options: List<String>, labels: Map<String, String> = emptyMap(),
    onSelect: (String) -> Unit,
) {
    val pack = NinetyState.pack
    var open by remember { mutableStateOf(false) }
    SetRow(label, hint) {
        Box {
            Row(
                Modifier.background(Ink.Ink3, RoundedCornerShape(8.dp))
                    .border(1.dp, Ink.Line2, RoundedCornerShape(8.dp))
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(labels[value] ?: value, style = MonoStyle, color = Ink.TextHi)
                Spacer(Modifier.width(6.dp))
                Icon(NinetyIcons.ChevronRight, null, tint = pack.accent, modifier = Modifier.size(13.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(labels[opt] ?: opt, style = MonoStyle, color = if (opt == value) pack.accentBright else Ink.TextMid) },
                        onClick = { open = false; onSelect(opt) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetText(label: String, hint: String?, value: String, onValue: (String) -> Unit) =
    EditRow(label, hint, value, KeyboardType.Text) { onValue(it) }

@Composable
private fun SetNumber(label: String, hint: String?, value: Int, min: Int, max: Int, onValue: (Int) -> Unit) =
    EditRow(label, hint, value.toString(), KeyboardType.Number) {
        it.toIntOrNull()?.coerceIn(min, max)?.let(onValue)
    }

@Composable
private fun EditRow(label: String, hint: String?, initial: String, kbType: KeyboardType, onCommit: (String) -> Unit) {
    val pack = NinetyState.pack
    var text by remember(initial) { mutableStateOf(initial) }
    SetRow(label, hint) {
        Box(
            Modifier.width(if (kbType == KeyboardType.Number) 92.dp else 150.dp)
                .background(Ink.Ink3, RoundedCornerShape(8.dp))
                .border(1.dp, Ink.Line2, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onCommit(it) },
                singleLine = true,
                textStyle = MonoStyle.copy(color = Ink.TextHi),
                cursorBrush = SolidColor(pack.accent),
                keyboardOptions = KeyboardOptions(keyboardType = kbType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ThemeCard(pack: ThemePack, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) pack.accentSoft else Ink.Ink2, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) pack.accent else Ink.Line2, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).background(pack.accent, CircleShape).border(if (selected) 2.dp else 0.dp, pack.accentBright, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(THEME_KICKERS[pack.id] ?: "", style = KickerStyle, color = if (selected) pack.accentBright else Ink.TextLo)
            Spacer(Modifier.height(2.dp))
            Text(pack.label, style = NinetyTypography.titleMedium, color = Ink.TextHi)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(1f, 0.6f, 0.35f).forEach { a ->
                Box(Modifier.size(10.dp).background(pack.accent.copy(alpha = a), CircleShape))
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        Modifier.background(Ink.Ink3, RoundedCornerShape(7.dp)).border(1.dp, Ink.Line2, RoundedCornerShape(7.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
    ) { Text(text, style = MonoStyle, color = Ink.TextMid) }
}

@Composable
private fun LinkRow(title: String, hint: String, cta: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NinetyTypography.titleMedium, color = Ink.TextHi)
            Text(hint, style = MonoStyle, color = Ink.TextLo)
        }
        Box(
            Modifier.background(NinetyState.pack.accentSoft, RoundedCornerShape(8.dp)).border(1.dp, NinetyState.pack.accent, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        ) { Text(cta, style = MonoStyle, color = NinetyState.pack.accentBright) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Ink.TextMid, style = NinetyTypography.bodyMedium)
        Text(value, color = Ink.TextHi, style = NinetyTypography.bodyMedium)
    }
}

// ── helpers ────────────────────────────────────────────────
private val REGION_LABELS = mapOf(
    "other" to "Не выбран", "ru" to "Россия (ru)", "cn" to "Китай (cn)",
    "ir" to "Иран (ir)", "tr" to "Турция (tr)", "by" to "Беларусь (by)",
)
private val IPV6_LABELS = mapOf(
    "disable" to "Отключить", "enable" to "Включить (prefer IPv4)",
    "prefer" to "Предпочитать IPv6", "only" to "Только IPv6",
)
private val TUN_STACK_LABELS = mapOf(
    "mixed" to "Mixed (реком.)", "gvisor" to "gVisor", "system" to "System",
)
private val FRAGMENT_LABELS = mapOf("record" to "По TLS-записям", "tcp" to "По TCP-сегментам")
private val THEME_KICKERS = mapOf(
    "kurogane" to "NEON · RED", "synthwave" to "VIOLET WAVE",
    "matrix" to "EMERALD", "mono" to "MONOCHROME",
)

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun copyDiag(context: Context) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ninety-diag", Diag.fullReport(context)))
    Toast.makeText(context, "Диагностика скопирована", Toast.LENGTH_SHORT).show()
}

private fun shareDiag(context: Context) {
    runCatching {
        val file = Diag.writeReportFile(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Сохранить логи").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "Не удалось сохранить логи", Toast.LENGTH_SHORT).show()
    }
}
