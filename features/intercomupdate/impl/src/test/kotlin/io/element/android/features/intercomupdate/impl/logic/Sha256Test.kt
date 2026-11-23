/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256Test {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `the digest of a file is the known one`() {
        val file = temporaryFolder.newFile().apply { writeText("abc") }
        assertThat(Sha256.of(file)).isEqualTo(A_SHA256)
        assertThat(Sha256.of("abc".toByteArray())).isEqualTo(A_SHA256)
    }

    @Test
    fun `a file matches its checksum in either case and nothing else`() {
        val file = temporaryFolder.newFile().apply { writeText("abc") }
        assertThat(Sha256.matches(file, A_SHA256)).isTrue()
        assertThat(Sha256.matches(file, A_SHA256.uppercase())).isTrue()
        assertThat(Sha256.matches(file, A_SHA256.replaceFirst('b', 'c'))).isFalse()
        assertThat(Sha256.matches(file, "")).isFalse()
        assertThat(Sha256.matches(file, A_SHA256.take(32))).isFalse()
        file.appendText("d")
        assertThat(Sha256.matches(file, A_SHA256)).isFalse()
    }

    @Test
    fun `a checksum is found in SHA256SUMS by the exact file name`() {
        val other = "0".repeat(64)
        val sums = """
            $other  InterCom-Android-26.11.1-intercom.4-arm64-v8a.apk.idsig
            $A_SHA256  InterCom-Android-26.11.1-intercom.4-arm64-v8a.apk
            $other *android-update.json
            not-a-hash  broken.apk
        """.trimIndent()
        assertThat(Sha256.findInSums(sums, "InterCom-Android-26.11.1-intercom.4-arm64-v8a.apk")).isEqualTo(A_SHA256)
        assertThat(Sha256.findInSums(sums, "android-update.json")).isEqualTo(other)
        assertThat(Sha256.findInSums(sums, "broken.apk")).isNull()
        assertThat(Sha256.findInSums(sums, "missing.apk")).isNull()
        assertThat(Sha256.findInSums("", "missing.apk")).isNull()
    }
}
