package com.phantom.tube.ui.screens.player.components

import com.phantom.tube.data.model.SponsorSegment
import com.phantom.tube.data.settings.PhantomPreferences

data class SponsorSkipMatch(
    val uuid: String,
    val category: String,
    val startSecond: Float,
    val endSecond: Float,
    val skippedSeconds: Int,
    val autoSkip: Boolean
)

object SponsorBlockEvaluator {

    fun findMatchingSegment(
        currentSec: Float,
        durationSec: Float,
        segments: List<SponsorSegment>,
        preferences: PhantomPreferences?,
        skippedUuids: Set<String>,
        undoneUuids: Set<String>
    ): SponsorSkipMatch? {
        val sbEnabled = preferences?.sponsorBlockEnabled?.value ?: true
        if (!sbEnabled || durationSec <= 0f || currentSec >= durationSec || segments.isEmpty()) {
            return null
        }

        val skipSponsor = preferences?.skipSponsor?.value ?: true
        val skipSelfPromo = preferences?.skipSelfPromo?.value ?: true
        val skipInteraction = preferences?.skipInteraction?.value ?: true
        val skipIntro = preferences?.skipIntro?.value ?: true
        val skipOutro = preferences?.skipOutro?.value ?: true
        val autoSkip = preferences?.sponsorBlockAutoSkip?.value ?: true

        for (seg in segments) {
            val isCategoryAllowed = when (seg.category) {
                "sponsor" -> skipSponsor
                "selfpromo" -> skipSelfPromo
                "interaction" -> skipInteraction
                "intro" -> skipIntro
                "outro" -> skipOutro
                else -> true
            }
            if (!isCategoryAllowed) continue

            val uuid = if (seg.uuid.isNotBlank()) {
                seg.uuid
            } else {
                "${seg.category}_${seg.startSecond}_${seg.endSecond}"
            }
            if (uuid in skippedUuids || uuid in undoneUuids) continue

            val isValidLength = seg.endSecond > (seg.startSecond + 0.5f) && seg.startSecond >= 0f
            val isOutroValid = if (seg.category == "outro") {
                durationSec >= 20f &&
                    seg.startSecond >= (durationSec * 0.65f) &&
                    seg.startSecond >= 15f &&
                    currentSec >= (durationSec * 0.60f)
            } else {
                true
            }
            val isIntroValid = if (seg.category == "intro") {
                seg.startSecond < (durationSec * 0.40f) &&
                    seg.startSecond < 300f &&
                    seg.endSecond <= (durationSec * 0.55f)
            } else {
                true
            }
            val isNotEntireVideo = (seg.endSecond - seg.startSecond) < (durationSec * 0.85f)

            if (isValidLength && isOutroValid && isIntroValid && isNotEntireVideo &&
                currentSec >= seg.startSecond && currentSec < (seg.endSecond - 0.5f)
            ) {
                return SponsorSkipMatch(
                    uuid = uuid,
                    category = seg.category,
                    startSecond = seg.startSecond,
                    endSecond = seg.endSecond,
                    skippedSeconds = (seg.endSecond - seg.startSecond).toInt().coerceAtLeast(1),
                    autoSkip = autoSkip
                )
            }
        }
        return null
    }
}
