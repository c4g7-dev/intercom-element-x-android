/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.install

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.intercomupdate.impl.logic.ApkInfo
import io.element.android.features.intercomupdate.impl.logic.ApkInfoReader
import io.element.android.features.intercomupdate.impl.logic.Sha256
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber

@ContributesBinding(AppScope::class)
class AndroidApkInfoReader(
    @ApplicationContext private val context: Context,
) : ApkInfoReader {
    private val packageManager get() = context.packageManager

    override fun installed(): ApkInfo? = read { flags -> packageManager.getPackageInfo(context.packageName, flags) }

    override fun archive(path: String): ApkInfo? = read { flags -> packageManager.getPackageArchiveInfo(path, flags) }

    /**
     * Asks for the certificates the current way first and the old way second: on some Android versions
     * `getPackageArchiveInfo` returns no `signingInfo` for a file although it can read its signatures.
     */
    @Suppress("DEPRECATION")
    private fun read(get: (Int) -> PackageInfo?): ApkInfo? {
        val attempts = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(PackageManager.GET_SIGNING_CERTIFICATES)
            add(PackageManager.GET_SIGNATURES)
        }
        val results = attempts.asSequence().mapNotNull { flags ->
            try {
                get(flags)?.let { it to it.signers(flags) }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Reading package info failed")
                null
            }
        }
        // The first answer with certificates, or else one without, which still tells package and version.
        val (info, signers) = results.firstOrNull { it.second.isNotEmpty() } ?: results.firstOrNull() ?: return null
        return ApkInfo(info.packageName, PackageInfoCompat.getLongVersionCode(info), signers)
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signers(flags: Int): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && flags == PackageManager.GET_SIGNING_CERTIFICATES) {
            signingInfo?.apkContentsSigners
        } else {
            signatures
        }
        return signatures.orEmpty().map { Sha256.of(it.toByteArray()) }.toSet()
    }

    private companion object {
        const val TAG = "InterComUpdate"
    }
}
