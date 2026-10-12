/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core.stylus

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.reverie.paint.core.PaintViewModel

/**
 * Dedicated Stylus Adapter for Xiaomi Focus Pen and Xiaomi Smart Pen (1st & 2nd Gen).
 * Supports primary (writing) key, secondary (screenshot) key, focus key (Focus Pen),
 * double-click detection, hold-to-erase, and tactile feedback.
 */
class XiaomiStylusAdapter : StylusBrandAdapter {
    override val brand: StylusBrand = StylusBrand.XIAOMI_STYLUS
    override val isSideButtonPressed: Boolean
        get() = isPrimaryCurrentlyDown || isSecondaryCurrentlyDown

    companion object {
        private const val DOUBLE_CLICK_TIMEOUT_MS = 320L
        private const val MIN_CLICK_INTERVAL_MS = 60L
        private const val LONG_PRESS_TIMEOUT_MS = 500L
        private const val KEY_FOCUS_KEYCODE_1 = 310
        private const val KEY_FOCUS_KEYCODE_2 = KeyEvent.KEYCODE_F1
        private const val KEY_FOCUS_KEYCODE_3 = KeyEvent.KEYCODE_DPAD_CENTER
        private const val KEY_FOCUS_KEYCODE_4 = KeyEvent.KEYCODE_ENTER
        private const val KEY_FOCUS_KEYCODE_5 = KeyEvent.KEYCODE_VOLUME_DOWN
        private const val KEY_FOCUS_KEYCODE_6 = KeyEvent.KEYCODE_VOLUME_UP
    }

    private var isSupportedXiaomiDevice: Boolean = run {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        m.contains("xiaomi") || b.contains("xiaomi") || b.contains("redmi") || m.contains("redmi")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentContext: Context? = null

    // Primary button tracking
    private var lastPrimaryDownTime: Long = 0L
    private var lastPrimaryReleaseTime: Long = 0L
    private var isPrimaryCurrentlyDown: Boolean = false
    private var primaryClickCount: Int = 0
    private var strokeHappenedSincePrimaryPress: Boolean = false
    private var pendingPrimarySingleClickRunnable: Runnable? = null

    // Secondary button tracking
    private var lastSecondaryDownTime: Long = 0L
    private var lastSecondaryReleaseTime: Long = 0L
    private var isSecondaryCurrentlyDown: Boolean = false
    private var strokeHappenedSinceSecondaryPress: Boolean = false

    // PenEngine (Xiaomi HyperOS 3.0+ TouchFilmUtils) reflection bridge
    private var currentVm: PaintViewModel? = null
    private var currentFeedbackManager: StylusFeedbackManager? = null
    private var touchFilmUtilsClass: Class<*>? = null
    private var onDispatchKeyEventMethod: java.lang.reflect.Method? = null
    private var isPenEngineInitialized = false

    override fun register(context: Context, vm: PaintViewModel, feedbackManager: StylusFeedbackManager) {
        currentVm = vm
        currentFeedbackManager = feedbackManager
        currentContext = context.applicationContext
        ensurePenEngineState(context.applicationContext, vm, feedbackManager)
    }

    override fun onActivityResume(activity: android.app.Activity) {
        currentVm?.let { vm ->
            currentFeedbackManager?.let { fm ->
                currentContext = activity.applicationContext
                ensurePenEngineState(activity.applicationContext, vm, fm)
            }
        }
    }

    override fun onActivityPause(activity: android.app.Activity) {
        destroyPenEngine()
    }

    override fun unregister(context: Context) {
        destroyPenEngine()
        release()
    }

    override fun syncSettings(vm: PaintViewModel, feedbackManager: StylusFeedbackManager) {
        currentVm = vm
        currentFeedbackManager = feedbackManager
        currentContext?.let { ctx ->
            ensurePenEngineState(ctx, vm, feedbackManager)
        }
    }

    override fun detect(context: Context, vm: PaintViewModel): StylusDeviceDetected? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brandName = Build.BRAND.lowercase()
        val isXiaomiDevice = manufacturer.contains("xiaomi") || brandName.contains("xiaomi") || brandName.contains("redmi")
        isSupportedXiaomiDevice = isXiaomiDevice

        var xiaomiStylusConnected = false
        var detectedPenName = "小米灵感触控笔"
        try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            if (inputManager != null) {
                for (id in inputManager.inputDeviceIds) {
                    val dev = inputManager.getInputDevice(id) ?: continue
                    val sources = dev.sources
                    val hasStylusSource = (sources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS ||
                            (sources and InputDevice.SOURCE_BLUETOOTH_STYLUS) == InputDevice.SOURCE_BLUETOOTH_STYLUS
                    val name = dev.name.lowercase()

                    if (hasStylusSource || name.contains("pen") || name.contains("stylus")) {
                        if (name.contains("xiaomi") || name.contains("mi pen") || name.contains("smart pen") || name.contains("focus")) {
                            xiaomiStylusConnected = true
                            if (name.contains("focus pro") || name.contains("focus pen pro") || name.contains("stylus pro")) {
                                detectedPenName = "小米焦点触控笔 Pro"
                            } else if (name.contains("focus")) {
                                detectedPenName = "小米焦点触控笔"
                            }
                            break
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        return StylusDeviceDetected(
            brand = StylusBrand.XIAOMI_STYLUS,
            isCurrentDeviceSupported = isXiaomiDevice,
            isConnected = xiaomiStylusConnected || isXiaomiDevice,
            deviceName = if (isXiaomiDevice) "$detectedPenName (${Build.MODEL})" else detectedPenName,
        )
    }

    fun detectModel(context: Context): XiaomiPencilModel {
        try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            if (inputManager != null) {
                for (id in inputManager.inputDeviceIds) {
                    val dev = inputManager.getInputDevice(id) ?: continue
                    val name = dev.name.lowercase()
                    if (name.contains("focus pro") || name.contains("focus pen pro") || name.contains("stylus pro")) {
                        return XiaomiPencilModel.FOCUS_PEN_PRO
                    }
                    if (name.contains("focus")) {
                        return XiaomiPencilModel.FOCUS_PEN
                    }
                    if (name.contains("smart pen 2") || name.contains("smartpen2")) {
                        return XiaomiPencilModel.SMART_PEN_2
                    }
                    if (name.contains("smart pen 1") || name.contains("smartpen1")) {
                        return XiaomiPencilModel.SMART_PEN_1
                    }
                }
            }
        } catch (_: Throwable) {}

        val model = Build.MODEL.lowercase()
        return when {
            model.contains("pad 8") -> XiaomiPencilModel.FOCUS_PEN_PRO
            model.contains("pad 6s") || model.contains("pad 7") -> XiaomiPencilModel.FOCUS_PEN
            model.contains("pad 6") -> XiaomiPencilModel.SMART_PEN_2
            model.contains("pad 5") -> XiaomiPencilModel.SMART_PEN_1
            else -> XiaomiPencilModel.SMART_PEN_2
        }
    }

    private fun ensurePenEngineState(context: Context, vm: PaintViewModel, fm: StylusFeedbackManager) {
        val needsPenEngine = !vm.xiaomiPencilModel.hasPhysicalButtons || vm.xiaomiPencilModel.hasSlideGesture
        if (needsPenEngine) {
            if (!isPenEngineInitialized) {
                initPenEngineIfAvailable(context, vm, fm)
            }
        } else {
            if (isPenEngineInitialized) {
                destroyPenEngine()
            }
        }
    }

    private fun initPenEngineIfAvailable(context: Context, vm: PaintViewModel, fm: StylusFeedbackManager) {
        if (isPenEngineInitialized) return
        try {
            val utilsClass = Class.forName("com.miui.penengine.touchfilm.MiuiTouchFilmUtils")
            val listenerInterface = Class.forName("com.miui.penengine.touchfilm.MiuiTouchFilmUtils\$TouchFilmListener")
            touchFilmUtilsClass = utilsClass
            onDispatchKeyEventMethod = utilsClass.getMethod("onDispatchKeyEvent", KeyEvent::class.java)

            val proxyListener = java.lang.reflect.Proxy.newProxyInstance(
                listenerInterface.classLoader,
                arrayOf(listenerInterface),
            ) { _, method, args ->
                when (method.name) {
                    "onTouchFilmTriggered" -> {
                        val function = args?.getOrNull(0) as? Int ?: return@newProxyInstance null
                        handler.post {
                            handleTouchFilmTriggered(function, vm, fm)
                        }
                        null
                    }
                    "onBrushPreviewChanged" -> {
                        null
                    }
                    else -> null
                }
            }

            val initMethod = utilsClass.getMethod("init", Context::class.java, listenerInterface)
            initMethod.invoke(null, context, proxyListener)
            isPenEngineInitialized = true
        } catch (_: Throwable) {
            // Not running on Xiaomi HyperOS with PenEngine SDK, fallback gracefully
        }
    }

    private fun destroyPenEngine() {
        if (!isPenEngineInitialized) return
        try {
            touchFilmUtilsClass?.getMethod("onDestroy")?.invoke(null)
        } catch (_: Throwable) {}
        onDispatchKeyEventMethod = null
        touchFilmUtilsClass = null
        isPenEngineInitialized = false
    }

    private fun handleTouchFilmTriggered(function: Int, vm: PaintViewModel, fm: StylusFeedbackManager) {
        val switchBrushEraser = getTouchFilmConstant("SWITCH_BETWEEN_BRUSH_AND_ERASER", 1)
        val switchToPrevious = getTouchFilmConstant("SWITCH_TO_PREVIOUS_BRUSH", 2)
        val showColorWheel = getTouchFilmConstant("SHOW_COLOR_WHEEL", 3)
        val showBrushSettings = getTouchFilmConstant("SHOW_BRUSH_SETTINGS", 4)
        val stylusSlideUp = getTouchFilmConstant("STYLUS_SLIDE_UP", 5)
        val stylusSlideDown = getTouchFilmConstant("STYLUS_SLIDE_DOWN", 6)

        when (function) {
            switchBrushEraser -> {
                fm.triggerActionConfirmation()
                vm.executeStylusAction(StylusAction.fromActionId(vm.xiaomiDoubleTapAction))
            }
            switchToPrevious -> {
                fm.triggerActionConfirmation()
                vm.executeStylusAction(StylusAction.TOGGLE_LAST_TOOL)
            }
            showColorWheel -> {
                fm.triggerActionConfirmation()
                vm.executeStylusAction(StylusAction.SHOW_COLOR_PALETTE)
            }
            showBrushSettings -> {
                fm.triggerActionConfirmation()
                vm.executeStylusAction(StylusAction.fromActionId(vm.xiaomiSqueezeAction))
            }
            stylusSlideUp -> {
                vm.executeXiaomiSlide(up = true)
            }
            stylusSlideDown -> {
                vm.executeXiaomiSlide(up = false)
            }
        }
    }

    private fun getTouchFilmConstant(name: String, fallback: Int): Int {
        return try {
            touchFilmUtilsClass?.getField(name)?.getInt(null) ?: fallback
        } catch (_: Throwable) {
            fallback
        }
    }

    override fun onStylusKeyEvent(
        event: KeyEvent,
        vm: PaintViewModel,
        feedbackManager: StylusFeedbackManager,
    ): Boolean {
        if (!isSupportedXiaomiDevice) return false

        // Focus Pen Pro is buttonless and handles touch-film gestures via HyperOS PenEngine
        if (!vm.xiaomiPencilModel.hasPhysicalButtons) {
            if (onDispatchKeyEventMethod != null) {
                try {
                    val handled = onDispatchKeyEventMethod?.invoke(null, event) as? Boolean ?: false
                    if (handled) return true
                } catch (_: Throwable) {}
            }
            return false
        }

        val keyCode = event.keyCode
        val now = SystemClock.uptimeMillis()

        // 1. Focus button (Focus Pen only)
        if (keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY ||
            keyCode == KeyEvent.KEYCODE_BUTTON_3 ||
            keyCode == KEY_FOCUS_KEYCODE_1 ||
            keyCode == KEY_FOCUS_KEYCODE_2 ||
            keyCode == KEY_FOCUS_KEYCODE_3 ||
            keyCode == KEY_FOCUS_KEYCODE_4 ||
            keyCode == KEY_FOCUS_KEYCODE_5 ||
            keyCode == KEY_FOCUS_KEYCODE_6 ||
            keyCode == KeyEvent.KEYCODE_CAMERA ||
            keyCode == KeyEvent.KEYCODE_BUTTON_C ||
            keyCode == KeyEvent.KEYCODE_BUTTON_Z ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        ) {
            val actionId = vm.xiaomiFocusButtonAction
            if (actionId.equals("none", ignoreCase = true)) {
                return false
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                val action = StylusAction.fromActionId(actionId)
                if (action != StylusAction.NONE) {
                    feedbackManager.triggerActionConfirmation()
                    vm.executeStylusAction(action)
                    return true
                }
                return false
            }
            return true
        }

        // 2. Primary button (Writing key / 主键 / 书写键 / 靠近笔尖下侧键: KEYCODE_PAGE_DOWN / 309)
        if (keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY ||
            keyCode == KeyEvent.KEYCODE_BUTTON_1 ||
            keyCode == 309 ||
            keyCode == KeyEvent.KEYCODE_PAGE_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN
        ) {
            val singleActionId = vm.xiaomiPrimaryButtonAction
            val doubleActionId = vm.xiaomiDoubleTapAction
            val hasDoubleTap = vm.xiaomiPencilModel.hasDoubleTap && !doubleActionId.equals("none", ignoreCase = true)
            val isEraseCandidate = vm.xiaomiSideButtonErase

            // 若既不需要按住擦除，也未配置主键单击和双击，且当前不在擦除状态，不拦截事件，透传至应用快捷键系统
            if (!isEraseCandidate && !hasDoubleTap && singleActionId.equals("none", ignoreCase = true) && !isPrimaryCurrentlyDown) {
                return false
            }

            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    if (now - lastPrimaryReleaseTime < MIN_CLICK_INTERVAL_MS) {
                        return true
                    }
                    isPrimaryCurrentlyDown = true
                    lastPrimaryDownTime = now
                    strokeHappenedSincePrimaryPress = false
                }
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                val pressDuration = now - lastPrimaryDownTime
                if (now - lastPrimaryReleaseTime < MIN_CLICK_INTERVAL_MS) {
                    isPrimaryCurrentlyDown = false
                    return true
                }
                isPrimaryCurrentlyDown = false
                lastPrimaryReleaseTime = now

                // 按住书写键画过笔画或长按超过阈值: 抑制单击动作
                if ((strokeHappenedSincePrimaryPress && vm.xiaomiSideButtonErase) || pressDuration > LONG_PRESS_TIMEOUT_MS) {
                    strokeHappenedSincePrimaryPress = false
                    pendingPrimarySingleClickRunnable?.let { handler.removeCallbacks(it) }
                    pendingPrimarySingleClickRunnable = null
                    primaryClickCount = 0
                    return true
                }
                strokeHappenedSincePrimaryPress = false

                return dispatchPrimaryClick(now, vm, feedbackManager)
            }
            return true
        }

        // 3. Secondary button (Screenshot key / 副键 / 截图键 / 靠上侧键: KEYCODE_PAGE_UP / 308)
        if (keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY ||
            keyCode == KeyEvent.KEYCODE_BUTTON_2 ||
            keyCode == 308 ||
            keyCode == KeyEvent.KEYCODE_PAGE_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_UP
        ) {
            val actionId = vm.xiaomiSecondaryButtonAction
            val isEraseCandidate = vm.xiaomiSideButtonErase

            // 若既不需要按住擦除，也未配置副键动作，且当前不在擦除状态，不拦截事件，透传至应用快捷键系统
            if (!isEraseCandidate && actionId.equals("none", ignoreCase = true) && !isSecondaryCurrentlyDown) {
                return false
            }

            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    if (now - lastSecondaryReleaseTime < MIN_CLICK_INTERVAL_MS) {
                        return true
                    }
                    isSecondaryCurrentlyDown = true
                    lastSecondaryDownTime = now
                    strokeHappenedSinceSecondaryPress = false
                }
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                val pressDuration = now - lastSecondaryDownTime
                if (now - lastSecondaryReleaseTime < MIN_CLICK_INTERVAL_MS) {
                    isSecondaryCurrentlyDown = false
                    return true
                }
                isSecondaryCurrentlyDown = false
                lastSecondaryReleaseTime = now

                // 按住副键画过笔画或长按超过阈值: 抑制单击动作
                if ((strokeHappenedSinceSecondaryPress && vm.xiaomiSideButtonErase) || pressDuration > LONG_PRESS_TIMEOUT_MS) {
                    strokeHappenedSinceSecondaryPress = false
                    return true
                }
                strokeHappenedSinceSecondaryPress = false

                if (!actionId.equals("none", ignoreCase = true)) {
                    val action = StylusAction.fromActionId(actionId)
                    if (action != StylusAction.NONE) {
                        feedbackManager.triggerActionConfirmation()
                        vm.executeStylusAction(action)
                        return true
                    }
                }
                return false
            }
            return true
        }

        return false
    }

    override fun onStylusMotionEvent(
        event: MotionEvent,
        vm: PaintViewModel,
        feedbackManager: StylusFeedbackManager,
    ): Boolean {
        if (!isSupportedXiaomiDevice) return false
        val buttonState = event.buttonState
        val isPrimaryBtnDown = (buttonState and MotionEvent.BUTTON_PRIMARY) != 0 ||
                (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        val isSecondaryBtnDown = (buttonState and MotionEvent.BUTTON_SECONDARY) != 0 ||
                (buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0

        val now = SystemClock.uptimeMillis()

        // 1. Primary button tracking (Writing key)
        if (isPrimaryBtnDown && !isPrimaryCurrentlyDown) {
            if (now - lastPrimaryReleaseTime >= MIN_CLICK_INTERVAL_MS) {
                isPrimaryCurrentlyDown = true
                lastPrimaryDownTime = now
                strokeHappenedSincePrimaryPress = false
            }
        } else if (isPrimaryBtnDown && isPrimaryCurrentlyDown) {
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                strokeHappenedSincePrimaryPress = true
            }
        } else if (!isPrimaryBtnDown && isPrimaryCurrentlyDown) {
            val pressDuration = now - lastPrimaryDownTime
            isPrimaryCurrentlyDown = false
            if (now - lastPrimaryReleaseTime >= MIN_CLICK_INTERVAL_MS) {
                lastPrimaryReleaseTime = now
                if ((strokeHappenedSincePrimaryPress && vm.xiaomiSideButtonErase) || pressDuration > LONG_PRESS_TIMEOUT_MS) {
                    strokeHappenedSincePrimaryPress = false
                    pendingPrimarySingleClickRunnable?.let { handler.removeCallbacks(it) }
                    pendingPrimarySingleClickRunnable = null
                    primaryClickCount = 0
                } else {
                    strokeHappenedSincePrimaryPress = false
                    dispatchPrimaryClick(now, vm, feedbackManager)
                }
            }
        }

        // 2. Secondary button tracking (Screenshot key)
        if (isSecondaryBtnDown && !isSecondaryCurrentlyDown) {
            if (now - lastSecondaryReleaseTime >= MIN_CLICK_INTERVAL_MS) {
                isSecondaryCurrentlyDown = true
                lastSecondaryDownTime = now
                strokeHappenedSinceSecondaryPress = false
            }
        } else if (isSecondaryBtnDown && isSecondaryCurrentlyDown) {
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                strokeHappenedSinceSecondaryPress = true
            }
        } else if (!isSecondaryBtnDown && isSecondaryCurrentlyDown) {
            val pressDuration = now - lastSecondaryDownTime
            isSecondaryCurrentlyDown = false
            if (now - lastSecondaryReleaseTime >= MIN_CLICK_INTERVAL_MS) {
                lastSecondaryReleaseTime = now
                if ((strokeHappenedSinceSecondaryPress && vm.xiaomiSideButtonErase) || pressDuration > LONG_PRESS_TIMEOUT_MS) {
                    strokeHappenedSinceSecondaryPress = false
                } else {
                    strokeHappenedSinceSecondaryPress = false
                    val actionId = vm.xiaomiSecondaryButtonAction
                    if (!actionId.equals("none", ignoreCase = true)) {
                        val action = StylusAction.fromActionId(actionId)
                        if (action != StylusAction.NONE) {
                            feedbackManager.triggerActionConfirmation()
                            vm.executeStylusAction(action)
                        }
                    }
                }
            }
        }

        // 3. Mark stroke happened if button is down while drawing
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            if (isPrimaryCurrentlyDown) strokeHappenedSincePrimaryPress = true
            if (isSecondaryCurrentlyDown) strokeHappenedSinceSecondaryPress = true
        }

        return false
    }

    private fun dispatchPrimaryClick(
        now: Long,
        vm: PaintViewModel,
        feedbackManager: StylusFeedbackManager,
    ): Boolean {
        val singleActionId = vm.xiaomiPrimaryButtonAction
        val doubleActionId = vm.xiaomiDoubleTapAction
        val hasDoubleTap = vm.xiaomiPencilModel.hasDoubleTap && !doubleActionId.equals("none", ignoreCase = true)

        if (!hasDoubleTap) {
            if (!singleActionId.equals("none", ignoreCase = true)) {
                val action = StylusAction.fromActionId(singleActionId)
                if (action != StylusAction.NONE) {
                    feedbackManager.triggerActionConfirmation()
                    vm.executeStylusAction(action)
                    return true
                }
            }
            return false
        }

        primaryClickCount++
        if (primaryClickCount == 1) {
            val singleRunnable = Runnable {
                primaryClickCount = 0
                if (!singleActionId.equals("none", ignoreCase = true)) {
                    val action = StylusAction.fromActionId(singleActionId)
                    if (action != StylusAction.NONE) {
                        feedbackManager.triggerActionConfirmation()
                        vm.executeStylusAction(action)
                    }
                }
            }
            pendingPrimarySingleClickRunnable = singleRunnable
            handler.postDelayed(singleRunnable, DOUBLE_CLICK_TIMEOUT_MS)
            return true
        } else if (primaryClickCount >= 2) {
            pendingPrimarySingleClickRunnable?.let { handler.removeCallbacks(it) }
            pendingPrimarySingleClickRunnable = null
            primaryClickCount = 0
            val action = StylusAction.fromActionId(doubleActionId)
            if (action != StylusAction.NONE) {
                feedbackManager.triggerActionConfirmation()
                vm.executeStylusAction(action)
                return true
            }
        }
        return false
    }

    override fun onStylusHoverExited(vm: PaintViewModel, feedbackManager: StylusFeedbackManager) {
        // 离开悬停区域时仅冲销按键状态与挂起的双击任务，绝不派发单击动作，避免悬停移出时误触发动作
        if (isPrimaryCurrentlyDown) {
            isPrimaryCurrentlyDown = false
            pendingPrimarySingleClickRunnable?.let { handler.removeCallbacks(it) }
            pendingPrimarySingleClickRunnable = null
            primaryClickCount = 0
        }
        if (isSecondaryCurrentlyDown) {
            isSecondaryCurrentlyDown = false
        }
        strokeHappenedSincePrimaryPress = false
        strokeHappenedSinceSecondaryPress = false
    }

    override fun release() {
        pendingPrimarySingleClickRunnable?.let { handler.removeCallbacks(it) }
        pendingPrimarySingleClickRunnable = null
        destroyPenEngine()
    }
}
