/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.rageshake.impl.logs

import com.google.common.truth.Truth.assertThat
import io.element.android.features.rageshake.api.logs.LogRedaction
import io.element.android.features.rageshake.api.logs.LogRedaction.REDACTED
import org.junit.Test

class LogRedactionTest {
    @Test
    fun `a JWT is redacted wherever it is`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJAYWxpY2U6ZXhhbXBsZS5vcmcifQ.c2lnbmF0dXJlLXNpZ25hdHVyZQ"
        assertThat(LogRedaction.redact("connecting with $jwt to the SFU")).isEqualTo("connecting with $REDACTED to the SFU")
        assertThat(LogRedaction.redact("wss://sfu.example.org/rtc?access_token=$jwt&auto_subscribe=1"))
            .isEqualTo("wss://sfu.example.org/rtc?access_token=$REDACTED&auto_subscribe=1")
    }

    @Test
    fun `matrix and MAS tokens are redacted`() {
        assertThat(LogRedaction.redact("token syt_YWxpY2U_abcdefghijklmnop_0a1b2c")).isEqualTo("token $REDACTED")
        assertThat(LogRedaction.redact("refreshing mar_0123456789abcdefABCDEF now")).isEqualTo("refreshing $REDACTED now")
        assertThat(LogRedaction.redact("Authorization: Bearer mat_0123456789abcdefABCDEF")).doesNotContain("0123456789")
        assertThat(LogRedaction.redact("Authorization: Bearer abcdefghijklmnop")).isEqualTo("Authorization: Bearer $REDACTED")
    }

    @Test
    fun `fields named like a secret lose their value and keep their name`() {
        assertThat(LogRedaction.redact("""{"access_token":"abcdefghijkl","token_type":"Bearer","expires_in":3600}"""))
            .isEqualTo("""{"access_token":"$REDACTED","token_type":"Bearer","expires_in":3600}""")
        assertThat(LogRedaction.redact("FfiOpenIdToken(accessToken=qwertyuiop, tokenType=Bearer, matrixServerName=example.org)"))
            .isEqualTo("FfiOpenIdToken(accessToken=$REDACTED, tokenType=Bearer, matrixServerName=example.org)")
        assertThat(LogRedaction.redact("password: hunter2")).isEqualTo("password: $REDACTED")
        assertThat(LogRedaction.redact("jwt=abc.def")).isEqualTo("jwt=$REDACTED")
    }

    @Test
    fun `ordinary lines and matrix ids are left alone`() {
        val line = "ElementCall: joining !room:example.org as @alice:example.org, \$event:example.org, 3 tokens left"
        assertThat(LogRedaction.redact(line)).isEqualTo(line)
        assertThat(LogRedaction.redact("")).isEmpty()
        val endpoint = "the homeserver does not support MSC4195 get_token: MSC4195 get_token is not exposed by the SDK bindings"
        assertThat(LogRedaction.redact(endpoint)).isEqualTo(endpoint)
    }
}
