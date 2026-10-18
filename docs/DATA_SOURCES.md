# Offline phone-number data sources

## Global numbering plans

The app uses Google's [`libphonenumber`](https://github.com/google/libphonenumber) Java library for parsing, formatting, region and number-type validation, localized offline geocoding, and original-carrier lookup. The selected upstream release is [v9.0.41](https://github.com/google/libphonenumber/releases/tag/v9.0.41), released October 7, 2026. Its bundled numbering metadata covers the regions returned by `PhoneNumberUtil.getSupportedRegions()`; `OfflinePhoneRepository.supportedRegions(locale)` exposes the geographical subset, with localized display names. Non-geographical calling codes use region code `001`; the repository returns an empty `countryName` for that sentinel so the UI can show its localized non-geographical label.

The app depends on these Maven Central artifacts:

| Artifact | Version | Purpose |
| --- | --- | --- |
| [`com.googlecode.libphonenumber:libphonenumber`](https://repo1.maven.org/maven2/com/googlecode/libphonenumber/libphonenumber/9.0.41/) | `9.0.41` | Parsing, formatting, numbering-plan validation, and number types |
| [`com.googlecode.libphonenumber:geocoder`](https://repo1.maven.org/maven2/com/googlecode/libphonenumber/geocoder/3.41/) | `3.41` | Offline prefix-based location descriptions |
| [`com.googlecode.libphonenumber:carrier`](https://repo1.maven.org/maven2/com/googlecode/libphonenumber/carrier/2.41/) | `2.41` | Offline original-carrier descriptions |
| [`com.googlecode.libphonenumber:prefixmapper`](https://repo1.maven.org/maven2/com/googlecode/libphonenumber/prefixmapper/3.41/) | `3.41` | Shared offline prefix tables, pulled transitively by geocoder and carrier |

These Java artifacts and their prefix data are Apache-2.0 licensed. See Google's [Android guidance](https://github.com/google/libphonenumber/blob/v9.0.41/FAQ.md#how-do-i-load-libphonenumber-resources-in-my-android-app) for loading metadata off the main thread. Carrier results describe the original number-range allocation, not necessarily the current carrier after number portability. Geocoder and carrier fields can be empty when the bundled prefix tables do not have a match. No location is obtained from a device, subscriber, or network lookup.

`PhoneNumberUtil.isValidNumber` checks the library's current numbering-plan metadata. It does not prove that a number is assigned to a subscriber or reachable. `LookupOutcome.NotValidated` means parsing succeeded but the bundled metadata could not validate the number; it does not prove that the range is unused. The library metadata changes in upstream releases, so the app should update these artifacts periodically.

## Supplemental China allocation data

The app includes [`china_phone.dat`](../app/src/main/assets/data/china_phone.dat), a JPHN v2 database from [jieeit/phone](https://github.com/jieeit/phone). Its upstream README reports a May 2026 data cutoff, 520,170 seven-digit mobile-prefix records, and 5,207,793 bytes. The data adds province/city and operator detail for China, including virtual operators. It is supplemental: global parsing and validation continue to use libphonenumber, while an exact seven-digit JPHN prefix match can validate an otherwise unrecognized 11-digit China number. This fallback follows the upstream range data and may inherit its errors or staleness; it is not an independent subscriber check.

For Simplified Chinese results, the app uses the table's Chinese province, city, and operator strings. Chinese-language results also expose the raw JPHN province, city, and virtual-carrier fields (`chinaProvince`, `chinaCity`, and `virtualCarrier`), which are stored in Simplified Chinese and must be converted by presentation code before showing them in a Traditional Chinese locale. The UI uses Android ICU's `Simplified-Traditional` conversion on API 29 and later; on API 26–28 it uses the localized country name as the geographic fallback. For Traditional Chinese (`zh-Hant`, `zh-TW`, `zh-HK`, or `zh-MO`), `geographicDescription` remains null and `originalCarrier` continues to come from libphonenumber's Traditional Chinese carrier mapper. The Google [China geocoder data](https://github.com/google/libphonenumber/tree/v9.0.41/java/geocoder/src/com/google/i18n/phonenumbers/geocoding/data) has a Simplified Chinese resource but no `zh_Hant` resource, so the app avoids its Simplified Chinese location fallback. Other locales use libphonenumber's localized offline geocoder and carrier mapper. The upstream project does not document independent telecom authority verification for each row, so the fine-grained China result should be read as the dataset's range attribution.

| Field | Value |
| --- | --- |
| Upstream repository | [jieeit/phone](https://github.com/jieeit/phone) |
| Pinned commit | [`e963c60de92383ca2c32eb318b10c469b34d11a4`](https://github.com/jieeit/phone/commit/e963c60de92383ca2c32eb318b10c469b34d11a4) |
| Upstream file | [`src/data/phone.dat`](https://github.com/jieeit/phone/blob/e963c60de92383ca2c32eb318b10c469b34d11a4/src/data/phone.dat) |
| Upstream blob SHA | `c19e053a1fc3c4437c236c171e88b80a2392649f` |
| Local SHA-256 | `7D0740136B98D7DBEE9A1C917E60D10453C739463898082D2CC1B9DCCB55D5BB` |
| File size | 5,207,793 bytes |
| Rows reported upstream | 520,170 |
| License | MIT; notice at [`jieeit-phone.txt`](../app/src/main/assets/licenses/jieeit-phone.txt) |

The upstream binary layout and source attribution are documented in the project's [README](https://github.com/jieeit/phone/blob/e963c60de92383ca2c32eb318b10c469b34d11a4/README.md). `metadata.json` beside the asset records the pinned commit, upstream blob identity, local size, and SHA-256. The reader verifies the JPHN magic, version, section bounds, dictionary, record ordering, place IDs, and carrier IDs before publishing the table for lookup.

## Offline behavior

All lookups use metadata bundled in the app's Maven dependencies and the bundled China asset. `OfflinePhoneRepository.initializeChinaData(input)` accepts a stream opened from `data/china_phone.dat`; the app calls this once on an IO dispatcher. Keep `supportedRegions` and `lookup` off the main thread as well: Google's Android guidance warns that first-time metadata loading can block or cause resource-loading errors on the main thread. The repository has no Android `Context` dependency and performs no network requests.
