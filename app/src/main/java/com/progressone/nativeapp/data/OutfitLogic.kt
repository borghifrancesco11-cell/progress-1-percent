package com.progressone.nativeapp.data

import kotlin.random.Random

/** Stateless helpers that can be unit tested without starting a Compose screen. */
object OutfitLogic {
    fun nextIndex(current: Int, size: Int, movement: Int): Int =
        if (size <= 1) 0 else Math.floorMod(current + movement, size)

    fun shuffleIndex(current: Int, size: Int, random: Random = Random.Default): Int =
        if (size <= 1) 0 else nextIndex(current, size, random.nextInt(1, size))
}
