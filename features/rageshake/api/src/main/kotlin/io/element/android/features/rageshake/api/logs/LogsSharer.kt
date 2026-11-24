/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.rageshake.api.logs

/**
 * InterCom: hands the app's log files to whatever the user picks in the system's share sheet, zipped and with their
 * secrets taken out ([LogRedaction]). Nothing is uploaded by the app itself.
 */
interface LogsSharer {
    /** Zips the logs and opens the share sheet. Fails when there is nothing to share or the zip cannot be written. */
    suspend fun share(): Result<Unit>
}
