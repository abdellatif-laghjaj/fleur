package com.abdellatif.fleur.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

private val modalBackground: Color
    @Composable
    get() =
        if (MaterialTheme.colorScheme.background.red < .5f) Color(0xFF191919) else Color(0xFFF8F8F6)

val FleurDestructive: Color
    @Composable
    get() =
        if (MaterialTheme.colorScheme.background.red < .5f) Color(0xFFFF5964) else Color(0xFFD83242)

@Composable
private fun FleurModal(onClose: () -> Unit, bottom: Boolean, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(.70f) }
        Box(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .pointerInput(onClose) { detectTapGestures { onClose() } },
            contentAlignment = if (bottom) Alignment.BottomCenter else Alignment.Center,
        ) {
            Box(
                Modifier.padding(if (bottom) 8.dp else 24.dp)
                    .widthIn(max = if (bottom) 600.dp else 320.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures {} },
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ModalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
) {
    Box(
        modifier
            .heightIn(min = 54.dp)
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun FleurAlert(
    title: String,
    onClose: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    message: String? = null,
    destructive: Boolean = false,
    cancelLabel: String? = "Cancel",
    content: (@Composable () -> Unit)? = null,
) {
    val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = .13f)
    FleurModal(onClose, bottom = false) {
        Surface(
            Modifier.fillMaxWidth().testTag("fleur-alert"),
            shape = RoundedCornerShape(17.dp),
            color = modalBackground,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.heightIn(max = 560.dp)) {
                Column(
                    Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (message != null)
                        Text(
                            message,
                            Modifier.padding(top = 9.dp),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                        )
                    if (content != null) {
                        Spacer(Modifier.height(16.dp))
                        content()
                    }
                }
                HorizontalDivider(thickness = .5.dp, color = divider)
                Row(Modifier.height(IntrinsicSize.Min)) {
                    if (cancelLabel != null) {
                        ModalButton(cancelLabel, onClose, Modifier.weight(1f))
                        VerticalDivider(
                            Modifier.fillMaxHeight(),
                            thickness = .5.dp,
                            color = divider,
                        )
                    }
                    ModalButton(
                        confirmLabel,
                        onConfirm,
                        Modifier.weight(1f),
                        color =
                            if (destructive) FleurDestructive
                            else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

data class FleurAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun FleurActionSheet(
    actions: List<FleurAction>,
    onClose: () -> Unit,
    title: String? = null,
    selected: String? = null,
) {
    val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = .13f)
    FleurModal(onClose, bottom = true) {
        Column(Modifier.testTag("fleur-action-sheet")) {
            Surface(
                modifier = Modifier.weight(1f, fill = false),
                shape = RoundedCornerShape(20.dp),
                color = modalBackground,
                tonalElevation = 0.dp,
            ) {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    if (title != null) {
                        Text(
                            title,
                            Modifier.fillMaxWidth().padding(17.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                        HorizontalDivider(thickness = .5.dp, color = divider)
                    }
                    actions.forEachIndexed { index, action ->
                        if (index > 0) HorizontalDivider(thickness = .5.dp, color = divider)
                        Box {
                            ModalButton(
                                action.label,
                                action.onClick,
                                Modifier.fillMaxWidth().semantics {
                                    if (selected != null) this.selected = action.label == selected
                                },
                                color =
                                    if (action.destructive) FleurDestructive
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            if (action.label == selected)
                                FleurIcon(
                                    "check",
                                    Modifier.align(Alignment.CenterEnd)
                                        .padding(end = 18.dp)
                                        .size(17.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = modalBackground,
                tonalElevation = 0.dp,
            ) {
                ModalButton("Cancel", onClose, Modifier.fillMaxWidth(), bold = true)
            }
        }
    }
}
