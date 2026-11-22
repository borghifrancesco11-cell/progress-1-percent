package com.progressone.nativeapp.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Progress 1% has one deliberately consistent interface language: English. */
val LocalAppLanguage = staticCompositionLocalOf { "en" }
object AppLanguages { val values = linkedMapOf("en" to "English") }
fun tr(value: String): String = value
