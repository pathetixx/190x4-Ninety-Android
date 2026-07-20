package pw.x4.ninety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    DesktopSettingSection("dns", "DNS", "Remote, direct и Fake-DNS", SettingsIcons.Dns),
    DesktopSettingSection("inbound", "TUN", "MTU, stack и strict route", SettingsIcons.Inbound),
    DesktopSettingSection("tls", "TLS", "Фрагментация и padding", SettingsIcons.Tls),
    DesktopSettingSection("mux", "Multiplex", "Параллельные transport streams", SettingsIcons.Mux),
    DesktopSettingSection("warp", "WARP", "Direct, Chain и AmneziaWG noise", NinetyIcons.Shield),
    DesktopSettingSection("logs", "Логи", "Runtime, stderr, crash и logcat", NinetyIcons.Logs),
    DesktopSettingSection("about", "О программе", "Версия, обновления и репозиторий", SettingsIcons.Info),
)

/**
 * Desktop settings navigation is deliberately single-pane at every width.
 * The Windows source shows a root menu and a separate section page with Back;
 * a permanent Android master-detail split made the product look unrelated.
 */
@Composable
fun DesktopSettingsScreen(metrics: NinetyLayoutMetrics) {
    val context = LocalContext.current
    Options.load(context)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    NinetyPage(metrics) {
        if (selected == null) {
            DesktopSettingsMenu(
                compact = metrics.isCompact,
                onOpen = { selected = it },
            )
        } else {
            DesktopSettingsSectionPage(
                sectionKey = selected!!,
                compact = metrics.isCompact,
                onBack = { selected = null },
            )
        }
    }
}

@Composable
private fun DesktopSettingsMenu(compact: Boolean, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = if (compact) 16.dp else 24.dp, bottom = 28.dp),
    ) {
        DesktopRootSettingsHeader()
        Spacer(Modifier.height(18.dp))
        DesktopSettingSections.forEachIndexed { index, section ->
            DesktopSettingsMenuItem(section) { onOpen(section.key) }
            if (index != DesktopSettingSections.lastIndex) Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DesktopRootSettingsHeader() {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Настройки",
            style = NinetyTypography.headlineSmall,
            color = Ink.TextHi,
            modifier = Modifier.weight(1f),
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
}

@Composable
private fun DesktopSettingsMenuItem(section: DesktopSettingSection, onClick: () -> Unit) {
    val shape = RoundedCornerShape(NinetyRadius.sm)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .desktopCard(shape = shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(NinetyRadius.xs))
                .background(NinetyState.pack.material.cardBottom),
            contentAlignment = Alignment.Center,
        ) {
            Icon(section.icon, null, tint = Ink.TextMid, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(section.title, style = NinetyTypography.titleSmall, color = Ink.TextHi)
            Spacer(Modifier.height(3.dp))
            Text(
                section.hint,
                style = NinetyTypography.bodySmall,
                color = Ink.TextLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(NinetyIcons.ChevronRight, null, tint = Ink.TextFaint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DesktopSettingsSectionPage(
    sectionKey: String,
    compact: Boolean,
    onBack: () -> Unit,
) {
    val section = DesktopSettingSections.first { it.key == sectionKey }
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = if (compact) 16.dp else 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(NinetyRadius.xs))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SettingsIcons.ArrowLeft, "Назад", tint = Ink.TextMid, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(section.title, style = NinetyTypography.headlineSmall, color = Ink.TextHi)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line1))
        DesktopSettingsContent(
            sectionKey = sectionKey,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp, bottom = 30.dp),
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
