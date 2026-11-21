package com.neonvpn.app

import android.content.Context
import org.amnezia.awg.config.Config
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TunnelEntry(val id: String, val name: String)

/** Keeps imported configs as files and a small index in SharedPreferences. */
object TunnelStore {
    private const val PREFS = "tunnels"
    private const val KEY_INDEX = "index"
    private const val KEY_SELECTED = "selected"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dir(ctx: Context): File = File(ctx.filesDir, "tunnels").apply { mkdirs() }

    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.conf")

    fun list(ctx: Context): List<TunnelEntry> {
        val raw = prefs(ctx).getString(KEY_INDEX, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isBlank() || !file(ctx, id).exists()) null
            else TunnelEntry(id, o.optString("name", id))
        }
    }

    private fun save(ctx: Context, items: List<TunnelEntry>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs(ctx).edit().putString(KEY_INDEX, arr.toString()).apply()
    }

    /** Validates the config and stores it. Throws with a readable message if it is invalid. */
    fun add(ctx: Context, name: String, rawConf: String): TunnelEntry {
        val confText = sanitize(rawConf)
        parse(confText)
        val id = "t" + System.currentTimeMillis().toString(36)
        file(ctx, id).writeText(confText)
        val entry = TunnelEntry(id, name.trim().ifBlank { "Сервер" }.take(40))
        save(ctx, list(ctx) + entry)
        if (selected(ctx) == null) setSelected(ctx, id)
        return entry
    }

    fun delete(ctx: Context, id: String) {
        file(ctx, id).delete()
        val rest = list(ctx).filter { it.id != id }
        save(ctx, rest)
        if (selected(ctx) == id) setSelected(ctx, rest.firstOrNull()?.id)
    }

    fun loadConfig(ctx: Context, id: String): Config = parse(file(ctx, id).readText())

    fun selected(ctx: Context): String? = prefs(ctx).getString(KEY_SELECTED, null)

    fun setSelected(ctx: Context, id: String?) {
        prefs(ctx).edit().putString(KEY_SELECTED, id).apply()
    }

    private fun parse(text: String): Config = try {
        Config.parse(text.byteInputStream())
    } catch (e: Exception) {
        val detail = e.message ?: e.cause?.message ?: e.javaClass.simpleName
        throw IllegalArgumentException("Ошибка в конфиге: $detail")
    }

    private val intKeys = setOf(
        "listenport", "mtu", "jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "persistentkeepalive",
    )

    /**
     * Drops lines the tunnel library can't read: empty values, unreplaced "$PLACEHOLDER"
     * templates from Amnezia keys, and numeric options that aren't plain integers.
     */
    fun sanitize(text: String): String = text
        .replace("\r\n", "\n")
        .lines()
        .filter { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[") || trimmed.startsWith("#")) return@filter true
            val eq = trimmed.indexOf('=')
            if (eq <= 0) return@filter true
            val key = trimmed.substring(0, eq).trim().lowercase()
            val value = trimmed.substring(eq + 1).substringBefore('#').trim()
            when {
                value.isEmpty() -> false
                value.startsWith("$") -> false
                key in intKeys -> value.toIntOrNull() != null
                else -> true
            }
        }
        .joinToString("\n")
}
