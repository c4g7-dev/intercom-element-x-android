/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.intercomupdate.impl.logic.UpdateCandidate
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

data class UpdateSettings(
    /** Look for updates when the app starts and every twelve hours. */
    val checkAutomatically: Boolean = true,
    /** Check and download over metered connections (mobile data) too. */
    val allowMetered: Boolean = false,
    /** Offer pre-releases too. */
    val includePrereleases: Boolean = false,
)

/** What the updater remembers between runs of the app. */
interface InterComUpdateStore {
    val settings: Flow<UpdateSettings>
    suspend fun setSettings(transform: (UpdateSettings) -> UpdateSettings)

    /** The time of the last successful check, in milliseconds since the epoch, or 0. */
    suspend fun lastCheck(): Long
    suspend fun setLastCheck(millis: Long)

    /** The release found by the last check, so that it is still on offer after a restart. */
    suspend fun candidate(): UpdateCandidate?
    suspend fun setCandidate(candidate: UpdateCandidate?)

    /** The version code that a notification has already announced. */
    suspend fun notifiedVersionCode(): Long
    suspend fun setNotifiedVersionCode(versionCode: Long)
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultInterComUpdateStore(
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
) : InterComUpdateStore {
    private val store = preferenceDataStoreFactory.create("intercom_update")
    private val json = Json { ignoreUnknownKeys = true }

    override val settings: Flow<UpdateSettings> = store.data.map { prefs ->
        UpdateSettings(
            checkAutomatically = prefs[checkAutomaticallyKey] ?: true,
            allowMetered = prefs[allowMeteredKey] ?: false,
            includePrereleases = prefs[includePrereleasesKey] ?: false,
        )
    }

    override suspend fun setSettings(transform: (UpdateSettings) -> UpdateSettings) {
        store.edit { prefs ->
            val current = UpdateSettings(
                checkAutomatically = prefs[checkAutomaticallyKey] ?: true,
                allowMetered = prefs[allowMeteredKey] ?: false,
                includePrereleases = prefs[includePrereleasesKey] ?: false,
            )
            val new = transform(current)
            prefs[checkAutomaticallyKey] = new.checkAutomatically
            prefs[allowMeteredKey] = new.allowMetered
            prefs[includePrereleasesKey] = new.includePrereleases
        }
    }

    override suspend fun lastCheck(): Long = store.data.first()[lastCheckKey] ?: 0

    override suspend fun setLastCheck(millis: Long) {
        store.edit { it[lastCheckKey] = millis }
    }

    override suspend fun candidate(): UpdateCandidate? {
        val text = store.data.first()[candidateKey] ?: return null
        return runCatchingExceptions { json.decodeFromString<UpdateCandidate>(text) }.getOrNull()
    }

    override suspend fun setCandidate(candidate: UpdateCandidate?) {
        store.edit { prefs ->
            if (candidate == null) prefs.remove(candidateKey) else prefs[candidateKey] = json.encodeToString(candidate)
        }
    }

    override suspend fun notifiedVersionCode(): Long = store.data.first()[notifiedKey] ?: 0

    override suspend fun setNotifiedVersionCode(versionCode: Long) {
        store.edit { it[notifiedKey] = versionCode }
    }

    private companion object {
        val checkAutomaticallyKey = booleanPreferencesKey("check_automatically")
        val allowMeteredKey = booleanPreferencesKey("allow_metered")
        val includePrereleasesKey = booleanPreferencesKey("include_prereleases")
        val lastCheckKey = longPreferencesKey("last_check")
        val candidateKey = stringPreferencesKey("candidate")
        val notifiedKey = longPreferencesKey("notified_version_code")
    }
}
