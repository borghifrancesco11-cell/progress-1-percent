package com.progressone.nativeapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GymSessionStateTest {
    @Test
    fun `active workout survives a save and restore cycle`() {
        val original = GymSessionState(
            templateJson = """{"id":"template-1","name":"Strength"}""",
            exercises = listOf(
                GymExercise(
                    name = "Squat",
                    rest = 90,
                    bodyweight = false,
                    sets = listOf(
                        GymSet(reps = 8, weight = 60.0, done = true),
                        GymSet(reps = 6, weight = 70.0, done = false),
                    ),
                ),
            ),
            elapsedSeconds = 725,
            restRemaining = 43,
        )

        val encoded = GymSessionStateCodec.encode(original)
        val restored = GymSessionStateCodec.decode(encoded)

        assertNotNull(restored)
        assertEquals(original.templateJson, restored!!.templateJson)
        assertEquals(original.elapsedSeconds, restored.elapsedSeconds)
        assertEquals(original.restRemaining, restored.restRemaining)
        assertEquals(1, restored.exercises.size)
        assertEquals(2, restored.exercises.first().sets.size)
        assertTrue(restored.exercises.first().sets.first().done)
        assertEquals(60.0, restored.exercises.first().sets.first().weight, 0.000_001)
    }

    @Test
    fun `invalid persisted workout payload is rejected instead of partially restored`() {
        assertEquals(null, GymSessionStateCodec.decode("not-json"))
        assertEquals(null, GymSessionStateCodec.decode("""{"template":"not-json"}"""))
    }
}
