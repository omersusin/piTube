package com.omersusin.pitube.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omersusin.pitube.data.Captions

@Composable
fun SubtitleOverlay(
    captions: List<Captions.Cue>,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val currentCaption = remember(captions, currentPositionMs) {
        captions.firstOrNull { currentPositionMs >= it.startMs && currentPositionMs < it.endMs }
    }

    AnimatedVisibility(
        visible = currentCaption != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
        modifier = modifier
    ) {
        currentCaption?.let { cue ->
            SubtitleText(text = cue.text)
        }
    }
}

@Composable
private fun SubtitleText(
    text: String,
    fontSize: Int = 16,
    textColor: Color = Color.White,
    backgroundColor: Color = Color.Black.copy(alpha = 0.7f)
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun RollingSubtitleOverlay(
    captions: List<Captions.Cue>,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val currentCaption = remember(captions, currentPositionMs) {
        captions.firstOrNull { currentPositionMs >= it.startMs && currentPositionMs < it.endMs }
    }

    val previousCaption = remember(captions, currentPositionMs) {
        captions.lastOrNull { it.endMs <= currentPositionMs }
    }

    // Smart deduplication for auto-generated captions
    val displayText = remember(currentCaption, previousCaption) {
        if (currentCaption == null) {
            ""
        } else if (previousCaption == null) {
            currentCaption.text
        } else {
            // Check if current caption overlaps with previous
            val currentWords = currentCaption.text.split(" ").filter { it.isNotBlank() }
            val previousWords = previousCaption.text.split(" ").filter { it.isNotBlank() }
            
            // Find unique words in current that weren't in previous
            val uniqueWords = currentWords.filter { word ->
                previousWords.none { prevWord -> 
                    prevWord.equals(word, ignoreCase = true) 
                }
            }
            
            if (uniqueWords.isEmpty()) {
                currentCaption.text
            } else {
                uniqueWords.joinToString(" ")
            }
        }
    }

    AnimatedVisibility(
        visible = displayText.isNotBlank(),
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
        modifier = modifier
    ) {
        SubtitleText(text = displayText)
    }
}
