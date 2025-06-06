/*
 * Copyright 2022 The Android Open Source Project
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

package com.tencent.kuikly.compose.ui.node

import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.drawscope.ContentDrawScope
import com.tencent.kuikly.compose.ui.node.DelegatableNode
import com.tencent.kuikly.compose.ui.node.Nodes
import com.tencent.kuikly.compose.ui.node.requireCoordinator
import com.tencent.kuikly.core.base.DeclarativeBaseView

/**
 * A [Modifier.Node] that draws into the space of the layout.
 *
 * This is the [com.tencent.kuikly.compose.ui.Modifier.Node] equivalent of
 * [com.tencent.kuikly.compose.ui.draw.DrawModifier]
 *
 * @sample com.tencent.kuikly.compose.ui.samples.DrawModifierNodeSample
 */
interface DrawModifierNode : DelegatableNode {
    fun ContentDrawScope.draw() { }
    fun onMeasureResultChanged() {}
    fun ContentDrawScope.draw(view: DeclarativeBaseView<*, *>?) {
        drawContent()
    }
}

/**
 * Invalidates this modifier's draw layer, ensuring that a draw pass will
 * be run on the next frame.
 */
fun DrawModifierNode.invalidateDraw() {
    if (node.isAttached) {
        requireCoordinator(Nodes.Any).invalidateLayer()
    }
}
