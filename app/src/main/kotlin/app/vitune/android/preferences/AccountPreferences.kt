/*
 * Copyright (C) 2026 twixxty
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package app.vitune.android.preferences

import app.vitune.android.GlobalPreferencesHolder

object AccountPreferences : GlobalPreferencesHolder() {
    var innerTubeCookie by string("")
    var visitorData by string("")
    var dataSyncId by string("")
    var innerTubeAuthUser by string("0")
    var accountName by string("")
    var accountEmail by string("")
    var accountChannelHandle by string("")
    var useLoginForBrowse by boolean(true)
    var reportHistory by boolean(true)

    val isLoggedIn: Boolean
        get() = innerTubeCookie.isNotBlank() && ("SAPISID" in innerTubeCookie || "SID" in innerTubeCookie || innerTubeCookie.length > 20)

    fun sanitizeHeaderValue(value: String): String =
        value.replace("\r", "").replace("\n", "").trim()

    /**
     * Parses either a raw cookie / SAPISID token or a multi-line exported auth bundle
     * (e.g. `***INNERTUBE COOKIE*** =...`, `***VISITOR DATA*** =...`) and strips any
     * illegal HTTP header control characters (`\r`, `\n`).
     */
    fun applyTokenInput(raw: String): String {
        val trimmed = raw.trim()
        if ("***INNERTUBE COOKIE***" in trimmed) {
            var parsedCookie = ""
            trimmed.lineSequence().forEach { line ->
                val clean = line.trim()
                when {
                    clean.startsWith("***INNERTUBE COOKIE***") ->
                        parsedCookie = sanitizeHeaderValue(clean.substringAfter("="))
                    clean.startsWith("***VISITOR DATA***") ->
                        sanitizeHeaderValue(clean.substringAfter("=")).takeIf { it.isNotBlank() }?.let { visitorData = it }
                    clean.startsWith("***DATASYNC ID***") ->
                        sanitizeHeaderValue(clean.substringAfter("=")).takeIf { it.isNotBlank() }?.let { dataSyncId = it }
                    clean.startsWith("***AUTH USER***") ->
                        sanitizeHeaderValue(clean.substringAfter("=")).takeIf { it.isNotBlank() }?.let { innerTubeAuthUser = it }
                    clean.startsWith("***ACCOUNT NAME***") ->
                        sanitizeHeaderValue(clean.substringAfter("=")).takeIf { it.isNotBlank() }?.let { accountName = it }
                    clean.startsWith("***ACCOUNT EMAIL***") ->
                        sanitizeHeaderValue(clean.substringAfter("=")).takeIf { it.isNotBlank() }?.let { accountEmail = it }
                    clean.startsWith("***ACCOUNT CHANNEL HANDLE***") ->
                        sanitizeHeaderValue(clean.substringAfter("=")).takeIf { it.isNotBlank() }?.let { accountChannelHandle = it }
                }
            }
            if (parsedCookie.isNotBlank()) {
                innerTubeCookie = parsedCookie
            }
            return parsedCookie
        }

        val sanitized = sanitizeHeaderValue(trimmed)
        val cookie = if (!sanitized.contains("=") && !sanitized.contains(";")) {
            "SAPISID=$sanitized"
        } else {
            sanitized
        }
        innerTubeCookie = cookie
        return cookie
    }

    /**
     * Repairs previously saved multi-line or newline-contaminated credentials in-place
     * so OkHttp never throws IllegalArgumentException on HTTP headers.
     */
    fun sanitizeStoredCredentials() {
        if ("***INNERTUBE COOKIE***" in innerTubeCookie) {
            applyTokenInput(innerTubeCookie)
        } else {
            val cleanCookie = sanitizeHeaderValue(innerTubeCookie)
            if (cleanCookie != innerTubeCookie) innerTubeCookie = cleanCookie
        }
        val cleanVisitor = sanitizeHeaderValue(visitorData)
        if (cleanVisitor != visitorData) visitorData = cleanVisitor
        val cleanDataSync = sanitizeHeaderValue(dataSyncId)
        if (cleanDataSync != dataSyncId) dataSyncId = cleanDataSync
        val cleanAuthUser = sanitizeHeaderValue(innerTubeAuthUser)
        if (cleanAuthUser != innerTubeAuthUser) innerTubeAuthUser = cleanAuthUser
    }

    fun clear() {
        innerTubeCookie = ""
        visitorData = ""
        dataSyncId = ""
        innerTubeAuthUser = "0"
        accountName = ""
        accountEmail = ""
        accountChannelHandle = ""
    }
}
