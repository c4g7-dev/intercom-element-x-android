/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ApkVerifierTest {
    private val installed = ApkInfo(PACKAGE, 2_026_110_123, setOf("release-key"))
    private val update = ApkInfo(PACKAGE, 2_026_110_124, setOf("release-key"))

    @Test
    fun `a newer version of the same app with the same certificate is accepted`() {
        assertThat(ApkVerifier.check(update, installed, 2_026_110_124)).isNull()
    }

    @Test
    fun `another certificate is refused`() {
        assertThat(ApkVerifier.check(update.copy(signers = setOf("another-key")), installed, 2_026_110_124))
            .isEqualTo(ApkRejection.SignatureMismatch)
        // An additional signer is another set of signers.
        assertThat(ApkVerifier.check(update.copy(signers = setOf("release-key", "another-key")), installed, 2_026_110_124))
            .isEqualTo(ApkRejection.SignatureMismatch)
    }

    @Test
    fun `certificates that cannot be read are no certificates`() {
        assertThat(ApkVerifier.check(update.copy(signers = emptySet()), installed, 2_026_110_124)).isEqualTo(ApkRejection.NoSignature)
        assertThat(ApkVerifier.check(update, installed.copy(signers = emptySet()), 2_026_110_124)).isEqualTo(ApkRejection.NoSignature)
        assertThat(ApkVerifier.check(update.copy(signers = emptySet()), installed.copy(signers = emptySet()), 2_026_110_124))
            .isEqualTo(ApkRejection.NoSignature)
        assertThat(ApkVerifier.check(update, null, 2_026_110_124)).isEqualTo(ApkRejection.NoSignature)
    }

    @Test
    fun `a file that is no APK, another app, or not the announced or a newer version is refused`() {
        assertThat(ApkVerifier.check(null, installed, 2_026_110_124)).isEqualTo(ApkRejection.Unreadable)
        assertThat(ApkVerifier.check(update.copy(packageName = "io.element.android.x"), installed, 2_026_110_124))
            .isEqualTo(ApkRejection.WrongPackage)
        assertThat(ApkVerifier.check(update.copy(versionCode = 2_026_110_125), installed, 2_026_110_124)).isEqualTo(ApkRejection.WrongVersion)
        assertThat(ApkVerifier.check(update.copy(versionCode = 2_026_110_123), installed, 2_026_110_123)).isEqualTo(ApkRejection.NotNewer)
        assertThat(ApkVerifier.check(update.copy(versionCode = 2_026_110_122), installed, 2_026_110_122)).isEqualTo(ApkRejection.NotNewer)
    }
}
