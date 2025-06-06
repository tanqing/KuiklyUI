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

import com.tencent.kuikly.compose.ui.node.LayoutNode
import com.tencent.kuikly.compose.ui.unit.Constraints
import com.tencent.kuikly.compose.ui.unit.constrainHeight
import com.tencent.kuikly.compose.ui.unit.constrainWidth
import com.tencent.kuikly.compose.ui.util.fastForEach
import com.tencent.kuikly.compose.ui.util.fastMap

internal object RootMeasurePolicy : LayoutNode.NoIntrinsicsMeasurePolicy(
    "Undefined intrinsics block and it is required"
) {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        return when {
            measurables.isEmpty() -> {
                layout(constraints.minWidth, constraints.minHeight) {}
            }
            measurables.size == 1 -> {
                val placeable = measurables[0].measure(constraints)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelativeWithLayer(0, 0)
                }
            }
            else -> {
                val placeables = measurables.fastMap {
                    it.measure(constraints)
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeables.fastForEach { placeable ->
                        placeable.placeRelativeWithLayer(0, 0)
                    }
                }
            }
        }
    }
}
