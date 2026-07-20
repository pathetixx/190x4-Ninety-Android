package pw.x4.ninety.ui.screens

import androidx.compose.runtime.Composable
import pw.x4.ninety.ui.layout.NinetyLayoutMetrics

@Composable
fun ProfilesScreen(metrics: NinetyLayoutMetrics) = DesktopProfilesScreen(metrics)

@Composable
fun NodesScreen(metrics: NinetyLayoutMetrics) = DesktopNodesScreen(metrics)

@Composable
fun SettingsScreen(metrics: NinetyLayoutMetrics) = DesktopSettingsScreen(metrics)
