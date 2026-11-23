/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

import java.io.File
import java.security.MessageDigest

object Sha256 {
    private val hex = Regex("[0-9a-fA-F]{64}")

    fun isHex(value: String) = hex.matches(value)

    fun of(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    /** Whether [file] has the SHA-256 [expected], given in hexadecimal of either case. */
    fun matches(file: File, expected: String): Boolean = isHex(expected) && of(file).equals(expected, ignoreCase = true)

    /**
     * The checksum of [fileName] in the text of a `SHA256SUMS` file (`<hex>  <name>` per line, as `sha256sum`
     * writes it, the name possibly with the `*` of binary mode), or null.
     */
    fun findInSums(sums: String, fileName: String): String? {
        return sums.lineSequence()
            .map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].removePrefix("*") == fileName && isHex(it[0]) }
            ?.get(0)
            ?.lowercase()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
