/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.kuikly.compose.foundation.gestures

// Note, that there is a copy-paste version of this file (DragGestureDetectorCopy.kt), don't
// forget to change it too.
//
// We can't make *PointerSlop* functions public just yet because the new pointer API isn't ready.

// TODO(b/193549931): when the new pointer API will be ready we should make *PointerSlop*
//  functions public

import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.input.pointer.AwaitPointerEventScope
import com.tencent.kuikly.compose.ui.input.pointer.PointerEvent
import com.tencent.kuikly.compose.ui.input.pointer.PointerEventPass
import com.tencent.kuikly.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import com.tencent.kuikly.compose.ui.input.pointer.PointerInputScope
import com.tencent.kuikly.compose.ui.platform.ViewConfiguration
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.util.fastAll
import com.tencent.kuikly.compose.ui.util.fastAny
import com.tencent.kuikly.compose.ui.util.fastFirstOrNull
import com.tencent.kuikly.compose.ui.util.fastForEach
import com.tencent.kuikly.compose.ui.input.pointer.PointerId
import com.tencent.kuikly.compose.ui.input.pointer.PointerInputChange
import com.tencent.kuikly.compose.ui.input.pointer.PointerType
import com.tencent.kuikly.compose.ui.input.pointer.changedToUp
import com.tencent.kuikly.compose.ui.input.pointer.changedToUpIgnoreConsumed
import com.tencent.kuikly.compose.ui.input.pointer.isOutOfBounds
import com.tencent.kuikly.compose.ui.input.pointer.positionChange
import com.tencent.kuikly.compose.ui.input.pointer.positionChangeIgnoreConsumed
import com.tencent.kuikly.compose.ui.input.pointer.positionChangedIgnoreConsumed
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CancellationException

/**
 * Waits for drag motion to pass [touch slop][ViewConfiguration.touchSlop], using [pointerId] as
 * the pointer to examine. If [pointerId] is raised, another pointer from those that are down
 * will be chosen to lead the gesture, and if none are down, `null` is returned. If [pointerId]
 * is not down when [awaitTouchSlopOrCancellation] is called, then `null` is returned.

 * [onTouchSlopReached] is called after [ViewConfiguration.touchSlop] motion in the any direction
 * with the change that caused the motion beyond touch slop and the [Offset] beyond touch slop that
 * has passed. [onTouchSlopReached] should consume the position change if it accepts the motion.
 * If it does, then the method returns that [PointerInputChange]. If not, touch slop detection will
 * continue.
 *
 * @return The [PointerInputChange] that was consumed in [onTouchSlopReached] or `null` if all
 * pointers are raised before touch slop is detected or another gesture consumed the position
 * change.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.AwaitDragOrCancellationSample
 *
 * @see awaitHorizontalTouchSlopOrCancellation
 * @see awaitVerticalTouchSlopOrCancellation
 */
suspend fun AwaitPointerEventScope.awaitTouchSlopOrCancellation(
    pointerId: PointerId,
    onTouchSlopReached: (change: PointerInputChange, overSlop: Offset) -> Unit
): PointerInputChange? {
    return awaitPointerSlopOrCancellation(
        pointerId,
        PointerType.Touch,
        onPointerSlopReached = onTouchSlopReached,
        pointerDirectionConfig = BidirectionalPointerDirectionConfig,
    )
}

/**
 * Reads position change events for [pointerId] and calls [onDrag] for every change in
 * position. If [pointerId] is raised, a new pointer is chosen from those that are down and if
 * none exist, the method returns. This does not wait for touch slop.
 *
 * @return `true` if the drag completed normally or `false` if the drag motion was
 * canceled by another gesture detector consuming position change events.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.DragSample
 *
 * @see awaitTouchSlopOrCancellation
 * @see awaitDragOrCancellation
 * @see horizontalDrag
 * @see verticalDrag
 */
suspend fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): Boolean {
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrCancellation(pointer) ?: return false

        if (change.changedToUpIgnoreConsumed()) {
            return true
        }

        onDrag(change)
        pointer = change.id
    }
}

/**
 * Reads pointer input events until a drag is detected or all pointers are up. When the  final
 * pointer is raised, the up event is returned. When a drag event is detected, the
 * drag change will be returned. Note that if [pointerId] has been raised, another pointer
 * that is down will be used, if available, so the returned [PointerInputChange.id] may
 * differ from [pointerId]. If the position change in the any direction has been
 * consumed by the [PointerEventPass.Main] pass, then the drag is considered canceled and `null`
 * is returned.  If [pointerId] is not down when [awaitDragOrCancellation] is called, then
 * `null` is returned.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.AwaitDragOrCancellationSample
 *
 * @see awaitVerticalDragOrCancellation
 * @see awaitHorizontalDragOrCancellation
 * @see drag
 */
suspend fun AwaitPointerEventScope.awaitDragOrCancellation(
    pointerId: PointerId,
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the gesture is canceled
    }
    val change = awaitDragOrUp(pointerId) { it.positionChangedIgnoreConsumed() }
    return if (change?.isConsumed == false) change else null
}

/**
 * Gesture detector that waits for pointer down and touch slop in any direction and then
 * calls [onDrag] for each drag event. It follows the touch slop detection of
 * [awaitTouchSlopOrCancellation] but will consume the position change automatically
 * once the touch slop has been crossed.
 *
 * [onDragStart] called when the touch slop has been passed and includes an [Offset] representing
 * the last known pointer position relative to the containing element. The [Offset] can be outside
 * the actual bounds of the element itself meaning the numbers can be negative or larger than the
 * element bounds if the touch target is smaller than the
 * [ViewConfiguration.minimumTouchTargetSize].
 *
 * [onDragEnd] is called after all pointers are up and [onDragCancel] is called if another gesture
 * has consumed pointer input, canceling this gesture.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.DetectDragGesturesSample
 *
 * @see detectVerticalDragGestures
 * @see detectHorizontalDragGestures
 * @see detectDragGesturesAfterLongPress to detect gestures after long press
 */
suspend fun PointerInputScope.detectDragGestures(
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var drag: PointerInputChange?
        var overSlop = Offset.Zero
        do {
            drag = awaitPointerSlopOrCancellation(
                down.id,
                down.type,
                pointerDirectionConfig = BidirectionalPointerDirectionConfig
            ) { change, over ->
                change.consume()
                overSlop = over
            }
        } while (drag != null && !drag.isConsumed)
        if (drag != null) {
            onDragStart.invoke(drag.position)
            onDrag(drag, overSlop)
            if (
                !drag(drag.id) {
                    onDrag(it, it.positionChange())
                    it.consume()
                }
            ) {
                onDragCancel()
            } else {
                onDragEnd()
            }
        }
    }
}

/**
 * Gesture detector that waits for pointer down and long press, after which it calls [onDrag] for
 * each drag event.
 *
 * [onDragStart] called when a long press is detected and includes an [Offset] representing
 * the last known pointer position relative to the containing element. The [Offset] can be outside
 * the actual bounds of the element itself meaning the numbers can be negative or larger than the
 * element bounds if the touch target is smaller than the
 * [ViewConfiguration.minimumTouchTargetSize].
 *
 * [onDragEnd] is called after all pointers are up and [onDragCancel] is called if another gesture
 * has consumed pointer input, canceling this gesture. This function will automatically consume all
 * the position change after the long press.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.DetectDragWithLongPressGesturesSample
 *
 * @see detectVerticalDragGestures
 * @see detectHorizontalDragGestures
 * @see detectDragGestures
 */
suspend fun PointerInputScope.detectDragGesturesAfterLongPress(
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        try {
            val down = awaitFirstDown(requireUnconsumed = false)
            val drag = awaitLongPressOrCancellation(down.id)
            if (drag != null) {
                onDragStart.invoke(drag.position)

                if (
                    drag(drag.id) {
                        onDrag(it, it.positionChange())
                        it.consume()
                    }
                ) {
                    // consume up if we quit drag gracefully with the up
                    currentEvent.changes.fastForEach {
                        if (it.changedToUp()) it.consume()
                    }
                    onDragEnd()
                } else {
                    onDragCancel()
                }
            }
        } catch (c: CancellationException) {
            onDragCancel()
            throw c
        }
    }
}

/**
 * Waits for vertical drag motion to pass [touch slop][ViewConfiguration.touchSlop], using
 * [pointerId] as the pointer to examine. If [pointerId] is raised, another pointer from
 * those that are down will be chosen to lead the gesture, and if none are down, `null` is returned.
 * If [pointerId] is not down when [awaitVerticalTouchSlopOrCancellation] is called, then `null`
 * is returned.
 *
 * [onTouchSlopReached] is called after [ViewConfiguration.touchSlop] motion in the vertical
 * direction with the change that caused the motion beyond touch slop and the pixels beyond touch
 * slop. [onTouchSlopReached] should consume the position change if it accepts the motion.
 * If it does, then the method returns that [PointerInputChange]. If not, touch slop detection will
 * continue.
 *
 * @return The [PointerInputChange] that was consumed in [onTouchSlopReached] or `null` if all
 * pointers are raised before touch slop is detected or another gesture consumed the position
 * change.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.AwaitVerticalDragOrCancellationSample
 *
 * @see awaitHorizontalTouchSlopOrCancellation
 * @see awaitTouchSlopOrCancellation
 */
suspend fun AwaitPointerEventScope.awaitVerticalTouchSlopOrCancellation(
    pointerId: PointerId,
    onTouchSlopReached: (change: PointerInputChange, overSlop: Float) -> Unit
) = awaitPointerSlopOrCancellation(
    pointerId = pointerId,
    pointerType = PointerType.Touch,
    onPointerSlopReached = { change, overSlop -> onTouchSlopReached(change, overSlop.y) },
    pointerDirectionConfig = VerticalPointerDirectionConfig
)

internal suspend fun AwaitPointerEventScope.awaitVerticalPointerSlopOrCancellation(
    pointerId: PointerId,
    pointerType: PointerType,
    onTouchSlopReached: (change: PointerInputChange, overSlop: Float) -> Unit
) = awaitPointerSlopOrCancellation(
    pointerId = pointerId,
    pointerType = pointerType,
    onPointerSlopReached = { change, overSlop -> onTouchSlopReached(change, overSlop.y) },
    pointerDirectionConfig = VerticalPointerDirectionConfig
)

/**
 * Reads vertical position change events for [pointerId] and calls [onDrag] for every change in
 * position. If [pointerId] is raised, a new pointer is chosen from those that are down and if
 * none exist, the method returns. This does not wait for touch slop
 *
 * @return `true` if the vertical drag completed normally or `false` if the drag motion was
 * canceled by another gesture detector consuming position change events.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.VerticalDragSample
 *
 * @see awaitVerticalTouchSlopOrCancellation
 * @see awaitVerticalDragOrCancellation
 * @see horizontalDrag
 * @see drag
 */
suspend fun AwaitPointerEventScope.verticalDrag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): Boolean = drag(
    pointerId = pointerId,
    onDrag = onDrag,
    hasDragged = { it.positionChangeIgnoreConsumed().y != 0f },
    motionConsumed = { it.isConsumed }
) != null

/**
 * Reads pointer input events until a vertical drag is detected or all pointers are up. When the
 * final pointer is raised, the up event is returned. When a drag event is detected, the
 * drag change will be returned. Note that if [pointerId] has been raised, another pointer
 * that is down will be used, if available, so the returned [PointerInputChange.id] may
 * differ from [pointerId]. If the position change  has been consumed by the
 * [PointerEventPass.Main] pass, then the drag is considered canceled and `null` is returned. If
 * [pointerId] is not down when [awaitVerticalDragOrCancellation] is called, then `null` is
 * returned.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.AwaitVerticalDragOrCancellationSample
 *
 * @see awaitHorizontalDragOrCancellation
 * @see awaitDragOrCancellation
 * @see verticalDrag
 */
suspend fun AwaitPointerEventScope.awaitVerticalDragOrCancellation(
    pointerId: PointerId,
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the gesture is canceled
    }
    val change = awaitDragOrUp(pointerId) { it.positionChangeIgnoreConsumed().y != 0f }
    return if (change?.isConsumed == false) change else null
}

/**
 * Gesture detector that waits for pointer down and touch slop in the vertical direction and then
 * calls [onVerticalDrag] for each vertical drag event. It follows the touch slop detection of
 * [awaitVerticalTouchSlopOrCancellation], but will consume the position change automatically
 * once the touch slop has been crossed.
 *
 * [onDragStart] called when the touch slop has been passed and includes an [Offset] representing
 * the last known pointer position relative to the containing element. The [Offset] can be outside
 * the actual bounds of the element itself meaning the numbers can be negative or larger than the
 * element bounds if the touch target is smaller than the
 * [ViewConfiguration.minimumTouchTargetSize].
 *
 * [onDragEnd] is called after all pointers are up and [onDragCancel] is called if another gesture
 * has consumed pointer input, canceling this gesture.
 *
 * This gesture detector will coordinate with [detectHorizontalDragGestures] and
 * [awaitHorizontalTouchSlopOrCancellation] to ensure only vertical or horizontal dragging
 * is locked, but not both.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.DetectVerticalDragGesturesSample
 *
 * @see detectDragGestures
 * @see detectHorizontalDragGestures
 */
suspend fun PointerInputScope.detectVerticalDragGestures(
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onVerticalDrag: (change: PointerInputChange, dragAmount: Float) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var overSlop = 0f
        val drag = awaitVerticalPointerSlopOrCancellation(down.id, down.type) { change, over ->
            change.consume()
            overSlop = over
        }
        if (drag != null) {
            onDragStart.invoke(drag.position)
            onVerticalDrag.invoke(drag, overSlop)
            if (
                verticalDrag(drag.id) {
                    onVerticalDrag(it, it.positionChange().y)
                    it.consume()
                }
            ) {
                onDragEnd()
            } else {
                onDragCancel()
            }
        }
    }
}

/**
 * Waits for horizontal drag motion to pass [touch slop][ViewConfiguration.touchSlop], using
 * [pointerId] as the pointer to examine. If [pointerId] is raised, another pointer from
 * those that are down will be chosen to lead the gesture, and if none are down, `null` is returned.

 * [onTouchSlopReached] is called after [ViewConfiguration.touchSlop] motion in the horizontal
 * direction with the change that caused the motion beyond touch slop and the pixels beyond touch
 * slop. [onTouchSlopReached] should consume the position change if it accepts the motion.
 * If it does, then the method returns that [PointerInputChange]. If not, touch slop detection will
 * continue. If [pointerId] is not down when [awaitHorizontalTouchSlopOrCancellation] is called,
 * then `null` is returned.
 *
 * @return The [PointerInputChange] that was consumed in [onTouchSlopReached] or `null` if all
 * pointers are raised before touch slop is detected or another gesture consumed the position
 * change.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.AwaitHorizontalDragOrCancellationSample
 *
 * @see awaitVerticalTouchSlopOrCancellation
 * @see awaitTouchSlopOrCancellation
 */
suspend fun AwaitPointerEventScope.awaitHorizontalTouchSlopOrCancellation(
    pointerId: PointerId,
    onTouchSlopReached: (change: PointerInputChange, overSlop: Float) -> Unit
) = awaitPointerSlopOrCancellation(
    pointerId = pointerId,
    pointerType = PointerType.Touch,
    onPointerSlopReached = { change, overSlop -> onTouchSlopReached(change, overSlop.x) },
    pointerDirectionConfig = HorizontalPointerDirectionConfig
)

internal suspend fun AwaitPointerEventScope.awaitHorizontalPointerSlopOrCancellation(
    pointerId: PointerId,
    pointerType: PointerType,
    onPointerSlopReached: (change: PointerInputChange, overSlop: Float) -> Unit
) = awaitPointerSlopOrCancellation(
    pointerId = pointerId,
    pointerType = pointerType,
    onPointerSlopReached = { change, overSlop -> onPointerSlopReached(change, overSlop.x) },
    pointerDirectionConfig = HorizontalPointerDirectionConfig
)

/**
 * Reads horizontal position change events for [pointerId] and calls [onDrag] for every change in
 * position. If [pointerId] is raised, a new pointer is chosen from those that are down and if
 * none exist, the method returns. This does not wait for touch slop.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.HorizontalDragSample
 *
 * @see awaitHorizontalTouchSlopOrCancellation
 * @see awaitDragOrCancellation
 * @see verticalDrag
 * @see drag
 */
suspend fun AwaitPointerEventScope.horizontalDrag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): Boolean = drag(
    pointerId = pointerId,
    onDrag = onDrag,
    hasDragged = { it.positionChangeIgnoreConsumed().x != 0f },
    motionConsumed = { it.isConsumed }
) != null

/**
 * Reads pointer input events until a horizontal drag is detected or all pointers are up. When the
 * final pointer is raised, the up event is returned. When a drag event is detected, the
 * drag change will be returned. Note that if [pointerId] has been raised, another pointer
 * that is down will be used, if available, so the returned [PointerInputChange.id] may
 * differ from [pointerId]. If the position change has been consumed by the
 * [PointerEventPass.Main] pass, then the drag is considered canceled and `null` is returned. If
 * [pointerId] is not down when [awaitHorizontalDragOrCancellation] is called, then `null` is
 * returned.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.AwaitHorizontalDragOrCancellationSample
 *
 * @see horizontalDrag
 * @see awaitVerticalDragOrCancellation
 * @see awaitDragOrCancellation
 */
suspend fun AwaitPointerEventScope.awaitHorizontalDragOrCancellation(
    pointerId: PointerId,
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the gesture is canceled
    }
    val change = awaitDragOrUp(pointerId) { it.positionChangeIgnoreConsumed().x != 0f }
    return if (change?.isConsumed == false) change else null
}

/**
 * Gesture detector that waits for pointer down and touch slop in the horizontal direction and
 * then calls [onHorizontalDrag] for each horizontal drag event. It follows the touch slop
 * detection of [awaitHorizontalTouchSlopOrCancellation], but will consume the position change
 * automatically once the touch slop has been crossed.
 *
 * [onDragStart] called when the touch slop has been passed and includes an [Offset] representing
 * the last known pointer position relative to the containing element. The [Offset] can be outside
 * the actual bounds of the element itself meaning the numbers can be negative or larger than the
 * element bounds if the touch target is smaller than the
 * [ViewConfiguration.minimumTouchTargetSize].
 *
 * [onDragEnd] is called after all pointers are up and [onDragCancel] is called if another gesture
 * has consumed pointer input, canceling this gesture.
 *
 * This gesture detector will coordinate with [detectVerticalDragGestures] and
 * [awaitVerticalTouchSlopOrCancellation] to ensure only vertical or horizontal dragging is locked,
 * but not both.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.DetectHorizontalDragGesturesSample
 *
 * @see detectVerticalDragGestures
 * @see detectDragGestures
 */
suspend fun PointerInputScope.detectHorizontalDragGestures(
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onHorizontalDrag: (change: PointerInputChange, dragAmount: Float) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var overSlop = 0f
        val drag = awaitHorizontalPointerSlopOrCancellation(
            down.id,
            down.type
        ) { change, over ->
            change.consume()
            overSlop = over
        }
        if (drag != null) {
            onDragStart.invoke(drag.position)
            onHorizontalDrag(drag, overSlop)
            if (
                horizontalDrag(drag.id) {
                    onHorizontalDrag(it, it.positionChange().x)
                    it.consume()
                }
            ) {
                onDragEnd()
            } else {
                onDragCancel()
            }
        }
    }
}

/**
 * Continues to read drag events until all pointers are up or the drag event is canceled.
 * The initial pointer to use for driving the drag is [pointerId]. [hasDragged]
 * passes the result whether a change was detected from the drag function or not. [onDrag] is called
 * whenever the pointer moves and [hasDragged] returns non-zero.
 *
 * @return The last pointer input event change when gesture ended with all pointers up
 * and null when the gesture was canceled.
 */
internal suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
    hasDragged: (PointerInputChange) -> Boolean,
    motionConsumed: (PointerInputChange) -> Boolean
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the gesture is canceled
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer, hasDragged) ?: return null

        if (motionConsumed(change)) {
            return null
        }

        if (change.changedToUpIgnoreConsumed()) {
            return change
        }

        onDrag(change)
        pointer = change.id
    }
}

/**
 * Waits for a single drag in one axis, final pointer up, or all pointers are up.
 * When [pointerId] has lifted, another pointer that is down is chosen to be the finger
 * governing the drag. When the final pointer is lifted, that [PointerInputChange] is
 * returned. When a drag is detected, that [PointerInputChange] is returned. A drag is
 * only detected when [hasDragged] returns `true`.
 *
 * `null` is returned if there was an error in the pointer input stream and the pointer
 * that was down was dropped before the 'up' was received.
 */
private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId,
    hasDragged: (PointerInputChange) -> Boolean
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                // This is the last "up"
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else if (hasDragged(dragEvent)) {
            return dragEvent
        }
    }
}

/**
 * Waits for drag motion along one axis when [pointerDirectionConfig] is
 * [HorizontalPointerDirectionConfig] or [VerticalPointerDirectionConfig], and drag motion along
 * any axis when using [BidirectionalPointerDirectionConfig]. It passes [pointerId] as the pointer
 * to examine. If [pointerId] is raised, another pointer from those that are down will be chosen to
 * lead the gesture, and if none are down, `null` is returned. If [pointerId] is not down when
 * [awaitPointerSlopOrCancellation] is called, then `null` is returned.
 *
 * When pointer slop is detected, [onPointerSlopReached] is called with the change and the distance
 * beyond the pointer slop. [PointerDirectionConfig.calculateDeltaChange] should return the position
 * change in the direction of the drag axis. If [onPointerSlopReached] does not consume the
 * position change, pointer slop will not have been considered detected and the detection will
 * continue or, if it is consumed, the [PointerInputChange] that was consumed will be returned.
 *
 * This works with [awaitTouchSlopOrCancellation] for the other axis to ensure that only horizontal
 * or vertical dragging is done, but not both. It also works for dragging in two ways when using
 * [awaitTouchSlopOrCancellation]
 *
 * @return The [PointerInputChange] of the event that was consumed in [onPointerSlopReached] or
 * `null` if all pointers are raised or the position change was consumed by another gesture
 * detector.
 */
internal suspend inline fun AwaitPointerEventScope.awaitPointerSlopOrCancellation(
    pointerId: PointerId,
    pointerType: PointerType,
    pointerDirectionConfig: PointerDirectionConfig,
    onPointerSlopReached: (PointerInputChange, Offset) -> Unit,
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the gesture is canceled
    }
    val touchSlop = viewConfiguration.pointerSlop(pointerType)
    var pointer: PointerId = pointerId
    var totalPositionChange = Offset.Zero

    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.isConsumed) {
            return null
        } else if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                // This is the last "up"
                return null
            } else {
                pointer = otherDown.id
            }
        } else {
            val currentPosition = dragEvent.position
            val previousPosition = dragEvent.previousPosition

            val positionChange = currentPosition - previousPosition

            totalPositionChange += positionChange

            val inDirection = pointerDirectionConfig.calculateDeltaChange(
                totalPositionChange
            )

            if (inDirection < touchSlop) {
                // verify that nothing else consumed the drag event
                awaitPointerEvent(PointerEventPass.Final)
                if (dragEvent.isConsumed) {
                    return null
                }
            } else {
                val postSlopOffset = pointerDirectionConfig.calculatePostSlopOffset(
                    totalPositionChange,
                    touchSlop
                )

                onPointerSlopReached(
                    dragEvent,
                    postSlopOffset
                )
                if (dragEvent.isConsumed) {
                    return dragEvent
                } else {
                    totalPositionChange = Offset.Zero
                }
            }
        }
    }
}

/**
 * Configures the calculations to get the change amount depending on the dragging type.
 * [calculatePostSlopOffset] will return the post offset slop when the touchSlop is reached.
 */
internal interface PointerDirectionConfig {
    fun calculateDeltaChange(offset: Offset): Float
    fun calculatePostSlopOffset(
        totalPositionChange: Offset,
        touchSlop: Float
    ): Offset
}

/**
 * Used for monitoring changes on X axis.
 */
internal val HorizontalPointerDirectionConfig = object : PointerDirectionConfig {
    override fun calculateDeltaChange(offset: Offset): Float = abs(offset.x)

    override fun calculatePostSlopOffset(
        totalPositionChange: Offset,
        touchSlop: Float
    ): Offset {
        val finalMainPositionChange = totalPositionChange.x -
            (sign(totalPositionChange.x) * touchSlop)
        return Offset(finalMainPositionChange, totalPositionChange.y)
    }
}

/**
 * Used for monitoring changes on Y axis.
 */
internal val VerticalPointerDirectionConfig = object : PointerDirectionConfig {
    override fun calculateDeltaChange(offset: Offset): Float = abs(offset.y)

    override fun calculatePostSlopOffset(
        totalPositionChange: Offset,
        touchSlop: Float
    ): Offset {
        val finalMainPositionChange = totalPositionChange.y -
            (sign(totalPositionChange.y) * touchSlop)
        return Offset(totalPositionChange.x, finalMainPositionChange)
    }
}

/**
 * Used for monitoring changes on both X and Y axes.
 */
internal val BidirectionalPointerDirectionConfig = object : PointerDirectionConfig {
    override fun calculateDeltaChange(offset: Offset): Float = offset.getDistance()

    override fun calculatePostSlopOffset(
        totalPositionChange: Offset,
        touchSlop: Float
    ): Offset {
        val touchSlopOffset =
            totalPositionChange / calculateDeltaChange(totalPositionChange) * touchSlop
        return totalPositionChange - touchSlopOffset
    }
}

internal fun Orientation.toPointerDirectionConfig(): PointerDirectionConfig =
    if (this == Orientation.Vertical) VerticalPointerDirectionConfig
    else HorizontalPointerDirectionConfig

/**
 * Waits for a long press by examining [pointerId].
 *
 * If that [pointerId] is raised (that is, the user lifts their finger), but another
 * finger ([PointerId]) is down at that time, another pointer will be chosen as the lead for the
 * gesture, and if none are down, `null` is returned.
 *
 * @return The latest [PointerInputChange] associated with a long press or `null` if all pointers
 * are raised before a long press is detected or another gesture consumed the change.
 *
 * Example Usage:
 * @sample com.tencent.kuikly.compose.foundation.samples.AwaitLongPressOrCancellationSample
 */
suspend fun AwaitPointerEventScope.awaitLongPressOrCancellation(
    pointerId: PointerId
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the long press is cancelled.
    }

    val initialDown =
        currentEvent.changes.fastFirstOrNull { it.id == pointerId } ?: return null

    var longPress: PointerInputChange? = null
    var currentDown = initialDown
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    return try {
        // wait for first tap up or long press
        withTimeout(longPressTimeout) {
            var finished = false
            while (!finished) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.fastAll { it.changedToUpIgnoreConsumed() }) {
                    // All pointers are up
                    finished = true
                }

                if (
                    event.changes.fastAny {
                        it.isConsumed || it.isOutOfBounds(size, extendedTouchPadding)
                    }
                ) {
                    finished = true // Canceled
                }

                // Check for cancel by position consumption. We can look on the Final pass of
                // the existing pointer event because it comes after the Main pass we checked
                // above.
                val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
                if (consumeCheck.changes.fastAny { it.isConsumed }) {
                    finished = true
                }
                if (event.isPointerUp(currentDown.id)) {
                    val newPressed = event.changes.fastFirstOrNull { it.pressed }
                    if (newPressed != null) {
                        currentDown = newPressed
                        longPress = currentDown
                    } else {
                        // should technically never happen as we checked it above
                        finished = true
                    }
                // Pointer (id) stayed down.
                } else {
                    longPress = event.changes.fastFirstOrNull { it.id == currentDown.id }
                }
            }
        }
        null
    } catch (_: PointerEventTimeoutCancellationException) {
        longPress ?: initialDown
    }
}

private fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
    changes.fastFirstOrNull { it.id == pointerId }?.pressed != true

// This value was determined using experiments and common sense.
// We can't use zero slop, because some hypothetical desktop/mobile devices can send
// pointer events with a very high precision (but I haven't encountered any that send
// events with less than 1px precision)
private val mouseSlop = 0.125.dp
private val defaultTouchSlop = 18.dp // The default touch slop on Android devices
private val mouseToTouchSlopRatio = mouseSlop / defaultTouchSlop

// TODO(demin): consider this as part of ViewConfiguration class after we make *PointerSlop*
//  functions public (see the comment at the top of the file).
//  After it will be a public API, we should get rid of `touchSlop / 144` and return absolute
//  value 0.125.dp.toPx(). It is not possible right now, because we can't access density.
internal fun ViewConfiguration.pointerSlop(pointerType: PointerType): Float {
    return when (pointerType) {
        PointerType.Mouse -> touchSlop * mouseToTouchSlopRatio
        else -> touchSlop
    }
}
