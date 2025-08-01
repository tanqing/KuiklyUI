/*
 * Tencent is pleased to support the open source community by making KuiklyUI
 * available.
 * Copyright (C) 2025 Tencent. All rights reserved.
 * Licensed under the License of KuiklyUI;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://github.com/Tencent-TDS/KuiklyUI/blob/main/LICENSE
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#import <UIKit/UIKit.h>
#import "KuiklyRenderViewExportProtocol.h"

NS_ASSUME_NONNULL_BEGIN

/**
 * @brief This is a container view component exposed for Kotlin side to call
 */
@interface KRView : UIView<KuiklyRenderViewExportProtocol>

// The touch down callback for the view
@property (nonatomic, strong, nullable) KuiklyRenderCallback css_touchDown;

// The touch up callback for the view
@property (nonatomic, strong, nullable) KuiklyRenderCallback css_touchUp;

// The touch move callback for the view
@property (nonatomic, strong, nullable) KuiklyRenderCallback css_touchMove;

@end

NS_ASSUME_NONNULL_END
