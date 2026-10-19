package com.lagradost.cloudstream4.theme

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager

/** TODO: This should be replaced with a better system */

fun perfToMode(perf: String?) =
    when (perf) {
        "System" -> CloudStreamThemeMode.FollowSystem
        "DEX" -> CloudStreamThemeMode.DEX
        "Black" -> CloudStreamThemeMode.Dark
        "Light" -> CloudStreamThemeMode.Light
        "Amoled" -> CloudStreamThemeMode.Amoled
        "AmoledLight" -> CloudStreamThemeMode.AmoledLight
        "Dracula" -> CloudStreamThemeMode.Dracula
        "Lavender" -> CloudStreamThemeMode.Lavender
        "SilentBlue" -> CloudStreamThemeMode.SilentBlue
        "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            CloudStreamThemeMode.Dynamic
        } else {
            CloudStreamThemeMode.DEX
        }

        else -> CloudStreamThemeMode.DEX
    }

fun perfToColor(perf : String?) = when (perf) {
    "Normal" -> CloudStreamPrimaryColor.NORMAL
    "DexWhite" -> CloudStreamPrimaryColor.DEX_WHITE
    "DexArcticBlue" -> CloudStreamPrimaryColor.DEX_ARCTIC_BLUE
    "DexGold" -> CloudStreamPrimaryColor.DEX_GOLD
    "DexJade" -> CloudStreamPrimaryColor.DEX_JADE
    "DexRoseGold" -> CloudStreamPrimaryColor.DEX_ROSE_GOLD
    "DexCrimson" -> CloudStreamPrimaryColor.DEX_CRIMSON
    "DexOcean" -> CloudStreamPrimaryColor.DEX_OCEAN
    "DexEmerald" -> CloudStreamPrimaryColor.DEX_EMERALD
    "Blue" -> CloudStreamPrimaryColor.BLUE
    "Purple" -> CloudStreamPrimaryColor.PURPLE
    "Green" -> CloudStreamPrimaryColor.GREEN
    "GreenApple" -> CloudStreamPrimaryColor.GREEN_APPLE
    "Red" -> CloudStreamPrimaryColor.RED
    "Banana" -> CloudStreamPrimaryColor.BANANA
    "Party" -> CloudStreamPrimaryColor.PARTY
    "Pink" -> CloudStreamPrimaryColor.PINK
    "CarnationPink" -> CloudStreamPrimaryColor.CARNATION_PINK
    "Maroon" -> CloudStreamPrimaryColor.MAROON
    "DarkGreen" -> CloudStreamPrimaryColor.DARK_GREEN
    "NavyBlue" -> CloudStreamPrimaryColor.NAVY_BLUE
    "Grey" -> CloudStreamPrimaryColor.GREY
    "White" -> CloudStreamPrimaryColor.WHITE
    "Brown" -> CloudStreamPrimaryColor.BROWN
    "Orange" -> CloudStreamPrimaryColor.ORANGE
    "DandelionYellow" -> CloudStreamPrimaryColor.DANDELION_YELLOW
    "CoolBlue" -> CloudStreamPrimaryColor.COOL_BLUE
    "Lavender" -> CloudStreamPrimaryColor.LAVENDER
    "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        CloudStreamPrimaryColor.DYNAMIC
    } else {
        CloudStreamPrimaryColor.NORMAL
    }

    "Monet2" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        CloudStreamPrimaryColor.DYNAMIC_TWO
    } else {
        CloudStreamPrimaryColor.NORMAL
    }

    else -> CloudStreamPrimaryColor.NORMAL
}

fun Context.loadThemeMode(): CloudStreamThemeMode {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    return perfToMode(prefs.getString("theme_key", "DEX"))
}

fun Context.loadPrimaryColor(): CloudStreamPrimaryColor {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    return perfToColor(prefs.getString("primary_color_key", "Normal"))
}