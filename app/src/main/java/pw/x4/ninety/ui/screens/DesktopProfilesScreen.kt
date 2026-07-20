package pw.x4.ninety.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pw.x4.ninety.data.Importer
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.components.PillButton
import pw.x4.ninety.ui.components.desktopCard
import pw.x4.ninety.ui.icons.NinetyIcons
import pw.x4.ninety.ui.layout.NinetyLayoutMetrics
import pw.x4.ninety.ui.layout.NinetyPage
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.NinetyRadius
import pw.x4.ninety.ui.theme.NinetyTypography
import pw.x4.ninety.vpn.NinetyVpnService
import pw.x4.ninety.vpn.TunnelModes
import pw.x4.ninety.vpn.VpnController

/** Desktop Profiles screen: header/actions followed by a single `.prof-list` column. */
@Composable
fun DesktopProfilesScreen(metrics: NinetyLayoutMetrics) {
    val context = LocalContext.current
    val profiles = Store.profiles
    var busy by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    NinetyPage(metrics) {
        Column(Modifier.fillMaxSize().padding(top = if (metrics.isCompact) 16.dp else 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("SUBSCRIPTIONS", style = KickerStyle, color = Ink.TextFaint)
                    Spacer(Modifier.height(5.dp))
                    Text("Профили", style = NinetyTypography.headlineMedium, color = Ink.TextHi)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Подписки и одиночные конфиги. Активный профиль используется при подключении.",
                        style = NinetyTypography.bodyMedium,
                        color = Ink.TextMid,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profiles.any { it.isSub && it.url.isNotBlank() }) {
                    PillButton(if (busy) "Обновляю…" else "Обновить", enabled = !busy) {
                        Importer.refreshAll(
                            onLoading = { busy = true },
                            onDone = { count, error ->
                                busy = false
                                desktopProfileToast(context, error ?: "Обновлено нод: $count")
                            },
                        )
                    }
                }
                PillButton("Добавить") { showAdd = true }
            }
            Spacer(Modifier.height(16.dp))

            if (profiles.isEmpty()) {
                DesktopEmptyProfiles(Modifier.fillMaxSize()) { showAdd = true }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        DesktopProfileEntry(
                            profile = profile,
                            active = profile.id == Store.activeProfileId,
                            compact = metrics.isCompact,
                            onSelect = { selectDesktopProfile(context, profile.id) },
                            onDelete = { deleteDesktopProfile(context, profile.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) DesktopAddProfileDialog(context, onDismiss = { showAdd = false })
}

@Composable
private fun DesktopEmptyProfiles(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(modifier.padding(top = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(NinetyRadius.lg)).desktopCard(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(NinetyIcons.Profiles, null, tint = Ink.TextFaint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("SUBSCRIPTIONS · EMPTY", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(8.dp))
        Text("Нет профилей", style = NinetyTypography.titleLarge, color = Ink.TextHi)
        Spacer(Modifier.height(8.dp))
        Text(
            "Добавьте подписку или одиночный VLESS, VMess, Trojan, Shadowsocks, Hysteria2 или TUIC.",
            style = NinetyTypography.bodyMedium,
            color = Ink.TextMid,
        )
        Spacer(Modifier.height(18.dp))
        PillButton("Добавить профиль", onClick = onAdd)
    }
}

private fun selectDesktopProfile(context: Context, profileId: String) {
    Store.setActiveProfile(profileId)
    if (VpnController.isActive && TunnelModes.current().requiresProxySelection) NinetyVpnService.reload(context)
}

private fun deleteDesktopProfile(context: Context, profileId: String) {
    Store.removeProfile(profileId)
    desktopProfileToast(context, "Профиль удалён")
}
