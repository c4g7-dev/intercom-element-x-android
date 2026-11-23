/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.data

import androidx.datastore.preferences.core.Preferences
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
    /** Download a release as soon as it is found and install it as soon as possible, without being asked. */
    val autoInstall: Boolean = true,
)

/** "Later": the release with [versionCode] is not offered in a dialog before [untilMillis]. */
data class Snooze(
    val versionCode: Long = 0,
    val untilMillis: Long = 0,
)

/** What the updater remembers between runs of the app. */
interface InterComUpdateStore {
    val settings: Flow<UpdateSettings>
    suspend fun setSettings(transform: (UpdateSettings) -> UpdateSettings)

    /** The time of the last successful check, in milliseconds since the epoch, or 0. */
    suspend fun lastCheck(): Long
    suspend fun setLastCheck(millis: Long)

    /** The time the last check was started, successful or not, which is what spaces the checks at the app's opening. */
    suspend fun lastCheckAttempt(): Long
    suspend fun setLastCheckAttempt(millis: Long)

    val snooze: Flow<Snooze>
    suspend fun setSnooze(snooze: Snooze)

    /** The version code whose automatic installation failed, so that it is offered instead of tried again and again. */
    suspend fun autoInstallFailedVersionCode(): Long
    suspend fun setAutoInstallFailedVersionCode(versionCode: Long)

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

    override val settings: Flow<UpdateSettings> = store.data.map { it.toSettings() }

    private fun Preferences.toSettings() = UpdateSettings(
        checkAutomatically = this[checkAutomaticallyKey] ?: true,
        allowMetered = this[allowMeteredKey] ?: false,
        includePrereleases = this[includePrereleasesKey] ?: false,
        autoInstall = this[autoInstallKey] ?: true,
    )

    override suspend fun setSettings(transform: (UpdateSettings) -> UpdateSettings) {
        store.edit { prefs ->
            val new = transform(prefs.toSettings())
            prefs[checkAutomaticallyKey] = new.checkAutomatically
            prefs[allowMeteredKey] = new.allowMetered
            prefs[includePrereleasesKey] = new.includePrereleases
            prefs[autoInstallKey] = new.autoInstall
        }
    }

    override suspend fun lastCheck(): Long = store.data.first()[lastCheckKey] ?: 0

    override suspend fun setLastCheck(millis: Long) {
        store.edit { it[lastCheckKey] = millis }
    }

    override suspend fun lastCheckAttempt(): Long = store.data.first()[lastCheckAttemptKey] ?: 0

    override suspend fun setLastCheckAttempt(millis: Long) {
        store.edit { it[lastCheckAttemptKey] = millis }
    }

    override val snooze: Flow<Snooze> = store.data.map { Snooze(it[snoozeVersionKey] ?: 0, it[snoozeUntilKey] ?: 0) }

    override suspend fun setSnooze(snooze: Snooze) {
        store.edit {
            it[snoozeVersionKey] = snooze.versionCode
            it[snoozeUntilKey] = snooze.untilMillis
        }
    }

    override suspend fun autoInstallFailedVersionCode(): Long = store.data.first()[autoInstallFailedKey] ?: 0

    override suspend fun setAutoInstallFailedVersionCode(versionCode: Long) {
        store.edit { it[autoInstallFailedKey] = versionCode }
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
        val autoInstallKey = booleanPreferencesKey("auto_install")
        val lastCheckAttemptKey = longPreferencesKey("last_check_attempt")
        val snoozeVersionKey = longPreferencesKey("snooze_version_code")
        val snoozeUntilKey = longPreferencesKey("snooze_until")
        val autoInstallFailedKey = longPreferencesKey("auto_install_failed_version_code")
    }
}
