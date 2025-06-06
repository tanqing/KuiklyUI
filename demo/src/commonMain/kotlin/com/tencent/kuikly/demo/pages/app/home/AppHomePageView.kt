/*
 * Tencent is pleased to support the open source community by making KuiklyUI
 * available.
 * Copyright (C) 2025 THL A29 Limited, a Tencent company. All rights reserved.
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

package com.tencent.kuikly.demo.pages.app.home

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.PageList
import com.tencent.kuikly.core.views.PageListView
import com.tencent.kuikly.core.views.ScrollParams
import com.tencent.kuikly.core.views.TabItem
import com.tencent.kuikly.core.views.Tabs
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.demo.pages.app.AppTabPage
import com.tencent.kuikly.demo.pages.app.model.AppFeedsType

internal class AppHomePageView: ComposeView<AppHomePageViewAttr, AppHomePageViewEvent>() {

    private var curIndex: Int by observable(0)
    private var scrollParams: ScrollParams? by observable(null)
    private var titles = listOf<String>("关注", "热门 ")
    private var pageListRef : ViewRef<PageListView<*, *>>? = null
    private var tabHeaderWidth by observable(300f)
    private lateinit var followViewRef: ViewRef<AppFeedListPageView>
    private lateinit var trendViewRef: ViewRef<AppTrendingPageView>

    override fun createEvent(): AppHomePageViewEvent {
        return AppHomePageViewEvent()
    }

    override fun createAttr(): AppHomePageViewAttr {
        return AppHomePageViewAttr()
    }

    fun tabsHeader(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(249, 249, 249, 1f))
            }
            Tabs {
                attr {
                    height(TAB_HEADER_HEIGHT)
                    width(ctx.tabHeaderWidth)
                    defaultInitIndex(ctx.curIndex)
                    alignSelfCenter()
                    backgroundColor(Color(249, 249, 249, 1f))
                    indicatorInTabItem {
                        View {
                            attr {
                                height(3f)
                                absolutePosition(left = 2f, right = 2f, bottom = 5f)
                                borderRadius(2f)
                                backgroundColor(Color.RED)
                            }
                        }
                    }
                    ctx.scrollParams?.also {
                        scrollParams(it)
                    }
                }
                event {
                    contentSizeChanged { width, _ ->
                        ctx.tabHeaderWidth = width
                    }
                }
                for (i in 0 until ctx.titles.size) {
                    TabItem { state ->
                        attr {
                            marginLeft(10f)
                            marginRight(10f)
                            allCenter()
//                            backgroundColor(Color.BLUE)
                        }
                        event {
                            click {
                                ctx.pageListRef?.view?.scrollToPageIndex(i, true)
                            }
                        }
                        Text {
                            attr {
                                text(ctx.titles[i])
                                fontSize(17f)
                                if (state.selected) {
                                    fontWeightBold()
                                    color(Color.BLACK)
                                } else {
                                    color(Color.GRAY)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        this.followViewRef.view?.loadFirstFeeds()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
            }
            ctx.tabsHeader().invoke(this)

            PageList {
                ref {
                    ctx.pageListRef = it
                }
                attr {
                    flexDirectionRow()
                    pageItemWidth(pagerData.pageViewWidth)
                    pageItemHeight(pagerData.pageViewHeight - pagerData.statusBarHeight - TAB_HEADER_HEIGHT - AppTabPage.TAB_BOTTOM_HEIGHT)
                    defaultPageIndex(ctx.curIndex)
                    showScrollerIndicator(false)
                }
                event {
                    scroll {
                        ctx.scrollParams = it
                    }
                    pageIndexDidChanged {
                        if ((it as JSONObject).optInt("index") == 1) {
                            ctx.trendViewRef.view?.loadFirstFeeds()
                        }
                    }
                }
                AppFeedListPage(AppFeedsType.Follow) {
                    ref {
                        ctx.followViewRef = it
                    }
                }
                AppTrendingPage {
                    ref {
                        ctx.trendViewRef = it
                    }
                }
            }
        }
    }

    companion object {
        const val TAB_HEADER_HEIGHT = 50f
    }
}

internal class AppHomePageViewAttr : ComposeAttr() {

}

internal class AppHomePageViewEvent : ComposeEvent() {
    
}

internal fun ViewContainer<*, *>.AppHomePage(init: AppHomePageView.() -> Unit) {
    addChild(AppHomePageView(), init)
}