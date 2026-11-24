package com.progressone.nativeapp.data

/** Always read fresh preferences; LocalStore.revision drives Compose recomposition. */
fun LocalStore.moduleEnabled(id: String): Boolean =
    ModulePreferences.enabled(id) { key, default -> setting(key, default) }

/** Effective module set used by score, Coach, calendar and Home. */
fun LocalStore.enabledModuleIds(): Set<String> = buildSet {
    ModulePreferences.all.forEach { module ->
        if (moduleEnabled(module.id)) add(module.id)
    }
    // Tracker, tasks and the annual calendar are core experiences of Progress 1%.
    add("track")
    add("tasks")
    add("calendar")
}

fun LocalStore.moduleSelected(id: String): Boolean =
    ModulePreferences.selected(id) { key, default -> setting(key, default) }

fun LocalStore.setModuleEnabled(id: String, enabled: Boolean) {
    if (ModulePreferences.canDisable(id)) {
        setSetting(ModulePreferences.settingKey(id), enabled.toString())
    }
}
