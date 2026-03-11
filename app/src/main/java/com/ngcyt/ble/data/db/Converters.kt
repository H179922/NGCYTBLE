package com.ngcyt.ble.data.db

import androidx.room.TypeConverter
import com.ngcyt.ble.domain.model.ThreatLevel
import com.ngcyt.ble.domain.model.ThreatSource
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String? =
        value?.joinToString(",")

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        value?.takeIf { it.isNotEmpty() }?.split(",")

    @TypeConverter
    fun fromStringSet(value: Set<String>?): String? =
        value?.joinToString(",")

    @TypeConverter
    fun toStringSet(value: String?): Set<String>? =
        value?.takeIf { it.isNotEmpty() }?.split(",")?.toSet()

    @TypeConverter
    fun fromThreatLevel(value: ThreatLevel?): String? =
        value?.name

    @TypeConverter
    fun toThreatLevel(value: String?): ThreatLevel? =
        value?.let { ThreatLevel.valueOf(it) }

    @TypeConverter
    fun fromThreatSource(value: ThreatSource?): String? =
        value?.name

    @TypeConverter
    fun toThreatSource(value: String?): ThreatSource? =
        value?.let { ThreatSource.valueOf(it) }

    @TypeConverter
    fun fromFloatArray(value: FloatArray?): ByteArray? {
        if (value == null) return null
        val buffer = ByteBuffer.allocate(value.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray?): FloatArray? {
        if (value == null) return null
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(value.size / 4) { buffer.getFloat() }
    }

    @TypeConverter
    fun fromIntByteArrayMap(value: Map<Int, ByteArray>?): ByteArray? {
        if (value == null || value.isEmpty()) return null
        // Format: [count][key1][len1][data1][key2][len2][data2]...
        val totalSize = 4 + value.entries.sumOf { 4 + 4 + it.value.size }
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(value.size)
        for ((key, data) in value) {
            buffer.putInt(key)
            buffer.putInt(data.size)
            buffer.put(data)
        }
        return buffer.array()
    }

    @TypeConverter
    fun toIntByteArrayMap(value: ByteArray?): Map<Int, ByteArray>? {
        if (value == null) return null
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        val count = buffer.getInt()
        val result = mutableMapOf<Int, ByteArray>()
        repeat(count) {
            val key = buffer.getInt()
            val len = buffer.getInt()
            val data = ByteArray(len)
            buffer.get(data)
            result[key] = data
        }
        return result
    }
}
