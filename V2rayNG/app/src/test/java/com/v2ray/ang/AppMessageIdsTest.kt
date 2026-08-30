package com.v2ray.ang

import org.junit.Assert.assertTrue
import org.junit.Test

class AppMessageIdsTest {
    @Test
    fun combinedFeaturesHaveDistinctMessageIds() {
        val collisions = AppConfig::class.java.declaredFields
            .filter { it.name.startsWith("MSG_") && it.type == Int::class.javaPrimitiveType }
            .groupBy { it.getInt(null) }
            .filterValues { it.size > 1 }
            .mapValues { (_, fields) -> fields.map { it.name } }

        assertTrue("IPC message ID collisions: $collisions", collisions.isEmpty())
    }
}
