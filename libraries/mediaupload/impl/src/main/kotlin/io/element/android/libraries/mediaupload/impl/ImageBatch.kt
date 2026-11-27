/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import io.element.android.libraries.matrix.api.media.ImageInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * InterCom: the pictures chosen together are sent as one batch, each an ordinary `m.image` message that carries
 * `io.intercom.gallery: { id, index, count }`, the mark InterCom on the desktop writes and reads: pictures with the
 * same `id` are one gallery however long their uploads took. Other clients ignore the field.
 *
 * The SDK's typed senders have no place for such a field, so these messages are written out here and sent raw,
 * and for an encrypted room the files are encrypted here, as the Matrix specification describes for attachments.
 */
object ImageBatch {
    const val FIELD = "io.intercom.gallery"
    const val EVENT_TYPE = "m.room.message"

    /** A file as it was uploaded: [url] is its `mxc://` address, [encryption] what an encrypted room needs to read it. */
    class Uploaded(val url: String, val encryption: EncryptedAttachment?)

    /**
     * The content of the `m.image` message of picture [index] of [count] in batch [batchId].
     * A [caption] makes the message say so the way MSC2530 has it: the caption as `body`, the name as `filename`.
     */
    fun content(
        batchId: String,
        index: Int,
        count: Int,
        filename: String,
        caption: String?,
        info: ImageInfo,
        image: Uploaded,
        thumbnail: Uploaded?,
    ): JsonObject = buildJsonObject {
        put("msgtype", "m.image")
        put("body", caption?.takeIf { it.isNotBlank() } ?: filename)
        put("filename", filename)
        putSource("url", "file", image)
        putJsonObject("info") {
            info.mimetype?.let { put("mimetype", it) }
            info.size?.let { put("size", it) }
            info.width?.let { put("w", it) }
            info.height?.let { put("h", it) }
            info.blurhash?.let { put("xyz.amorgan.blurhash", it) }
            if (thumbnail != null) {
                putSource("thumbnail_url", "thumbnail_file", thumbnail)
                info.thumbnailInfo?.let { thumbnailInfo ->
                    putJsonObject("thumbnail_info") {
                        thumbnailInfo.mimetype?.let { put("mimetype", it) }
                        thumbnailInfo.size?.let { put("size", it) }
                        thumbnailInfo.width?.let { put("w", it) }
                        thumbnailInfo.height?.let { put("h", it) }
                    }
                }
            }
        }
        putJsonObject(FIELD) {
            put("id", batchId)
            put("index", index)
            put("count", count)
        }
    }

    private fun JsonObjectBuilder.putSource(plainKey: String, encryptedKey: String, uploaded: Uploaded) {
        val encryption = uploaded.encryption
        if (encryption == null) {
            put(plainKey, uploaded.url)
        } else {
            putJsonObject(encryptedKey) {
                put("v", "v2")
                put("url", uploaded.url)
                putJsonObject("key") {
                    put("kty", "oct")
                    putJsonArray("key_ops") {
                        add("encrypt")
                        add("decrypt")
                    }
                    put("alg", "A256CTR")
                    put("k", encryption.key)
                    put("ext", true)
                }
                put("iv", encryption.iv)
                putJsonObject("hashes") { put("sha256", encryption.sha256) }
            }
        }
    }
}

/**
 * A file encrypted as an attachment (Matrix specification, "Sending encrypted attachments"): AES-256 in CTR mode
 * with a key of its own, and the SHA-256 of what was encrypted. [key] is base64url, [iv] and [sha256] base64, all
 * without padding, as the `file` object of a message has them.
 */
class EncryptedAttachment(
    val data: ByteArray,
    val key: String,
    val iv: String,
    val sha256: String,
) {
    companion object {
        private const val KEY_SIZE = 32
        private const val IV_SIZE = 16
        private const val NONCE_SIZE = 8

        fun encrypt(plain: ByteArray, random: SecureRandom = SecureRandom()): EncryptedAttachment {
            val key = ByteArray(KEY_SIZE).also(random::nextBytes)
            // The upper half is random, the lower half the counter, which starts at zero.
            val iv = ByteArray(IV_SIZE)
            ByteArray(NONCE_SIZE).also(random::nextBytes).copyInto(iv)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val data = cipher.doFinal(plain)
            val hash = MessageDigest.getInstance("SHA-256").digest(data)
            return EncryptedAttachment(
                data = data,
                key = Base64.getUrlEncoder().withoutPadding().encodeToString(key),
                iv = Base64.getEncoder().withoutPadding().encodeToString(iv),
                sha256 = Base64.getEncoder().withoutPadding().encodeToString(hash),
            )
        }
    }
}
