/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.intercomupdate.impl.logic.UpdateCandidate
import io.element.android.libraries.di.CacheDirectory
import java.io.File

/** Where a downloaded APK is kept until it is installed: a directory of its own in the app's cache. */
interface UpdateFiles {
    fun apkFile(candidate: UpdateCandidate): File

    /** Deletes every downloaded APK. */
    fun clear()

    /** Free bytes where the APK is kept, which is also where Android installs apps. */
    fun freeBytes(): Long
}

@ContributesBinding(AppScope::class)
class DefaultUpdateFiles(
    @CacheDirectory private val cacheDir: File,
) : UpdateFiles {
    private val directory get() = File(cacheDir, "intercom-update")

    // The name comes from a release; only its last segment is used, so that it cannot point elsewhere.
    override fun apkFile(candidate: UpdateCandidate): File = File(directory.apply { mkdirs() }, File(candidate.apkName).name)

    override fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    override fun freeBytes(): Long = (directory.takeIf { it.exists() } ?: cacheDir).usableSpace
}
