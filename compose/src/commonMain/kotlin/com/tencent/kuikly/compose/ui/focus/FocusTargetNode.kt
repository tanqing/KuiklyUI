/*
 * Copyright 2023 The Android Open Source Project
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

package com.tencent.kuikly.compose.ui.focus

import com.tencent.kuikly.compose.ui.ExperimentalComposeUiApi
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.focus.FocusDirection.Companion.Exit
import com.tencent.kuikly.compose.ui.focus.FocusRequester.Companion.Default
import com.tencent.kuikly.compose.ui.focus.FocusStateImpl.Active
import com.tencent.kuikly.compose.ui.focus.FocusStateImpl.ActiveParent
import com.tencent.kuikly.compose.ui.focus.FocusStateImpl.Captured
import com.tencent.kuikly.compose.ui.focus.FocusStateImpl.Inactive
import com.tencent.kuikly.compose.ui.internal.checkPreconditionNotNull
import com.tencent.kuikly.compose.ui.layout.BeyondBoundsLayout
import com.tencent.kuikly.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import com.tencent.kuikly.compose.ui.modifier.ModifierLocalModifierNode
import com.tencent.kuikly.compose.ui.node.CompositionLocalConsumerModifierNode
import com.tencent.kuikly.compose.ui.node.ModifierNodeElement
import com.tencent.kuikly.compose.ui.node.Nodes
import com.tencent.kuikly.compose.ui.node.ObserverModifierNode
import com.tencent.kuikly.compose.ui.node.observeReads
import com.tencent.kuikly.compose.ui.node.requireOwner
import com.tencent.kuikly.compose.ui.node.visitAncestors
import com.tencent.kuikly.compose.ui.node.visitSelfAndAncestors
import com.tencent.kuikly.compose.ui.node.visitSubtreeIf
import com.tencent.kuikly.compose.ui.platform.InspectorInfo

internal class FocusTargetNode :
    CompositionLocalConsumerModifierNode,
    FocusTargetModifierNode,
    ObserverModifierNode,
    ModifierLocalModifierNode,
    Modifier.Node() {

    private var isProcessingCustomExit = false
    private var isProcessingCustomEnter = false

    // During a transaction, changes to the state are stored as uncommitted focus state. At the
    // end of the transaction, this state is stored as committed focus state.
    private var committedFocusState: FocusStateImpl? = null

    override val shouldAutoInvalidate = false

    @OptIn(ExperimentalComposeUiApi::class)
    override var focusState: FocusStateImpl
        get() = focusTransactionManager?.run { uncommittedFocusState }
            ?: committedFocusState
            ?: Inactive
        set(value) {
            with(requireTransactionManager()) {
                uncommittedFocusState = value
            }
        }

    var previouslyFocusedChildHash: Int = 0

    val beyondBoundsLayoutParent: BeyondBoundsLayout?
        get() = ModifierLocalBeyondBoundsLayout.current

    override fun onObservedReadsChanged() {
        val previousFocusState = focusState
        invalidateFocus()
        if (previousFocusState != focusState) refreshFocusEventNodes()
    }

    /**
     * Clears focus if this focus target has it.
     */
    override fun onDetach() {
        //  Note: this is called after onEndApplyChanges, so we can't schedule any nodes for
        //  invalidation here. If we do, they will be run on the next onEndApplyChanges.
        when (focusState) {
            // Clear focus from the current FocusTarget.
            // This currently clears focus from the entire hierarchy, but we can change the
            // implementation so that focus is sent to the immediate focus parent.
            Active, Captured -> {
                requireOwner().focusOwner.clearFocus(
                    force = true,
                    refreshFocusEvents = true,
                    clearOwnerFocus = false,
                    focusDirection = @OptIn(ExperimentalComposeUiApi::class) Exit
                )
                // We don't clear the owner's focus yet, because this could trigger an initial
                // focus scenario after the focus is cleared. Instead, we schedule invalidation
                // after onApplyChanges. The FocusInvalidationManager contains the invalidation
                // logic and calls clearFocus() on the owner after all the nodes in the hierarchy
                // are invalidated.
                invalidateFocusTarget()
            }
            // This node might be reused, so reset the state to Inactive.
            ActiveParent -> requireTransactionManager().withNewTransaction { focusState = Inactive }
            Inactive -> {}
        }
        // This node might be reused, so we reset its state.
        committedFocusState = null
    }

    /**
     * Visits parent [FocusPropertiesModifierNode]s and runs
     * [FocusPropertiesModifierNode.applyFocusProperties] on each parent.
     * This effectively collects an aggregated focus state.
     */
    internal fun fetchFocusProperties(): FocusProperties {
        val properties = FocusPropertiesImpl()
        visitSelfAndAncestors(Nodes.FocusProperties, untilType = Nodes.FocusTarget) {
            it.applyFocusProperties(properties)
        }
        return properties
    }

    /**
     * Fetch custom enter destination associated with this [focusTarget].
     *
     * Custom focus enter properties are specified as a lambda. If the user runs code in this
     * lambda that triggers a focus search, or some other focus change that causes focus to leave
     * the sub-hierarchy associated with this node, we could end up in a loop as that operation
     * will trigger another invocation of the lambda associated with the focus exit property.
     * This function prevents that re-entrant scenario by ensuring there is only one concurrent
     * invocation of this lambda.
     */
    internal inline fun fetchCustomEnter(
        focusDirection: FocusDirection,
        block: (FocusRequester) -> Unit
    ) {
        if (!isProcessingCustomEnter) {
            isProcessingCustomEnter = true
            try {
                @OptIn(ExperimentalComposeUiApi::class)
                fetchFocusProperties().enter(focusDirection).also {
                    if (it !== Default) block(it)
                }
            } finally {
                isProcessingCustomEnter = false
            }
        }
    }

    /**
     * Fetch custom exit destination associated with this [focusTarget].
     *
     * Custom focus exit properties are specified as a lambda. If the user runs code in this
     * lambda that triggers a focus search, or some other focus change that causes focus to leave
     * the sub-hierarchy associated with this node, we could end up in a loop as that operation
     * will trigger another invocation of the lambda associated with the focus exit property.
     * This function prevents that re-entrant scenario by ensuring there is only one concurrent
     * invocation of this lambda.
     */
    internal inline fun fetchCustomExit(
        focusDirection: FocusDirection,
        block: (FocusRequester) -> Unit
    ) {
        if (!isProcessingCustomExit) {
            isProcessingCustomExit = true
            try {
                @OptIn(ExperimentalComposeUiApi::class)
                fetchFocusProperties().exit(focusDirection).also {
                    if (it !== Default) block(it)
                }
            } finally {
                isProcessingCustomExit = false
            }
        }
    }

    internal fun commitFocusState() {
        with(requireTransactionManager()) {
            committedFocusState = checkPreconditionNotNull(uncommittedFocusState) {
                "committing a node that was not updated in the current transaction"
            }
        }
    }

    internal fun invalidateFocus() {
        if (committedFocusState == null) initializeFocusState()
        when (focusState) {
            // Clear focus from the current FocusTarget.
            // This currently clears focus from the entire hierarchy, but we can change the
            // implementation so that focus is sent to the immediate focus parent.
            Active, Captured -> {
                lateinit var focusProperties: FocusProperties
                observeReads {
                    focusProperties = fetchFocusProperties()
                }
                if (!focusProperties.canFocus) {
                    requireOwner().focusOwner.clearFocus(force = true)
                }
            }

            ActiveParent, Inactive -> {}
        }
    }

    internal object FocusTargetElement : ModifierNodeElement<FocusTargetNode>() {
        override fun create() = FocusTargetNode()

        override fun update(node: FocusTargetNode) {}

        override fun InspectorInfo.inspectableProperties() {
            name = "focusTarget"
        }

        override fun hashCode() = "focusTarget".hashCode()
        override fun equals(other: Any?) = other === this
    }

    private fun initializeFocusState() {

        fun FocusTargetNode.isInitialized(): Boolean = committedFocusState != null

        fun isInActiveSubTree(): Boolean {
            visitAncestors(Nodes.FocusTarget) {
                if (!it.isInitialized()) return@visitAncestors

                return when (it.focusState) {
                    ActiveParent -> true
                    Active, Captured, Inactive -> false
                }
            }
            return false
        }

        fun hasActiveChild(): Boolean {
            visitSubtreeIf(Nodes.FocusTarget) {
                if (!it.isInitialized()) return@visitSubtreeIf true

                return when (it.focusState) {
                    Active, ActiveParent, Captured -> true
                    Inactive -> false
                }
            }
            return false
        }

        check(!isInitialized()) { "Re-initializing focus target node." }

        requireTransactionManager().withNewTransaction {
            // Note: hasActiveChild() is expensive since it searches the entire subtree. So we only
            // do this if we are part of the active subtree.
            focusState = if (isInActiveSubTree() && hasActiveChild()) ActiveParent else Inactive
        }
    }
}

internal fun FocusTargetNode.requireTransactionManager(): FocusTransactionManager {
    return requireOwner().focusOwner.focusTransactionManager
}

private val FocusTargetNode.focusTransactionManager: FocusTransactionManager?
    get() = node.coordinator?.layoutNode?.owner?.focusOwner?.focusTransactionManager

internal fun FocusTargetNode.invalidateFocusTarget() {
    requireOwner().focusOwner.scheduleInvalidation(this)
}
