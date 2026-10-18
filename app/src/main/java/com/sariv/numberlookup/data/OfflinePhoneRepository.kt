// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 RiversLee9312 and contributors

package com.sariv.numberlookup.data

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.io.InputStream
import java.text.Collator
import java.util.Locale

/** A region for the country picker. The calling code can be shared by several regions. */
data class SupportedRegion(
    val regionIso: String,
    val countryCode: Int,
    val countryName: String,
)

/** The type reported by libphonenumber's numbering-plan metadata. */
enum class NumberType {
    FIXED_LINE,
    MOBILE,
    FIXED_LINE_OR_MOBILE,
    TOLL_FREE,
    PREMIUM_RATE,
    SHARED_COST,
    VOIP,
    PERSONAL_NUMBER,
    PAGER,
    UAN,
    VOICEMAIL,
    UNKNOWN,
}

/** Formatted number and best-effort, offline numbering-plan details. */
data class PhoneNumberDetails(
    val raw: String,
    val international: String,
    val national: String,
    val e164: String,
    val countryCode: Int,
    /** ISO 3166-1 alpha-2 region, or `001` for a non-geographical calling code. */
    val regionIso: String,
    val countryName: String,
    /** A prefix-derived place name, not a live device or subscriber location. */
    val geographicDescription: String?,
    /** The carrier assigned to the number range; number portability can change the current carrier. */
    val originalCarrier: String?,
    val type: NumberType,
    /** Raw Simplified Chinese virtual-operator name; presentation converts it for Traditional Chinese. */
    val virtualCarrier: String? = null,
    /** Raw Simplified Chinese province; presentation converts it for Traditional Chinese. */
    val chinaProvince: String? = null,
    /** Raw Simplified Chinese city; presentation converts it for Traditional Chinese. */
    val chinaCity: String? = null,
)

enum class LookupError {
    EMPTY_NUMBER,
    NO_DIGITS,
    INVALID_CHARACTER,
    EXTENSION_NOT_SUPPORTED,
    DEFAULT_REGION_REQUIRED,
    UNSUPPORTED_REGION,
    PARSE_FAILED,
}

sealed interface LookupOutcome {
    /** The number parses and is valid according to global metadata or the supplemental CN table. */
    data class Success(val number: PhoneNumberDetails) : LookupOutcome

    /**
     * Parsing succeeded, but current bundled metadata could not validate the number. This does not
     * prove that the number is unused or unallocated; numbering plans and metadata can change.
     */
    data class NotValidated(val number: PhoneNumberDetails) : LookupOutcome

    /** The text is malformed or cannot be interpreted using the selected default region. */
    data class InvalidInput(val reason: LookupError, val message: String) : LookupOutcome
}

/**
 * Offline global phone-number parsing, validation, formatting, geocoding, and original-carrier
 * lookup. Google libphonenumber provides the worldwide numbering-plan backbone. Call
 * [initializeChinaData] once from an IO dispatcher with the bundled `data/china_phone.dat` asset
 * to enable finer Chinese province/city and operator details.
 *
 * This object never uses the network. A number beginning with `+` is parsed internationally;
 * national-format numbers require a valid [defaultRegion].
 */
object OfflinePhoneRepository {
    private val phoneUtil by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { PhoneNumberUtil.getInstance() }
    private val geocoder by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PhoneNumberOfflineGeocoder.getInstance()
    }
    private val carrierMapper by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PhoneNumberToCarrierMapper.getInstance()
    }

    @Volatile
    private var chinaDatabase: ChinaPhonePrefixDatabase? = null

    /** True after the bundled Chinese allocation data has been loaded successfully. */
    val isChinaDataReady: Boolean
        get() = chinaDatabase != null

    /** Number of geographical ISO regions with phone-number metadata. */
    val supportedRegionCount: Int
        get() = phoneUtil.supportedRegions.count {
            it != PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY
        }

    /**
     * Loads the JPHN v2 Chinese prefix database from the app asset.
     *
     * The caller owns [input] and should close it. The parsed data is immutable after publication;
     * replacing it is safe while lookups are running. This method can throw [IllegalArgumentException]
     * for malformed or unsupported data.
     */
    @Synchronized
    fun initializeChinaData(input: InputStream) {
        // Force the large global numbering metadata to load during the caller's IO initialization.
        phoneUtil.supportedRegions
        val parsed = ChinaPhonePrefixDatabase.parse(input.readBytes())
        chinaDatabase = parsed
    }

    /**
     * Regions for the country picker, named in [locale]. Non-geographical calling codes are not
     * included. A calling code may appear more than once for shared numbering plans.
     */
    fun supportedRegions(locale: Locale = Locale.getDefault()): List<SupportedRegion> {
        val collator = Collator.getInstance(locale)
        return phoneUtil.supportedRegions
            .asSequence()
            .filter { it != PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY }
            .map { regionIso ->
                val name = Locale.Builder().setRegion(regionIso).build().getDisplayCountry(locale)
                SupportedRegion(
                    regionIso = regionIso,
                    countryCode = phoneUtil.getCountryCodeForRegion(regionIso),
                    countryName = name.ifBlank { regionIso },
                )
            }
            .sortedWith { left, right ->
                val byName = collator.compare(left.countryName, right.countryName)
                if (byName != 0) byName else left.regionIso.compareTo(right.regionIso)
            }
            .toList()
    }

    /**
     * Parses one number and returns only offline numbering-plan facts. Unicode decimal digits,
     * full-width plus, common separators, and parentheses are normalized. Extensions and vanity
     * phonewords are rejected instead of being silently interpreted.
     */
    fun lookup(
        raw: String,
        defaultRegion: String?,
        locale: Locale = Locale.getDefault(),
    ): LookupOutcome {
        val normalized = normalizeInput(raw)
            ?: return invalid(LookupError.EMPTY_NUMBER, "Enter a phone number.")

        val normalizedNumber = when (normalized) {
            is Normalization.Invalid -> return invalid(normalized.reason, normalized.message)
            is Normalization.Valid -> normalized.number
        }

        val startsInternationally = normalizedNumber.startsWith("+")
        val parseRegion = if (startsInternationally) {
            // libphonenumber ignores the default region when an explicit + calling code is present.
            "ZZ"
        } else {
            val candidate = defaultRegion?.uppercase(Locale.ROOT)
                ?: return invalid(
                    LookupError.DEFAULT_REGION_REQUIRED,
                    "Choose a country or enter the number with a leading +.",
                )
            if (candidate !in phoneUtil.supportedRegions) {
                return invalid(LookupError.UNSUPPORTED_REGION, "The selected region is unsupported.")
            }
            candidate
        }

        val parsed = try {
            phoneUtil.parse(normalizedNumber, parseRegion)
        } catch (exception: NumberParseException) {
            return invalid(LookupError.PARSE_FAILED, exception.message ?: "The number could not be parsed.")
        }

        val numberRegion = phoneUtil.getRegionCodeForNumber(parsed)
            ?.takeIf(String::isNotBlank)
            ?: phoneUtil.getRegionCodeForCountryCode(parsed.countryCode)
        val countryName = countryNameFor(numberRegion, locale)
        val parsedDetails = basicDetails(
            raw = raw,
            parsed = parsed,
            regionIso = numberRegion,
            countryName = countryName,
            type = NumberType.UNKNOWN,
        )

        val isGlobalValid = phoneUtil.isValidNumber(parsed)
        val chinaMatch = if (numberRegion == "CN") {
            val nationalSignificantNumber = phoneUtil.getNationalSignificantNumber(parsed)
            chinaDatabase?.find(nationalSignificantNumber)
        } else {
            null
        }

        if (!isGlobalValid && chinaMatch == null) {
            return LookupOutcome.NotValidated(parsedDetails)
        }

        val libType = phoneUtil.getNumberType(parsed)
        val type = if (chinaMatch != null && libType == PhoneNumberType.UNKNOWN) {
            NumberType.MOBILE
        } else {
            libType.toDomainType()
        }

        val useChineseDetails = isSimplifiedChinese(locale) && chinaMatch != null
        val traditionalChinaLocale = isTraditionalChinese(locale) && chinaMatch != null
        val exposeRawChinaDetails = isChineseLocale(locale) && chinaMatch != null
        val geographicDescription = if (useChineseDetails) {
            chinaMatch!!.let { formatChinaLocation(it.province, it.city) }
        } else if (traditionalChinaLocale) {
            // Google's China geocoder bundle has no zh_Hant file; its zh fallback is Simplified.
            // Country-name fallback remains correctly localized without leaking simplified place names.
            null
        } else {
            geocoder.getDescriptionForNumber(parsed, locale).takeIf(String::isNotBlank)
        }
        val originalCarrier = if (useChineseDetails) {
            chinaMatch!!.baseCarrierName
        } else {
            carrierMapper.getNameForNumber(parsed, locale).takeIf(String::isNotBlank)
        }

        return LookupOutcome.Success(
            basicDetails(
                raw = raw,
                parsed = parsed,
                regionIso = numberRegion,
                countryName = countryName,
                type = type,
                geographicDescription = geographicDescription,
                originalCarrier = originalCarrier,
                virtualCarrier = if (exposeRawChinaDetails) chinaMatch!!.virtualCarrierName else null,
                chinaProvince = if (exposeRawChinaDetails) chinaMatch!!.province else null,
                chinaCity = if (exposeRawChinaDetails) chinaMatch!!.city else null,
            ),
        )
    }

    private fun countryNameFor(regionIso: String, locale: Locale): String {
        if (regionIso == PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY) {
            // The presentation layer maps this sentinel to its localized non-geographical label.
            return ""
        }
        val name = Locale.Builder().setRegion(regionIso).build().getDisplayCountry(locale)
        return name.ifBlank { regionIso }
    }

    private fun isSimplifiedChinese(locale: Locale): Boolean =
        locale.language.equals("zh", ignoreCase = true) && !isTraditionalChinese(locale)

    private fun isChineseLocale(locale: Locale): Boolean =
        locale.language.equals("zh", ignoreCase = true)

    private fun isTraditionalChinese(locale: Locale): Boolean =
        locale.language.equals("zh", ignoreCase = true) &&
            (locale.script.equals("Hant", ignoreCase = true) ||
                locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO"))

    private fun formatChinaLocation(province: String, city: String): String =
        if (city == province || city.startsWith(province)) city else "$province$city"

    private fun basicDetails(
        raw: String,
        parsed: com.google.i18n.phonenumbers.Phonenumber.PhoneNumber,
        regionIso: String,
        countryName: String,
        type: NumberType,
        geographicDescription: String? = null,
        originalCarrier: String? = null,
        virtualCarrier: String? = null,
        chinaProvince: String? = null,
        chinaCity: String? = null,
    ) = PhoneNumberDetails(
        raw = raw,
        international = phoneUtil.format(parsed, PhoneNumberFormat.INTERNATIONAL),
        national = phoneUtil.format(parsed, PhoneNumberFormat.NATIONAL),
        e164 = phoneUtil.format(parsed, PhoneNumberFormat.E164),
        countryCode = parsed.countryCode,
        regionIso = regionIso,
        countryName = countryName,
        geographicDescription = geographicDescription,
        originalCarrier = originalCarrier,
        type = type,
        virtualCarrier = virtualCarrier,
        chinaProvince = chinaProvince,
        chinaCity = chinaCity,
    )

    private fun PhoneNumberType.toDomainType(): NumberType = when (this) {
        PhoneNumberType.FIXED_LINE -> NumberType.FIXED_LINE
        PhoneNumberType.MOBILE -> NumberType.MOBILE
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> NumberType.FIXED_LINE_OR_MOBILE
        PhoneNumberType.TOLL_FREE -> NumberType.TOLL_FREE
        PhoneNumberType.PREMIUM_RATE -> NumberType.PREMIUM_RATE
        PhoneNumberType.SHARED_COST -> NumberType.SHARED_COST
        PhoneNumberType.VOIP -> NumberType.VOIP
        PhoneNumberType.PERSONAL_NUMBER -> NumberType.PERSONAL_NUMBER
        PhoneNumberType.PAGER -> NumberType.PAGER
        PhoneNumberType.UAN -> NumberType.UAN
        PhoneNumberType.VOICEMAIL -> NumberType.VOICEMAIL
        PhoneNumberType.UNKNOWN -> NumberType.UNKNOWN
    }

    private fun invalid(reason: LookupError, message: String) =
        LookupOutcome.InvalidInput(reason = reason, message = message)

    private sealed interface Normalization {
        data class Valid(val number: String) : Normalization
        data class Invalid(val reason: LookupError, val message: String) : Normalization
    }

    private fun normalizeInput(raw: String): Normalization? {
        if (raw.isBlank()) return null
        if (EXTENSION_PATTERN.containsMatchIn(raw)) {
            return Normalization.Invalid(
                LookupError.EXTENSION_NOT_SUPPORTED,
                "Extensions are not supported. Enter the main phone number only.",
            )
        }

        val result = StringBuilder(raw.length)
        var digitCount = 0
        var plusCount = 0
        var offset = 0
        while (offset < raw.length) {
            val codePoint = raw.codePointAt(offset)
            offset += Character.charCount(codePoint)
            when {
                Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER.toInt() -> {
                    val digit = Character.digit(codePoint, 10)
                    if (digit < 0) {
                        return Normalization.Invalid(LookupError.INVALID_CHARACTER, "The number contains an unsupported character.")
                    }
                    result.append(('0'.code + digit).toChar())
                    digitCount++
                }
                codePoint == '+'.code || codePoint == 0xFF0B || codePoint == 0xFE62 -> {
                    result.append('+')
                    plusCount++
                }
                Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || isSeparator(codePoint) -> {
                    result.append(' ')
                }
                Character.isLetter(codePoint) || codePoint == '#'.code -> {
                    return Normalization.Invalid(
                        LookupError.INVALID_CHARACTER,
                        "Letters and vanity phonewords are not supported.",
                    )
                }
                else -> {
                    return Normalization.Invalid(
                        LookupError.INVALID_CHARACTER,
                        "The number contains an unsupported character.",
                    )
                }
            }
        }

        if (digitCount == 0) {
            return Normalization.Invalid(LookupError.NO_DIGITS, "Enter at least one digit.")
        }
        val number = result.toString().trim()
        if (plusCount > 0 && (plusCount != 1 || !number.startsWith('+'))) {
            return Normalization.Invalid(LookupError.INVALID_CHARACTER, "Use + only before an international number.")
        }
        return Normalization.Valid(number)
    }

    private fun isSeparator(codePoint: Int): Boolean = when (codePoint) {
        '-'.code,
        '.'.code,
        '/'.code,
        '('.code,
        ')'.code,
        0x2010, // hyphen
        0x2011, // non-breaking hyphen
        0x2012, // figure dash
        0x2013, // en dash
        0x2014, // em dash
        0x2015, // horizontal bar
        0x2212, // minus sign
        -> true
        else -> false
    }

    private val EXTENSION_PATTERN = Regex("(?i)(?:\\bext\\.?|\\bx|#)\\s*\\d+\\s*$")
}
