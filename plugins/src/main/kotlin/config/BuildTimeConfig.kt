/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

object BuildTimeConfig {
    const val APPLICATION_ID = "com.c4g7.intercom.android"
    const val APPLICATION_NAME = "Lattice"
    // InterCom: MAS requires the custom redirect scheme to be the reversed host of URL_WEBSITE (client_uri).
    val METADATA_HOST_REVERSED: String? = "com.c4g7.intercom.android"
    val OAUTH_CLIENT_URL_PATH: String? = null
    val URL_WEBSITE: String? = "https://c4g7.com"
    val URL_LOGO: String? = "https://c4g7.com/favicon.ico"
    val URL_COPYRIGHT: String? = null
    val URL_ACCEPTABLE_USE: String? = null
    val URL_PRIVACY: String? = null
    val URL_POLICY: String? = null
    val SERVICES_MAPTILER_BASE_URL: String? = null
    val SERVICES_MAPTILER_APIKEY: String? = null
    val SERVICES_MAPTILER_LIGHT_MAPID: String? = null
    val SERVICES_MAPTILER_DARK_MAPID: String? = null
    val SERVICES_POSTHOG_HOST: String? = null
    val SERVICES_POSTHOG_APIKEY: String? = null
    val SERVICES_SENTRY_DSN: String? = null
    val SERVICES_SENTRY_DSN_RUST: String? = null
    val BUG_REPORT_URL: String? = null
    val BUG_REPORT_APP_NAME: String? = null
    // InterCom: Firebase is tied to Element's package name and Google project, UnifiedPush only.
    const val PUSH_CONFIG_INCLUDE_FIREBASE: Boolean = false
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH: Boolean = true
    val PUSHER_APP_ID_RELEASE: String? = "com.c4g7.intercom.android"
    val PUSHER_APP_ID_DEBUG: String? = "com.c4g7.intercom.android.debug"
    val PUSHER_APP_ID_NIGHTLY: String? = "com.c4g7.intercom.android.nightly"
    // InterCom: do not claim Element's https links (app.element.io, mobile.element.io...).
    const val ELEMENT_LINKS_ENABLED: Boolean = false
    const val ELEMENT_MOBILE_LINK_PATH: String = "/element/"
}
