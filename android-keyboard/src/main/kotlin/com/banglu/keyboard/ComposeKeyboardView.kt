package com.banglu.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banglu.engine.types.SmartSuggestion

// Colors matching Samsung dark keyboard
private val KeyboardBg = Color(0xFF1B1B1B)
private val KeyBg = Color(0xFF2C2C2C)
private val KeyPressed = Color(0xFF4A4A4A)
private val SpecialKeyBg = Color(0xFF3A3A3A)
private val KeyText = Color.White
private val SuggestionBg = Color(0xFF1E1E1E)
private val SuggestionHighlight = Color(0xFF3D5AFE)
private val SuggestionChipBg = Color(0xFF333333)

private val KeyHeight = 52.dp
private val KeyGap = 4.dp
private val KeyCorner = 8.dp

@Composable
fun BangluKeyboardLayout(
    suggestions: List<SmartSuggestion>,
    isShifted: Boolean,
    onKeyPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onShift: () -> Unit,
    onSuggestionClick: (SmartSuggestion) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KeyboardBg)
            .padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        // Suggestion bar
        SuggestionRow(suggestions, onSuggestionClick)

        Spacer(modifier = Modifier.height(4.dp))

        // Row 1: q w e r t y u i o p
        KeyRow(
            keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            isShifted = isShifted,
            onKeyPress = onKeyPress
        )

        Spacer(modifier = Modifier.height(KeyGap))

        // Row 2: a s d f g h j k l (indented)
        KeyRow(
            keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            isShifted = isShifted,
            onKeyPress = onKeyPress,
            indent = true
        )

        Spacer(modifier = Modifier.height(KeyGap))

        // Row 3: Shift z x c v b n m Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KeyGap)
        ) {
            SpecialKey(
                label = "⇧",
                modifier = Modifier.weight(1.5f),
                onClick = onShift
            )
            for (key in listOf("z", "x", "c", "v", "b", "n", "m")) {
                LetterKey(
                    label = if (isShifted) key.uppercase() else key,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val ch = if (isShifted) key.uppercase()[0] else key[0]
                        onKeyPress(ch)
                    }
                )
            }
            SpecialKey(
                label = "⌫",
                modifier = Modifier.weight(1.5f),
                onClick = onBackspace
            )
        }

        Spacer(modifier = Modifier.height(KeyGap))

        // Row 4: 123 | , | Space | . | Enter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KeyGap)
        ) {
            SpecialKey(label = "123", modifier = Modifier.weight(1.2f), onClick = {})
            SpecialKey(label = ",", modifier = Modifier.weight(0.8f), onClick = { onKeyPress(',') })
            SpaceKey(modifier = Modifier.weight(4.5f), onClick = onSpace)
            SpecialKey(label = ".", modifier = Modifier.weight(0.8f), onClick = { onKeyPress('.') })
            SpecialKey(label = "↵", modifier = Modifier.weight(1.5f), onClick = onEnter)
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestions: List<SmartSuggestion>,
    onSuggestionClick: (SmartSuggestion) -> Unit
) {
    if (suggestions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(SuggestionBg)
        )
        return
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(SuggestionBg),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(suggestions) { suggestion ->
            val isFirst = suggestion == suggestions.firstOrNull()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isFirst) SuggestionHighlight else SuggestionChipBg)
                    .clickable { onSuggestionClick(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = suggestion.bengali,
                    color = KeyText,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun KeyRow(
    keys: List<String>,
    isShifted: Boolean,
    onKeyPress: (Char) -> Unit,
    indent: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (indent) Modifier.padding(horizontal = 16.dp) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(KeyGap)
    ) {
        for (key in keys) {
            LetterKey(
                label = if (isShifted) key.uppercase() else key,
                modifier = Modifier.weight(1f),
                onClick = {
                    val ch = if (isShifted) key.uppercase()[0] else key[0]
                    onKeyPress(ch)
                }
            )
        }
    }
}

@Composable
private fun LetterKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(KeyHeight)
            .clip(RoundedCornerShape(KeyCorner))
            .background(if (isPressed) KeyPressed else KeyBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = KeyText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SpecialKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(KeyHeight)
            .clip(RoundedCornerShape(KeyCorner))
            .background(if (isPressed) KeyPressed else SpecialKeyBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = KeyText,
            fontSize = if (label.length > 2) 14.sp else 18.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SpaceKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(KeyHeight)
            .clip(RoundedCornerShape(KeyCorner))
            .background(if (isPressed) KeyPressed else KeyBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Banglu",
            color = Color(0xFF888888),
            fontSize = 14.sp
        )
    }
}
