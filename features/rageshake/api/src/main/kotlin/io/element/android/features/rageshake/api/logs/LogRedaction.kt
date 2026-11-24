/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.rageshake.api.logs

/**
 * InterCom: takes the secrets out of a log line before it leaves the app's private storage - printed to logcat, which
 * other apps with log access can read, or zipped to be shared.
 *
 * What it removes: JSON Web Tokens (the SFU's, the JWT service's), Matrix and MAS access, refresh and login tokens
 * (`syt_`, `mat_`, `mar_`, `mct_`...), `Bearer` credentials, and the value of any field or query parameter named like
 * a token, a password or a secret. Matrix IDs are kept: they are what a log is read by.
 */
object LogRedaction {
    const val REDACTED = "<redacted>"

    private val jwt = Regex("""eyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]*""")
    private val matrixToken = Regex("""\b(?:syt|syr|syl|mat|mar|mct|mcr|mxo)_[A-Za-z0-9_-]{8,}""")
    private val bearer = Regex("""(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{8,}""")

    /**
     * `access_token=…`, `"refresh_token": "…"`, `token: …`, `password=…`, `openid_token` and the like: the name is kept,
     * the value goes. A value that is an object (`"openid_token": {`) is left alone, its fields are matched in turn.
     */
    private val namedSecret = Regex(
        """(?i)(["']?\b[A-Za-z_]*(?:token|password|passwd|secret|jwt)["']?\s*[:=]\s*["']?)([^"'&\s,;{}\[\]]+)"""
    )

    fun redact(line: String): String {
        if (line.isEmpty()) return line
        var result = jwt.replace(line, REDACTED)
        result = matrixToken.replace(result, REDACTED)
        result = bearer.replace(result) { "${it.groupValues[1]} $REDACTED" }
        result = namedSecret.replace(result) { match ->
            if (match.groupValues[2] == REDACTED) match.value else match.groupValues[1] + REDACTED
        }
        return result
    }
}
