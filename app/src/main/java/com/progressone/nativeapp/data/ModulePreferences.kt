package com.progressone.nativeapp.data

/** One main menu for feature visibility. Tracker never turns off; data is unaffected. */
data class AppModule(val id: String, val title: String, val description: String)
object ModulePreferences {
    const val TRACK = "track"
    val navigation = listOf("today", TRACK, "wardrobe", "food", "gym", "sleep", "social", "school", "money")
    val main = listOf(
        AppModule(TRACK, "Tracker", "Always on · habits, goals, tasks and reports"),
        AppModule("today", "Today", "Daily overview and quick actions"),
        AppModule("challenges", "Challenges", "Private offline challenges"),
        AppModule("wardrobe", "Wardrobe", "Clothes, photos and outfits"),
        AppModule("food", "Food", "Meal photos, AI nutrition and food scores"),
        AppModule("gym", "Gym", "Workout templates, sessions and personal records"),
        AppModule("sleep", "Sleep", "Nights, quality and recovery score"),
        AppModule("social", "Social detox", "Daily limits for social apps"),
        AppModule("school", "School", "Homework, exams and grades"),
        AppModule("money", "Money", "Budget and transactions"))
    val wardrobe = listOf(
        AppModule("outfits", "Saved outfits", "Your outfit gallery"),
        AppModule("mixmatch", "Mix & Match", "Compose outfits by combining pieces"),
        AppModule("lists", "Wardrobe lists", "Packing and wishes"),
        AppModule("weather", "Weather", "Optional weather in Wardrobe"))
    val home = listOf(
        AppModule("quotes", "Daily quotes", "Short daily quotes"),
        AppModule("summary", "Progress summary", "Extra progress report"),
        AppModule("insights", "Insights", "Additional information cards"))
    val all = main + wardrobe + home
    private val ids = all.map { it.id }.toSet()
    private val parents = mapOf("outfits" to listOf("wardrobe"), "mixmatch" to listOf("wardrobe","outfits"),
        "lists" to listOf("wardrobe"), "weather" to listOf("wardrobe"), "quotes" to listOf("today"),
        "summary" to listOf("today"), "insights" to listOf("today"))
    fun settingKey(id: String): String = if (id == "school") "show_school" else "module_enabled_$id"
    fun selected(id: String, read: (String, String) -> String): Boolean =
        (id == TRACK || id == "today") || (id in ids && read(settingKey(id), "true") == "true")
    fun enabled(id: String, read: (String, String) -> String): Boolean =
        selected(id, read) && parents[id].orEmpty().all { enabled(it, read) }
    fun enabledTabs(read: (String, String) -> String): List<String> = navigation.filter { enabled(it, read) }
    fun canDisable(id: String): Boolean = id in ids && id != TRACK && id != "today"
}
