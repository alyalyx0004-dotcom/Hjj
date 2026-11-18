package com.premiumglass.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class AppEntry(val label: String, val packageName: String, val activityName: String) {
    val id: String get() = "$packageName/$activityName"
}

class LauncherStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

    var accent: Int
        get() = prefs.getInt("accent", 0)
        set(v) = prefs.edit().putInt("accent", v).apply()
    var glassOpacity: Float
        get() = prefs.getFloat("glass", .18f)
        set(v) = prefs.edit().putFloat("glass", v).apply()
    var columns: Int
        get() = prefs.getInt("columns", 4).coerceIn(3, 6)
        set(v) = prefs.edit().putInt("columns", v.coerceIn(3, 6)).apply()
    var showLabels: Boolean
        get() = prefs.getBoolean("labels", true)
        set(v) = prefs.edit().putBoolean("labels", v).apply()
    var reduceMotion: Boolean
        get() = prefs.getBoolean("reduce_motion", false)
        set(v) = prefs.edit().putBoolean("reduce_motion", v).apply()
    var showRecent: Boolean
        get() = prefs.getBoolean("recent", true)
        set(v) = prefs.edit().putBoolean("recent", v).apply()
    var defaultPage: String
        get() = prefs.getString("default_page", "home") ?: "home"
        set(v) = prefs.edit().putString("default_page", v).apply()

    private fun getSet(key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    private fun setSet(key: String, value: Set<String>) { prefs.edit().putStringSet(key, value).apply() }
    var favorites: Set<String>
        get() = getSet("favorites")
        set(v) = setSet("favorites", v)
    var hidden: Set<String>
        get() = getSet("hidden")
        set(v) = setSet("hidden", v)

    fun toggleFavorite(id: String) { favorites = favorites.toMutableSet().also { if (!it.add(id)) it.remove(id) } }
    fun toggleHidden(id: String) { hidden = hidden.toMutableSet().also { if (!it.add(id)) it.remove(id) } }
    fun recordLaunch(id: String) {
        val recent = recentApps().toMutableList().apply { remove(id); add(0, id) }.take(12)
        prefs.edit().putString("recents", JSONArray(recent).toString()).apply()
    }
    fun recentApps(): List<String> = try {
        val arr = JSONArray(prefs.getString("recents", "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) { emptyList() }

    // A PIN limits access to launcher controls, but does NOT secure an app from other launchers or Settings.
    fun hasPin(): Boolean = prefs.contains("pin_hash")
    fun setPin(pin: String) {
        require(pin.length in 4..12 && pin.all { it.isDigit() })
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("pin_salt", salt.toHex())
            .putString("pin_hash", derive(pin, salt)).apply()
    }
    fun verifyPin(pin: String): Boolean {
        val salt = try { prefs.getString("pin_salt", "")!!.hexBytes() } catch (_: Exception) { return false }
        val hash = prefs.getString("pin_hash", "") ?: ""
        if (salt.isEmpty() || hash.isEmpty()) return false
        return MessageDigest.isEqual(hash.toByteArray(), derive(pin, salt).toByteArray())
    }
    fun clearPin() { prefs.edit().remove("pin_salt").remove("pin_hash").apply() }
    private fun derive(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.toHex() }
        finally { spec.clearPassword() }
    }
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun exportJson(): String = JSONObject().apply {
        put("format", "premium-glass-launcher")
        put("version", 1)
        put("accent", accent)
        put("glass", glassOpacity.toDouble())
        put("columns", columns)
        put("labels", showLabels)
        put("reduceMotion", reduceMotion)
        put("recent", showRecent)
        put("favorites", JSONArray(favorites.toList()))
        put("hidden", JSONArray(hidden.toList()))
    }.toString(2)

    fun importJson(json: String) {
        val obj = JSONObject(json)
        require(obj.optString("format") == "premium-glass-launcher") { "Not a Premium Glass backup" }
        require(obj.optInt("version") == 1) { "Unsupported backup version" }
        fun strings(key: String): Set<String> {
            val arr = obj.optJSONArray(key) ?: JSONArray()
            require(arr.length() <= 1000)
            return (0 until arr.length()).map { arr.getString(it).take(300) }.toSet()
        }
        accent = obj.optInt("accent", 0).coerceIn(0, 3)
        glassOpacity = obj.optDouble("glass", .18).toFloat().coerceIn(.08f, .60f)
        columns = obj.optInt("columns", 4)
        showLabels = obj.optBoolean("labels", true)
        reduceMotion = obj.optBoolean("reduceMotion", false)
        showRecent = obj.optBoolean("recent", true)
        favorites = strings("favorites")
        hidden = strings("hidden")
        // Never import/export passwords, credential hashes or salts.
    }
}

@Suppress("DEPRECATION")
fun installedLauncherApps(context: Context): List<AppEntry> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val pm = context.packageManager
    val list = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else pm.queryIntentActivities(intent, 0)
    return list.mapNotNull { info ->
        val activity = info.activityInfo ?: return@mapNotNull null
        if (activity.packageName == context.packageName) return@mapNotNull null
        AppEntry(info.loadLabel(pm).toString(), activity.packageName, activity.name)
    }.distinctBy { it.id }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
