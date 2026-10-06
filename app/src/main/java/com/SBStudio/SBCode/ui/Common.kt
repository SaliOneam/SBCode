package com.SBStudio.SBCode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.SBStudio.SBCode.ui.theme.SbAccent
import com.SBStudio.SBCode.ui.theme.SbCard
import com.SBStudio.SBCode.ui.theme.SbCardBorder
import com.SBStudio.SBCode.ui.theme.SbCss
import com.SBStudio.SBCode.ui.theme.SbHtml
import com.SBStudio.SBCode.ui.theme.SbJs
import com.SBStudio.SBCode.ui.theme.SbRed
import com.SBStudio.SBCode.ui.theme.SbText
import com.SBStudio.SBCode.ui.theme.SbTextDim
import kotlinx.coroutines.delay

@Composable
fun SbIcon(icon: ImageVector, tint: Color = SbTextDim, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun IconAction(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = SbTextDim,
    boxSize: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    background: Color = Color.Transparent,
) {
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SbIcon(icon, tint, iconSize)
    }
}

private class BadgeStyle(val bg: Color, val label: String, val fg: Color)

/** Small colored badge that shows the file type (HTML, CSS, JS) or a plain file icon. */
@Composable
fun FileBadge(name: String, size: Dp = 20.dp) {
    val style = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> BadgeStyle(SbHtml, "<>", Color.White)
        "css" -> BadgeStyle(SbCss, "#", Color.White)
        "js", "mjs" -> BadgeStyle(SbJs, "JS", Color(0xFF1A1400))
        else -> null
    }
    if (style == null) {
        SbIcon(SbIcons.File, SbTextDim, size)
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(5.dp))
                .background(style.bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = style.label,
                color = style.fg,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun TagChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun NameDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    validate: (String) -> String?,
    initial: String = "",
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        try {
            focus.requestFocus()
        } catch (_: Exception) {
        }
    }
    val submit: () -> Unit = {
        val err = validate(text)
        if (err != null) error = err else onConfirm(text.trim())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SbCard,
        titleContentColor = SbText,
        textContentColor = SbTextDim,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                singleLine = true,
                label = { Text(label) },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SbText,
                    unfocusedTextColor = SbText,
                    focusedBorderColor = SbAccent,
                    unfocusedBorderColor = SbCardBorder,
                    focusedLabelColor = SbAccent,
                    unfocusedLabelColor = SbTextDim,
                    cursorColor = SbText,
                    errorTextColor = SbText,
                    errorBorderColor = SbRed,
                    errorLabelColor = SbRed,
                    errorSupportingTextColor = SbRed,
                    errorCursorColor = SbText,
                ),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = submit) { Text(confirmText, color = SbAccent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SbTextDim) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SbCard,
        titleContentColor = SbText,
        textContentColor = SbTextDim,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = SbRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SbTextDim) } },
    )
}

@Composable
fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SbCard,
        titleContentColor = SbText,
        textContentColor = SbTextDim,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = SbAccent) } },
    )
}
