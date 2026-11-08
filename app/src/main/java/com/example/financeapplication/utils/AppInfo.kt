package com.example.financeapplication.utils

import android.content.Context

/** The installed app's version name (from `versionName` in build.gradle.kts, "-debug" in debug builds). */
fun Context.appVersionName(): String = try {
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
} catch (_: Exception) {
    ""
}
