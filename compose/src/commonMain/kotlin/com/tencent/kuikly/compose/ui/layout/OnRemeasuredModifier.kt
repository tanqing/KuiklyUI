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

package com.tencent.kuikly.compose.ui.layout

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.internal.JvmDefaultWithCompatibility
import com.tencent.kuikly.compose.ui.node.LayoutAwareModifierNode
import com.tencent.kuikly.compose.ui.node.ModifierNodeElement
import com.tencent.kuikly.compose.ui.platform.InspectorInfo
import com.tencent.kuikly.compose.ui.unit.IntSize

/**
 * Invoked with the size of the modified Compose UI element when the element is first measured or
 * when the size of the element changes.
 *
 * There are no guarantees `onSizeChanged` will not be re-invoked with the same size.
 *
 * Using the `onSizeChanged` size value in a [MutableState] to update layout causes the new size
 * value to be read and the layout to be recomposed in the succeeding frame, resulting in a one
 * frame lag.
 *
 * You can use `onSizeChanged` to affect drawing operations. Use [Layout] or [SubcomposeLayout] to
 * enable the size of one component to affect the size of another.
 *
 * Example usage:
 * @sample androidx.compose.ui.samples.OnSizeChangedSample
 */
@Stable
fun Modifier.onSizeChanged(
    onSizeChanged: (IntSize) -> Unit
) = this.then(OnSizeChangedModifier(onSizeChanged = onSizeChanged))

private class OnSizeChangedModifier(
    private val onSizeChanged: (IntSize) -> Unit
) : ModifierNodeElement<OnSizeChangedNode>() {
    override fun create(): OnSizeChangedNode = OnSizeChangedNode(onSizeChanged)

    override fun update(node: OnSizeChangedNode) {
        node.update(onSizeChanged)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OnSizeChangedModifier) return false

        return onSizeChanged === other.onSizeChanged
    }

    override fun hashCode(): Int {
        return onSizeChanged.hashCode()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "onSizeChanged"
        properties["onSizeChanged"] = onSizeChanged
    }
}

private class OnSizeChangedNode(
    private var onSizeChanged: (IntSize) -> Unit
) : Modifier.Node(), LayoutAwareModifierNode {
    // When onSizeChanged changes, we want to invalidate so onRemeasured is called again
    override val shouldAutoInvalidate: Boolean = true
    private var previousSize = IntSize(Int.MIN_VALUE, Int.MIN_VALUE)

    fun update(onSizeChanged: (IntSize) -> Unit) {
        this.onSizeChanged = onSizeChanged
        // Reset the previous size, so when onSizeChanged changes the new lambda gets invoked,
        // matching previous behavior
        previousSize = IntSize(Int.MIN_VALUE, Int.MIN_VALUE)
    }

    override fun onRemeasured(size: IntSize) {
        if (previousSize != size) {
            onSizeChanged(size)
            previousSize = size
        }
    }
}

/**
 * A modifier whose [onRemeasured] is called when the layout content is remeasured. The
 * most common usage is [onSizeChanged].
 *
 * Example usage:
 * @sample androidx.compose.ui.samples.OnSizeChangedSample
 */
@JvmDefaultWithCompatibility
interface OnRemeasuredModifier : Modifier.Element {
    /**
     * Called after a layout's contents have been remeasured.
     */
    fun onRemeasured(size: IntSize)
}
