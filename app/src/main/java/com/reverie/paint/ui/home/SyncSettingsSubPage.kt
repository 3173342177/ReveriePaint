/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reverie.paint.R
import com.reverie.paint.core.*
import com.reverie.paint.core.sync.SyncCategory
import com.reverie.paint.core.sync.SyncCredentials
import com.reverie.paint.core.sync.SyncTimeFormat
import com.reverie.paint.ui.components.pressScale
import com.reverie.paint.ui.theme.Theme

@Composable
internal fun SyncSettingsSubPage(
    vm: PaintViewModel,
    showBackButton: Boolean = true,
    compact: Boolean = false,
    onBack: () -> Unit,
) {
    val colors = Theme.current
    val state = vm.syncState

    var draftServer by remember { mutableStateOf("") }
    var draftUser by remember { mutableStateOf("") }
    var draftPass by remember { mutableStateOf("") }

    LaunchedEffect(state.editing) {
        if (state.editing) {
            draftServer = state.serverUrl
            draftUser = state.username
            draftPass = ""
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.bg)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = if (compact) 12.dp else 20.dp, vertical = if (compact) 12.dp else 20.dp),
        ) {
            SettingSubPageHeader(
                title = stringResource(R.string.sync_page_title),
                subtitle = stringResource(R.string.sync_page_subtitle),
                showBackButton = showBackButton,
                compact = compact,
                onBack = onBack,
            )

            when {
                state.editing -> {
                    SettingCategoryTitle(stringResource(R.string.sync_category_server))
                    SettingGroup {
                        SyncInputFieldItem(
                            label = stringResource(R.string.sync_field_server),
                            value = draftServer,
                            placeholder = stringResource(R.string.sync_field_server_placeholder),
                            keyboardType = KeyboardType.Uri,
                            shape = settingGroupShape(0, 3),
                            onValueChange = { draftServer = it },
                        )
                        SyncInputFieldItem(
                            label = stringResource(R.string.sync_field_username),
                            value = draftUser,
                            placeholder = stringResource(R.string.sync_field_username_placeholder),
                            shape = settingGroupShape(1, 3),
                            onValueChange = { draftUser = it },
                        )
                        SyncInputFieldItem(
                            label = stringResource(R.string.sync_field_password),
                            value = draftPass,
                            placeholder =
                                stringResource(
                                    if (state.hasSavedPassword) {
                                        R.string.sync_password_keep_hint
                                    } else {
                                        R.string.sync_field_password_placeholder
                                    },
                                ),
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = settingGroupShape(2, 3),
                            onValueChange = { draftPass = it },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    SettingGroup {
                        SyncActionRow(
                            index = 0,
                            total = 2,
                            text = stringResource(R.string.sync_connect_action),
                            color = colors.accent,
                        ) { vm.connectSync(draftServer, draftUser, draftPass) }
                        SyncActionRow(
                            index = 1,
                            total = 2,
                            text = stringResource(R.string.cancel),
                            color = colors.subText,
                        ) { vm.closeSyncEditor() }
                    }
                }

                state.connected -> {
                    SettingCategoryTitle(stringResource(R.string.sync_category_server))
                    SettingGroup {
                        SyncSummaryItem(
                            server = SyncCredentials.redactServerUrl(state.serverUrl),
                            username = state.username,
                            passwordSaved = state.hasSavedPassword,
                            lastBackupAtMs = state.lastBackupAtMs,
                            shape = settingGroupShape(0, 1),
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    SettingGroup {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(settingGroupShape(0, 1))
                                    .background(colors.panel),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SyncInlineAction(
                                text = stringResource(R.string.sync_test_connection),
                                color = colors.accent,
                                modifier = Modifier.weight(1f),
                            ) { vm.testSyncConnection() }
                            SyncDivider()
                            SyncInlineAction(
                                text = stringResource(R.string.sync_edit),
                                color = colors.accent,
                                modifier = Modifier.weight(1f),
                            ) { vm.openSyncEditor() }
                            SyncDivider()
                            SyncInlineAction(
                                text = stringResource(R.string.sync_disconnect),
                                color = Color(0xFFE05555),
                                modifier = Modifier.weight(1f),
                            ) { vm.disconnectSync() }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    SyncStatusLine(vm)

                }

                else -> {
                    SettingGroup {
                        SyncActionRow(
                            index = 0,
                            total = 1,
                            text = stringResource(R.string.sync_connect_entry),
                            color = colors.accent,
                        ) { vm.openSyncEditor() }
                    }
                    Spacer(Modifier.height(8.dp))
                    SyncStatusLine(vm)
                }
            }

            Spacer(Modifier.height(8.dp))
            SettingCategoryTitle(stringResource(R.string.sync_category_backup))
            SettingGroup {
                SettingSwitchGroupItem(
                    icon = R.drawable.ic_cloud,
                    title = stringResource(R.string.sync_auto_backup),
                    summary = stringResource(R.string.sync_auto_backup_summary),
                    checked = state.autoBackupEnabled,
                    enabled = state.connected,
                    shape = settingGroupShape(0, 4),
                    onCheckedChange = { vm.setSyncAutoBackupEnabled(it) },
                )
                SettingSwitchGroupItem(
                    title = stringResource(R.string.sync_wifi_only),
                    summary = stringResource(R.string.sync_wifi_only_summary),
                    checked = state.wifiOnly,
                    enabled = state.connected,
                    shape = settingGroupShape(1, 4),
                    onCheckedChange = { vm.setSyncWifiOnly(it) },
                )
                SettingSwitchGroupItem(
                    title = stringResource(R.string.sync_on_exit),
                    summary = stringResource(R.string.sync_on_exit_summary),
                    checked = state.syncOnExitEnabled,
                    enabled = state.connected,
                    shape = settingGroupShape(2, 4),
                    onCheckedChange = { vm.setSyncOnExitEnabled(it) },
                )
                SyncActionRow(
                    index = 3,
                    total = 4,
                    text = stringResource(R.string.sync_backup_now),
                    color = colors.accent,
                    enabled = state.connected,
                ) { vm.backupToCloud() }
            }

            if (!state.connected) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.sync_backup_locked_hint),
                    color = colors.subText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            SyncBackupStatusLine(vm)

            Spacer(Modifier.height(8.dp))
            SettingCategoryTitle(stringResource(R.string.sync_category_restore))
            SettingGroup {
                SyncActionRow(
                    index = 0,
                    total = 2,
                    text = stringResource(R.string.sync_restore_artworks),
                    color = colors.accent,
                    enabled = state.connected,
                ) { vm.restoreFromCloud(SyncCategory.ARTWORKS) }
                SyncActionRow(
                    index = 1,
                    total = 2,
                    text = stringResource(R.string.sync_restore_brushes),
                    color = colors.accent,
                    enabled = state.connected,
                ) { vm.restoreFromCloud(SyncCategory.BRUSHES) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.sync_restore_hint),
                color = colors.subText,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
            SyncRestoreStatusLine(vm)

            Spacer(Modifier.height(16.dp))
            SettingInfoCard(
                title = stringResource(R.string.sync_backup_info_title),
                text = stringResource(R.string.sync_backup_info_text),
            )

            Spacer(Modifier.height(12.dp))
            SettingInfoCard(
                title = stringResource(R.string.sync_info_title),
                text = stringResource(R.string.sync_info_text),
            )

            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun SyncActionRow(
    index: Int,
    total: Int,
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = Theme.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(settingGroupShape(index, total))
                .background(colors.panel)
                .then(
                    if (enabled) {
                        Modifier
                            .pressScale(interaction, pressedScale = 0.98f)
                            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (enabled) color else colors.subText.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SyncInlineAction(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .pressScale(interaction, pressedScale = 0.95f)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SyncDivider() {
    val colors = Theme.current
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(20.dp)
                .background(colors.border),
    )
}

@Composable
private fun SyncSummaryItem(
    server: String,
    username: String,
    passwordSaved: Boolean,
    lastBackupAtMs: Long,
    shape: RoundedCornerShape,
) {
    val colors = Theme.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.panel)
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                SyncSummaryLine(stringResource(R.string.sync_summary_server), server)
            }
            Spacer(Modifier.width(8.dp))
            SyncConnectedBadge()
        }
        Spacer(Modifier.height(6.dp))
        val passwordText =
            if (passwordSaved) stringResource(R.string.sync_password_saved) else stringResource(R.string.sync_password_not_saved)
        SyncSummaryLine(stringResource(R.string.sync_summary_username), "$username · $passwordText")
        Spacer(Modifier.height(6.dp))
        SyncSummaryLine(stringResource(R.string.sync_last_backup), SyncRelativeTime(lastBackupAtMs))
    }
}

@Composable
private fun SyncRelativeTime(lastBackupAtMs: Long): String {
    val rel = SyncTimeFormat.relative(System.currentTimeMillis(), lastBackupAtMs) ?: return stringResource(R.string.sync_time_never)
    return when (rel.unit) {
        SyncTimeFormat.Unit.JUST_NOW -> stringResource(R.string.sync_time_just_now)
        SyncTimeFormat.Unit.MINUTES -> stringResource(R.string.sync_time_minutes, rel.value)
        SyncTimeFormat.Unit.HOURS -> stringResource(R.string.sync_time_hours, rel.value)
        SyncTimeFormat.Unit.DAYS -> stringResource(R.string.sync_time_days, rel.value)
    }
}

@Composable
private fun SyncConnectedBadge() {
    val colors = Theme.current
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.accent.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(R.string.sync_connected_badge),
            color = colors.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SyncSummaryLine(
    label: String,
    value: String,
) {
    val colors = Theme.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = colors.subText, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Text(
            text = value,
            color = colors.text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SyncStatusLine(vm: PaintViewModel) {
    val colors = Theme.current
    val state = vm.syncState
    if (state.status == SyncConnectionStatus.IDLE) return
    val connected = stringResource(R.string.sync_status_ok)
    val (text, color) =
        when (state.status) {
            SyncConnectionStatus.IDLE -> return
            SyncConnectionStatus.TESTING -> stringResource(R.string.sync_status_testing) to colors.subText
            SyncConnectionStatus.OK -> connected to colors.accent
            SyncConnectionStatus.FAILED ->
                if (state.statusDetail == SYNC_DETAIL_NOT_CONFIGURED) {
                    stringResource(R.string.sync_error_not_configured) to Color(0xFFE05555)
                } else {
                    stringResource(R.string.sync_status_failed) + " (" + state.statusDetail + ")" to Color(0xFFE05555)
                }
        }
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 6.dp),
    )
}

@Composable
private fun SyncBackupStatusLine(vm: PaintViewModel) {
    val colors = Theme.current
    val state = vm.syncState
    when (state.backupStatus) {
        SyncBackupStatus.IDLE -> Unit

        SyncBackupStatus.RUNNING -> {
            Text(
                text = stringResource(R.string.sync_backup_running, state.backupDone, state.backupTotal),
                color = colors.subText,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        SyncBackupStatus.WAITING_WIFI -> {
            Text(
                text = stringResource(R.string.sync_status_waiting_wifi),
                color = colors.subText,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        SyncBackupStatus.DONE -> {
            Column(modifier = Modifier.padding(start = 6.dp)) {
                Text(
                    text =
                        stringResource(
                            R.string.sync_backup_result,
                            state.lastBackupUploaded,
                            state.lastBackupSkipped,
                            state.lastBackupDeleted,
                            state.lastBackupFailed,
                        ),
                    color = if (state.lastBackupFailed > 0) Color(0xFFE05555) else colors.accent,
                    fontSize = 12.sp,
                )
                if (state.lastBackupError.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = state.lastBackupError, color = Color(0xFFE05555), fontSize = 11.sp)
                }
            }
        }

        SyncBackupStatus.FAILED -> {
            Text(
                text = stringResource(R.string.sync_status_failed) + " (" + state.lastBackupError + ")",
                color = Color(0xFFE05555),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun SyncRestoreStatusLine(vm: PaintViewModel) {
    val colors = Theme.current
    val state = vm.syncState
    val label =
        when (state.restoreCategory) {
            SyncCategory.ARTWORKS -> stringResource(R.string.sync_artworks)
            SyncCategory.BRUSHES -> stringResource(R.string.sync_brushes)
            null -> ""
        }
    val prefix = if (label.isEmpty()) "" else "$label · "
    when (state.restoreStatus) {
        SyncRestoreStatus.IDLE -> Unit

        SyncRestoreStatus.RUNNING -> {
            Text(
                text = prefix + stringResource(R.string.sync_restore_running, state.restoreDone, state.restoreTotal),
                color = colors.subText,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        SyncRestoreStatus.DONE -> {
            Column(modifier = Modifier.padding(start = 6.dp)) {
                Text(
                    text =
                        prefix +
                            stringResource(
                                R.string.sync_restore_result,
                                state.lastRestoreDownloaded,
                                state.lastRestoreSkipped,
                                state.lastRestoreConflicts,
                                state.lastRestoreFailed,
                            ),
                    color = if (state.lastRestoreFailed > 0) Color(0xFFE05555) else colors.accent,
                    fontSize = 12.sp,
                )
                if (state.lastRestoreError.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = state.lastRestoreError, color = Color(0xFFE05555), fontSize = 11.sp)
                }
            }
        }

        SyncRestoreStatus.FAILED -> {
            Text(
                text = prefix + stringResource(R.string.sync_status_failed) + " (" + state.lastRestoreError + ")",
                color = Color(0xFFE05555),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun SyncInputFieldItem(
    label: String,
    value: String,
    placeholder: String,
    shape: RoundedCornerShape,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onValueChange: (String) -> Unit,
) {
    val colors = Theme.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.panel)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = colors.subText.copy(alpha = 0.5f), fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next,
                ),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    cursorColor = colors.accent,
                ),
        )
    }
}
