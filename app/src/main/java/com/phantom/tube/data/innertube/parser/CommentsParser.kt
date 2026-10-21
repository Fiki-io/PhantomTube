package com.phantom.tube.data.innertube.parser

import com.phantom.tube.data.model.CommentsResult
import com.phantom.tube.data.model.VideoComment
import org.json.JSONArray
import org.json.JSONObject

object CommentsParser {

    fun parseComments(jsonString: String): CommentsResult {
        val comments = mutableListOf<VideoComment>()
        var totalCountText = ""
        var nextContinuationToken: String? = null

        try {
            val root = JSONObject(jsonString)

            // 1. Check endpoints for commentsHeaderRenderer, continuationItemRenderer, and classic commentThreadRenderer
            val endpoints = root.optJSONArray("onResponseReceivedEndpoints")
                ?: root.optJSONArray("onResponseReceivedCommands")
                ?: JSONArray()

            for (i in 0 until endpoints.length()) {
                val ep = endpoints.optJSONObject(i) ?: continue
                val cmd = ep.optJSONObject("reloadContinuationItemsCommand")
                    ?: ep.optJSONObject("appendContinuationItemsAction")
                    ?: continue
                val items = cmd.optJSONArray("continuationItems") ?: JSONArray()
                for (j in 0 until items.length()) {
                    val it = items.optJSONObject(j) ?: continue
                    val header = it.optJSONObject("commentsHeaderRenderer")
                    if (header != null) {
                        val countObj = header.optJSONObject("countText")
                        totalCountText = InnerTubeHelpers.parseRunsText(countObj).ifBlank {
                            countObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                        }
                    }
                    val continuationItem = it.optJSONObject("continuationItemRenderer")
                    if (continuationItem != null) {
                        val token = continuationItem.optJSONObject("continuationEndpoint")
                            ?.optJSONObject("continuationCommand")
                            ?.optString("token")
                        if (!token.isNullOrBlank()) {
                            nextContinuationToken = token
                        }
                    }
                    val thread = it.optJSONObject("commentThreadRenderer")
                    if (thread != null) {
                        val cr = thread.optJSONObject("comment")?.optJSONObject("commentRenderer")
                        if (cr != null) {
                            val cId = cr.optString("commentId")
                            val author = InnerTubeHelpers.parseRunsText(cr.optJSONObject("authorText"))
                            val authorChId = cr.optJSONObject("authorEndpoint")
                                ?.optJSONObject("browseEndpoint")
                                ?.optString("browseId") ?: ""
                            val avatar = InnerTubeHelpers.extractThumbnail(cr.optJSONObject("authorThumbnail"), "")
                            val published = InnerTubeHelpers.parseRunsText(cr.optJSONObject("publishedTimeText"))
                            val content = InnerTubeHelpers.parseRunsText(cr.optJSONObject("contentText"))
                            val likes = InnerTubeHelpers.parseRunsText(cr.optJSONObject("voteCount"))
                            val replies = cr.optInt("replyCount", 0).let { count -> if (count > 0) count.toString() else "" }
                            if (content.isNotBlank()) {
                                comments.add(
                                    VideoComment(
                                        id = cId,
                                        authorName = author,
                                        authorHandle = author,
                                        authorChannelId = authorChId,
                                        authorAvatarUrl = avatar,
                                        publishedTimeText = published,
                                        contentText = content,
                                        likeCountText = likes,
                                        replyCountText = replies
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Parse modern framework mutations only if primary endpoints didn't yield comments
            val finalComments = if (comments.isNotEmpty()) {
                comments
            } else {
                val mutations = root.optJSONObject("frameworkUpdates")
                    ?.optJSONObject("entityBatchUpdate")
                    ?.optJSONArray("mutations") ?: JSONArray()

                val mutationComments = mutableListOf<VideoComment>()
                for (i in 0 until mutations.length()) {
                    val m = mutations.optJSONObject(i) ?: continue
                    val cep = m.optJSONObject("payload")?.optJSONObject("commentEntityPayload") ?: continue
                    val props = cep.optJSONObject("properties")
                    val author = cep.optJSONObject("author")
                    val toolbar = cep.optJSONObject("toolbar")

                    val cId = props?.optString("commentId") ?: ""
                    val content = InnerTubeHelpers.parseRunsText(props?.optJSONObject("content"))
                    val published = props?.optString("publishedTime") ?: ""
                    val displayName = author?.optString("displayName") ?: ""
                    val authorChId = author?.optString("channelId") ?: ""
                    val avatarUrl = author?.optString("avatarThumbnailUrl") ?: ""
                    val likes = toolbar?.optString("likeCountNotliked") ?: ""
                    val replies = toolbar?.optString("replyCount") ?: ""

                    if (content.isNotBlank()) {
                        mutationComments.add(
                            VideoComment(
                                id = cId,
                                authorName = displayName,
                                authorHandle = displayName,
                                authorChannelId = authorChId,
                                authorAvatarUrl = avatarUrl,
                                publishedTimeText = published,
                                contentText = content,
                                likeCountText = likes,
                                replyCountText = replies
                            )
                        )
                    }
                }
                mutationComments
            }

            return CommentsResult(
                comments = finalComments,
                totalCountText = totalCountText,
                continuationToken = nextContinuationToken
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return CommentsResult()
        }
    }
}
