/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.rageshake.impl.logs

import com.google.common.truth.Truth.assertThat
import io.element.android.features.rageshake.api.logs.LogRedaction.REDACTED
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

class DefaultLogsSharerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `the zip holds every file, oldest first, with text files redacted`() {
        val older = temporaryFolder.newFile("logs.2026-11-24-15").apply {
            writeText("first line\naccess_token=abcdefghijkl\n")
            setLastModified(1_000_000)
        }
        val gzipped = ByteArrayOutputStream().also { bytes -> GZIPOutputStream(bytes).use { it.write("syt_secret_secret_secret".toByteArray()) } }.toByteArray()
        val compressed = temporaryFolder.newFile("logs.2026-11-24-14.gz").apply {
            writeBytes(gzipped)
            setLastModified(500_000)
        }
        val newer = temporaryFolder.newFile("logcat.log").apply {
            writeText("ElementCall: the call ended, Left")
            setLastModified(2_000_000)
        }

        val output = ByteArrayOutputStream()
        zipRedactedLogs(listOf(newer, older, compressed), output)

        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += entry.name to zip.readBytes()
            }
        }
        assertThat(entries.map { it.first }).containsExactly(compressed.name, older.name, newer.name).inOrder()
        assertThat(entries[0].second).isEqualTo(gzipped)
        assertThat(String(entries[1].second)).isEqualTo("first line\naccess_token=$REDACTED\n")
        assertThat(String(entries[2].second)).isEqualTo("ElementCall: the call ended, Left\n")
    }
}
