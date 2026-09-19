package com.example.autolog.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** 날짜는 ISO 문자열(정렬 가능), 시각은 epoch millis로 저장한다. */
object Converters {

    @TypeConverter
    fun toLocalDate(value: String): LocalDate = LocalDate.parse(value)

    @TypeConverter
    fun fromLocalDate(value: LocalDate): String = value.toString()

    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilli()
}
