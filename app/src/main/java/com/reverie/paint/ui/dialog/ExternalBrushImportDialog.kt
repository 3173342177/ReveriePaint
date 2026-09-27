/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.ui.dialog

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reverie.paint.R
import com.reverie.paint.core.PaintViewModel
import com.reverie.paint.core.importBrushesFromUris
import com.reverie.paint.core.queryFileName
import com.reverie.paint.ui.components.ReTextButton
import com.reverie.paint.ui.theme.Morandi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExternalBrushImportDialog(
    uris: List<Uri>,
    vm: PaintViewModel,
    onDismiss: () -> Unit,
) {
    if (uris.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val isSingle = uris.size == 1

    val fileNames = remember(uris) {
        uris.map { uri ->
            queryFileName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "brush_preset"
        }
    }

    val defaultImportGroup = stringResource(R.string.brush_import_dialog_default_group)

    val recommendedGroup = remember(fileNames, defaultImportGroup) {
        if (isSingle) {
            val name = fileNames.first()
            if (name.endsWith(".bundle", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true)) {
                name.substringBeforeLast('.')
                    .removeSuffix(".bundle")
                    .removeSuffix(".zip")
                    .trim()
                    .ifBlank { defaultImportGroup }
            } else {
                defaultImportGroup
            }
        } else {
            defaultImportGroup
        }
    }

    val existingGroups = remember(vm.brushPresets, vm.customBrushGroups, recommendedGroup, defaultImportGroup) {
        val list = mutableListOf<String>()
        if (recommendedGroup.isNotBlank()) list.add(recommendedGroup)
        if (defaultImportGroup !in list) list.add(defaultImportGroup)

        val userGroups = vm.customBrushGroups + vm.brushPresets.map { it.group }
        for (g in userGroups) {
            val trimmed = g.trim()
            if (trimmed.isNotEmpty() && trimmed !in listOf("全部", "常用", "最近") && trimmed !in list) {
                list.add(trimmed)
            }
        }
        list
    }

    var selectedGroup by remember(recommendedGroup) { mutableStateOf(recommendedGroup) }
    var isCustomGroupMode by remember { mutableStateOf(false) }
    var customGroupName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Morandi.panel,
            border = BorderStroke(1.dp, Morandi.border),
            shadowElevation = 16.dp,
            modifier = Modifier.width(360.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Morandi.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_brush),
                            contentDescription = null,
                            tint = Morandi.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (isSingle) {
                            stringResource(R.string.brush_import_dialog_title_single)
                        } else {
                            stringResource(R.string.brush_import_dialog_title_multiple, uris.size)
                        },
                        color = Morandi.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isSingle) {
                        stringResource(R.string.brush_import_dialog_desc_single)
                    } else {
                        stringResource(R.string.brush_import_dialog_desc_multiple, uris.size)
                    },
                    color = Morandi.subText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                // File list preview
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Morandi.panelHi.copy(alpha = 0.5f))
                        .border(1.dp, Morandi.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    fileNames.forEach { name ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_brush),
                                contentDescription = null,
                                tint = Morandi.subText,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = name,
                                color = Morandi.text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Target Group Header
                Text(
                    text = stringResource(R.string.brush_import_dialog_target_group),
                    color = Morandi.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // Group Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    existingGroups.take(6).forEach { group ->
                        val isSelected = !isCustomGroupMode && selectedGroup == group
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) Morandi.accent
                                    else Morandi.panelHi.copy(alpha = 0.8f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Morandi.accent else Morandi.border,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    isCustomGroupMode = false
                                    selectedGroup = group
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = group,
                                color = if (isSelected) Color.White else Morandi.text,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }

                    // "+ 新建分组" chip
                    val isNewSelected = isCustomGroupMode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isNewSelected) Morandi.accent
                                else Morandi.panelHi.copy(alpha = 0.8f)
                            )
                            .border(
                                1.dp,
                                if (isNewSelected) Morandi.accent else Morandi.border,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                isCustomGroupMode = true
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.brush_import_dialog_new_group),
                            color = if (isNewSelected) Color.White else Morandi.text,
                            fontSize = 12.sp,
                            fontWeight = if (isNewSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                // Custom Group Input Field
                AnimatedVisibility(visible = isCustomGroupMode) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Morandi.panelHi.copy(alpha = 0.6f))
                                .border(1.dp, Morandi.border, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            if (customGroupName.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.brush_import_dialog_new_group_hint),
                                    color = Morandi.subText,
                                    fontSize = 13.sp,
                                )
                            }
                            BasicTextField(
                                value = customGroupName,
                                onValueChange = { customGroupName = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Morandi.text,
                                    fontSize = 13.sp,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action Buttons
                ReTextButton(
                    text = stringResource(R.string.brush_import_dialog_btn_import),
                    onClick = {
                        val targetGroup = if (isCustomGroupMode && customGroupName.isNotBlank()) {
                            customGroupName.trim()
                        } else {
                            selectedGroup.trim().ifBlank { defaultImportGroup }
                        }
                        onDismiss()
                        vm.importBrushesFromUris(uris, targetGroup = targetGroup) { success, count ->
                            if (success) {
                                val msg = if (count == 1) {
                                    val name = fileNames.firstOrNull()?.substringBeforeLast('.') ?: ""
                                    context.getString(R.string.toast_brush_import_success_single, name)
                                } else {
                                    context.getString(R.string.toast_brush_import_success_multiple, count)
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.ic_import,
                    containerColor = Morandi.accent,
                    contentColor = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(8.dp))

                ReTextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    contentColor = Morandi.subText,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
