package com.abdellatif.fleur.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdellatif.fleur.R
import com.abdellatif.fleur.data.Preferences
import kotlin.math.roundToInt

val Inter =
    FontFamily(
        Font(R.font.inter_regular),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )
val Almondita = FontFamily(Font(R.font.ios_font_almondita))
val Bergenia = FontFamily(Font(R.font.ios_font_bergenia_script))
val Baskerville = FontFamily(Font(R.font.ios_font_librebaskerville_regular))
val BaskervilleItalic = FontFamily(Font(R.font.ios_font_librebaskerville_italic))
val Positive = Color(0xFF51BF83)
val Negative = Color(0xFFED8F8C)
val LocalPreferences = staticCompositionLocalOf { Preferences() }

@Composable
fun FleurTheme(preferences: Preferences, content: @Composable () -> Unit) {
    val dark =
        when (preferences.darkMode) {
            "Dark" -> true
            "Light" -> false
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
    val accent = Color(preferences.accent)
    val colors =
        if (dark)
            darkColorScheme(
                primary = accent,
                background = Color(0xFF202124),
                surface = Color(0xFF292A2D),
                onBackground = Color(0xFFF0ECE9),
                onSurface = Color(0xFFF0ECE9),
                surfaceVariant = Color(0xFF363537),
                outline = Color(0xFF777477),
            )
        else
            lightColorScheme(
                primary = accent,
                background = Color(0xFFFCFCFA),
                surface = Color(0xFFFFFEFC),
                onBackground = Color(0xFF292927),
                onSurface = Color(0xFF292927),
                surfaceVariant = Color(0xFFF1F0EE),
                outline = Color(0xFFB7B5B3),
            )
    CompositionLocalProvider(LocalPreferences provides preferences) {
        MaterialTheme(
            colorScheme = colors,
            typography =
                Typography(
                    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 15.sp),
                    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp),
                    bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                    labelLarge = TextStyle(fontFamily = Inter, fontSize = 13.sp),
                    labelMedium = TextStyle(fontFamily = Inter, fontSize = 11.sp),
                    titleLarge = TextStyle(fontFamily = Inter, fontSize = 22.sp),
                ),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.onBackground,
                LocalTextSelectionColors provides
                    TextSelectionColors(accent, accent.copy(alpha = .25f)),
            ) {
                content()
            }
        }
    }
}

@Composable
fun FleurDesign(preferences: Preferences, content: @Composable () -> Unit) {
    FleurTheme(preferences) {
        val dark = MaterialTheme.colorScheme.background.red < .5f
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Image(
                painterResource(if (dark) R.drawable.paper_dark else R.drawable.paper_light),
                null,
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = if (dark) .48f else .63f,
            )
            content()
        }
    }
}

@Composable
fun ScriptHeading(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = LocalPreferences.current.fontSize,
) {
    val family =
        when (LocalPreferences.current.font) {
            "Bergenia" -> Bergenia
            "Baskerville" -> BaskervilleItalic
            "Gowun Batang" -> FontFamily(Font(R.font.ios_font_gowunbatang_regular))
            "Kaisei Tokumin" -> FontFamily(Font(R.font.ios_font_kaiseitokumin_regular))
            "Long Cang" -> FontFamily(Font(R.font.ios_font_longcang_regular))
            "Miss Katherine" -> FontFamily(Font(R.font.ios_font_misskatherinecyredit1_script))
            "Nanum Pen" -> FontFamily(Font(R.font.ios_font_nanumpenscript_regular))
            else -> Almondita
        }
    Text(
        text,
        modifier.padding(top = 4.dp, bottom = 2.dp),
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = (size * 1.15f).sp,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outline.copy(alpha = .48f)
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset.Zero,
            androidx.compose.ui.geometry.Offset(size.width, 0f),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
        )
    }
}

private val iconAliases =
    mapOf(
        "dollar" to "fleur_dollar",
        "restaurant" to "local_dining",
        "vacation" to "beach_access",
        "fitness" to "fitness_center",
        "savings" to "piggy",
        "phone" to "phone_iphone",
        "taxi" to "local_taxi",
        "heart" to "volunteer_activism",
        "other" to "more_horiz",
        "bank" to "account_balance",
    )

@Composable
fun FleurIcon(
    key: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val resource =
        when (key) {
            "tab_home" -> R.drawable.ios_asset_tab_home_95041abf
            "tab_insights" -> R.drawable.ios_asset_tab_insights_e80efccd
            "tab_transactions" -> R.drawable.ios_asset_tab_transactions_c2843339
            "tab_accounts" -> R.drawable.ios_asset_tab_accounts_3184a62b
            else -> CategoryIcons.find { it.key == (iconAliases[key] ?: key) }?.resource
        }
    if (resource != null) Icon(painterResource(resource), null, modifier.size(23.dp), tint)
    else
        Canvas(modifier.size(23.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.65.dp.toPx()
            val p = Path()
            when (key) {
                "close" -> {
                    drawLine(
                        tint,
                        androidx.compose.ui.geometry.Offset(w * .25f, h * .25f),
                        androidx.compose.ui.geometry.Offset(w * .75f, h * .75f),
                        stroke,
                    )
                    drawLine(
                        tint,
                        androidx.compose.ui.geometry.Offset(w * .75f, h * .25f),
                        androidx.compose.ui.geometry.Offset(w * .25f, h * .75f),
                        stroke,
                    )
                }
                "plus" -> {
                    drawLine(
                        tint,
                        androidx.compose.ui.geometry.Offset(w * .18f, h * .5f),
                        androidx.compose.ui.geometry.Offset(w * .82f, h * .5f),
                        stroke,
                        StrokeCap.Round,
                    )
                    drawLine(
                        tint,
                        androidx.compose.ui.geometry.Offset(w * .5f, h * .18f),
                        androidx.compose.ui.geometry.Offset(w * .5f, h * .82f),
                        stroke,
                        StrokeCap.Round,
                    )
                }
                "back",
                "next",
                "down" -> {
                    if (key == "down") {
                        p.moveTo(w * .25f, h * .4f)
                        p.lineTo(w * .5f, h * .65f)
                        p.lineTo(w * .75f, h * .4f)
                    } else {
                        val a = if (key == "back") .65f else .35f
                        val b = 1 - a
                        p.moveTo(w * a, h * .2f)
                        p.lineTo(w * b, h * .5f)
                        p.lineTo(w * a, h * .8f)
                    }
                    drawPath(p, tint, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                "search" -> {
                    drawCircle(
                        tint,
                        w * .26f,
                        androidx.compose.ui.geometry.Offset(w * .43f, h * .43f),
                        style = Stroke(stroke),
                    )
                    drawLine(
                        tint,
                        androidx.compose.ui.geometry.Offset(w * .63f, h * .63f),
                        androidx.compose.ui.geometry.Offset(w * .88f, h * .88f),
                        stroke,
                    )
                }
                "check" -> {
                    p.moveTo(w * .2f, h * .52f)
                    p.lineTo(w * .42f, h * .74f)
                    p.lineTo(w * .82f, h * .25f)
                    drawPath(p, tint, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                "paid",
                "unpaid" -> {
                    drawCircle(tint, w * .40f, style = Stroke(stroke * .6f))
                    if (key == "paid") {
                        p.moveTo(w * .3f, h * .51f)
                        p.lineTo(w * .45f, h * .66f)
                        p.lineTo(w * .72f, h * .33f)
                        drawPath(p, tint, style = Stroke(stroke * .6f, cap = StrokeCap.Round))
                    }
                }
                "triangle" -> {
                    p.moveTo(w * .5f, h * .15f)
                    p.lineTo(w * .88f, h * .83f)
                    p.lineTo(w * .12f, h * .83f)
                    p.close()
                    drawPath(
                        p,
                        tint,
                        style =
                            Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                    )
                }
                "filter" -> {
                    for (i in 0..2) {
                        val x = .16f + i * .12f
                        drawLine(
                            tint,
                            androidx.compose.ui.geometry.Offset(w * x, h * (.25f + i * .24f)),
                            androidx.compose.ui.geometry.Offset(w * (1 - x), h * (.25f + i * .24f)),
                            stroke,
                        )
                    }
                }
                else -> drawCircle(tint, w * .38f, style = Stroke(stroke))
            }
        }
}

@Composable
fun SmallIconButton(
    key: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, label = "press")
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        FleurIcon(
            key,
            Modifier.size(if (key == "paid" || key == "unpaid") 14.dp else 23.dp),
            tint = tint,
        )
    }
}

@Composable
fun WatercolorSpot(color: Color, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val texture =
        painterResource(if (dark) R.drawable.watercolor_spot_dark else R.drawable.watercolor_spot)
    Box(modifier) {
        // Fleur layers the same translucent paint twice to retain the pigment texture.
        repeat(2) {
            Image(
                texture,
                null,
                Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(color),
            )
        }
    }
}

@Composable
fun CategoryBadge(
    icon: String,
    color: Long,
    modifier: Modifier = Modifier,
    size: Dp = 31.dp,
    filled: Boolean = true,
) {
    Box(
        modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) WatercolorSpot(Color(color), Modifier.matchParentSize())
        FleurIcon(icon, Modifier.size(size * .57f), if (filled) Color.White else Color(color))
    }
}

@Composable
fun StripedBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Positive,
    remainder: Color = Negative,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Canvas(
        modifier.fillMaxWidth().height(26.dp).semantics {
            contentDescription = "${(fraction*100).roundToInt()} percent"
        },
    ) {
        val gap = 4.dp.toPx()
        val width = 1.65.dp.toPx()
        val count = (size.width / gap).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val x = i * gap + width
            drawLine(
                if (i.toFloat() / count < fraction) color else remainder,
                androidx.compose.ui.geometry.Offset(x, 1.dp.toPx()),
                androidx.compose.ui.geometry.Offset(x, size.height - 1.dp.toPx()),
                width,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
fun SheetTopBar(
    title: String,
    onClose: () -> Unit,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        SmallIconButton("close", "Close", onClose)
        Text(
            title.uppercase(),
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Medium,
        )
        if (action != null)
            TextButton(onClick = onAction, modifier = Modifier.widthIn(min = 56.dp)) {
                Text(action, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }
        else Spacer(Modifier.width(48.dp))
    }
}

@Composable
fun FleurInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    centered: Boolean = false,
    showLabel: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    val textColor = MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        if (showLabel)
            Text(
                label,
                Modifier.padding(start = 4.dp, bottom = 6.dp),
                color = textColor.copy(alpha = .65f),
                fontSize = 12.sp,
            )
        BasicTextField(
            value,
            onValueChange,
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(textColor.copy(alpha = .055f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp)
                    .semantics { contentDescription = label },
            singleLine = true,
            keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            textStyle =
                MaterialTheme.typography.bodyMedium.copy(
                    color = textColor,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { input ->
                Row(
                    Modifier.heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.weight(1f).padding(vertical = 10.dp),
                        contentAlignment =
                            if (centered) Alignment.Center else Alignment.CenterStart,
                    ) {
                        if (value.isEmpty() && !showLabel)
                            Text(
                                label,
                                fontSize = 14.sp,
                                color = textColor.copy(alpha = .45f),
                            )
                        input()
                    }
                    trailing?.invoke()
                }
            },
        )
    }
}

@Composable
fun FormRow(
    label: String,
    value: String = "",
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), fontSize = 14.sp)
            if (trailing != null) trailing()
            else {
                Text(
                    value,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 190.dp),
                )
                if (onClick != null)
                    FleurIcon(
                        "next",
                        Modifier.padding(start = 4.dp).size(12.dp),
                        MaterialTheme.colorScheme.outline,
                    )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = .16f),
            thickness = .5.dp,
        )
    }
}

@Composable
fun EmptyMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
            fontSize = 13.sp,
            lineHeight = 21.sp,
        )
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun ChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 40.dp),
        border =
            androidx.compose.foundation.BorderStroke(
                if (selected) 1.dp else 0.dp,
                if (selected) color.copy(alpha = .65f) else Color.Transparent,
            ),
        colors =
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        if (icon != null) {
            FleurIcon(icon, Modifier.size(13.dp), color)
            Spacer(Modifier.width(5.dp))
        }
        Text(text.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp)
    }
}
