/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.rageshake.test.logs

import io.element.android.features.rageshake.api.logs.LogsSharer
import io.element.android.tests.testutils.lambda.lambdaError

class FakeLogsSharer(
    private val shareResult: () -> Result<Unit> = { lambdaError() },
) : LogsSharer {
    override suspend fun share(): Result<Unit> = shareResult()
}
