package com.v2ray.ang.handler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartOnBootMigrationTest {

    @Test
    fun previousReleasedAutoConnectValueSeedsMissingStartOnBootSetting() {
        val persisted = mutableListOf<Boolean>()

        assertTrue(migrateStartOnBootSetting(null, true, persisted::add))
        assertEquals(listOf(true), persisted)
    }

    @Test
    fun explicitStartOnBootValueWinsWithoutRewritingStorage() {
        val persisted = mutableListOf<Boolean>()

        assertFalse(migrateStartOnBootSetting(false, true, persisted::add))
        assertTrue(persisted.isEmpty())
    }
}
