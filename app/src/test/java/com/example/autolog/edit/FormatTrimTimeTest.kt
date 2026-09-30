package com.example.autolog.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTrimTimeTest {

    @Test
    fun 일분_미만은_초로_쓴다() {
        assertEquals("0.0초", formatTrimTime(0))
        assertEquals("7.7초", formatTrimTime(7_789))
        assertEquals("59.9초", formatTrimTime(59_999))
    }

    @Test
    fun 일분부터는_분과_초로_쓴다() {
        assertEquals("1:00.0", formatTrimTime(60_000))
        assertEquals("1:05.3", formatTrimTime(65_300))
    }
}
