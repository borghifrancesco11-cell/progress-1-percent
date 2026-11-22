package com.progressone.nativeapp

import com.progressone.nativeapp.data.OutfitLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class OutfitLogicTest {
    @Test fun previousWrapsToLast() {
        assertEquals(3, OutfitLogic.nextIndex(0, 4, -1))
    }

    @Test fun nextWrapsToFirst() {
        assertEquals(0, OutfitLogic.nextIndex(3, 4, 1))
    }

    @Test fun emptyOrSingletonGroupsDoNotFail() {
        assertEquals(0, OutfitLogic.nextIndex(0, 0, 1))
        assertEquals(0, OutfitLogic.shuffleIndex(0, 1))
    }

    @Test fun shuffledItemAlwaysChangesWhenAlternativesExist() {
        for (current in 0..3) {
            val result = OutfitLogic.shuffleIndex(current, 4, Random(current))
            assertNotEquals(current, result)
        }
    }
}
