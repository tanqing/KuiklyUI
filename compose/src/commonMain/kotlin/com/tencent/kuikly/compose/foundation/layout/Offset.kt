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

package com.tencent.kuikly.compose.foundation.layout

import androidx.compose.runtime.Stable
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.layout.Measurable
import com.tencent.kuikly.compose.ui.layout.MeasureResult
import com.tencent.kuikly.compose.ui.layout.MeasureScope
import com.tencent.kuikly.compose.ui.node.LayoutModifierNode
import com.tencent.kuikly.compose.ui.node.ModifierNodeElement
import com.tencent.kuikly.compose.ui.platform.InspectorInfo
import com.tencent.kuikly.compose.ui.unit.Constraints
import com.tencent.kuikly.compose.ui.unit.Density
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.IntOffset
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * Offset the content by ([x] dp, [y] dp). The offsets can be positive as well as non-positive.
 * Applying an offset only changes the position of the content, without interfering with
 * its size measurement.
 *
 * This modifier will automatically adjust the horizontal offset according to the layout direction:
 * when the layout direction is LTR, positive [x] offsets will move the content to the right and
 * when the layout direction is RTL, positive [x] offsets will move the content to the left.
 * For a modifier that offsets without considering layout direction, see [absoluteOffset].
 *
 * @see absoluteOffset
 *
 * Example usage:
 * @sample androidx.compose.foundation.layout.samples.OffsetModifier
 */
@Stable
fun Modifier.offset(x: Dp = 0.dp, y: Dp = 0.dp) = this then OffsetElement(
    x = x,
    y = y,
    rtlAware = true,
    inspectorInfo = {
        name = "offset"
        properties["x"] = x
        properties["y"] = y
    }
)

/**
 * Offset the content by ([x] dp, [y] dp). The offsets can be positive as well as non-positive.
 * Applying an offset only changes the position of the content, without interfering with
 * its size measurement.
 *
 * This modifier will not consider layout direction when calculating the position of the content:
 * a positive [x] offset will always move the content to the right.
 * For a modifier that considers the layout direction when applying the offset, see [offset].
 *
 * @see offset
 *
 * Example usage:
 * @sample androidx.compose.foundation.layout.samples.AbsoluteOffsetModifier
 */
@Stable
fun Modifier.absoluteOffset(x: Dp = 0.dp, y: Dp = 0.dp) = this then OffsetElement(
    x = x,
    y = y,
    rtlAware = false,
    inspectorInfo = {
        name = "absoluteOffset"
        properties["x"] = x
        properties["y"] = y
    }
)

/**
 * Offset the content by [offset] px. The offsets can be positive as well as non-positive.
 * Applying an offset only changes the position of the content, without interfering with
 * its size measurement.
 *
 * This modifier is designed to be used for offsets that change, possibly due to user interactions.
 * It avoids recomposition when the offset is changing, and also adds a graphics layer that
 * prevents unnecessary redrawing of the context when the offset is changing.
 *
 * This modifier will automatically adjust the horizontal offset according to the layout direction:
 * when the LD is LTR, positive horizontal offsets will move the content to the right and
 * when the LD is RTL, positive horizontal offsets will move the content to the left.
 * For a modifier that offsets without considering layout direction, see [absoluteOffset].
 *
 * @see [absoluteOffset]
 *
 * Example usage:
 * @sample androidx.compose.foundation.layout.samples.OffsetPxModifier
 */
fun Modifier.offset(offset: Density.() -> IntOffset) = this then
    OffsetPxElement(
        offset = offset,
        rtlAware = true,
        inspectorInfo = {
            name = "offset"
            properties["offset"] = offset
        }
    )

/**
 * Offset the content by [offset] px. The offsets can be positive as well as non-positive.
 * Applying an offset only changes the position of the content, without interfering with
 * its size measurement.
 *
 * This modifier is designed to be used for offsets that change, possibly due to user interactions.
 * It avoids recomposition when the offset is changing, and also adds a graphics layer that
 * prevents unnecessary redrawing of the context when the offset is changing.
 *
 * This modifier will not consider layout direction when calculating the position of the content:
 * a positive horizontal offset will always move the content to the right.
 * For a modifier that considers layout direction when applying the offset, see [offset].
 *
 * @see offset
 *
 * Example usage:
 * @sample androidx.compose.foundation.layout.samples.AbsoluteOffsetPxModifier
 */
fun Modifier.absoluteOffset(
    offset: Density.() -> IntOffset
) = this then OffsetPxElement(
    offset = offset,
    rtlAware = false,
    inspectorInfo = {
        name = "absoluteOffset"
        properties["offset"] = offset
    }
)

private class OffsetElement(
    val x: Dp,
    val y: Dp,
    val rtlAware: Boolean,
    val inspectorInfo: InspectorInfo.() -> Unit
) : ModifierNodeElement<OffsetNode>() {
    override fun create(): OffsetNode {
        return OffsetNode(x, y, rtlAware)
    }

    override fun update(node: OffsetNode) {
        node.x = x
        node.y = y
        node.rtlAware = rtlAware
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifierElement = other as? OffsetElement ?: return false

        return x == otherModifierElement.x &&
            y == otherModifierElement.y &&
            rtlAware == otherModifierElement.rtlAware
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + rtlAware.hashCode()
        return result
    }

    override fun toString(): String = "OffsetModifierElement(x=$x, y=$y, rtlAware=$rtlAware)"

    override fun InspectorInfo.inspectableProperties() { inspectorInfo() }
}

private class OffsetNode(
    var x: Dp,
    var y: Dp,
    var rtlAware: Boolean
) : LayoutModifierNode, Modifier.Node() {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            if (rtlAware) {
                placeable.placeRelative(x.roundToPx(), y.roundToPx())
            } else {
                placeable.place(x.roundToPx(), y.roundToPx())
            }
        }
    }
}

private class OffsetPxElement(
    val offset: Density.() -> IntOffset,
    val rtlAware: Boolean,
    val inspectorInfo: InspectorInfo.() -> Unit
) : ModifierNodeElement<OffsetPxNode>() {
    override fun create(): OffsetPxNode {
        return OffsetPxNode(offset, rtlAware)
    }

    override fun update(node: OffsetPxNode) {
        node.offset = offset
        node.rtlAware = rtlAware
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifier = other as? OffsetPxElement ?: return false

        return offset === otherModifier.offset &&
            rtlAware == otherModifier.rtlAware
    }

    override fun toString(): String = "OffsetPxModifier(offset=$offset, rtlAware=$rtlAware)"

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + rtlAware.hashCode()
        return result
    }

    override fun InspectorInfo.inspectableProperties() {
        inspectorInfo()
    }
}

private class OffsetPxNode(
    var offset: Density.() -> IntOffset,
    var rtlAware: Boolean
) : LayoutModifierNode, Modifier.Node() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            val offsetValue = offset()
            if (rtlAware) {
                placeable.placeRelativeWithLayer(offsetValue.x, offsetValue.y)
            } else {
                placeable.placeWithLayer(offsetValue.x, offsetValue.y)
            }
        }
    }
}
