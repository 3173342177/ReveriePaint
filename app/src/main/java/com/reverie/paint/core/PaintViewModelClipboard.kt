/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.reverie.paint.core

import com.reverie.paint.R
import com.reverie.paint.model.RecordingEvents

/** Only capabilities cross into UI state; pixels stay in the native document-local clipboard. */
internal fun PaintViewModel.refreshCanvasEditCapabilities() {
    if (canvasEditBusy || renderHandler == null) return
    canvasEditBusy = true
    canvasEditCapabilities = 0
    var capabilities = 0
    var available = false
    runCore(render = false, after = {
        canvasEditCapabilities = capabilities
        canvasClipboardAvailable = available
        canvasEditBusy = false
    }) {
        capabilities = ReverieCoreBridge.canvasClipboardCapabilities()
        available = true
    }
}

internal fun PaintViewModel.editCanvasClipboard(cut: Boolean = false, paste: Boolean = false) {
    if (canvasEditBusy || renderHandler == null) return
    val required = if (paste) 4 else if (cut) 2 else 1
    if (canvasEditCapabilities and required == 0) return
    canvasEditBusy = true
    // Record in input order, before enqueueing, like other document commands.
    if (recorder.recording) {
        if (paste) recorder.toolOp(RecordingEvents.T_CANVAS_PASTE)
        else recorder.toolOp(if (cut) RecordingEvents.T_CANVAS_CUT else RecordingEvents.T_CANVAS_COPY)
    }
    var success = false
    val pastedName = appContext.getString(R.string.canvas_edit_pasted_layer)
    runCore(render = cut || paste, after = {
        canvasEditBusy = false
        if (success) {
            if (cut || paste) {
                if (paste) selectedLayerIndices = emptySet()
                notifyLayerChanged(pixelChanged = true)
            }
            showActionToast(
                when {
                    paste -> R.string.canvas_edit_pasted
                    cut -> R.string.canvas_edit_cut_done
                    else -> R.string.canvas_edit_copied
                },
                R.drawable.ic_copy,
            )
        } else {
            showActionToast(R.string.canvas_edit_failed, R.drawable.ic_copy)
        }
        refreshCanvasEditCapabilities()
    }) {
        if (paste) {
            val index = ReverieCoreBridge.pasteCanvasClipboard()
            success = index >= 0
            if (success) ReverieCoreBridge.setLayerName(index, pastedName)
        } else {
            success = ReverieCoreBridge.copyCanvasToClipboard(cut)
        }
    }
}
