// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 RiversLee9312 and contributors

package com.sariv.numberlookup.data

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflinePhoneRepositoryTest {
    @Before
    fun loadChinaData() {
        val asset = locateChinaAsset()
        asset.inputStream().use(OfflinePhoneRepository::initializeChinaData)
    }

    @Test
    fun parsesNationalAndInternationalNumbers() {
        val national = OfflinePhoneRepository.lookup("650-253-0000", "US", Locale.US)
        val international = OfflinePhoneRepository.lookup("+1 (650) 253-0000", null, Locale.US)

        assertEquals("+16502530000", national.success().e164)
        assertEquals("+16502530000", international.success().e164)
        assertEquals("US", international.success().regionIso)
    }

    @Test
    fun normalizesUnicodeDigitsAndFullWidthPlus() {
        val result = OfflinePhoneRepository.lookup("＋８６ １３８００１３８０００", null, Locale.SIMPLIFIED_CHINESE)

        assertEquals("+8613800138000", result.success().e164)
    }

    @Test
    fun reportsMalformedTextSeparatelyFromUnvalidatedNumbers() {
        val malformed = OfflinePhoneRepository.lookup("1-800-FLOWERS", "US", Locale.US)
        val unvalidated = OfflinePhoneRepository.lookup("1234567890", "US", Locale.US)

        assertTrue(malformed is LookupOutcome.InvalidInput)
        assertEquals(LookupError.INVALID_CHARACTER, (malformed as LookupOutcome.InvalidInput).reason)
        assertTrue(unvalidated is LookupOutcome.NotValidated)
    }

    @Test
    fun chinaTableReturnsItsDetailedPrefixAttribution() {
        val result = OfflinePhoneRepository.lookup("13800138000", "CN", Locale.SIMPLIFIED_CHINESE).success()

        assertEquals("CN", result.regionIso)
        assertEquals("北京", result.chinaProvince)
        assertEquals("北京", result.chinaCity)
        assertEquals("北京", result.geographicDescription)
        assertEquals("移动", result.originalCarrier)
        assertEquals(NumberType.MOBILE, result.type)
    }

    @Test
    fun traditionalChineseKeepsRawChinaDetailsForPresentationConversion() {
        val result = OfflinePhoneRepository.lookup(
            "13800138000",
            "CN",
            Locale.forLanguageTag("zh-Hant-TW"),
        ).success()

        assertEquals("北京", result.chinaProvince)
        assertEquals("北京", result.chinaCity)
        assertNull(result.geographicDescription)
        assertTrue(result.countryName.isNotBlank())
    }

    @Test
    fun traditionalChineseKeepsRawVirtualCarrierForPresentationConversion() {
        // Prefix 1604695 is present in the pinned JPHN asset and maps to a virtual operator.
        val result = OfflinePhoneRepository.lookup(
            "16046950000",
            "CN",
            Locale.forLanguageTag("zh-Hant-TW"),
        ).success()

        assertNull(result.geographicDescription)
        assertEquals("铁通", result.virtualCarrier)
    }

    @Test
    fun supportedRegionCountMatchesPickerAndExamplesValidateAcrossRegions() {
        val util = PhoneNumberUtil.getInstance()
        val regions = OfflinePhoneRepository.supportedRegions(Locale.US)
        assertEquals(OfflinePhoneRepository.supportedRegionCount, regions.size)

        var checkedExamples = 0
        for (region in regions) {
            for (type in arrayOf(PhoneNumberUtil.PhoneNumberType.MOBILE, PhoneNumberUtil.PhoneNumberType.FIXED_LINE)) {
                val example = util.getExampleNumberForType(region.regionIso, type) ?: continue
                val e164 = util.format(example, PhoneNumberUtil.PhoneNumberFormat.E164)
                val result = OfflinePhoneRepository.lookup(e164, region.regionIso, Locale.US)
                assertTrue("${region.regionIso} $type example $e164 should be valid: $result", result is LookupOutcome.Success)
                checkedExamples++
            }
        }
        println("supportedRegionCount=${regions.size}, checkedExamples=$checkedExamples")
        assertTrue("At least one regional example should be checked", checkedExamples > 0)
    }

    @Test
    fun validNumbersRemainSuccessWhenOptionalDetailsAreUnavailable() {
        // A non-geographical toll-free example has no original-carrier mapping.
        val util = PhoneNumberUtil.getInstance()
        val example = checkNotNull(util.getExampleNumberForNonGeoEntity(800))
        val e164 = util.format(example, PhoneNumberUtil.PhoneNumberFormat.E164)
        val result = OfflinePhoneRepository.lookup(e164, null, Locale.US).success()

        assertEquals("001", result.regionIso)
        assertEquals("", result.countryName)
        assertNull(result.originalCarrier)
    }

    private fun LookupOutcome.success(): PhoneNumberDetails {
        assertTrue("Expected a successful lookup, got $this", this is LookupOutcome.Success)
        return (this as LookupOutcome.Success).number
    }

    private fun locateChinaAsset(): File {
        var directory: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(6) {
            val base = directory ?: return@repeat
            val candidates = listOf(
                File(base, "app/src/main/assets/data/china_phone.dat"),
                File(base, "src/main/assets/data/china_phone.dat"),
            )
            candidates.firstOrNull(File::isFile)?.let { return it }
            directory = base.parentFile
        }
        throw AssertionError("Could not locate app/src/main/assets/data/china_phone.dat for the unit test.")
    }
}
