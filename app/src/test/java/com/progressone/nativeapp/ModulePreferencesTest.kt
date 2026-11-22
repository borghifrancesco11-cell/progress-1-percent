package com.progressone.nativeapp

import com.progressone.nativeapp.data.ModulePreferences
import org.junit.Assert.*
import org.junit.Test

class ModulePreferencesTest {
    private fun reader(values: Map<String, String>) = { key: String, default: String -> values[key] ?: default }

    @Test fun trackerCannotBeHidden() {
        val read = reader(mapOf("module_enabled_track" to "false"))
        assertTrue(ModulePreferences.enabled("track", read))
        assertFalse(ModulePreferences.canDisable("track"))
    }
    @Test fun everyOptionalModuleCanBeSwitchedOff() {
        for (module in ModulePreferences.all) {
            if (module.id in listOf("track", "today")) continue
            val read = reader(mapOf(ModulePreferences.settingKey(module.id) to "false"))
            assertFalse("${module.id} unexpectedly enabled", ModulePreferences.enabled(module.id, read))
        }
    }
    @Test fun justTrackerRemainsWhenEverythingOff() {
        val allOff = ModulePreferences.all.filterNot { it.id == "track" || it.id == "today" }
            .associate { ModulePreferences.settingKey(it.id) to "false" }
        assertEquals(listOf("today", "track"), ModulePreferences.enabledTabs(reader(allOff)))
    }
    @Test fun parentHidesChildrenWithoutChangingSelection() {
        val read = reader(mapOf("module_enabled_wardrobe" to "false"))
        assertTrue(ModulePreferences.selected("mixmatch", read))
        assertFalse(ModulePreferences.enabled("mixmatch", read))
        assertFalse(ModulePreferences.enabled("outfits", read))
        assertFalse(ModulePreferences.enabled("lists", read))
    }
    @Test fun historicalSchoolPreferenceIsRespected() {
        assertEquals("show_school", ModulePreferences.settingKey("school"))
        assertFalse(ModulePreferences.enabled("school", reader(mapOf("show_school" to "false"))))
    }
    @Test fun foodIndependentFromOtherModules() {
        val read = reader(mapOf("module_enabled_today" to "false", "module_enabled_wardrobe" to "false"))
        assertTrue(ModulePreferences.enabled("food", read))
        assertTrue(ModulePreferences.enabled("track", read))
    }
}
