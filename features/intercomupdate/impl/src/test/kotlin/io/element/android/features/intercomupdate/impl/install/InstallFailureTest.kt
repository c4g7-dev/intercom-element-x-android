/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.install

import android.content.pm.PackageInstaller
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InstallFailureTest {
    @Test
    fun `every status of the installer has its meaning`() {
        assertThat(InstallFailure.of(PackageInstaller.STATUS_FAILURE_ABORTED)).isEqualTo(InstallFailure.Aborted)
        assertThat(InstallFailure.of(PackageInstaller.STATUS_FAILURE_BLOCKED)).isEqualTo(InstallFailure.Blocked)
        assertThat(InstallFailure.of(PackageInstaller.STATUS_FAILURE_CONFLICT)).isEqualTo(InstallFailure.Conflict)
        assertThat(InstallFailure.of(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)).isEqualTo(InstallFailure.Incompatible)
        assertThat(InstallFailure.of(PackageInstaller.STATUS_FAILURE_INVALID)).isEqualTo(InstallFailure.Invalid)
        assertThat(InstallFailure.of(PackageInstaller.STATUS_FAILURE_STORAGE)).isEqualTo(InstallFailure.Storage)
        assertThat(InstallFailure.of(PackageInstaller.STATUS_FAILURE_TIMEOUT)).isEqualTo(InstallFailure.Timeout)
        assertThat(InstallFailure.of(PackageInstaller.STATUS_FAILURE)).isEqualTo(InstallFailure.Other)
        assertThat(InstallFailure.of(999)).isEqualTo(InstallFailure.Other)
    }
}
