/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

/** What the releases feed (`releases.atom`) answered. */
sealed interface FeedAnswer {
    /** "304 Not Modified": nothing was released, edited or deleted since the answer with the ETag that was sent. */
    data object NotModified : FeedAnswer

    /**
     * [fingerprint] tells the feed's Android releases and when each was last edited; the desktop's releases of the
     * same repository are not in it. [newestVersionCode] is the highest version code among them, 0 without any.
     */
    data class Changed(val etag: String?, val fingerprint: String, val newestVersionCode: Long) : FeedAnswer

    /** No answer, or one that is not the feed. */
    data object Failed : FeedAnswer
}

/**
 * What is remembered of the feed: the [etag] and [fingerprint] of the last answer that was acted on, and whether
 * the releases are to be asked for once more ([retryPending]) because the feed showed a newer release that the API,
 * which may answer from a copy that is a minute old, did not have yet.
 */
data class FeedMemory(
    val etag: String? = null,
    val fingerprint: String? = null,
    val retryPending: Boolean = false,
)

object ReleaseFeed {
    const val URL = "https://github.com/c4g7-dev/InterCom/releases.atom"

    private val entry = Regex("""<entry[\s>].*?</entry>""", RegexOption.DOT_MATCHES_ALL)
    private val tag = Regex("""/releases/tag/([A-Za-z0-9._%+-]+)["'<\s]""")
    private val updated = Regex("""<updated>\s*([^<\s]+)\s*</updated>""")

    /** The Android releases of the feed's text, as `tag@updated`, newest version first. */
    fun androidEntries(xml: String): List<Pair<InterComVersion, String>> {
        return entry.findAll(xml)
            .mapNotNull { match ->
                val name = tag.find(match.value)?.groupValues?.get(1) ?: return@mapNotNull null
                val version = InterComVersion.parseTag(name) ?: return@mapNotNull null
                Pair(version, updated.find(match.value)?.groupValues?.get(1).orEmpty())
            }
            .distinctBy { it.first }
            .sortedByDescending { it.first }
            .toList()
    }

    /**
     * Reads the feed's text.
     *
     * @throws IllegalArgumentException when it is not an Atom feed.
     */
    fun parse(xml: String, etag: String?): FeedAnswer.Changed {
        require(xml.contains("<feed")) { "not an Atom feed" }
        val entries = androidEntries(xml)
        return FeedAnswer.Changed(
            etag = etag,
            fingerprint = entries.joinToString(",") { (version, updated) -> "${version.name}@$updated" },
            newestVersionCode = entries.firstOrNull()?.first?.versionCode() ?: 0,
        )
    }
}
