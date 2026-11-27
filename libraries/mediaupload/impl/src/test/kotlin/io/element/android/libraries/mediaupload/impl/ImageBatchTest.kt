/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.media.ImageInfo
import io.element.android.libraries.matrix.api.media.ThumbnailInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ImageBatchTest {
    private val info = ImageInfo(
        height = 600,
        width = 800,
        mimetype = "image/jpeg",
        size = 1234,
        thumbnailInfo = ThumbnailInfo(height = 60, width = 80, mimetype = "image/jpeg", size = 123),
        thumbnailSource = null,
        blurhash = "LEHV6nWB2yk8",
    )

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    @Test
    fun `an attachment is encrypted as the specification says and decrypts to what it was`() {
        val plain = ByteArray(100_000) { (it * 31).toByte() }
        val encrypted = EncryptedAttachment.encrypt(plain)
        assertThat(encrypted.data).hasLength(plain.size)
        assertThat(encrypted.data).isNotEqualTo(plain)

        // Unpadded base64, the key in the URL-safe alphabet.
        listOf(encrypted.key, encrypted.iv, encrypted.sha256).forEach { assertThat(it).doesNotContain("=") }
        assertThat(encrypted.key).containsMatch("^[A-Za-z0-9_-]{43}$")
        val key = Base64.getUrlDecoder().decode(encrypted.key)
        val iv = Base64.getDecoder().decode(encrypted.iv)
        assertThat(key).hasLength(32)
        assertThat(iv).hasLength(16)
        // The counter half of the IV starts at zero.
        assertThat(iv.drop(8)).containsExactlyElementsIn(List<Byte>(8) { 0 })
        assertThat(Base64.getDecoder().decode(encrypted.sha256)).isEqualTo(MessageDigest.getInstance("SHA-256").digest(encrypted.data))

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        assertThat(cipher.doFinal(encrypted.data)).isEqualTo(plain)

        // Every file has a key of its own.
        assertThat(EncryptedAttachment.encrypt(plain).key).isNotEqualTo(encrypted.key)
    }

    @Test
    fun `a picture of a batch is an ordinary image message with the batch's mark`() {
        val content = ImageBatch.content(
            batchId = "batch-1",
            index = 2,
            count = 5,
            filename = "IMG_1.jpg",
            caption = null,
            info = info,
            image = ImageBatch.Uploaded("mxc://example.org/image", null),
            thumbnail = ImageBatch.Uploaded("mxc://example.org/thumbnail", null),
        )
        // What is sent parses back.
        val parsed = Json.parseToJsonElement(content.toString()).jsonObject
        assertThat(parsed.string("msgtype")).isEqualTo("m.image")
        assertThat(parsed.string("body")).isEqualTo("IMG_1.jpg")
        assertThat(parsed.string("filename")).isEqualTo("IMG_1.jpg")
        assertThat(parsed.string("url")).isEqualTo("mxc://example.org/image")
        assertThat(parsed).doesNotContainKey("file")
        val mark = parsed.getValue("io.intercom.gallery").jsonObject
        assertThat(mark.string("id")).isEqualTo("batch-1")
        assertThat(mark.getValue("index").jsonPrimitive.int).isEqualTo(2)
        assertThat(mark.getValue("count").jsonPrimitive.int).isEqualTo(5)
        val parsedInfo = parsed.getValue("info").jsonObject
        assertThat(parsedInfo.getValue("w").jsonPrimitive.int).isEqualTo(800)
        assertThat(parsedInfo.getValue("h").jsonPrimitive.int).isEqualTo(600)
        assertThat(parsedInfo.string("mimetype")).isEqualTo("image/jpeg")
        assertThat(parsedInfo.string("xyz.amorgan.blurhash")).isEqualTo("LEHV6nWB2yk8")
        assertThat(parsedInfo.string("thumbnail_url")).isEqualTo("mxc://example.org/thumbnail")
        assertThat(parsedInfo.getValue("thumbnail_info").jsonObject.getValue("w").jsonPrimitive.int).isEqualTo(80)
    }

    @Test
    fun `in an encrypted room the files are file objects, and a caption is the body beside the file name`() {
        val encrypted = EncryptedAttachment.encrypt(byteArrayOf(1, 2, 3))
        val content = ImageBatch.content(
            batchId = "batch-1",
            index = 0,
            count = 2,
            filename = "IMG_1.jpg",
            caption = "Two pictures",
            info = info,
            image = ImageBatch.Uploaded("mxc://example.org/image", encrypted),
            thumbnail = ImageBatch.Uploaded("mxc://example.org/thumbnail", encrypted),
        )
        assertThat(content.string("body")).isEqualTo("Two pictures")
        assertThat(content.string("filename")).isEqualTo("IMG_1.jpg")
        assertThat(content).doesNotContainKey("url")
        val file = content.getValue("file").jsonObject
        assertThat(file.string("v")).isEqualTo("v2")
        assertThat(file.string("url")).isEqualTo("mxc://example.org/image")
        assertThat(file.string("iv")).isEqualTo(encrypted.iv)
        assertThat(file.getValue("hashes").jsonObject.string("sha256")).isEqualTo(encrypted.sha256)
        val key = file.getValue("key").jsonObject
        assertThat(key.string("kty")).isEqualTo("oct")
        assertThat(key.string("alg")).isEqualTo("A256CTR")
        assertThat(key.string("k")).isEqualTo(encrypted.key)
        assertThat(key.getValue("ext").jsonPrimitive.boolean).isTrue()
        assertThat(key.getValue("key_ops").jsonArray.map { it.jsonPrimitive.content }).containsExactly("encrypt", "decrypt")
        val infoObject = content.getValue("info").jsonObject
        assertThat(infoObject).doesNotContainKey("thumbnail_url")
        assertThat(infoObject.getValue("thumbnail_file").jsonObject.string("url")).isEqualTo("mxc://example.org/thumbnail")
    }
}
