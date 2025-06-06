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

@file:OptIn(ExperimentalComposeUiApi::class)

package com.tencent.kuikly.compose.ui.layout

import com.tencent.kuikly.compose.ui.ExperimentalComposeUiApi
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Rect
import com.tencent.kuikly.compose.ui.graphics.Matrix
import com.tencent.kuikly.compose.ui.internal.checkPrecondition
import com.tencent.kuikly.compose.ui.node.LookaheadDelegate
import com.tencent.kuikly.compose.ui.node.NodeCoordinator
import com.tencent.kuikly.compose.ui.unit.IntSize
import com.tencent.kuikly.compose.ui.unit.round
import com.tencent.kuikly.compose.ui.unit.toOffset

internal class LookaheadLayoutCoordinates(val lookaheadDelegate: LookaheadDelegate) :
    LayoutCoordinates {
    val coordinator: NodeCoordinator
        get() = lookaheadDelegate.coordinator

    override val size: IntSize
        get() = lookaheadDelegate.let { IntSize(it.width, it.height) }
    override val providedAlignmentLines: Set<AlignmentLine>
        get() = coordinator.providedAlignmentLines

    override val parentLayoutCoordinates: LayoutCoordinates?
        get() {
            checkPrecondition(isAttached) { NodeCoordinator.ExpectAttachedLayoutCoordinates }
            return coordinator.layoutNode.outerCoordinator.wrappedBy?.let {
                it.lookaheadDelegate?.coordinates
            }
        }
    override val parentCoordinates: LayoutCoordinates?
        get() {
            checkPrecondition(isAttached) { NodeCoordinator.ExpectAttachedLayoutCoordinates }
            return coordinator.wrappedBy?.lookaheadDelegate?.coordinates
        }

    override val isAttached: Boolean
        get() = coordinator.isAttached

    override val introducesMotionFrameOfReference: Boolean
        get() = lookaheadDelegate.isPlacedUnderMotionFrameOfReference

    private val lookaheadOffset: Offset
        get() = lookaheadDelegate.rootLookaheadDelegate.let {
            localPositionOf(it.coordinates, Offset.Zero) -
                coordinator.localPositionOf(it.coordinator, Offset.Zero)
        }

    override fun screenToLocal(relativeToScreen: Offset): Offset =
        coordinator.screenToLocal(relativeToScreen) + lookaheadOffset

    override fun localToScreen(relativeToLocal: Offset): Offset =
        coordinator.localToScreen(relativeToLocal + lookaheadOffset)

    override fun windowToLocal(relativeToWindow: Offset): Offset =
        coordinator.windowToLocal(relativeToWindow) + lookaheadOffset

    override fun localToWindow(relativeToLocal: Offset): Offset =
        coordinator.localToWindow(relativeToLocal + lookaheadOffset)

    override fun localToRoot(relativeToLocal: Offset): Offset =
        coordinator.localToRoot(relativeToLocal + lookaheadOffset)

    override fun localPositionOf(
        sourceCoordinates: LayoutCoordinates,
        relativeToSource: Offset
    ): Offset = localPositionOf(
        sourceCoordinates = sourceCoordinates,
        relativeToSource = relativeToSource,
        includeMotionFrameOfReference = true
    )

    override fun localPositionOf(
        sourceCoordinates: LayoutCoordinates,
        relativeToSource: Offset,
        includeMotionFrameOfReference: Boolean
    ): Offset {
        if (sourceCoordinates is LookaheadLayoutCoordinates) {
            val source = sourceCoordinates.lookaheadDelegate
            source.coordinator.onCoordinatesUsed()
            val commonAncestor = coordinator.findCommonAncestor(source.coordinator)

            return commonAncestor.lookaheadDelegate?.let { ancestor ->
                // Common ancestor is in lookahead
                val sourceInCommonAncestor = source.positionIn(
                    ancestor = ancestor,
                    excludingAgnosticOffset = !includeMotionFrameOfReference
                ) + relativeToSource.round()

                val lookaheadPosInAncestor = lookaheadDelegate.positionIn(
                    ancestor = ancestor,
                    excludingAgnosticOffset = !includeMotionFrameOfReference
                )

                (sourceInCommonAncestor - lookaheadPosInAncestor).toOffset()
            } ?: commonAncestor.let {
                // The two coordinates are in two separate LookaheadLayouts
                val sourceRoot = source.rootLookaheadDelegate

                val sourcePosition = source.positionIn(
                    ancestor = sourceRoot,
                    excludingAgnosticOffset = !includeMotionFrameOfReference
                ) + sourceRoot.position + relativeToSource.round()

                val rootDelegate = lookaheadDelegate.rootLookaheadDelegate
                val lookaheadPosition = lookaheadDelegate.positionIn(
                    ancestor = rootDelegate,
                    excludingAgnosticOffset = !includeMotionFrameOfReference
                ) + rootDelegate.position

                val relativePosition = (sourcePosition - lookaheadPosition).toOffset()

                rootDelegate.coordinator.wrappedBy!!.localPositionOf(
                    sourceCoordinates = sourceRoot.coordinator.wrappedBy!!,
                    relativeToSource = relativePosition,
                    includeMotionFrameOfReference = includeMotionFrameOfReference
                )
            }
        } else {
            val rootDelegate = lookaheadDelegate.rootLookaheadDelegate
            // This is a case of mixed coordinates where `this` is lookahead coords, and
            // `sourceCoordinates` isn't. Therefore we'll break this into two parts:
            // local position in lookahead coords space && local position in regular layout coords
            // space.
            val localLookaheadPos = localPositionOf(
                sourceCoordinates = rootDelegate.lookaheadLayoutCoordinates,
                relativeToSource = relativeToSource,
                includeMotionFrameOfReference = includeMotionFrameOfReference
            )

            val localPos = rootDelegate.coordinator.coordinates.localPositionOf(
                sourceCoordinates = sourceCoordinates,
                relativeToSource = Offset.Zero,
                includeMotionFrameOfReference = includeMotionFrameOfReference
            )
            return localLookaheadPos + localPos
        }
    }

    override fun localBoundingBoxOf(
        sourceCoordinates: LayoutCoordinates,
        clipBounds: Boolean
    ): Rect = coordinator.localBoundingBoxOf(sourceCoordinates, clipBounds)

    override fun transformFrom(sourceCoordinates: LayoutCoordinates, matrix: Matrix) {
        coordinator.transformFrom(sourceCoordinates, matrix)
    }

    override fun transformToScreen(matrix: Matrix) {
        coordinator.transformToScreen(matrix)
    }

    override fun get(alignmentLine: AlignmentLine): Int = lookaheadDelegate.get(alignmentLine)
}

internal val LookaheadDelegate.rootLookaheadDelegate: LookaheadDelegate
    get() {
        var root = layoutNode
        while (root.parent?.lookaheadRoot != null) {
            val lookaheadRoot = root.parent?.lookaheadRoot!!
            if (lookaheadRoot.isVirtualLookaheadRoot) {
                root = root.parent!!
            } else {
                root = root.parent!!.lookaheadRoot!!
            }
        }
        return root.outerCoordinator.lookaheadDelegate!!
    }
