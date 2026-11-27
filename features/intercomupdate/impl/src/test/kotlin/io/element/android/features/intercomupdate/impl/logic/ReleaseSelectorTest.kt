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

class ReleaseSelectorTest {
    @Test
    fun `the newest Android release is selected, the desktop's are ignored`() {
        val releases = listOf(
            aRelease("v1.12.35-intercom.5", assets = listOf(anAsset("v1.12.35-intercom.5", "InterCom-Setup-1.12.35-intercom.5-x64.exe"))),
            anAndroidRelease("26.11.1-intercom.4"),
            anAndroidRelease("26.11.1-intercom.5"),
            anAndroidRelease("26.11.1-intercom.3"),
        )
        val selected = ReleaseSelector.select(releases, currentVersionCode = 2_026_110_123, includePrereleases = false)
        assertThat(selected?.version?.name).isEqualTo("26.11.1-intercom.5")
        assertThat(selected?.apk?.name).isEqualTo("InterCom-Android-26.11.1-intercom.5-arm64-v8a.apk")
        assertThat(selected?.manifest).isNotNull()
    }

    @Test
    fun `nothing is selected when the running version is the newest`() {
        val releases = listOf(anAndroidRelease("26.11.1-intercom.3"), anAndroidRelease("26.11.1-intercom.4"))
        assertThat(ReleaseSelector.select(releases, 2_026_110_124, includePrereleases = true)).isNull()
        assertThat(ReleaseSelector.select(emptyList(), 2_026_110_124, includePrereleases = true)).isNull()
    }

    @Test
    fun `drafts never count, pre-releases only when asked for`() {
        val releases = listOf(
            anAndroidRelease("26.11.1-intercom.4"),
            anAndroidRelease("26.11.1-intercom.5", prerelease = true),
            anAndroidRelease("26.11.1-intercom.6", draft = true),
        )
        assertThat(ReleaseSelector.select(releases, 2_026_110_123, includePrereleases = false)?.version?.revision).isEqualTo(4)
        assertThat(ReleaseSelector.select(releases, 2_026_110_123, includePrereleases = true)?.version?.revision).isEqualTo(5)
    }

    @Test
    fun `a release without the APK or without a checksum is passed over`() {
        val noApk = aRelease("android-v26.11.1-intercom.6", assets = listOf(anAsset("android-v26.11.1-intercom.6", "android-update.json")))
        val noChecksum = aRelease(
            "android-v26.11.1-intercom.5",
            assets = listOf(anAsset("android-v26.11.1-intercom.5", "InterCom-Android-26.11.1-intercom.5-arm64-v8a.apk")),
        )
        val releases = listOf(noApk, noChecksum, anAndroidRelease("26.11.1-intercom.4"))
        assertThat(ReleaseSelector.select(releases, 2_026_110_123, includePrereleases = false)?.version?.revision).isEqualTo(4)
    }

    @Test
    fun `files that are not served from the InterCom releases do not count`() {
        val name = "InterCom-Android-26.11.1-intercom.5-arm64-v8a.apk"
        val foreign = aRelease(
            "android-v26.11.1-intercom.5",
            assets = listOf(
                GitHubAsset(name, 1, "https://example.org/$name"),
                GitHubAsset("android-update.json", 1, "http://github.com/c4g7-dev/InterCom/releases/download/x/android-update.json"),
            ),
        )
        assertThat(ReleaseSelector.select(listOf(foreign), 2_026_110_123, includePrereleases = false)).isNull()
    }

    @Test
    fun `the manifest gives the candidate its version code, checksum and notes`() {
        val selected = ReleaseSelector.select(listOf(anAndroidRelease("26.11.1-intercom.4")), 2_026_110_123, false)!!
        val candidate = ReleaseSelector.toCandidate(selected, aManifest("26.11.1-intercom.4"), null, PACKAGE, 2_026_110_123)
        assertThat(candidate.versionCode).isEqualTo(2_026_110_124L)
        assertThat(candidate.versionName).isEqualTo("26.11.1-intercom.4")
        assertThat(candidate.sha256).isEqualTo(A_SHA256)
        assertThat(candidate.apkSize).isEqualTo(1234)
        assertThat(candidate.notes).isEqualTo("Notes")
        assertThat(candidate.apkUrl).startsWith(ReleaseSelector.DOWNLOAD_PREFIX)
    }

    @Test
    fun `a manifest that does not fit the release is refused`() {
        val selected = ReleaseSelector.select(listOf(anAndroidRelease("26.11.1-intercom.4")), 2_026_110_123, false)!!
        val good = aManifest("26.11.1-intercom.4")
        val bad = listOf(
            good.copy(packageName = "io.element.android.x"),
            good.copy(apk = "other.apk"),
            good.copy(versionName = "26.11.1-intercom.5"),
            good.copy(versionCode = 2_026_110_123),
            good.copy(sha256 = "abc"),
            good.copy(schema = 2),
        )
        bad.forEach { manifest ->
            assertThrows(IllegalArgumentException::class.java) {
                ReleaseSelector.toCandidate(selected, manifest, null, PACKAGE, 2_026_110_123)
            }
        }
    }

    @Test
    fun `without a manifest the checksum comes from SHA256SUMS and the version code from the tag`() {
        val selected = ReleaseSelector.select(listOf(anAndroidRelease("26.11.1-intercom.4", withManifest = false)), 2_026_110_123, false)!!
        assertThat(selected.manifest).isNull()
        val sums = "${A_SHA256.uppercase()}  InterCom-Android-26.11.1-intercom.4-arm64-v8a.apk\n"
        val candidate = ReleaseSelector.toCandidate(selected, null, sums, PACKAGE, 2_026_110_123)
        assertThat(candidate.versionCode).isEqualTo(2_026_110_124L)
        assertThat(candidate.sha256).isEqualTo(A_SHA256)
        assertThat(candidate.notes).isEqualTo("Body")
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseSelector.toCandidate(selected, null, "$A_SHA256  another-file.apk\n", PACKAGE, 2_026_110_123)
        }
    }

    @Test
    fun `the notes of an update are those of every release it skips, newest first`() {
        val releases = listOf(
            anAndroidRelease("26.11.1-intercom.5").copy(body = "five"),
            aRelease("v1.12.35-intercom.14").copy(body = "desktop"),
            anAndroidRelease("26.11.1-intercom.14").copy(body = "fourteen"),
            anAndroidRelease("26.11.1-intercom.7").copy(body = "seven"),
            anAndroidRelease("26.11.1-intercom.9", prerelease = true).copy(body = "nine, a pre-release"),
            anAndroidRelease("26.11.1-intercom.8", draft = true).copy(body = "a draft"),
            anAndroidRelease("26.11.1-intercom.6").copy(body = "  "),
            anAndroidRelease("26.11.2-intercom.1").copy(body = "newer than the update"),
        )
        val notes = ReleaseSelector.notes(releases, afterVersionCode = 2_026_110_125, upToVersionCode = 2_026_110_134, includePrereleases = false)
        assertThat(notes).containsExactly(
            ReleaseNote("26.11.1-intercom.14", 2_026_110_134, "fourteen"),
            ReleaseNote("26.11.1-intercom.7", 2_026_110_127, "seven"),
        ).inOrder()
        val withPrereleases = ReleaseSelector.notes(releases, 2_026_110_125, 2_026_110_134, includePrereleases = true)
        assertThat(withPrereleases.map { it.versionName }).containsExactly("26.11.1-intercom.14", "26.11.1-intercom.9", "26.11.1-intercom.7").inOrder()
    }
}

const val PACKAGE = "com.c4g7.intercom.android"
const val A_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

fun anAsset(tag: String, name: String, size: Long = 1234) = GitHubAsset(name, size, "${ReleaseSelector.DOWNLOAD_PREFIX}$tag/$name")

fun aRelease(
    tag: String,
    draft: Boolean = false,
    prerelease: Boolean = false,
    assets: List<GitHubAsset> = emptyList(),
) = GitHubRelease(tagName = tag, draft = draft, prerelease = prerelease, body = "Body", htmlUrl = null, assets = assets)

fun anAndroidRelease(
    versionName: String,
    draft: Boolean = false,
    prerelease: Boolean = false,
    withManifest: Boolean = true,
): GitHubRelease {
    val tag = "android-v$versionName"
    return aRelease(
        tag = tag,
        draft = draft,
        prerelease = prerelease,
        assets = listOfNotNull(
            anAsset(tag, "InterCom-Android-$versionName-arm64-v8a.apk"),
            anAsset(tag, "SHA256SUMS"),
            anAsset(tag, "android-update.json").takeIf { withManifest },
        ),
    )
}

fun aManifest(versionName: String, sha256: String = A_SHA256, size: Long = 1234) = UpdateManifest(
    packageName = PACKAGE,
    versionCode = InterComVersion.parse(versionName)!!.versionCode(),
    versionName = versionName,
    apk = "InterCom-Android-$versionName-arm64-v8a.apk",
    sha256 = sha256,
    size = size,
    abi = "arm64-v8a",
    minSdk = 24,
    notes = "Notes",
)
