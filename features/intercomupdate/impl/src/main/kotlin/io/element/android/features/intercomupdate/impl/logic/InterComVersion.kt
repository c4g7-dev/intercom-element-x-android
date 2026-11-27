/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

/**
 * An InterCom for Android version, `<yy>.<mm>.<n>-intercom.<revision>`: the Element X version it is based on and
 * the revision of the fork.
 *
 * The version code follows from it, as `plugins/src/main/kotlin/Versions.kt` and `app/build.gradle.kts` compute it:
 * `((2000 + yy) * 10_000 + mm * 100 + n) * 100 + abi * 10 + revision`, e.g. `26.11.1-intercom.4` for arm64-v8a
 * (ABI digit 2) is `2026110124`, and `26.11.1-intercom.14` is `2026110134`: a revision of two digits runs into the
 * ABI's digit, which is the same in every release, so the codes keep rising up to revision 79. A release states
 * its version code in `android-update.json`; this derivation is what orders the releases before that file is
 * read, and what a release without the file falls back to.
 */
data class InterComVersion(
    val year: Int,
    val month: Int,
    val release: Int,
    val revision: Int,
) : Comparable<InterComVersion> {
    val name: String
        get() = "$year.${month.toString().padStart(2, '0')}.$release-intercom.$revision"

    fun versionCode(abiCode: Int = ABI_CODE_ARM64): Long {
        val base = (2000L + year) * 10_000 + month * 100 + release
        return base * 100 + abiCode * 10 + revision
    }

    override fun compareTo(other: InterComVersion): Int = compareValuesBy(
        this,
        other,
        InterComVersion::year,
        InterComVersion::month,
        InterComVersion::release,
        InterComVersion::revision,
    )

    companion object {
        const val TAG_PREFIX = "android-v"

        /** The ABI digit of the one APK there is, arm64-v8a. */
        const val ABI_CODE_ARM64 = 2

        /** The last revision whose version code is below the next upstream version's first. */
        const val MAX_REVISION = 79

        private val pattern = Regex("""(\d{2})\.(\d{1,2})\.(\d{1,2})-intercom\.(\d{1,2})""")

        /** Parses a version name, or returns null when it is not one of InterCom for Android. */
        fun parse(name: String): InterComVersion? {
            val match = pattern.matchEntire(name.trim()) ?: return null
            val (year, month, release, revision) = match.destructured
            return InterComVersion(year.toInt(), month.toInt(), release.toInt(), revision.toInt())
                .takeIf { it.month in 1..12 && it.revision <= MAX_REVISION }
        }

        /**
         * Parses a release tag, `android-v<version name>`. The desktop's releases are tagged `v<version>` and give null.
         */
        fun parseTag(tag: String): InterComVersion? {
            if (!tag.startsWith(TAG_PREFIX)) return null
            return parse(tag.removePrefix(TAG_PREFIX))
        }
    }
}
