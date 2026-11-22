package com.progressone.nativeapp.data

import org.json.JSONObject

/** Default workout templates (gym, calisthenics, hybrid) stored as local records. */
object GymDefaults {

    private fun set(reps: Int, weight: Double): JSONObject = JSONObject().apply {
        put("reps", reps)
        put("weight", weight)
    }

    private fun exercise(
        id: String,
        name: String,
        restSeconds: Int,
        bodyweight: Boolean,
        sets: List<JSONObject>
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("rest", restSeconds)
        put("bodyweight", bodyweight)
        put("sets", org.json.JSONArray(sets))
    }

    private fun template(id: String, name: String, type: String, exercises: List<JSONObject>): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("name", name)
            put("type", type)
            put("exercises", org.json.JSONArray(exercises))
        }

    fun templatesFor(type: String): List<JSONObject> = when (type) {
        "calisthenics" -> listOf(
            template(
                "cali_1", "Calisthenics · Pull & Push", "calisthenics", listOf(
                    exercise("c1", "Pull-up", 90, true, listOf(set(8, 0.0), set(8, 0.0), set(6, 0.0))),
                    exercise("c2", "Parallel bar dips", 90, true, listOf(set(10, 0.0), set(10, 0.0), set(8, 0.0))),
                    exercise("c3", "Push-up", 60, true, listOf(set(15, 0.0), set(15, 0.0), set(12, 0.0))),
                    exercise("c4", "Australian pull-up", 60, true, listOf(set(12, 0.0), set(10, 0.0), set(10, 0.0)))
                )
            ),
            template(
                "cali_2", "Calisthenics · Core & Skills", "calisthenics", listOf(
                    exercise("c5", "Hanging leg raises", 75, true, listOf(set(10, 0.0), set(10, 0.0), set(8, 0.0))),
                    exercise("c6", "L-sit hold (seconds)", 60, true, listOf(set(20, 0.0), set(15, 0.0), set(15, 0.0))),
                    exercise("c7", "Pike push-up", 75, true, listOf(set(8, 0.0), set(8, 0.0), set(6, 0.0))),
                    exercise("c8", "Plank (seconds)", 45, true, listOf(set(60, 0.0), set(60, 0.0)))
                )
            )
        )
        "hybrid" -> listOf(
            template(
                "hyb_1", "Hybrid · Upper (weights & bar)", "hybrid", listOf(
                    exercise("h1", "Pull-up", 90, true, listOf(set(8, 0.0), set(8, 0.0), set(6, 0.0))),
                    exercise("h2", "Barbell bench press", 90, false, listOf(set(8, 60.0), set(8, 65.0), set(6, 70.0))),
                    exercise("h3", "Parallel bar dips", 75, true, listOf(set(10, 0.0), set(10, 0.0), set(8, 0.0))),
                    exercise("h4", "Dumbbell curl", 60, false, listOf(set(10, 14.0), set(10, 14.0), set(8, 16.0)))
                )
            ),
            template(
                "hyb_2", "Hybrid · Legs & core", "hybrid", listOf(
                    exercise("h5", "Barbell squat", 120, false, listOf(set(8, 70.0), set(8, 80.0), set(6, 90.0))),
                    exercise("h6", "Walking lunges", 75, false, listOf(set(10, 16.0), set(10, 16.0))),
                    exercise("h7", "Hanging leg raises", 60, true, listOf(set(10, 0.0), set(10, 0.0))),
                    exercise("h8", "Plank (seconds)", 45, true, listOf(set(60, 0.0), set(60, 0.0)))
                )
            )
        )
        else -> listOf(
            template(
                "gym_1", "Upper body · Chest & arms", "gym", listOf(
                    exercise("g1", "Barbell bench press", 90, false, listOf(set(10, 50.0), set(8, 60.0), set(6, 65.0))),
                    exercise("g2", "Incline dumbbell press", 75, false, listOf(set(10, 18.0), set(10, 20.0), set(8, 22.0))),
                    exercise("g3", "Dumbbell biceps curl", 60, false, listOf(set(12, 12.0), set(10, 14.0), set(8, 14.0))),
                    exercise("g4", "Triceps French press", 60, false, listOf(set(12, 20.0), set(10, 25.0), set(10, 25.0)))
                )
            ),
            template(
                "gym_2", "Lower body · Squat & legs", "gym", listOf(
                    exercise("g5", "Barbell squat", 120, false, listOf(set(10, 60.0), set(8, 75.0), set(6, 85.0))),
                    exercise("g6", "Leg press", 90, false, listOf(set(12, 120.0), set(10, 140.0), set(10, 140.0))),
                    exercise("g7", "Leg curl", 60, false, listOf(set(12, 35.0), set(10, 40.0))),
                    exercise("g8", "Plank (seconds)", 45, true, listOf(set(60, 0.0), set(60, 0.0)))
                )
            )
        )
    }

    /** Seeds the default templates once. The user can regenerate them at any time. */
    fun seedIfEmpty(store: LocalStore) {
        if (store.setting("gym_seeded", "false") == "true") return
        templatesFor(store.setting("gym_type", "gym")).forEach { store.put("gym_template", it) }
        store.setSetting("gym_seeded", "true")
    }

    fun regenerate(store: LocalStore, type: String) {
        store.all("gym_template").forEach { store.remove("gym_template", it.optString("id")) }
        templatesFor(type).forEach { store.put("gym_template", it) }
        store.setSetting("gym_type", type)
        store.setSetting("gym_seeded", "true")
    }
}
