// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 RiversLee9312 and contributors

package com.sariv.numberlookup.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** Parsed allocation details for one China mobile-number prefix. */
internal data class ChinaPhonePrefixMatch(
    val province: String,
    val city: String,
    val baseCarrierName: String,
    val virtualCarrierName: String?,
)

/**
 * Read-only reader for jieeit/phone's documented JPHN v2 format.
 *
 * The source file stores sorted seven-digit prefixes, followed by province/city dictionary rows.
 * Absolute ByteBuffer reads make concurrent lookups safe without moving a shared cursor.
 */
internal class ChinaPhonePrefixDatabase private constructor(
    private val bytes: ByteArray,
    private val recordCount: Int,
    private val indexOffset: Int,
    private val provinces: Array<String>,
    private val cities: Array<String>,
) {
    private val buffer: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    fun find(nationalNumber: String): ChinaPhonePrefixMatch? {
        if (nationalNumber.length != 11 || nationalNumber.any { it !in '0'..'9' }) return null
        val key = nationalNumber.substring(0, 7).toIntOrNull() ?: return null
        var low = 0
        var high = recordCount - 1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val entryOffset = indexOffset + middle * INDEX_ENTRY_SIZE
            val prefix = buffer.getInt(entryOffset)
            when {
                prefix < key -> low = middle + 1
                prefix > key -> high = middle - 1
                else -> {
                    val cityId = buffer.getInt(entryOffset + 4)
                    val carrierId = buffer.getShort(entryOffset + 8).toInt() and 0xFFFF
                    val originalCarrier = CARRIERS[carrierId] ?: return null
                    val isVirtual = carrierId >= FIRST_VIRTUAL_CARRIER_ID
                    val baseCarrier = if (isVirtual) {
                        baseCarrierFor(nationalNumber) ?: originalCarrier
                    } else {
                        originalCarrier
                    }
                    return ChinaPhonePrefixMatch(
                        province = provinces[cityId],
                        city = cities[cityId],
                        baseCarrierName = baseCarrier,
                        virtualCarrierName = originalCarrier.takeIf { isVirtual },
                    )
                }
            }
        }
        return null
    }

    companion object {
        private const val HEADER_SIZE = 36
        private const val INDEX_ENTRY_SIZE = 10
        private const val SUPPORTED_VERSION = 2
        private const val MAX_RECORD_COUNT = 1_000_000
        private const val MAX_CITY_COUNT = 100_000
        private const val FIRST_VIRTUAL_CARRIER_ID = 5

        private val CARRIERS = mapOf(
            1 to "移动",
            2 to "联通",
            3 to "电信",
            4 to "广电",
            5 to "铁通",
            6 to "天音移动",
            7 to "小米移动",
            8 to "星美通讯",
            9 to "星美通信",
            10 to "长江时代",
            11 to "海航通信",
            12 to "国美极信",
            13 to "民生通讯",
            14 to "朗玛移动",
            15 to "迪信通",
            16 to "京东通信",
            17 to "分享在线",
            18 to "话机通信",
            19 to "话机世界",
            20 to "用友通信",
            21 to "三五互联",
            22 to "联想懂的",
            23 to "连连科技",
            24 to "丰信通信",
            25 to "乐语通信",
            26 to "爱施德",
            27 to "虚拟运营商",
            28 to "中兴视通",
            29 to "中邮世纪",
            30 to "世纪互联",
            31 to "银盛通信",
            32 to "中邮普泰",
            33 to "华翔联信",
            34 to "苏宁互联",
            35 to "鹏博士移动",
            36 to "国美通信",
            37 to "优友互联",
            38 to "博元讯息",
            39 to "阿里通信",
            40 to "中期移动",
            41 to "远特信时空",
            42 to "广东恒大",
            43 to "红豆集团",
            44 to "星美生活",
            45 to "网信移动",
            46 to "分享通信",
            47 to "无限互联",
            48 to "日日顺通信",
            49 to "北纬通信",
            50 to "远特通信",
            51 to "长城移动",
            52 to "中麦通信",
            53 to "巴士在线",
            54 to "普泰移动",
            55 to "蜗牛移动",
            56 to "全民优打",
            57 to "豆电信",
            58 to "优酷移动",
            59 to "华云互联",
        )

        private val VIRTUAL_PREFIX_TO_BASE_CARRIER = mapOf(
            "165" to "移动",
            "1703" to "移动",
            "1705" to "移动",
            "1706" to "移动",
            "167" to "联通",
            "1704" to "联通",
            "1707" to "联通",
            "1708" to "联通",
            "1709" to "联通",
            "1710" to "联通",
            "1711" to "联通",
            "1712" to "联通",
            "1713" to "联通",
            "1714" to "联通",
            "1715" to "联通",
            "1716" to "联通",
            "1717" to "联通",
            "1718" to "联通",
            "1719" to "联通",
            "162" to "电信",
            "1700" to "电信",
            "1701" to "电信",
            "1702" to "电信",
        )

        fun parse(bytes: ByteArray): ChinaPhonePrefixDatabase {
            require(bytes.size >= HEADER_SIZE) { "JPHN database is shorter than its header." }
            require(bytes[0] == 'J'.code.toByte() &&
                bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'H'.code.toByte() &&
                bytes[3] == 'N'.code.toByte()
            ) { "JPHN database magic does not match." }

            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val version = header.getShort(4).toInt() and 0xFFFF
            require(version == SUPPORTED_VERSION) { "Unsupported JPHN database version: $version." }

            val recordCount = header.getInt(8)
            val indexOffset = header.getInt(12)
            val indexLength = header.getInt(16)
            val dictionaryOffset = header.getInt(20)
            val dictionaryLength = header.getInt(24)
            val cityCount = header.getInt(28)

            require(recordCount in 1..MAX_RECORD_COUNT) { "Invalid JPHN record count." }
            require(cityCount in 1..MAX_CITY_COUNT) { "Invalid JPHN city count." }
            require(indexOffset >= HEADER_SIZE) { "Invalid JPHN index offset." }
            require(indexLength.toLong() == recordCount.toLong() * INDEX_ENTRY_SIZE) {
                "JPHN index length does not match its record count."
            }
            val indexEnd = indexOffset.toLong() + indexLength.toLong()
            val dictionaryEnd = dictionaryOffset.toLong() + dictionaryLength.toLong()
            require(indexEnd <= bytes.size && dictionaryOffset >= indexEnd && dictionaryEnd == bytes.size.toLong()) {
                "JPHN sections are outside the database or overlap."
            }
            require(dictionaryLength >= 0) { "Invalid JPHN dictionary length." }

            val provinces = Array(cityCount) { "" }
            val cities = Array(cityCount) { "" }
            var cursor = dictionaryOffset
            val dictionaryLimit = dictionaryEnd.toInt()
            repeat(cityCount) { cityId ->
                val province = readLengthPrefixedString(bytes, cursor, dictionaryLimit)
                cursor = province.nextOffset
                val city = readLengthPrefixedString(bytes, cursor, dictionaryLimit)
                cursor = city.nextOffset
                require(province.value.isNotBlank() && city.value.isNotBlank()) {
                    "JPHN dictionary contains an empty place name."
                }
                provinces[cityId] = province.value
                cities[cityId] = city.value
            }
            require(cursor == dictionaryLimit) { "JPHN dictionary has trailing or missing bytes." }

            val index = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var previousPrefix = -1
            repeat(recordCount) { recordIndex ->
                val entryOffset = indexOffset + recordIndex * INDEX_ENTRY_SIZE
                val prefix = index.getInt(entryOffset)
                val cityId = index.getInt(entryOffset + 4)
                val carrierId = index.getShort(entryOffset + 8).toInt() and 0xFFFF
                require(prefix in 0..9_999_999 && prefix > previousPrefix) {
                    "JPHN prefixes are not in strictly increasing order."
                }
                require(cityId in 0 until cityCount) { "JPHN record has an invalid city id." }
                require(carrierId in CARRIERS) { "JPHN record has an unknown carrier id." }
                previousPrefix = prefix
            }

            return ChinaPhonePrefixDatabase(
                bytes = bytes,
                recordCount = recordCount,
                indexOffset = indexOffset,
                provinces = provinces,
                cities = cities,
            )
        }

        private data class DecodedString(val value: String, val nextOffset: Int)

        private fun readLengthPrefixedString(
            bytes: ByteArray,
            offset: Int,
            limit: Int,
        ): DecodedString {
            require(offset >= 0 && offset + 2 <= limit) { "JPHN dictionary string length is truncated." }
            val length = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            val end = offset.toLong() + 2L + length
            require(end <= limit) { "JPHN dictionary string is truncated." }
            val value = String(bytes, offset + 2, length, StandardCharsets.UTF_8)
            return DecodedString(value, end.toInt())
        }

        private fun baseCarrierFor(number: String): String? =
            VIRTUAL_PREFIX_TO_BASE_CARRIER[number.take(4)]
                ?: VIRTUAL_PREFIX_TO_BASE_CARRIER[number.take(3)]
    }
}
