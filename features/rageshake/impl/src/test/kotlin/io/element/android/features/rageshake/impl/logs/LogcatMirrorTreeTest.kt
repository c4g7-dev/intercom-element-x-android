/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.rageshake.impl.logs

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.element.android.features.rageshake.api.logs.LogRedaction.REDACTED
import io.element.android.features.rageshake.api.logs.LogcatMirrorTree
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class LogcatMirrorTreeTest {
    private val printed = mutableListOf<Triple<Int, String, String>>()
    private val tree = LogcatMirrorTree { priority, tag, message -> printed += Triple(priority, tag, message) }

    @Before
    fun plant() = Timber.plant(tree)

    @After
    fun uproot() = Timber.uproot(tree)

    @Test
    fun `call lines are mirrored from debug up, under the call tag`() {
        Timber.d("ElementCall: joining !room:example.org")
        Timber.i("MatrixRTC backend: sendStickyEvent(org.matrix.msc4075.rtc.notification)")
        Timber.v("ElementCall: verbose is not mirrored")
        assertThat(printed).containsExactly(
            Triple(Log.DEBUG, LogcatMirrorTree.CALL_TAG, "ElementCall: joining !room:example.org"),
            Triple(Log.INFO, LogcatMirrorTree.CALL_TAG, "MatrixRTC backend: sendStickyEvent(org.matrix.msc4075.rtc.notification)"),
        )
    }

    @Test
    fun `the core's records keep their tag`() {
        Timber.tag("matrix-rtc").d("matrix_rtc_call::direct_call: direct call: Ringing -> Ended")
        assertThat(printed).containsExactly(Triple(Log.DEBUG, "matrix-rtc", "matrix_rtc_call::direct_call: direct call: Ringing -> Ended"))
    }

    @Test
    fun `other lines are mirrored only from warn up`() {
        Timber.i("Timeline: loaded 20 events")
        Timber.tag("Sync").w("sync failed")
        assertThat(printed).containsExactly(Triple(Log.WARN, LogcatMirrorTree.TAG, "[Sync] sync failed"))
    }

    @Test
    fun `what is printed is redacted and split into logcat sized lines`() {
        Timber.w("ElementCall: token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhIn0.c2lnbmF0dXJl")
        Timber.e("x".repeat(9000))
        assertThat(printed.first()).isEqualTo(Triple(Log.WARN, LogcatMirrorTree.CALL_TAG, "ElementCall: token $REDACTED"))
        assertThat(printed.drop(1).map { it.third.length }).containsExactly(4000, 4000, 1000).inOrder()
    }
}
