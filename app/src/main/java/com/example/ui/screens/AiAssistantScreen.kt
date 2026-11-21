package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AiAssistantMessage
import com.example.domain.model.Track
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextMuted

@Composable
fun AiAssistantScreen(
    messages: List<AiAssistantMessage>,
    aiBusy: Boolean,
    onSendCommand: (String) -> Unit,
    onStreamQuestion: (String) -> Unit,
    onPlayTracks: (List<Track>) -> Unit
) {
    var input by remember { mutableStateOf("") }

    val quickPrompts = remember {
        listOf(
            "Play relaxing music",
            "Play Bhojpuri romantic songs",
            "Create a workout playlist",
            "Suggest songs similar to this song",
            "Find trending Hindi songs",
            "Create a 30-minute meditation playlist"
        )
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onSendCommand(spoken)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.img_ai_dj_avatar),
                contentDescription = "MusicVerse AI Assistant Orb",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, CyberCyan, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("MUSICVERSE AI ASSISTANT", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Powered by Gemini · Firebase AI Logic", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
            }
            if (aiBusy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = CyberCyan, strokeWidth = 2.dp)
            }
        }

        // Quick command chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { prompt ->
                AssistChip(
                    onClick = { onSendCommand(prompt) },
                    label = { Text(prompt, color = Color.White) },
                    leadingIcon = {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(containerColor = ObsidianCard)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Conversation list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                if (msg.fromUser) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                                .background(Brush.linearGradient(listOf(NeonViolet, Color(0xFF6D28D9))))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        }
                    }
                } else {
                    LiquidGlassCard(glowColor = CyberCyan) {
                        Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)

                        if (msg.createdPlaylistName != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Created & synced playlist: ${msg.createdPlaylistName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberCyan
                            )
                        }

                        if (msg.suggestedTracks.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { onPlayTracks(msg.suggestedTracks) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Play ${msg.suggestedTracks.size}-Track Mix", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            msg.suggestedTracks.take(4).forEach { t ->
                                Text(
                                    "• ${t.title} — ${t.artist}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    modifier = Modifier
                                        .clickable { onPlayTracks(listOf(t) + msg.suggestedTracks.filterNot { it.id == t.id }) }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Input bar above mini-player / nav bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 128.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask MUSICVERSE AI DJ")
                    }
                    runCatching { voiceLauncher.launch(intent) }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ObsidianCard)
                    .testTag("ai_voice_button")
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice command", tint = CyberCyan)
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_prompt_input"),
                placeholder = { Text("Ask AI DJ or request a mix...", color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ObsidianCard,
                    unfocusedContainerColor = ObsidianCard,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            IconButton(
                onClick = {
                    val trimmed = input.trim()
                    if (trimmed.isNotEmpty()) {
                        input = ""
                        if (trimmed.endsWith("?") && !trimmed.contains("play", true) && !trimmed.contains("playlist", true)) {
                            onStreamQuestion(trimmed)
                        } else {
                            onSendCommand(trimmed)
                        }
                    }
                },
                enabled = !aiBusy && input.isNotBlank(),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NeonViolet)
                    .testTag("ai_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send prompt", tint = Color.White)
            }
        }
    }
}
