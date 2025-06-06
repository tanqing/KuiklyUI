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

package com.tencent.kuikly.compose.ui.scene

import com.tencent.kuikly.compose.ui.InternalComposeUiApi
import com.tencent.kuikly.compose.ui.platform.PlatformContext

/**
 * Interface representing the context for [ComposeScene].
 * It's used to share resources between multiple scenes and provide a way for platform interaction.
 */
@InternalComposeUiApi
interface ComposeSceneContext {
    /**
     * Represents the platform-specific context used for platform interaction in a [ComposeScene].
     */
    val platformContext: PlatformContext get() = PlatformContext.Empty

    companion object {
        /**
         * Represents an empty implementation of [ComposeSceneContext] and used to provide
         * a default value.
         */
        val Empty =
            object : ComposeSceneContext {
            }
    }
}
