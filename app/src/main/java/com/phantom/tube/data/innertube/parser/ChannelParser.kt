package com.phantom.tube.data.innertube.parser

import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import com.phantom.tube.data.model.ChannelProfile
import com.phantom.tube.data.model.ChannelSortChip
import com.phantom.tube.data.model.FeedResult
import com.phantom.tube.data.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject

object ChannelParser {

    fun parseChannelPage(jsonString: String, fallbackChannelId: String): ChannelProfile? {
        try {
            val root = JSONObject(jsonString)
            var channelId = fallbackChannelId
            var title = ""
            var handle = ""
            var avatarUrl = ""
            var bannerUrl = ""
            var subscriberCountText = ""
            var videoCountText = ""
            var description = ""
            var externalLinksText = ""
            val isVerified = false

            // 1. Parse Header
            val header = root.optJSONObject("header")
            val pHeader = header?.optJSONObject("pageHeaderRenderer")
            if (pHeader != null) {
                val content = pHeader.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")
                if (content != null) {
                    title = content.optJSONObject("title")?.optJSONObject("dynamicTextViewModel")
                        ?.optJSONObject("text")?.optString("content") ?: ""
                    if (title.isBlank()) {
                        title = pHeader.optString("pageTitle", "")
                    }

                    // Avatar
                    val avatarSources = content.optJSONObject("image")
                        ?.optJSONObject("decoratedAvatarViewModel")
                        ?.optJSONObject("avatar")
                        ?.optJSONObject("avatarViewModel")
                        ?.optJSONObject("image")
                        ?.optJSONArray("sources")
                    if (avatarSources != null && avatarSources.length() > 0) {
                        avatarUrl = avatarSources.optJSONObject(avatarSources.length() - 1)?.optString("url") ?: ""
                    }
                    if (avatarUrl.isBlank()) {
                        avatarUrl = InnerTubeHelpers.extractAvatar(content.optJSONObject("image") ?: content)
                    }
                    avatarUrl = InnerTubeHelpers.normalizeUrl(avatarUrl)

                    // Banner
                    val bannerSources = content.optJSONObject("banner")
                        ?.optJSONObject("imageBannerViewModel")
                        ?.optJSONObject("image")
                        ?.optJSONArray("sources")
                    if (bannerSources != null && bannerSources.length() > 0) {
                        bannerUrl = InnerTubeHelpers.normalizeUrl(bannerSources.optJSONObject(bannerSources.length() - 1)?.optString("url") ?: "")
                    }

                    // Metadata (handle, subscribers, videos)
                    val metadataRows = content.optJSONObject("metadata")
                        ?.optJSONObject("contentMetadataViewModel")
                        ?.optJSONArray("metadataRows") ?: JSONArray()
                    if (metadataRows.length() > 0) {
                        val row0 = metadataRows.optJSONObject(0)?.optJSONArray("metadataParts")
                        if (row0 != null && row0.length() > 0) {
                            handle = row0.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: ""
                        }
                    }
                    if (metadataRows.length() > 1) {
                        val row1 = metadataRows.optJSONObject(1)?.optJSONArray("metadataParts")
                        if (row1 != null) {
                            if (row1.length() > 0) {
                                subscriberCountText = row1.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: ""
                            }
                            if (row1.length() > 1) {
                                videoCountText = row1.optJSONObject(1)?.optJSONObject("text")?.optString("content") ?: ""
                            }
                        }
                    }

                    // Description
                    description = InnerTubeHelpers.parseRunsText(
                        content.optJSONObject("description")
                            ?.optJSONObject("descriptionPreviewViewModel")
                            ?.optJSONObject("description")
                    )

                    // External links / attribution
                    externalLinksText = InnerTubeHelpers.parseRunsText(
                        content.optJSONObject("attribution")
                            ?.optJSONObject("attributionViewModel")
                            ?.optJSONObject("text")
                    )
                }
            }

            // Fallback c4TabbedHeaderRenderer
            val c4Header = header?.optJSONObject("c4TabbedHeaderRenderer")
            if (c4Header != null) {
                if (title.isBlank()) title = c4Header.optString("title", "")
                if (avatarUrl.isBlank()) avatarUrl = InnerTubeHelpers.extractThumbnail(c4Header.optJSONObject("avatar"), "")
                if (bannerUrl.isBlank()) bannerUrl = InnerTubeHelpers.extractThumbnail(c4Header.optJSONObject("banner") ?: c4Header.optJSONObject("tvBanner"), "")
                if (subscriberCountText.isBlank()) subscriberCountText = c4Header.optJSONObject("subscriberCountText")?.optString("simpleText") ?: ""
                val cId = c4Header.optString("channelId")
                if (cId.isNotBlank()) channelId = cId
            }

            // Full description from channelMetadataRenderer / microformatDataRenderer (contains complete links)
            val metaRenderer = root.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
            if (metaRenderer != null) {
                val fullMetaDesc = metaRenderer.optString("description", "")
                if (fullMetaDesc.length > description.length) {
                    description = fullMetaDesc
                }
                if (title.isBlank()) title = metaRenderer.optString("title", "")
                val externalId = metaRenderer.optString("externalId", "")
                if (externalId.isNotBlank() && !channelId.startsWith("UC")) {
                    channelId = externalId
                }
            }

            avatarUrl = InnerTubeHelpers.normalizeUrl(avatarUrl)
            if (avatarUrl.isNotBlank() && channelId.isNotBlank()) {
                ChannelAvatarCache.put(channelId, title, avatarUrl)
            }

            if (title.isBlank() && fallbackChannelId.isNotBlank()) {
                title = fallbackChannelId
            }

            // 2. Parse Tabs (find video tab params and parse active tab contents)
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs") ?: JSONArray()

            var videoTabParams: String? = null
            var activeTabContent: JSONObject? = null
            var firstTabContent: JSONObject? = null
            var featuredVideo: VideoItem? = null
            val homeVideos = mutableListOf<VideoItem>()

            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                val tabTitle = tab.optString("title", "")
                val endpoint = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")
                val params = endpoint?.optString("params")

                if (tabTitle.equals("Video", ignoreCase = true) || tabTitle.equals("Videos", ignoreCase = true)) {
                    if (!params.isNullOrBlank()) {
                        videoTabParams = params
                    }
                }

                if (tab.optBoolean("selected", false)) {
                    activeTabContent = tab.optJSONObject("content")
                }
                if (firstTabContent == null) {
                    firstTabContent = tab.optJSONObject("content")
                }

                // Check sectionListRenderer in any tab (especially Beranda / Home tab)
                val tabContent = tab.optJSONObject("content")
                val secList = tabContent?.optJSONObject("sectionListRenderer")
                if (secList != null) {
                    val secContents = secList.optJSONArray("contents") ?: JSONArray()
                    for (s in 0 until secContents.length()) {
                        val sec = secContents.optJSONObject(s) ?: continue
                        val itemSec = sec.optJSONObject("itemSectionRenderer")
                        val isContents = itemSec?.optJSONArray("contents") ?: JSONArray()
                        for (k in 0 until isContents.length()) {
                            val ic = isContents.optJSONObject(k) ?: continue
                            // channelVideoPlayerRenderer (official channel trailer/recommendation)
                            val cvp = ic.optJSONObject("channelVideoPlayerRenderer")
                            if (cvp != null && featuredVideo == null) {
                                val vid = cvp.optString("videoId")
                                val vTitle = cvp.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                    ?: cvp.optJSONObject("title")?.optString("simpleText") ?: ""
                                val vViews = cvp.optJSONObject("viewCountText")?.optString("simpleText")
                                    ?: cvp.optJSONObject("viewCountText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                                val vPub = cvp.optJSONObject("publishedTimeText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                    ?: cvp.optJSONObject("publishedTimeText")?.optString("simpleText") ?: ""
                                if (vid.isNotBlank()) {
                                    featuredVideo = VideoItem(
                                        id = vid,
                                        title = vTitle,
                                        channelTitle = title,
                                        channelId = channelId,
                                        thumbnailUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                                        channelAvatarUrl = avatarUrl,
                                        viewCountText = InnerTubeHelpers.normalizeViewCount(vViews),
                                        publishedTimeText = InnerTubeHelpers.normalizePublishedTime(vPub)
                                    )
                                }
                            }

                            // channelFeaturedVideoRenderer / channelFeaturedContentRenderer
                            val cfv = ic.optJSONObject("channelFeaturedVideoRenderer")
                            if (cfv != null && featuredVideo == null) {
                                val vid = cfv.optString("videoId")
                                val vTitle = cfv.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                    ?: cfv.optJSONObject("title")?.optString("simpleText") ?: ""
                                val thumb = InnerTubeHelpers.extractThumbnail(cfv.optJSONObject("thumbnail"), vid)
                                if (vid.isNotBlank()) {
                                    featuredVideo = VideoItem(
                                        id = vid,
                                        title = vTitle,
                                        channelTitle = title,
                                        channelId = channelId,
                                        thumbnailUrl = thumb,
                                        channelAvatarUrl = avatarUrl
                                    )
                                }
                            }

                            // shelfRenderer (Home tab shelves)
                            val shelf = ic.optJSONObject("shelfRenderer")
                            if (shelf != null) {
                                val hList = shelf.optJSONObject("content")?.optJSONObject("horizontalListRenderer")
                                val grid = shelf.optJSONObject("content")?.optJSONObject("gridRenderer")
                                val sItems = hList?.optJSONArray("items") ?: grid?.optJSONArray("items") ?: JSONArray()
                                for (si in 0 until sItems.length()) {
                                    val sItem = sItems.optJSONObject(si) ?: continue
                                    FeedParser.parseVideoItem(sItem)?.let { rawV ->
                                        val enriched = rawV.copy(
                                            channelTitle = if (rawV.channelTitle.isNotBlank()) rawV.channelTitle else title,
                                            channelAvatarUrl = if (rawV.channelAvatarUrl.isNotBlank()) rawV.channelAvatarUrl else avatarUrl,
                                            channelId = if (rawV.channelId.isNotBlank()) rawV.channelId else channelId
                                        )
                                        homeVideos.add(enriched)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val targetContent = activeTabContent ?: firstTabContent
            val videos = mutableListOf<VideoItem>()
            val sortChips = mutableListOf<ChannelSortChip>()
            var continuationToken: String? = null

            if (targetContent != null) {
                val richGrid = targetContent.optJSONObject("richGridRenderer")
                if (richGrid != null) {
                    // Extract chips from chipBarViewModel
                    val chipBar = richGrid.optJSONObject("header")?.optJSONObject("chipBarViewModel")
                    val chipsArray = chipBar?.optJSONArray("chips") ?: JSONArray()
                    for (c in 0 until chipsArray.length()) {
                        val chipObj = chipsArray.optJSONObject(c)?.optJSONObject("chipViewModel") ?: continue
                        val cText = chipObj.optString("text")
                        val cSelected = chipObj.optBoolean("selected", false)
                        val cToken = chipObj.optJSONObject("tapCommand")
                            ?.optJSONObject("innertubeCommand")
                            ?.optJSONObject("continuationCommand")
                            ?.optString("token") ?: ""
                        if (cText.isNotBlank()) {
                            sortChips.add(ChannelSortChip(title = cText, continuationToken = cToken, isSelected = cSelected))
                        }
                    }

                    // Extract videos and continuation from contents
                    val contents = richGrid.optJSONArray("contents") ?: JSONArray()
                    for (j in 0 until contents.length()) {
                        val itm = contents.optJSONObject(j) ?: continue
                        val cItem = itm.optJSONObject("continuationItemRenderer")
                        if (cItem != null) {
                            val token = cItem.optJSONObject("continuationEndpoint")
                                ?.optJSONObject("continuationCommand")
                                ?.optString("token")
                            if (!token.isNullOrBlank()) {
                                continuationToken = token
                            }
                        }
                        val richContent = itm.optJSONObject("richItemRenderer")?.optJSONObject("content")
                        if (richContent != null) {
                            FeedParser.parseVideoItem(richContent)?.let { rawV ->
                                val enriched = rawV.copy(
                                    channelTitle = if (rawV.channelTitle.isNotBlank()) rawV.channelTitle else title,
                                    channelAvatarUrl = if (rawV.channelAvatarUrl.isNotBlank()) rawV.channelAvatarUrl else avatarUrl,
                                    channelId = if (rawV.channelId.isNotBlank()) rawV.channelId else channelId
                                )
                                videos.add(enriched)
                            }
                        }
                    }
                }

                // Fallback: sectionListRenderer
                val sectionList = targetContent.optJSONObject("sectionListRenderer")
                if (sectionList != null && videos.isEmpty()) {
                    val sContents = sectionList.optJSONArray("contents") ?: JSONArray()
                    for (s in 0 until sContents.length()) {
                        val sec = sContents.optJSONObject(s) ?: continue
                        val itemSec = sec.optJSONObject("itemSectionRenderer")
                        val isContents = itemSec?.optJSONArray("contents") ?: JSONArray()
                        for (k in 0 until isContents.length()) {
                            val raw = isContents.optJSONObject(k) ?: continue
                            // check continuation
                            val cItem = raw.optJSONObject("continuationItemRenderer")
                            if (cItem != null) {
                                val token = cItem.optJSONObject("continuationEndpoint")
                                    ?.optJSONObject("continuationCommand")
                                    ?.optString("token")
                                if (!token.isNullOrBlank()) continuationToken = token
                            }
                            FeedParser.parseVideoItem(raw)?.let { rawV ->
                                val enriched = rawV.copy(
                                    channelTitle = if (rawV.channelTitle.isNotBlank()) rawV.channelTitle else title,
                                    channelAvatarUrl = if (rawV.channelAvatarUrl.isNotBlank()) rawV.channelAvatarUrl else avatarUrl,
                                    channelId = if (rawV.channelId.isNotBlank()) rawV.channelId else channelId
                                )
                                videos.add(enriched)
                            }
                        }
                    }
                }
            }

            return ChannelProfile(
                id = channelId,
                title = title,
                handle = handle,
                avatarUrl = avatarUrl,
                bannerUrl = bannerUrl,
                subscriberCountText = subscriberCountText,
                videoCountText = videoCountText,
                description = description,
                externalLinksText = externalLinksText,
                isVerified = isVerified,
                featuredVideo = featuredVideo,
                homeVideos = homeVideos.distinctBy { it.id },
                videos = videos.distinctBy { it.id },
                continuationToken = continuationToken,
                videoTabParams = videoTabParams,
                sortChips = sortChips
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun parseChannelContinuation(jsonString: String): FeedResult {
        val items = mutableListOf<VideoItem>()
        var nextContinuationToken: String? = null
        try {
            val root = JSONObject(jsonString)
            val actions = root.optJSONArray("onResponseReceivedActions")
                ?: root.optJSONArray("onResponseReceivedCommands") ?: JSONArray()
            for (i in 0 until actions.length()) {
                val act = actions.optJSONObject(i) ?: continue
                val appendAction = act.optJSONObject("appendContinuationItemsAction")
                    ?: act.optJSONObject("reloadContinuationItemsCommand") ?: continue
                val continuationItems = appendAction.optJSONArray("continuationItems") ?: JSONArray()
                for (j in 0 until continuationItems.length()) {
                    val item = continuationItems.optJSONObject(j) ?: continue
                    val cItem = item.optJSONObject("continuationItemRenderer")
                    if (cItem != null) {
                        val token = cItem.optJSONObject("continuationEndpoint")
                            ?.optJSONObject("continuationCommand")
                            ?.optString("token")
                        if (!token.isNullOrBlank()) {
                            nextContinuationToken = token
                        }
                    }
                    val richContent = item.optJSONObject("richItemRenderer")?.optJSONObject("content")
                    if (richContent != null) {
                        FeedParser.parseVideoItem(richContent)?.let { items.add(it) }
                    } else {
                        FeedParser.parseVideoItem(item)?.let { items.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return FeedResult(videos = items.distinctBy { it.id }, continuationToken = nextContinuationToken)
    }
}
