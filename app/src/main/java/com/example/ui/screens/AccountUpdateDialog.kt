package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppUpdateInfo
import com.example.domain.model.AudioQuality
import com.example.domain.model.UserProfile
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted

@Composable
fun AccountAndSettingsDialog(
    userProfile: UserProfile?,
    fallbackName: String,
    fallbackEmail: String,
    updateInfo: AppUpdateInfo?,
    updateChecking: Boolean,
    onDismiss: () -> Unit,
    onUpdatePrivacy: (Boolean?, Boolean?, AudioQuality?, String?) -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenUpdateInstall: (AppUpdateInfo) -> Unit,
    onRemindLater: (Int) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account, Plans & Updates") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidGlassCard(glowColor = NeonViolet) {
                    Text(
                        userProfile?.displayName ?: fallbackName.ifBlank { "MusicVerse User" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        userProfile?.email ?: fallbackEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyberCyan
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Plan: ${userProfile?.subscriptionTier ?: "FREE"} · Cloud Sync Active",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }

                // Privacy & Audio Preferences
                Text("Privacy & Audio Quality", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AI Personalization", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = userProfile?.personalizationEnabled ?: true,
                        onCheckedChange = { onUpdatePrivacy(null, it, null, null) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Listening Analytics", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = userProfile?.analyticsEnabled ?: true,
                        onCheckedChange = { onUpdatePrivacy(it, null, null, null) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AudioQuality.entries.forEach { q ->
                        FilterChip(
                            selected = (userProfile?.preferredQuality ?: "HIGH") == q.name,
                            onClick = { onUpdatePrivacy(null, null, q, null) },
                            label = { Text(q.label) }
                        )
                    }
                }

                // Plans Overview
                LiquidGlassCard(glowColor = CyberCyan) {
                    Text("Plans Overview", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("• FREE: Local player, Open CC catalog, Synced Glass Lyrics, Gemini AI DJ.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    Text("• MUSICVERSE PRO: Lossless DSP presets, unlimited cloud playlist backups & priority AI mixes (Google Play Billing ready).", style = MaterialTheme.typography.bodyMedium, color = CyberCyan)
                }

                // Automatic App Update System
                LiquidGlassCard(glowColor = NeonViolet) {
                    Text("App Updates", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    if (updateInfo != null) {
                        Text(
                            "Installed: v${updateInfo.currentVersionName} (${updateInfo.currentVersionCode})\nLatest: v${updateInfo.latestVersionName} (${updateInfo.latestVersionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(updateInfo.changelog, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        if (updateInfo.updateAvailable) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onOpenUpdateInstall(updateInfo) }) {
                                    Text("Update Now")
                                }
                                OutlinedButton(onClick = { onRemindLater(updateInfo.latestVersionCode) }) {
                                    Text("Later")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = onCheckUpdates, enabled = !updateChecking) {
                        Text(if (updateChecking) "Checking..." else "Check for Updates")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sign Out")
                    }
                    Button(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Account")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Account & Cloud Data?") },
            text = { Text("This permanently deletes your cloud playlists, favorites, listening history, and Firebase account.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Permanently") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
