/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.install

import android.content.pm.PackageInstaller
import com.google.common.truth.Truth.assertThat
import io.element.android.features.intercomupdate.impl.logic.UpdateError
import org.junit.Test

class InstallHintTest {
    private fun install(status: Int) = UpdateError.Install(status, null)

    @Test
    fun `on a Samsung phone a refusal without another explanation points to Auto Blocker`() {
        for (manufacturer in listOf("samsung", "Samsung", " SAMSUNG ")) {
            assertThat(InstallHint.of(install(PackageInstaller.STATUS_FAILURE_BLOCKED), manufacturer)).isEqualTo(InstallHint.SamsungAutoBlocker)
            assertThat(InstallHint.of(install(PackageInstaller.STATUS_FAILURE_ABORTED), manufacturer)).isEqualTo(InstallHint.SamsungAutoBlocker)
            assertThat(InstallHint.of(install(PackageInstaller.STATUS_FAILURE), manufacturer)).isEqualTo(InstallHint.SamsungAutoBlocker)
            assertThat(InstallHint.of(UpdateError.NotConfirmed, manufacturer)).isEqualTo(InstallHint.SamsungAutoBlocker)
        }
    }

    @Test
    fun `on other phones those refusals have no hint`() {
        for (manufacturer in listOf("Google", "", null)) {
            assertThat(InstallHint.of(install(PackageInstaller.STATUS_FAILURE_BLOCKED), manufacturer)).isNull()
            assertThat(InstallHint.of(install(PackageInstaller.STATUS_FAILURE_ABORTED), manufacturer)).isNull()
            assertThat(InstallHint.of(UpdateError.NotConfirmed, manufacturer)).isNull()
        }
    }

    @Test
    fun `storage, an unsupported device and another build have their own hint on every phone`() {
        for (manufacturer in listOf("samsung", "Google")) {
            assertThat(InstallHint.of(install(PackageInstaller.STATUS_FAILURE_STORAGE), manufacturer)).isEqualTo(InstallHint.Storage)
            assertThat(InstallHint.of(UpdateError.NoSpace, manufacturer)).isEqualTo(InstallHint.Storage)
            assertThat(InstallHint.of(install(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE), manufacturer)).isEqualTo(InstallHint.Incompatible)
            assertThat(InstallHint.of(install(PackageInstaller.STATUS_FAILURE_CONFLICT), manufacturer)).isEqualTo(InstallHint.Conflict)
            assertThat(InstallHint.of(install(PackageInstaller.STATUS_FAILURE_INVALID), manufacturer)).isNull()
            assertThat(InstallHint.of(UpdateError.Network(null), manufacturer)).isNull()
            assertThat(InstallHint.of(UpdateError.ChecksumMismatch, manufacturer)).isNull()
        }
    }
}
