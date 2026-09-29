/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.reverie.paint.ui.painting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.reverie.paint.R
import com.reverie.paint.core.PaintViewModel
import com.reverie.paint.core.editCanvasClipboard
import com.reverie.paint.ui.theme.Morandi

/** Focusable, dismissible popup with no full-screen dimming over the artwork. */
@Composable
internal fun CanvasEditMenu(vm: PaintViewModel, onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, with(LocalDensity.current) { 72.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 320.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .background(Morandi.panel, RoundedCornerShape(16.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.canvas_edit_title), color = Morandi.text, fontSize = 15.sp)
            Text(
                stringResource(
                    when {
                        vm.canvasEditBusy -> R.string.canvas_edit_loading
                        !vm.canvasClipboardAvailable -> R.string.canvas_edit_unavailable
                        vm.canvasEditCapabilities and 1 == 0 -> R.string.canvas_edit_no_pixels
                        vm.hasSelection -> R.string.canvas_edit_selection_hint
                        else -> R.string.canvas_edit_layer_hint
                    },
                ),
                color = Morandi.subText,
                fontSize = 12.sp,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (action in 0..2) {
                    val capability = when (action) { 0 -> 2; 1 -> 1; else -> 4 }
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Morandi.text,
                            disabledContentColor = Morandi.subText.copy(alpha = 0.5f),
                        ),
                        enabled = !vm.canvasEditBusy && vm.canvasEditCapabilities and capability != 0,
                        onClick = {
                            vm.editCanvasClipboard(cut = action == 0, paste = action == 2)
                            onDismiss()
                        },
                    ) {
                        Text(
                            stringResource(when (action) {
                                0 -> R.string.canvas_edit_cut
                                1 -> R.string.copy
                                else -> R.string.paste
                            }),
                        )
                    }
                }
            }
            Text(stringResource(R.string.canvas_edit_clipboard_hint), color = Morandi.subText, fontSize = 11.sp)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.close), color = Morandi.text)
            }
        }
    }
}
