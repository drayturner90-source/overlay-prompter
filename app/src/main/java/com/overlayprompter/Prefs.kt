package com.overlayprompter

import android.content.Context

/** Settings shared by the editor screen and the floating overlay. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prompter", Context.MODE_PRIVATE)

    var script: String
        get() = sp.getString("script", "") ?: ""
        set(v) = sp.edit().putString("script", v).apply()

    /** Scroll speed in dp per second. */
    var speed: Int
        get() = sp.getInt("speed", 35)
        set(v) = sp.edit().putInt("speed", v.coerceIn(5, 150)).apply()

    /** Text size in sp. */
    var textSize: Int
        get() = sp.getInt("textSize", 28)
        set(v) = sp.edit().putInt("textSize", v.coerceIn(14, 64)).apply()

    /** Background darkness behind the text, 0 (fully clear) to 90 percent. */
    var opacity: Int
        get() = sp.getInt("opacity", 35)
        set(v) = sp.edit().putInt("opacity", v.coerceIn(0, 90)).apply()

    // Last window position and size, in pixels (-1 = not set yet).
    var winX: Int
        get() = sp.getInt("winX", -1)
        set(v) = sp.edit().putInt("winX", v).apply()
    var winY: Int
        get() = sp.getInt("winY", -1)
        set(v) = sp.edit().putInt("winY", v).apply()
    var winW: Int
        get() = sp.getInt("winW", -1)
        set(v) = sp.edit().putInt("winW", v).apply()
    var winH: Int
        get() = sp.getInt("winH", -1)
        set(v) = sp.edit().putInt("winH", v).apply()
}
