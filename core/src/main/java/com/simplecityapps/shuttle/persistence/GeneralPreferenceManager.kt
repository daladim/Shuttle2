package com.simplecityapps.shuttle.persistence

import android.content.SharedPreferences

class GeneralPreferenceManager(private val sharedPreferences: SharedPreferences) {

    var previousVersionCode: Int
        set(value) {
            sharedPreferences.put("previous_version_code", value)
        }
        get() {
            return sharedPreferences.get("previous_version_code", -1)
        }

    var hasSeenChangelog: Boolean
        set(value) {
            sharedPreferences.put("changelog_viewed", value)
        }
        get() {
            return sharedPreferences.get("changelog_viewed", false)
        }

    var showChangelogOnLaunch: Boolean
        set(value) {
            sharedPreferences.put("changelog_show_on_launch", value)
        }
        get() {
            return sharedPreferences.get("changelog_show_on_launch", true)
        }

    var nightMode: String
        set(value) {
            sharedPreferences.put("pref_night_mode", value)
        }
        get() {
            return sharedPreferences.get("pref_night_mode", "0")
        }

    var artworkWifiOnly: Boolean
        set(value) {
            sharedPreferences.put("artwork_wifi_only", value)
        }
        get() {
            return sharedPreferences.get("artwork_wifi_only", true)
        }

    var artworkLocalOnly: Boolean
        set(value) {
            sharedPreferences.put("artwork_local_only", value)
        }
        get() {
            return sharedPreferences.get("artwork_local_only", false)
        }

    var crashReportingEnabled: Boolean
        set(value) {
            sharedPreferences.put("pref_crash_reporting", value)
        }
        get() {
            return sharedPreferences.get("pref_crash_reporting", true)
        }

    var artistListViewMode: String?
        set(value) {
            sharedPreferences.put("pref_artist_view_mode", value)
        }
        get() {
            return sharedPreferences.getString("pref_artist_view_mode", null)
        }

    var albumListViewMode: String?
        set(value) {
            sharedPreferences.put("pref_album_view_mode", value)
        }
        get() {
            return sharedPreferences.getString("pref_album_view_mode", null)
        }
}