package com.phantom.tube.data.innertube.parser

import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import com.phantom.tube.data.model.FeedResult
import com.phantom.tube.data.model.SearchChannelItem
import com.phantom.tube.data.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject

object FeedParser {

    private val VI_ID_REGEX = Regex("""/vi/([a-zA-Z0-9_-]{11})/""")
    private val BROWSE_UC_ID_REGEX = Regex("""\"browseId\":\s*\"(UC[a-zA-Z0-9_-]{22})\"""")

    fun parseSearchResults(jsonString: String): List<VideoItem> {
        return parseFeedWithContinuation(jsonString).videos
    }

    fun parseFeedWithContinuation(jsonString: String): FeedResult {
        val items = mutableListOf<VideoItem>()
        val channels = mutableListOf<SearchChannelItem>()
        var continuationToken: String? = null

        try {
            val root = JSONObject(jsonString)

            // 1. Initial page format: contents.twoColumnSearchResultsRenderer.primaryContents.sectionListRenderer.contents
            val contents = root.optJSONObject("contents")
            val twoCol = contents?.optJSONObject("twoColumnSearchResultsRenderer")
            val primary = twoCol?.optJSONObject("primaryContents")
            val sectionList = primary?.optJSONObject("sectionListRenderer")
            val sections = sectionList?.optJSONArray("contents") ?: JSONArray()

            for (i in 0 until sections.length()) {
                val sec = sections.optJSONObject(i) ?: continue

                // Check for continuation token in initial page
                val continuationItem = sec.optJSONObject("continuationItemRenderer")
                if (continuationItem != null) {
                    val token = continuationItem
                        .optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token")
                    if (!token.isNullOrBlank()) {
                        continuationToken = token
                    }
                }

                // Check videos, channels, and continuation in itemSectionRenderer
                val itemSection = sec.optJSONObject("itemSectionRenderer")
                if (itemSection != null) {
                    val contentsArray = itemSection.optJSONArray("contents") ?: JSONArray()
                    for (j in 0 until contentsArray.length()) {
                        val rawItem = contentsArray.optJSONObject(j) ?: continue
                        val cItem = rawItem.optJSONObject("continuationItemRenderer")
                        if (cItem != null) {
                            val token = cItem
                                .optJSONObject("continuationEndpoint")
                                ?.optJSONObject("continuationCommand")
                                ?.optString("token")
                            if (!token.isNullOrBlank()) {
                                continuationToken = token
                            }
                            continue
                        }
                        val ch = parseChannelItem(rawItem)
                        if (ch != null) {
                            channels.add(ch)
                        } else {
                            parseVideoItem(rawItem)?.let { items.add(it) }
                        }
                    }
                }
            }

            // Fallback: check sectionListRenderer.continuations
            if (continuationToken.isNullOrBlank()) {
                val continuations = sectionList?.optJSONArray("continuations")
                if (continuations != null) {
                    for (ci in 0 until continuations.length()) {
                        val cObj = continuations.optJSONObject(ci) ?: continue
                        val token = cObj.optJSONObject("nextContinuationData")?.optString("continuation")
                            ?: cObj.optJSONObject("reloadContinuationData")?.optString("continuation")
                        if (!token.isNullOrBlank()) {
                            continuationToken = token
                            break
                        }
                    }
                }
            }

            // 2. Continuation page format: onResponseReceivedCommands
            val commands = root.optJSONArray("onResponseReceivedCommands") ?: JSONArray()
            for (i in 0 until commands.length()) {
                val cmd = commands.optJSONObject(i) ?: continue
                val appendAction = cmd.optJSONObject("appendContinuationItemsAction")
                    ?: cmd.optJSONObject("reloadContinuationItemsCommand") ?: continue
                val continuationItems = appendAction.optJSONArray("continuationItems") ?: JSONArray()

                for (j in 0 until continuationItems.length()) {
                    val cItem = continuationItems.optJSONObject(j) ?: continue

                    // Check for next continuation token
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

                    // Check videos and channels in itemSectionRenderer
                    val itemSection = cItem.optJSONObject("itemSectionRenderer")
                    if (itemSection != null) {
                        val subContents = itemSection.optJSONArray("contents") ?: JSONArray()
                        for (k in 0 until subContents.length()) {
                            val subItem = subContents.optJSONObject(k) ?: continue
                            val ch = parseChannelItem(subItem)
                            if (ch != null) {
                                channels.add(ch)
                            } else {
                                parseVideoItem(subItem)?.let { items.add(it) }
                            }
                        }
                    } else {
                        // Direct video item or channel item or lockupViewModel
                        val ch = parseChannelItem(cItem)
                        if (ch != null) {
                            channels.add(ch)
                        } else {
                            parseVideoItem(cItem)?.let { items.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return FeedResult(
            videos = items,
            channels = channels,
            continuationToken = continuationToken
        )
    }

    fun parseChannelItem(json: JSONObject): SearchChannelItem? {
        if (json.has("channelRenderer")) {
            return parseChannelRenderer(json.optJSONObject("channelRenderer") ?: JSONObject())
        }
        if (json.has("officialCardViewModel")) {
            return parseOfficialCardViewModel(json.optJSONObject("officialCardViewModel") ?: JSONObject())
        }
        if (json.has("header") && json.optJSONObject("header")?.has("pageHeaderViewModel") == true) {
            return parseOfficialCardViewModel(json)
        }
        return null
    }

    private fun parseChannelRenderer(cr: JSONObject): SearchChannelItem? {
        val channelId = cr.optString("channelId").ifBlank {
            cr.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId") ?: ""
        }
        if (channelId.isBlank()) return null

        val title = cr.optJSONObject("title")?.optString("simpleText")?.ifBlank { null }
            ?: InnerTubeHelpers.parseRunsText(cr.optJSONObject("title"))

        var avatarUrl = InnerTubeHelpers.extractThumbnail(cr.optJSONObject("thumbnail"), "")
        if (avatarUrl.isBlank()) {
            avatarUrl = InnerTubeHelpers.extractAvatar(cr)
        }
        avatarUrl = InnerTubeHelpers.normalizeUrl(avatarUrl)
        if (avatarUrl.isNotBlank() && channelId.isNotBlank()) {
            ChannelAvatarCache.put(channelId, title, avatarUrl)
        }

        val subscriberText = cr.optJSONObject("subscriberCountText")?.optString("simpleText")?.ifBlank { null }
            ?: InnerTubeHelpers.parseRunsText(cr.optJSONObject("subscriberCountText"))

        val videoText = cr.optJSONObject("videoCountText")?.optString("simpleText")?.ifBlank { null }
            ?: InnerTubeHelpers.parseRunsText(cr.optJSONObject("videoCountText"))

        val canonicalUrl = cr.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("canonicalBaseUrl", "") ?: ""

        val handle = if (subscriberText.startsWith("@")) {
            subscriberText
        } else if (canonicalUrl.isNotBlank()) {
            canonicalUrl.removePrefix("/")
        } else {
            ""
        }

        val subscriberCount = if (!subscriberText.startsWith("@") && subscriberText.isNotBlank()) {
            subscriberText
        } else if (videoText.contains("subscriber", ignoreCase = true) || videoText.contains("langganan", ignoreCase = true) || videoText.contains("sub", ignoreCase = true)) {
            videoText
        } else {
            ""
        }

        val videoCount = if (videoText != subscriberCount && (videoText.contains("video", ignoreCase = true) || videoText.contains("vid", ignoreCase = true))) {
            videoText
        } else {
            ""
        }

        val description = InnerTubeHelpers.parseRunsText(cr.optJSONObject("descriptionSnippet"))

        // Check verification badge
        var isVerified = false
        val badges = cr.optJSONArray("ownerBadges") ?: JSONArray()
        for (i in 0 until badges.length()) {
            val badgeObj = badges.optJSONObject(i)?.optJSONObject("metadataBadgeRenderer")
            val style = badgeObj?.optString("style", "") ?: ""
            val iconType = badgeObj?.optJSONObject("icon")?.optString("iconType", "") ?: ""
            if (style.contains("VERIFIED", ignoreCase = true) || iconType.contains("CHECK", ignoreCase = true)) {
                isVerified = true
                break
            }
        }

        return SearchChannelItem(
            id = channelId,
            title = title.ifBlank { "Channel" },
            handle = handle,
            avatarUrl = avatarUrl,
            subscriberCountText = subscriberCount,
            videoCountText = videoCount,
            description = description,
            isVerified = isVerified
        )
    }

    private fun parseOfficialCardViewModel(card: JSONObject): SearchChannelItem? {
        val header = card.optJSONObject("header")?.optJSONObject("pageHeaderViewModel") ?: return null
        val title = header.optJSONObject("title")?.optJSONObject("dynamicTextViewModel")
            ?.optJSONObject("text")?.optString("content")?.ifBlank { null }
            ?: return null

        // Avatar image
        val imgSources = header.optJSONObject("image")?.optJSONObject("contentPreviewImageViewModel")
            ?.optJSONObject("image")?.optJSONArray("sources")
        var avatarUrl = InnerTubeHelpers.normalizeUrl(imgSources?.optJSONObject(0)?.optString("url", ""))
        if (avatarUrl.isBlank()) {
            avatarUrl = InnerTubeHelpers.extractAvatar(header).ifBlank {
                InnerTubeHelpers.extractAvatar(card)
            }
        }

        // Browse ID / channelId
        var channelId = card.optJSONObject("rendererContext")?.optJSONObject("commandContext")
            ?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
            ?.optJSONObject("browseEndpoint")?.optString("browseId", "") ?: ""

        if (channelId.isBlank()) {
            channelId = header.optJSONObject("rendererContext")?.optJSONObject("commandContext")
                ?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
                ?.optJSONObject("browseEndpoint")?.optString("browseId", "") ?: ""
        }

        if (channelId.isBlank()) {
            val actionRows = header.optJSONObject("actions")?.optJSONObject("flexibleActionsViewModel")
                ?.optJSONArray("actionsRows") ?: JSONArray()
            for (i in 0 until actionRows.length()) {
                val row = actionRows.optJSONObject(i)?.optJSONArray("actions") ?: JSONArray()
                for (j in 0 until row.length()) {
                    val bCmd = row.optJSONObject(j)?.optJSONObject("buttonViewModel")
                        ?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
                        ?.optJSONObject("browseEndpoint")?.optString("browseId", "") ?: ""
                    if (bCmd.isNotBlank()) {
                        channelId = bCmd
                        break
                    }
                }
                if (channelId.isNotBlank()) break
            }
        }

        if (channelId.isBlank()) return null

        // Metadata rows: handle, subscriber count, video count
        var handle = ""
        var subscriberCount = ""
        var videoCount = ""
        val metadataRows = header.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows") ?: JSONArray()
        for (i in 0 until metadataRows.length()) {
            val rowParts = metadataRows.optJSONObject(i)?.optJSONArray("metadataParts") ?: JSONArray()
            for (j in 0 until rowParts.length()) {
                val txt = rowParts.optJSONObject(j)?.optJSONObject("text")?.optString("content", "") ?: ""
                if (txt.startsWith("@")) {
                    handle = txt
                } else if (txt.contains("subscriber", ignoreCase = true) || txt.contains("langganan", ignoreCase = true) || txt.contains("sub", ignoreCase = true)) {
                    subscriberCount = txt
                } else if (txt.contains("video", ignoreCase = true) || txt.contains("vid", ignoreCase = true)) {
                    videoCount = txt
                }
            }
        }

        if (avatarUrl.isNotBlank() && channelId.isNotBlank()) {
            ChannelAvatarCache.put(channelId, title, avatarUrl)
        }

        return SearchChannelItem(
            id = channelId,
            title = title,
            handle = handle,
            avatarUrl = avatarUrl,
            subscriberCountText = subscriberCount,
            videoCountText = videoCount,
            description = "",
            isVerified = true
        )
    }

    fun parseVideoItem(json: JSONObject): VideoItem? {
        // Option 1: Classic videoRenderer
        if (json.has("videoRenderer")) {
            val vr = json.getJSONObject("videoRenderer")
            val videoId = vr.optString("videoId")
            if (videoId.isBlank()) return null
            val title = InnerTubeHelpers.parseRunsText(vr.optJSONObject("title"))
            val channel = InnerTubeHelpers.parseRunsText(vr.optJSONObject("ownerText"))
            val duration = vr.optJSONObject("lengthText")?.optString("simpleText") ?: ""
            val views = vr.optJSONObject("shortViewCountText")?.optString("simpleText") ?: ""
            val published = vr.optJSONObject("publishedTimeText")?.optString("simpleText") ?: ""
            val thumb = InnerTubeHelpers.extractThumbnail(vr.optJSONObject("thumbnail"), videoId)
            val channelId = vr.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                ?: vr.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                ?: ""

            var avatar = InnerTubeHelpers.normalizeUrl(InnerTubeHelpers.extractAvatar(vr))
            if (avatar.isNotBlank()) {
                ChannelAvatarCache.put(channelId, channel, avatar)
            } else {
                avatar = ChannelAvatarCache.get(channelId, channel)
            }

            return VideoItem(
                id = videoId,
                title = title,
                channelTitle = channel,
                channelId = channelId,
                thumbnailUrl = thumb,
                channelAvatarUrl = avatar,
                durationText = duration,
                viewCountText = InnerTubeHelpers.normalizeViewCount(views),
                publishedTimeText = InnerTubeHelpers.normalizePublishedTime(published)
            )
        }

        // Option 2: compactVideoRenderer (related videos)
        if (json.has("compactVideoRenderer")) {
            val cvr = json.getJSONObject("compactVideoRenderer")
            val videoId = cvr.optString("videoId")
            if (videoId.isBlank()) return null
            val title = InnerTubeHelpers.parseRunsText(cvr.optJSONObject("title"))
            val channel = InnerTubeHelpers.parseRunsText(cvr.optJSONObject("longBylineText") ?: cvr.optJSONObject("shortBylineText"))
            val duration = cvr.optJSONObject("lengthText")?.optString("simpleText") ?: ""
            val views = cvr.optJSONObject("shortViewCountText")?.optString("simpleText") ?: ""
            val published = cvr.optJSONObject("publishedTimeText")?.optString("simpleText") ?: ""
            val thumb = InnerTubeHelpers.extractThumbnail(cvr.optJSONObject("thumbnail"), videoId)
            val channelId = cvr.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                ?: cvr.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                ?: ""

            var avatar = InnerTubeHelpers.normalizeUrl(InnerTubeHelpers.extractAvatar(cvr))
            if (avatar.isNotBlank()) {
                ChannelAvatarCache.put(channelId, channel, avatar)
            } else {
                avatar = ChannelAvatarCache.get(channelId, channel)
            }

            return VideoItem(
                id = videoId,
                title = title,
                channelTitle = channel,
                channelId = channelId,
                thumbnailUrl = thumb,
                channelAvatarUrl = avatar,
                durationText = duration,
                viewCountText = InnerTubeHelpers.normalizeViewCount(views),
                publishedTimeText = InnerTubeHelpers.normalizePublishedTime(published)
            )
        }

        // Option 3: playlistRenderer (Channel & User Playlists)
        if (json.has("playlistRenderer")) {
            val pr = json.getJSONObject("playlistRenderer")
            val playlistId = pr.optString("playlistId")
            val nav = pr.optJSONObject("navigationEndpoint")
            val watchEndpoint = nav?.optJSONObject("watchEndpoint")
            var videoId = watchEndpoint?.optString("videoId") ?: ""
            if (videoId.isBlank()) {
                val thumbsStr = (pr.optJSONObject("thumbnails") ?: pr.optJSONArray("thumbnails"))?.toString() ?: ""
                val vMatch = VI_ID_REGEX.find(thumbsStr)
                if (vMatch != null) {
                    videoId = vMatch.groupValues[1]
                }
            }
            val title = InnerTubeHelpers.parseRunsText(pr.optJSONObject("title"))
            val channel = InnerTubeHelpers.parseRunsText(pr.optJSONObject("longBylineText") ?: pr.optJSONObject("shortBylineText"))
            val videoCount = pr.optString("videoCount").ifBlank {
                InnerTubeHelpers.parseRunsText(pr.optJSONObject("videoCountText"))
            }
            val thumb = InnerTubeHelpers.extractThumbnail(pr.optJSONObject("thumbnail") ?: pr.optJSONArray("thumbnails")?.optJSONObject(0), videoId)
            val channelId = pr.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""

            return VideoItem(
                id = videoId.ifBlank { "pl_$playlistId" },
                title = title,
                channelTitle = channel,
                channelId = channelId,
                thumbnailUrl = thumb,
                durationText = if (videoCount.isNotBlank()) "$videoCount video" else "Playlist",
                playlistId = playlistId.ifBlank { null },
                isPlaylist = true
            )
        }

        // Option 4: radioRenderer (YouTube Mix)
        if (json.has("radioRenderer")) {
            val rr = json.getJSONObject("radioRenderer")
            val playlistId = rr.optString("playlistId")
            val nav = rr.optJSONObject("navigationEndpoint")
            val watchEndpoint = nav?.optJSONObject("watchEndpoint")
            val videoId = watchEndpoint?.optString("videoId") ?: ""
            val title = InnerTubeHelpers.parseRunsText(rr.optJSONObject("title"))
            val channel = InnerTubeHelpers.parseRunsText(rr.optJSONObject("shortBylineText"))
            val videoCount = InnerTubeHelpers.parseRunsText(rr.optJSONObject("videoCountText"))
            val thumb = InnerTubeHelpers.extractThumbnail(rr.optJSONObject("thumbnail"), videoId)

            return VideoItem(
                id = videoId.ifBlank { "rd_$playlistId" },
                title = title,
                channelTitle = channel,
                thumbnailUrl = thumb,
                durationText = if (videoCount.isNotBlank()) videoCount else "Mix",
                playlistId = playlistId.ifBlank { null },
                isPlaylist = true
            )
        }

        // Option 5: Modern lockupViewModel
        if (json.has("lockupViewModel")) {
            val lvm = json.getJSONObject("lockupViewModel")
            val onTap = lvm.optJSONObject("rendererContext")
                ?.optJSONObject("commandContext")
                ?.optJSONObject("onTap")
                ?.optJSONObject("innertubeCommand")
            val watchEndpoint = onTap?.optJSONObject("watchEndpoint")
            val watchPlaylistEndpoint = onTap?.optJSONObject("watchPlaylistEndpoint")
            var videoId = watchEndpoint?.optString("videoId") ?: ""
            if (videoId.isBlank()) {
                videoId = watchPlaylistEndpoint?.optString("videoId") ?: ""
            }
            var playlistId = watchEndpoint?.optString("playlistId") ?: ""
            if (playlistId.isBlank()) {
                playlistId = watchPlaylistEndpoint?.optString("playlistId") ?: ""
            }

            val metadata = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            val title = metadata?.optJSONObject("title")?.optString("content") ?: ""
            val contentType = lvm.optString("contentType")
            val isPlaylist = contentType == "LOCKUP_CONTENT_TYPE_PLAYLIST" ||
                    playlistId.isNotBlank() ||
                    title.startsWith("Mix -", ignoreCase = true)

            if (videoId.isBlank() && isPlaylist) {
                val thumbSourcesStr = lvm.optJSONObject("contentImage")
                    ?.optJSONObject("thumbnailViewModel")
                    ?.optJSONObject("image")
                    ?.optJSONArray("sources")?.toString() ?: ""
                val vMatch = VI_ID_REGEX.find(thumbSourcesStr)
                if (vMatch != null) {
                    videoId = vMatch.groupValues[1]
                }
            }
            if (videoId.isBlank() && !isPlaylist) return null
            if (videoId.isBlank() && playlistId.isNotBlank()) {
                videoId = "pl_$playlistId"
            }

            // channel and view count
            val metaRows = metadata?.optJSONObject("metadata")
                ?.optJSONObject("contentMetadataViewModel")
                ?.optJSONArray("metadataRows") ?: JSONArray()

            var channel = ""
            var views = ""
            var published = ""

            if (metaRows.length() == 1) {
                val parts = metaRows.optJSONObject(0)?.optJSONArray("metadataParts")
                if (parts != null) {
                    if (parts.length() >= 2) {
                        views = parts.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: ""
                        published = parts.optJSONObject(1)?.optJSONObject("text")?.optString("content") ?: ""
                    } else if (parts.length() == 1) {
                        val txt = parts.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: ""
                        if (txt.contains("ditonton", ignoreCase = true) || txt.contains("views", ignoreCase = true) || txt.any { it.isDigit() }) {
                            views = txt
                        } else {
                            channel = txt
                        }
                    }
                }
            } else if (metaRows.length() >= 2) {
                val row0 = metaRows.optJSONObject(0)?.optJSONArray("metadataParts")
                channel = row0?.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: ""
                val row1 = metaRows.optJSONObject(1)?.optJSONArray("metadataParts")
                if (row1 != null) {
                    if (row1.length() >= 2) {
                        views = row1.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: ""
                        published = row1.optJSONObject(1)?.optJSONObject("text")?.optString("content") ?: ""
                    } else if (row1.length() == 1) {
                        views = row1.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: ""
                    }
                }
            }

            var thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val thumbSources = lvm.optJSONObject("contentImage")
                ?.optJSONObject("thumbnailViewModel")
                ?.optJSONObject("image")
                ?.optJSONArray("sources")
            if (thumbSources != null && thumbSources.length() > 0) {
                var chosenThumb = ""
                for (ti in 0 until thumbSources.length()) {
                    val srcObj = thumbSources.optJSONObject(ti) ?: continue
                    val u = srcObj.optString("url")
                    val w = srcObj.optInt("width", 0)
                    if (u.isNotBlank()) {
                        chosenThumb = u
                        if (w in 320..720) break
                    }
                }
                if (chosenThumb.isNotBlank()) {
                    thumb = InnerTubeHelpers.normalizeUrl(chosenThumb)
                }
            }

            // Extract duration from thumbnail overlays
            var duration = ""
            val overlays = lvm.optJSONObject("contentImage")
                ?.optJSONObject("thumbnailViewModel")
                ?.optJSONArray("overlays") ?: JSONArray()
            for (oi in 0 until overlays.length()) {
                val ov = overlays.optJSONObject(oi) ?: continue
                val bottomOv = ov.optJSONObject("thumbnailBottomOverlayViewModel")
                if (bottomOv != null) {
                    val badges = bottomOv.optJSONArray("badges") ?: JSONArray()
                    for (bi in 0 until badges.length()) {
                        val badge = badges.optJSONObject(bi)?.optJSONObject("thumbnailBadgeViewModel")
                        val text = badge?.optString("text") ?: ""
                        if (text.isNotBlank()) {
                            duration = text
                            break
                        }
                    }
                }
                if (duration.isBlank()) {
                    val timeStatus = ov.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                    val text = timeStatus?.optJSONObject("text")?.optString("simpleText") ?: ""
                    if (text.isNotBlank()) {
                        duration = text
                    }
                }
                if (duration.isNotBlank()) break
            }
            if (isPlaylist && duration.isBlank()) {
                duration = "Playlist"
            }

            // Extract channelId from lvm: direct inspection from metadataRows commandRuns, then regex fallback
            var channelId = ""
            for (ri in 0 until metaRows.length()) {
                val parts = metaRows.optJSONObject(ri)?.optJSONArray("metadataParts") ?: continue
                for (pi in 0 until parts.length()) {
                    val textObj = parts.optJSONObject(pi)?.optJSONObject("text") ?: continue
                    val cmdRuns = textObj.optJSONArray("commandRuns") ?: continue
                    for (ci in 0 until cmdRuns.length()) {
                        val bId = cmdRuns.optJSONObject(ci)?.optJSONObject("onTap")
                            ?.optJSONObject("innertubeCommand")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId", "") ?: ""
                        if (bId.startsWith("UC")) {
                            channelId = bId
                            break
                        }
                    }
                    if (channelId.isNotBlank()) break
                }
                if (channelId.isNotBlank()) break
            }
            if (channelId.isBlank()) {
                val bIdMatch = BROWSE_UC_ID_REGEX.find(lvm.toString())
                if (bIdMatch != null) {
                    channelId = bIdMatch.groupValues[1]
                }
            }

            var avatar = InnerTubeHelpers.normalizeUrl(InnerTubeHelpers.extractAvatar(lvm))
            if (avatar.isNotBlank()) {
                ChannelAvatarCache.put(channelId, channel, avatar)
            } else {
                avatar = ChannelAvatarCache.get(channelId, channel)
            }

            return VideoItem(
                id = videoId,
                title = title,
                channelTitle = channel,
                channelId = channelId,
                thumbnailUrl = thumb,
                channelAvatarUrl = avatar,
                durationText = duration,
                viewCountText = InnerTubeHelpers.normalizeViewCount(views),
                publishedTimeText = InnerTubeHelpers.normalizePublishedTime(published),
                playlistId = playlistId.ifBlank { null },
                isPlaylist = isPlaylist
            )
        }

        return null
    }
}
