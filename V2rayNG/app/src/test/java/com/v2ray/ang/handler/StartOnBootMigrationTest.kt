package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StartOnBootMigrationTest {

    @Test
    fun upstreamBootPreferenceSeedsMissingStartOnBootSetting() {
        for (legacy in listOf(false, true)) {
            val persisted = mutableListOf<Boolean>()
            assertEquals(legacy, migrateStartOnBootSetting(null, legacy, persisted::add))
            assertEquals(listOf(legacy), persisted)
        }
    }

    @Test
    fun explicitStartOnBootValueWinsWithoutRewritingStorage() {
        for (stored in listOf(false, true)) {
            for (legacy in listOf(false, true)) {
                val persisted = mutableListOf<Boolean>()
                assertEquals(stored, migrateStartOnBootSetting(stored, legacy, persisted::add))
                assertTrue(persisted.isEmpty())
            }
        }
    }
}
