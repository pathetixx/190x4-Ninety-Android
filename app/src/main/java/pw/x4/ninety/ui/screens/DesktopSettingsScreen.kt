package pw.x4.ninety.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Options
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.icons.SettingsIcons
import pw.x4.ninety.ui.layout.NinetyLayoutMetrics
import pw.x4.ninety.ui.layout.NinetyPage
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

private data class DesktopSettingSection(
    val key: String,
    val title: String,
    val hint: String,
    val icon: ImageVector,
)

private val DesktopSettingSections = listOf(
    DesktopSettingSection("general", "Общие", "Запуск, ping и журнал ядра", SettingsIcons.General),
    DesktopSettingSection("appearance", "Оформление", "Материалы и 16 desktop-тем", SettingsIcons.Theme),
    DesktopSettingSection("routing", "Маршрутизация", "Правила, регион и LAN", SettingsIcons.Routing),
    DesktopSettingSection("warp", "WARP", "Direct, Chain и AmneziaWG noise", NinetyIcons.Shield),
    DesktopSettingSection("dns", "DNS", "Remote, direct и Fake-DNS", SettingsIcons.Dns),
    DesktopSettingSection("inbound", "TUN", "MTU, stack и strict route", SettingsIcons.Inbound),
    DesktopSettingSection("tls", "TLS", "Фрагментация и padding", SettingsIcons.Tls),
    DesktopSettingSection("mux", "Multiplex", "Параллельные transport streams", SettingsIcons.Mux),
    DesktopSettingSection("logs", "Логи", "Runtime, stderr, crash и logcat", NinetyIcons.Logs),
    DesktopSettingSection("about", "О программе", "Версия, обновления и репозиторий", SettingsIcons.Info),
)

@Composable
fun DesktopSettingsScreen(metrics: NinetyLayoutMetrics) {
    val context = LocalContext.current
    Options.load(context)
    var selected by rememberSaveable { mutableStateOf(if (metrics.isExpanded) "general" else null) }

    NinetyPage(metrics) {
        if (metrics.isExpanded) {
            Row(
                Modifier.fillMaxSize().padding(top = 22.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                DesktopSettingsNavigation(
                    selected = selected ?: "general",
                    onSelect = { selected = it },
                    modifier = Modifier.width(286.dp).fillMaxHeight(),
                )
                DesktopSettingsPane(
                    sectionKey = selected ?: "general",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else if (selected == null) {
            DesktopSettingsMenu(onOpen = { selected = it })
        } else {
            DesktopSettingsMobilePane(sectionKey = selected!!, onBack = { selected = null })
        }
    }
}

@Composable
private fun DesktopSettingsMenu(onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 28.dp),
    ) {
        DesktopSettingsTitle("SYSTEM · SETTINGS", "Настройки", "Полный master-detail desktop Ninety, адаптированный под ширину Android.")
        Spacer(Modifier.height(18.dp))
        DesktopSettingSections.forEach { section ->
            DesktopSettingsNavCard(section, selected = false) { onOpen(section.key) }
            Spacer(Modifier.height(9.dp))
        }
    }
}

@Composable
private fun DesktopSettingsNavigation(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(NinetyRadius.lg))
            .desktopCard(shape = RoundedCornerShape(NinetyRadius.lg))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("NINETY · SYSTEM", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(6.dp))
        Text("Параметры", style = NinetyTypography.titleLarge, color = Ink.TextHi)
        Spacer(Modifier.height(14.dp))
        DesktopSettingSections.forEach { section ->
            DesktopSettingsNavCard(section, selected = section.key == selected) { onSelect(section.key) }
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun DesktopSettingsNavCard(
    section: DesktopSettingSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pack = NinetyState.pack
    val shape = RoundedCornerShape(NinetyRadius.sm)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) pack.material.rowActive else pack.material.rowMiddle, shape)
            .border(1.dp, if (selected) pack.material.border else Ink.Line1, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(NinetyRadius.sm))
                .background(if (selected) pack.accentSoft else pack.material.cardBottom),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                section.icon,
                null,
                tint = if (selected) pack.material.secondary else Ink.TextMid,
                modifier = Modifier.size(19.dp),
            )
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
private fun DesktopSettingsPane(sectionKey: String, modifier: Modifier = Modifier) {
    val section = DesktopSettingSections.first { it.key == sectionKey }
    Column(
        modifier
            .clip(RoundedCornerShape(NinetyRadius.lg))
            .desktopCard(shape = RoundedCornerShape(NinetyRadius.lg))
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        DesktopSettingsTitle("SETTINGS · ${section.key.uppercase()}", section.title, section.hint)
        Spacer(Modifier.height(15.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
        DesktopSettingsContent(
            sectionKey,
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun DesktopSettingsMobilePane(sectionKey: String, onBack: () -> Unit) {
    val section = DesktopSettingSections.first { it.key == sectionKey }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).desktopCard().clickable { onBack() },
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
        DesktopSettingsContent(
            sectionKey,
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 30.dp),
        )
    }
}

@Composable
private fun DesktopSettingsContent(sectionKey: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        when (sectionKey) {
            "general" -> DesktopGeneralSettings()
            "appearance" -> DesktopAppearanceSettings()
            "routing" -> DesktopRoutingSettings()
            "warp" -> DesktopWarpSettings()
            "dns" -> DesktopDnsSettings()
            "inbound" -> DesktopInboundSettings()
            "tls" -> DesktopTlsSettings()
            "mux" -> DesktopMuxSettings()
            "logs" -> DesktopLogsSettings()
            "about" -> DesktopAboutSettings()
        }
    }
}
