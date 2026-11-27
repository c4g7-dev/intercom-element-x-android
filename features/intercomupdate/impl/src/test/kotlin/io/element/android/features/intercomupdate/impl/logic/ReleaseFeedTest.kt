/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseFeedTest {
    private fun entry(tag: String, updated: String) = """
        <entry>
          <id>tag:github.com,2008:Repository/1/$tag</id>
          <updated>$updated</updated>
          <link rel="alternate" type="text/html" href="https://github.com/c4g7-dev/InterCom/releases/tag/$tag"/>
          <title>$tag</title>
          <content type="html">&lt;p&gt;android-v99.01.1-intercom.1 is only mentioned here&lt;/p&gt;</content>
        </entry>
    """.trimIndent()

    private fun feed(vararg entries: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <id>tag:github.com,2008:https://github.com/c4g7-dev/InterCom/releases</id>
          <updated>2026-11-27T10:00:00Z</updated>
          ${entries.joinToString("\n")}
        </feed>
    """.trimIndent()

    @Test
    fun `only the Android releases count, newest first, with the time of their last change`() {
        val answer = ReleaseFeed.parse(
            feed(
                entry("v1.12.35-intercom.13", "2026-11-27T09:00:00Z"),
                entry("android-v26.11.1-intercom.7", "2026-11-24T20:00:00Z"),
                entry("android-v26.11.1-intercom.14", "2026-11-27T18:00:00Z"),
                entry("android-v26.11.1-intercom.5", "2026-11-23T20:00:00Z"),
            ),
            etag = "W/\"abc\"",
        )
        assertThat(answer.etag).isEqualTo("W/\"abc\"")
        assertThat(answer.fingerprint).isEqualTo(
            "26.11.1-intercom.14@2026-11-27T18:00:00Z,26.11.1-intercom.7@2026-11-24T20:00:00Z,26.11.1-intercom.5@2026-11-23T20:00:00Z"
        )
        assertThat(answer.newestVersionCode).isEqualTo(2_026_110_134L)
    }

    @Test
    fun `a desktop release leaves the fingerprint as it was, an edited Android release changes it`() {
        val before = ReleaseFeed.parse(feed(entry("android-v26.11.1-intercom.7", "2026-11-24T20:00:00Z")), null)
        val withDesktop = ReleaseFeed.parse(
            feed(entry("v1.12.35-intercom.14", "2026-11-27T19:00:00Z"), entry("android-v26.11.1-intercom.7", "2026-11-24T20:00:00Z")),
            null,
        )
        val edited = ReleaseFeed.parse(feed(entry("android-v26.11.1-intercom.7", "2026-11-25T08:00:00Z")), null)
        assertThat(withDesktop.fingerprint).isEqualTo(before.fingerprint)
        assertThat(edited.fingerprint).isNotEqualTo(before.fingerprint)
    }

    @Test
    fun `a feed without Android releases is empty, and what is not a feed is refused`() {
        val answer = ReleaseFeed.parse(feed(entry("v1.12.35-intercom.13", "2026-11-27T09:00:00Z")), null)
        assertThat(answer.fingerprint).isEmpty()
        assertThat(answer.newestVersionCode).isEqualTo(0)
        assertThrows(IllegalArgumentException::class.java) { ReleaseFeed.parse("<html><body>Sign in</body></html>", null) }
    }
}
