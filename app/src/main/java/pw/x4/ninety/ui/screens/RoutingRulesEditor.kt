package pw.x4.ninety.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import pw.x4.ninety.core.model.DomainMatch
import pw.x4.ninety.core.model.RoutingRule
import pw.x4.ninety.core.model.RoutingRuleAction
import pw.x4.ninety.core.model.RoutingRuleSanitizer
import pw.x4.ninety.core.model.RoutingRuleType
import pw.x4.ninety.ui.components.PillButton
import pw.x4.ninety.ui.components.SurfaceCard
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle
import pw.x4.ninety.ui.theme.MonoStyle
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.NinetyTypography

@Composable
internal fun RoutingRulesEditor(
    rules: List<RoutingRule>,
    onRulesChange: (List<RoutingRule>) -> Unit,
) {
    val packageRoutingSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var editing by remember { mutableStateOf<RoutingRule?>(null) }
    var showNew by remember { mutableStateOf(false) }

    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Пользовательские правила", style = NinetyTypography.titleLarge, color = Ink.TextHi)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Первое совпадение побеждает · выше региона, LAN и блокировки рекламы",
                    style = MonoStyle,
                    color = Ink.TextLo,
                )
            }
            Spacer(Modifier.width(12.dp))
            PillButton("Добавить") { showNew = true }
        }

        if (!packageRoutingSupported) {
            Spacer(Modifier.height(12.dp))
            WarningStrip("Правила по приложениям требуют Android 10+. Домены и IP работают на Android 8+.")
        }

        Spacer(Modifier.height(14.dp))
        if (rules.isEmpty()) {
            EmptyRules()
        } else {
            rules.forEachIndexed { index, rule ->
                RuleCard(
                    rule = rule,
                    index = index,
                    total = rules.size,
                    packageRoutingSupported = packageRoutingSupported,
                    onToggle = { enabled ->
                        onRulesChange(rules.toMutableList().apply { set(index, rule.copy(enabled = enabled)) })
                    },
                    onMoveUp = {
                        if (index > 0) {
                            onRulesChange(rules.toMutableList().apply {
                                val previous = removeAt(index - 1)
                                add(index, previous)
                            })
                        }
                    },
                    onMoveDown = {
                        if (index < rules.lastIndex) {
                            onRulesChange(rules.toMutableList().apply {
                                val current = removeAt(index)
                                add(index + 1, current)
                            })
                        }
                    },
                    onEdit = { editing = rule },
                    onDelete = { onRulesChange(rules.filterNot { it.id == rule.id }) },
                )
                if (index != rules.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }

    val dialogRule = when {
        showNew -> RoutingRule(id = UUID.randomUUID().toString())
        editing != null -> editing
        else -> null
    }
    dialogRule?.let { rule ->
        RuleDialog(
            initial = rule,
            packageRoutingSupported = packageRoutingSupported,
            onDismiss = {
                showNew = false
                editing = null
            },
            onSave = { saved ->
                val existingIndex = rules.indexOfFirst { it.id == saved.id }
                val next = if (existingIndex >= 0) {
                    rules.toMutableList().apply { set(existingIndex, saved) }
                } else {
                    rules + saved
                }
                onRulesChange(next)
                showNew = false
                editing = null
            },
        )
    }
}

@Composable
private fun RuleCard(
    rule: RoutingRule,
    index: Int,
    total: Int,
    packageRoutingSupported: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val unsupported = rule.type == RoutingRuleType.PROCESS_NAME ||
        (rule.type == RoutingRuleType.ANDROID_PACKAGE && !packageRoutingSupported)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Ink3)
            .border(1.dp, if (rule.enabled) Ink.Line2 else Ink.Line1, shape)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(
                        when {
                            unsupported -> Ink.Warn
                            rule.enabled -> NinetyState.pack.accent
                            else -> Ink.TextFaint
                        },
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { onEdit() }) {
                Text(
                    "${index + 1}. ${ruleTitle(rule)}",
                    style = NinetyTypography.titleMedium,
                    color = if (rule.enabled) Ink.TextHi else Ink.TextLo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    rule.values.joinToString(" · ").ifBlank { "Нет значений" },
                    style = MonoStyle,
                    color = Ink.TextLo,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            MiniToggle(rule.enabled, onToggle)
        }

        if (unsupported) {
            Spacer(Modifier.height(9.dp))
            Text(
                if (rule.type == RoutingRuleType.PROCESS_NAME) {
                    "PROCESS_NAME поддерживается только desktop-клиентом"
                } else {
                    "На этой версии Android package rule не будет добавлено в конфиг"
                },
                style = KickerStyle,
                color = Ink.Warn,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CompactAction("↑", enabled = index > 0, onClick = onMoveUp)
            CompactAction("↓", enabled = index < total - 1, onClick = onMoveDown)
            CompactAction("ИЗМЕНИТЬ", onClick = onEdit)
            Spacer(Modifier.weight(1f))
            CompactAction("УДАЛИТЬ", danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun EmptyRules() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Ink.Ink3)
            .border(1.dp, Ink.Line1, RoundedCornerShape(11.dp))
            .padding(16.dp),
    ) {
        Text("ROUTING · EMPTY", style = KickerStyle, color = Ink.TextFaint)
        Spacer(Modifier.height(6.dp))
        Text("Базовая маршрутизация без исключений", style = NinetyTypography.titleMedium, color = Ink.TextHi)
        Spacer(Modifier.height(4.dp))
        Text(
            "Добавьте домен, IP/CIDR или Android package ID и выберите Proxy, Direct либо Block.",
            style = MonoStyle,
            color = Ink.TextLo,
        )
    }
}

@Composable
private fun RuleDialog(
    initial: RoutingRule,
    packageRoutingSupported: Boolean,
    onDismiss: () -> Unit,
    onSave: (RoutingRule) -> Unit,
) {
    var type by remember(initial.id) { mutableStateOf(initial.type) }
    var match by remember(initial.id) { mutableStateOf(initial.match) }
    var action by remember(initial.id) { mutableStateOf(initial.action) }
    var valuesText by remember(initial.id) { mutableStateOf(initial.values.joinToString("\n")) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    var warning by remember(initial.id) { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .width(560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Ink.Ink1)
                .border(1.dp, Ink.Line2, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text("CUSTOM · ROUTING", style = KickerStyle, color = NinetyState.pack.accentBright)
            Spacer(Modifier.height(6.dp))
            Text(
                if (initial.values.isEmpty()) "Новое правило" else "Изменить правило",
                style = NinetyTypography.titleLarge,
                color = Ink.TextHi,
            )
            Spacer(Modifier.height(16.dp))

            SelectorRow(
                label = "Тип",
                value = type,
                options = buildList {
                    add(RoutingRuleType.DOMAIN)
                    add(RoutingRuleType.IP)
                    if (packageRoutingSupported || initial.type == RoutingRuleType.ANDROID_PACKAGE) {
                        add(RoutingRuleType.ANDROID_PACKAGE)
                    }
                    if (initial.type == RoutingRuleType.PROCESS_NAME) add(RoutingRuleType.PROCESS_NAME)
                },
                display = ::typeLabel,
                onValue = { type = it; error = null; warning = null },
            )
            if (type == RoutingRuleType.DOMAIN) {
                Spacer(Modifier.height(10.dp))
                SelectorRow(
                    label = "Совпадение",
                    value = match,
                    options = DomainMatch.entries,
                    display = ::matchLabel,
                    onValue = { match = it },
                )
            }
            Spacer(Modifier.height(10.dp))
            SelectorRow(
                label = "Действие",
                value = action,
                options = RoutingRuleAction.entries,
                display = ::actionLabel,
                onValue = { action = it },
            )

            Spacer(Modifier.height(14.dp))
            Text("ЗНАЧЕНИЯ · ПО ОДНОМУ НА СТРОКУ", style = KickerStyle, color = Ink.TextFaint)
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 126.dp, max = 240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink.Ink2)
                    .border(1.dp, if (error == null) Ink.Line2 else Ink.Err, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                if (valuesText.isBlank()) {
                    Text(valueHint(type), style = MonoStyle, color = Ink.TextFaint)
                }
                BasicTextField(
                    value = valuesText,
                    onValueChange = { valuesText = it; error = null; warning = null },
                    textStyle = MonoStyle.copy(color = Ink.TextHi),
                    cursorBrush = SolidColor(NinetyState.pack.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (type == RoutingRuleType.ANDROID_PACKAGE && !packageRoutingSupported) {
                Spacer(Modifier.height(9.dp))
                WarningStrip("Package routing требует Android 10+. Сохранение отключено.")
            }
            if (type == RoutingRuleType.PROCESS_NAME) {
                Spacer(Modifier.height(9.dp))
                WarningStrip("Process rules предназначены для desktop и не попадут в Android config.")
            }
            error?.let {
                Spacer(Modifier.height(9.dp))
                Text(it, style = MonoStyle, color = Ink.Err)
            }
            warning?.let {
                Spacer(Modifier.height(9.dp))
                Text(it, style = MonoStyle, color = Ink.Warn)
            }

            Spacer(Modifier.height(17.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Отмена", modifier = Modifier.weight(1f), onClick = onDismiss)
                PillButton(
                    "Сохранить",
                    modifier = Modifier.weight(1f),
                    enabled = type != RoutingRuleType.PROCESS_NAME &&
                        (type != RoutingRuleType.ANDROID_PACKAGE || packageRoutingSupported),
                ) {
                    val raw = initial.copy(
                        enabled = true,
                        type = type,
                        match = match,
                        action = action,
                        values = valuesText.lines(),
                    )
                    val result = RoutingRuleSanitizer.sanitize(raw)
                    if (result.rule.values.isEmpty()) {
                        error = "Нет ни одного валидного значения"
                    } else {
                        warning = when {
                            result.dropped > 0 -> "Отброшено невалидных значений: ${result.dropped}"
                            result.duplicates > 0 -> "Удалено дубликатов: ${result.duplicates}"
                            else -> null
                        }
                        onSave(result.rule)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SelectorRow(
    label: String,
    value: T,
    options: List<T>,
    display: (T) -> String,
    onValue: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NinetyTypography.titleMedium, color = Ink.TextHi, modifier = Modifier.weight(1f))
        Box {
            Text(
                display(value),
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
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(display(option), style = MonoStyle) },
                        onClick = {
                            expanded = false
                            onValue(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniToggle(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        Modifier
            .width(42.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) NinetyState.pack.accent else Ink.Line2)
            .clickable { onToggle(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).background(if (checked) Ink.Ink0 else Ink.TextHi, CircleShape))
    }
}

@Composable
private fun CompactAction(
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> Ink.TextFaint
        danger -> Ink.Err
        else -> Ink.TextMid
    }
    Text(
        text,
        style = KickerStyle,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Ink.Ink2)
            .border(1.dp, Ink.Line1, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 7.dp),
    )
}

@Composable
private fun WarningStrip(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Ink.Warn.copy(alpha = 0.08f))
            .border(1.dp, Ink.Warn.copy(alpha = 0.32f), RoundedCornerShape(9.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(Ink.Warn, CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(text, style = MonoStyle, color = Ink.Warn, modifier = Modifier.weight(1f))
    }
}

private fun ruleTitle(rule: RoutingRule): String =
    "${typeLabel(rule.type)} · ${if (rule.type == RoutingRuleType.DOMAIN) matchLabel(rule.match) + " · " else ""}${actionLabel(rule.action)}"

private fun typeLabel(type: RoutingRuleType): String = when (type) {
    RoutingRuleType.DOMAIN -> "Домен"
    RoutingRuleType.IP -> "IP / CIDR"
    RoutingRuleType.ANDROID_PACKAGE -> "Приложение"
    RoutingRuleType.PROCESS_NAME -> "Процесс · desktop"
}

private fun matchLabel(match: DomainMatch): String = when (match) {
    DomainMatch.SUFFIX -> "Суффикс"
    DomainMatch.EXACT -> "Точное"
    DomainMatch.KEYWORD -> "Ключевое слово"
}

private fun actionLabel(action: RoutingRuleAction): String = when (action) {
    RoutingRuleAction.PROXY -> "Через VPN"
    RoutingRuleAction.DIRECT -> "Напрямую"
    RoutingRuleAction.BLOCK -> "Блокировать"
}

private fun valueHint(type: RoutingRuleType): String = when (type) {
    RoutingRuleType.DOMAIN -> "youtube.com\napi.example.com"
    RoutingRuleType.IP -> "1.1.1.1\n10.0.0.0/8\n2001:db8::/32"
    RoutingRuleType.ANDROID_PACKAGE -> "org.telegram.messenger\ncom.discord"
    RoutingRuleType.PROCESS_NAME -> "Telegram.exe"
}
