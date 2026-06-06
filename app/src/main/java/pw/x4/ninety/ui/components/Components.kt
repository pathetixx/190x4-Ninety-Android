package pw.x4.ninety.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import pw.x4.ninety.ui.theme.Ink
import pw.x4.ninety.ui.theme.KickerStyle

/** Uppercase-кикер из tokens.css (.kicker). */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text = AnnotatedString(text.uppercase()),
        style = KickerStyle,
        color = if (accent) pw.x4.ninety.ui.theme.NinetyState.pack.accent else Ink.TextLo,
        modifier = modifier,
    )
}

/** Карточка-поверхность (ink-2 + тонкая обводка line-2), радиус r-lg. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Ink.Ink2, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.Line2, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        content()
    }
}
