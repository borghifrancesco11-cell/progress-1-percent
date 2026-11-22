package com.progressone.nativeapp.data

/** Always read fresh preferences; LocalStore.revision drives Compose recomposition. */
fun LocalStore.moduleEnabled(id: String): Boolean =
    ModulePreferences.enabled(id) { key, default -> setting(key, default) }

fun LocalStore.moduleSelected(id: String): Boolean =
    ModulePreferences.selected(id) { key, default -> setting(key, default) }

fun LocalStore.setModuleEnabled(id: String, enabled: Boolean) {
    if (ModulePreferences.canDisable(id)) {
        setSetting(ModulePreferences.settingKey(id), enabled.toString())
    }
}
