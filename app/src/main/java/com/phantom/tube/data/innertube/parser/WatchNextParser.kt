package com.phantom.tube.data.innertube.parser

import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import com.phantom.tube.data.model.FeedResult
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.VideoComment
import com.phantom.tube.data.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject

object WatchNextParser {

    private val LIKE_COUNT_REGEX = Regex(""""iconName"\s*:\s*"LIKE"[^}]*?"title"\s*:\s*"([^"]+)"""")
    private val DESC_BODY_REGEX = Regex(""""attributedDescriptionBodyText"\s*:\s*\{[^}]*?"content"\s*:\s*"((?:\\.|[^"\\])*)"""")

    fun parseWatchNext(jsonString: String, currentVideoId: String): NextQueue? {
        try {
            val root = JSONObject(jsonString)
            val contents = root.optJSONObject("contents") ?: return null
            val watchNext = contents.optJSONObject("twoColumnWatchNextResults") ?: return null

            // 1. Current Video Details & Metadata
            val results = watchNext.optJSONObject("results")?.optJSONObject("results")
            val primaryContents = results?.optJSONArray("contents") ?: JSONArray()
            var currentTitle = "Video"
            var currentChannel = ""
            var currentAvatar = ""
            var currentChannelId = ""
            var currentHandle = ""
            var currentSubs = ""
            var fullViews = ""
            var dateUploaded = ""
            var likeCount = ""
            var videoDescription = ""
            var commentsCount = ""
            var commentsContinuationToken: String? = null
            var topComment: VideoComment? = null

            for (i in 0 until primaryContents.length()) {
                val p = primaryContents.optJSONObject(i) ?: continue
                val videoPrimaryInfo = p.optJSONObject("videoPrimaryInfoRenderer")
                if (videoPrimaryInfo != null) {
                    currentTitle = InnerTubeHelpers.parseRunsText(videoPrimaryInfo.optJSONObject("title"))
                    fullViews = videoPrimaryInfo.optJSONObject("viewCount")?.optJSONObject("videoViewCountRenderer")?.let {
                        InnerTubeHelpers.parseRunsText(it.optJSONObject("viewCount"))
                    } ?: InnerTubeHelpers.parseRunsText(videoPrimaryInfo.optJSONObject("viewCount"))
                    dateUploaded = InnerTubeHelpers.parseRunsText(videoPrimaryInfo.optJSONObject("dateText"))

                    // Extract like count from topLevelButtons
                    val topButtons = videoPrimaryInfo.optJSONObject("videoActions")
                        ?.optJSONObject("menuRenderer")
                        ?.optJSONArray("topLevelButtons")
                    if (topButtons != null) {
                        for (b in 0 until topButtons.length()) {
                            val btn = topButtons.optJSONObject(b) ?: continue
                            val segVm = btn.optJSONObject("segmentedLikeDislikeButtonViewModel")
                            if (segVm != null) {
                                val likeBtnVm = segVm.optJSONObject("likeButtonViewModel")?.optJSONObject("likeButtonViewModel")
                                    ?: segVm.optJSONObject("likeButtonViewModel")
                                val toggleVm = likeBtnVm?.optJSONObject("toggleButtonViewModel")?.optJSONObject("toggleButtonViewModel")
                                    ?: likeBtnVm?.optJSONObject("toggleButtonViewModel")
                                val defaultVm = toggleVm?.optJSONObject("defaultButtonViewModel")
                                val bVm = defaultVm?.optJSONObject("buttonViewModel")
                                val t = bVm?.optString("title")
                                if (!t.isNullOrBlank()) {
                                    likeCount = t
                                    break
                                }
                            }
                            val segRenderer = btn.optJSONObject("segmentedLikeDislikeButtonRenderer")
                            if (segRenderer != null) {
                                val likeBtn = segRenderer.optJSONObject("likeButton")?.optJSONObject("toggleButtonRenderer")
                                val t = InnerTubeHelpers.parseRunsText(likeBtn?.optJSONObject("defaultText"))
                                if (t.isNotBlank()) {
                                    likeCount = t
                                    break
                                }
                            }
                        }
                    }
                }

                val videoSecondaryInfo = p.optJSONObject("videoSecondaryInfoRenderer")
                if (videoSecondaryInfo != null) {
                    val owner = videoSecondaryInfo.optJSONObject("owner")?.optJSONObject("videoOwnerRenderer")
                    if (owner != null) {
                        currentChannel = InnerTubeHelpers.parseRunsText(owner.optJSONObject("title"))
                        val ownerThumb = InnerTubeHelpers.extractThumbnail(owner.optJSONObject("thumbnail"), "").ifBlank {
                            InnerTubeHelpers.extractAvatar(owner)
                        }
                        val cleanOwnerThumb = InnerTubeHelpers.normalizeUrl(ownerThumb)
                        if (cleanOwnerThumb.isNotBlank()) {
                            currentAvatar = cleanOwnerThumb
                        }
                        val nav = owner.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                        currentChannelId = nav?.optString("browseId") ?: ""
                        currentHandle = nav?.optString("canonicalBaseUrl")?.removePrefix("/") ?: ""
                        currentSubs = InnerTubeHelpers.parseRunsText(owner.optJSONObject("subscriberCountText"))
                        if (currentAvatar.isNotBlank() && currentChannelId.isNotBlank()) {
                            ChannelAvatarCache.put(currentChannelId, currentChannel, currentAvatar)
                        }
                    }
                    val desc = InnerTubeHelpers.parseRunsText(videoSecondaryInfo.optJSONObject("description"))
                    videoDescription = if (desc.isNotBlank()) desc else {
                        InnerTubeHelpers.parseRunsText(videoSecondaryInfo.optJSONObject("attributedDescription"))
                    }
                }

                val itemSection = p.optJSONObject("itemSectionRenderer")
                if (itemSection != null) {
                    val secId = itemSection.optString("sectionIdentifier")
                    if (secId == "comment-item-section" || commentsContinuationToken == null) {
                        val subContents = itemSection.optJSONArray("contents") ?: JSONArray()
                        for (c in 0 until subContents.length()) {
                            val cObj = subContents.optJSONObject(c) ?: continue
                            val cHeader = cObj.optJSONObject("commentsEntryPointHeaderRenderer")
                            if (cHeader != null) {
                                commentsCount = InnerTubeHelpers.parseRunsText(cHeader.optJSONObject("commentCount"))
                                val teaser = InnerTubeHelpers.parseRunsText(cHeader.optJSONObject("teaserContent"))
                                val teaserThumb = InnerTubeHelpers.extractThumbnail(cHeader.optJSONObject("teaserAvatar"), "")
                                if (teaser.isNotBlank()) {
                                    topComment = VideoComment(
                                        contentText = teaser,
                                        authorAvatarUrl = teaserThumb
                                    )
                                }
                            }
                            val cont = cObj.optJSONObject("continuationItemRenderer")
                            if (cont != null) {
                                val token = cont.optJSONObject("continuationEndpoint")
                                    ?.optJSONObject("continuationCommand")
                                    ?.optString("token")
                                if (!token.isNullOrBlank()) {
                                    commentsContinuationToken = token
                                }
                            }
                        }
                    }
                }
            }

            if (likeCount.isBlank()) {
                val m = LIKE_COUNT_REGEX.find(jsonString)
                if (m != null) {
                    likeCount = m.groupValues[1]
                }
            }

            // 1.5 Robust Description Extraction (engagementPanels & regex fallback)
            if (videoDescription.isBlank()) {
                val panels = root.optJSONArray("engagementPanels") ?: JSONArray()
                for (p in 0 until panels.length()) {
                    val panel = panels.optJSONObject(p)?.optJSONObject("engagementPanelSectionListRenderer") ?: continue
                    val pId = panel.optString("panelIdentifier").ifBlank { panel.optString("targetId") }
                    if (pId.contains("description", ignoreCase = true)) {
                        val items = panel.optJSONObject("content")
                            ?.optJSONObject("structuredDescriptionContentRenderer")
                            ?.optJSONArray("items") ?: JSONArray()
                        for (itIdx in 0 until items.length()) {
                            val itm = items.optJSONObject(itIdx) ?: continue
                            val body = itm.optJSONObject("expandableVideoDescriptionBodyRenderer")
                            if (body != null) {
                                val bodyText = InnerTubeHelpers.parseRunsText(body.optJSONObject("attributedDescriptionBodyText"))
                                    .ifBlank { InnerTubeHelpers.parseRunsText(body.optJSONObject("descriptionBodyText")) }
                                if (bodyText.isNotBlank()) {
                                    videoDescription = bodyText
                                    break
                                }
                            }
                        }
                    }
                    if (videoDescription.isNotBlank()) break
                }
            }
            if (videoDescription.isBlank()) {
                val descMatch = DESC_BODY_REGEX.find(jsonString)
                if (descMatch != null) {
                    val raw = descMatch.groupValues[1]
                    videoDescription = raw.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
                }
            }

            // 2. Full YouTube Mix Playlist
            val playlistObj = watchNext.optJSONObject("playlist")?.optJSONObject("playlist")
                ?: watchNext.optJSONObject("playlist")?.optJSONObject("playlistPanelRenderer")
            val detectedPlaylistId = playlistObj?.optString("playlistId")?.ifBlank { null }

            val fullPlaylist = mutableListOf<VideoItem>()
            var detectedCurrentIndex = 0

            if (playlistObj != null) {
                val plContents = playlistObj.optJSONArray("contents") ?: JSONArray()
                detectedCurrentIndex = playlistObj.optInt("currentIndex", 0)

                for (i in 0 until plContents.length()) {
                    val raw = plContents.optJSONObject(i) ?: continue
                    val ppvr = raw.optJSONObject("playlistPanelVideoRenderer") ?: continue
                    val videoId = ppvr.optString("videoId")
                    if (videoId.isBlank()) continue

                    val title = InnerTubeHelpers.parseRunsText(ppvr.optJSONObject("title"))
                    val bylineObj = ppvr.optJSONObject("longBylineText") ?: ppvr.optJSONObject("shortBylineText")
                    val channel = InnerTubeHelpers.parseRunsText(bylineObj)
                    val runs = bylineObj?.optJSONArray("runs")
                    var itemChannelId = ""
                    if (runs != null && runs.length() > 0) {
                        itemChannelId = runs.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""
                    }
                    val thumb = InnerTubeHelpers.extractThumbnail(ppvr.optJSONObject("thumbnail"), videoId)
                    val duration = ppvr.optJSONObject("lengthText")?.optString("simpleText") ?: ""

                    val item = VideoItem(
                        id = videoId,
                        title = title,
                        channelTitle = channel,
                        channelId = itemChannelId,
                        thumbnailUrl = thumb,
                        durationText = duration,
                        playlistId = detectedPlaylistId
                    )
                    fullPlaylist.add(item)

                    val isSelected = ppvr.optBoolean("selected", false)
                    if (isSelected || videoId == currentVideoId) {
                        detectedCurrentIndex = fullPlaylist.lastIndex
                        if (currentTitle == "Video" && title.isNotBlank()) {
                            currentTitle = title
                        }
                        if (currentChannel.isBlank() && channel.isNotBlank()) {
                            currentChannel = channel
                        }
                    }
                }
            }

            var playlistTitle = ""
            if (playlistObj != null) {
                playlistTitle = playlistObj.optString("title")
                if (playlistTitle.isBlank()) {
                    playlistTitle = InnerTubeHelpers.parseRunsText(playlistObj.optJSONObject("titleText"))
                }
                if (playlistTitle.isBlank()) {
                    playlistTitle = "Mix - $currentTitle"
                }
            }

            // 3. Recommended Videos
            val recommendationsList = mutableListOf<VideoItem>()
            var recContinuationToken: String? = null
            val secondary = watchNext.optJSONObject("secondaryResults")?.optJSONObject("secondaryResults")
            val secondaryResults = secondary?.optJSONArray("results") ?: JSONArray()

            for (i in 0 until secondaryResults.length()) {
                val item = secondaryResults.optJSONObject(i) ?: continue

                val continuationItem = item.optJSONObject("continuationItemRenderer")
                if (continuationItem != null) {
                    val token = continuationItem
                        .optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token")
                    if (!token.isNullOrBlank()) {
                        recContinuationToken = token
                    }
                }

                val itemSection = item.optJSONObject("itemSectionRenderer")
                if (itemSection != null) {
                    val subContents = itemSection.optJSONArray("contents") ?: JSONArray()
                    for (k in 0 until subContents.length()) {
                        val subItem = subContents.optJSONObject(k) ?: continue
                        val subCont = subItem.optJSONObject("continuationItemRenderer")
                        if (subCont != null) {
                            val token = subCont
                                .optJSONObject("continuationEndpoint")
                                ?.optJSONObject("continuationCommand")
                                ?.optString("token")
                            if (!token.isNullOrBlank()) {
                                recContinuationToken = token
                            }
                        }
                        FeedParser.parseVideoItem(subItem)?.let {
                            if (it.id != currentVideoId) {
                                recommendationsList.add(it)
                            }
                        }
                    }
                } else {
                    FeedParser.parseVideoItem(item)?.let {
                        if (it.id != currentVideoId) {
                            recommendationsList.add(it)
                        }
                    }
                }
            }

            // 4. Endscreen Videos from playerOverlays
            val endscreenList = mutableListOf<VideoItem>()
            val playerOverlays = root.optJSONObject("playerOverlays")
            val endscreen = playerOverlays?.optJSONObject("playerOverlayRenderer")
                ?.optJSONObject("endscreen")
                ?.optJSONObject("endscreenRenderer")
            val endscreenElements = endscreen?.optJSONArray("elements")
            if (endscreenElements != null) {
                for (e in 0 until endscreenElements.length()) {
                    val elem = endscreenElements.optJSONObject(e) ?: continue
                    val renderer = elem.optJSONObject("endscreenElementRenderer") ?: continue
                    val endpoint = renderer.optJSONObject("endpoint")?.optJSONObject("watchEndpoint")
                    val vidId = endpoint?.optString("videoId") ?: ""
                    if (vidId.isBlank() || vidId == currentVideoId) continue

                    val title = InnerTubeHelpers.parseRunsText(renderer.optJSONObject("title")).ifBlank {
                        renderer.optJSONObject("title")?.optString("simpleText") ?: ""
                    }
                    val channelTitle = InnerTubeHelpers.parseRunsText(renderer.optJSONObject("shortBylineText")).ifBlank {
                        InnerTubeHelpers.parseRunsText(renderer.optJSONObject("longBylineText"))
                    }
                    val durationText = renderer.optJSONObject("videoDuration")?.optString("simpleText") ?: ""
                    val thumb = InnerTubeHelpers.extractThumbnail(renderer.optJSONObject("image"), vidId)

                    endscreenList.add(
                        VideoItem(
                            id = vidId,
                            title = title,
                            channelTitle = channelTitle,
                            thumbnailUrl = thumb.ifBlank { "https://i.ytimg.com/vi/$vidId/hqdefault.jpg" },
                            durationText = durationText
                        )
                    )
                }
            }

            val currentVideo = VideoItem(
                id = currentVideoId,
                title = currentTitle,
                channelTitle = currentChannel,
                channelId = currentChannelId,
                channelAvatarUrl = currentAvatar,
                viewCountText = InnerTubeHelpers.normalizeViewCount(fullViews),
                publishedTimeText = InnerTubeHelpers.normalizePublishedTime(dateUploaded),
                thumbnailUrl = "https://i.ytimg.com/vi/$currentVideoId/hqdefault.jpg"
            )

            return NextQueue(
                currentVideo = currentVideo,
                mixPlaylist = fullPlaylist,
                recommendations = recommendationsList,
                recommendationsContinuationToken = recContinuationToken,
                endscreenVideos = endscreenList,
                playlistTitle = playlistTitle,
                playlistId = detectedPlaylistId,
                currentIndex = detectedCurrentIndex,
                likeCountText = likeCount,
                fullViewCountText = InnerTubeHelpers.normalizeViewCount(fullViews),
                dateText = InnerTubeHelpers.normalizePublishedTime(dateUploaded),
                description = videoDescription,
                channelSubscriberCountText = currentSubs,
                channelHandle = currentHandle,
                commentsCountText = commentsCount,
                commentsContinuationToken = commentsContinuationToken,
                topComment = topComment
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun parseWatchNextContinuation(jsonString: String, currentVideoId: String = ""): FeedResult {
        val items = mutableListOf<VideoItem>()
        var continuationToken: String? = null

        try {
            val root = JSONObject(jsonString)
            val commands = root.optJSONArray("onResponseReceivedEndpoints")
                ?: root.optJSONArray("onResponseReceivedCommands")
                ?: JSONArray()

            for (i in 0 until commands.length()) {
                val cmd = commands.optJSONObject(i) ?: continue
                val appendAction = cmd.optJSONObject("appendContinuationItemsAction") ?: continue
                val continuationItems = appendAction.optJSONArray("continuationItems") ?: JSONArray()

                for (j in 0 until continuationItems.length()) {
                    val cItem = continuationItems.optJSONObject(j) ?: continue

                    val nextTokenItem = cItem.optJSONObject("continuationItemRenderer")
                    if (nextTokenItem != null) {
                        val token = nextTokenItem
                            .optJSONObject("continuationEndpoint")
                            ?.optJSONObject("continuationCommand")
                            ?.optString("token")
                        if (!token.isNullOrBlank()) {
                            continuationToken = token
                        }
                    }

                    val itemSection = cItem.optJSONObject("itemSectionRenderer")
                    if (itemSection != null) {
                        val subContents = itemSection.optJSONArray("contents") ?: JSONArray()
                        for (k in 0 until subContents.length()) {
                            val subItem = subContents.optJSONObject(k) ?: continue
                            FeedParser.parseVideoItem(subItem)?.let {
                                if (it.id != currentVideoId) items.add(it)
                            }
                        }
                    } else {
                        FeedParser.parseVideoItem(cItem)?.let {
                            if (it.id != currentVideoId) items.add(it)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return FeedResult(videos = items, continuationToken = continuationToken)
    }
}
