/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InterComVersionTest {
    @Test
    fun `a version name is parsed and written back`() {
        val version = InterComVersion.parse("26.11.1-intercom.4")
        assertThat(version).isEqualTo(InterComVersion(26, 11, 1, 4))
        assertThat(version!!.name).isEqualTo("26.11.1-intercom.4")
        assertThat(InterComVersion(27, 1, 0, 1).name).isEqualTo("27.01.0-intercom.1")
    }

    @Test
    fun `the version code is the one the build gives the arm64 APK`() {
        // 26.11.1-intercom.3 was released with version code 2026110123.
        assertThat(InterComVersion(26, 11, 1, 3).versionCode()).isEqualTo(2_026_110_123L)
        assertThat(InterComVersion(26, 11, 1, 4).versionCode()).isEqualTo(2_026_110_124L)
        assertThat(InterComVersion(27, 1, 12, 1).versionCode()).isEqualTo(2_027_011_221L)
    }

    @Test
    fun `only tags of InterCom for Android are versions`() {
        assertThat(InterComVersion.parseTag("android-v26.11.1-intercom.4")).isEqualTo(InterComVersion(26, 11, 1, 4))
        // The desktop's releases live in the same repository.
        assertThat(InterComVersion.parseTag("v1.12.35-intercom.5")).isNull()
        assertThat(InterComVersion.parseTag("android-v26.11.1")).isNull()
        assertThat(InterComVersion.parseTag("android-v26.13.1-intercom.1")).isNull()
        assertThat(InterComVersion.parseTag("android-v26.11.1-intercom.10")).isNull()
        assertThat(InterComVersion.parseTag("android-v26.11.1-intercom.4-rc1")).isNull()
        assertThat(InterComVersion.parseTag("")).isNull()
    }

    @Test
    fun `versions are ordered as their version codes`() {
        val versions = listOf(
            InterComVersion(26, 11, 1, 3),
            InterComVersion(26, 11, 1, 4),
            InterComVersion(26, 11, 2, 1),
            InterComVersion(26, 12, 0, 1),
            InterComVersion(27, 1, 0, 1),
        )
        assertThat(versions.shuffled().sorted()).isEqualTo(versions)
        assertThat(versions.map { it.versionCode() }).isInStrictOrder()
    }
}
