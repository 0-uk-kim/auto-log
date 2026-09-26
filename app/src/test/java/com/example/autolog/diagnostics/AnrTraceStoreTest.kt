package com.example.autolog.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class AnrTraceStoreTest {

    @Test
    fun `최근 것부터 남기고 나머지를 지운다`() {
        val names = listOf("1000.txt", "5000.txt", "3000.txt", "4000.txt", "2000.txt")
        assertEquals(listOf("2000.txt", "1000.txt"), oldTraces(names, keep = 3))
    }

    @Test
    fun `자릿수가 달라도 시각 순으로 판단한다`() {
        assertEquals(listOf("999.txt"), oldTraces(listOf("999.txt", "1000.txt"), keep = 1))
    }

    @Test
    fun `남길 개수보다 적으면 지우지 않는다`() {
        assertEquals(emptyList<String>(), oldTraces(listOf("1000.txt"), keep = 5))
    }
}
