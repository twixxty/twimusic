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
        get() = innerTubeCookie.isNotBlank() && "SAPISID" in innerTubeCookie

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
