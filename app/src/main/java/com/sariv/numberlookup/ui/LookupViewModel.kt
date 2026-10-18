// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 RiversLee9312 and contributors

package com.sariv.numberlookup.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sariv.numberlookup.data.LookupOutcome
import com.sariv.numberlookup.data.LookupError
import com.sariv.numberlookup.data.PhoneNumberDetails
import com.sariv.numberlookup.data.OfflinePhoneRepository
import com.sariv.numberlookup.data.SupportedRegion
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LookupUiState(
    val raw: String,
    val regionIso: String,
    val ready: Boolean = false,
    val loading: Boolean = false,
    val dataError: Boolean = false,
    val outcome: LookupOutcome? = null,
    val regions: List<SupportedRegion> = emptyList(),
)

class LookupViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private var displayLocale = application.resources.configuration.locales[0] ?: Locale.getDefault()
    private val mutableState = MutableStateFlow(LookupUiState(
        raw = savedState["raw"] ?: "",
        regionIso = if (savedState.get<String>("regionLocale") == displayLocale.toLanguageTag())
            savedState["region"] ?: defaultRegionForLocale(displayLocale) else defaultRegionForLocale(displayLocale),
    ))
    val state = mutableState.asStateFlow()
    private var queryJob: Job? = null
    private var initializationJob: Job? = null
    private var resultLocale: String? = null

    init { initialize() }

    fun initialize() {
        if (initializationJob?.isActive == true) return
        mutableState.update { it.copy(ready = false, dataError = false) }
        initializationJob = viewModelScope.launch {
            try {
                val regions = withContext(Dispatchers.IO) {
                    if (!OfflinePhoneRepository.isChinaDataReady) {
                        getApplication<Application>().assets.open("data/china_phone.dat").use {
                            OfflinePhoneRepository.initializeChinaData(it)
                        }
                    }
                    OfflinePhoneRepository.supportedRegions(displayLocale)
                }
                mutableState.update { it.copy(ready = true, regions = regions,
                    regionIso = it.regionIso.takeIf { iso -> regions.any { region -> region.regionIso == iso } } ?: "CN") }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutableState.update { it.copy(ready = false, dataError = true) }
            }
        }
    }

    fun setRaw(raw: String) {
        queryJob?.cancel()
        val value = raw.take(128)
        savedState["raw"] = value
        mutableState.update { it.copy(raw = value, outcome = null, loading = false) }
    }

    fun setRegion(regionIso: String) {
        queryJob?.cancel()
        savedState["region"] = regionIso
        savedState["regionLocale"] = displayLocale.toLanguageTag()
        mutableState.update { it.copy(regionIso = regionIso, outcome = null, loading = false) }
    }

    fun clear() = setRaw("")

    fun lookup(locale: Locale) {
        val input = mutableState.value
        if (!input.ready || input.loading) return
        queryJob?.cancel()
        mutableState.update { it.copy(loading = true, outcome = null) }
        queryJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    if (input.raw.any { it == '+' || it == '\uFF0B' || it == '\uFE62' }) {
                        LookupOutcome.InvalidInput(LookupError.INVALID_CHARACTER, "")
                    } else {
                        when (val found = OfflinePhoneRepository.lookup(input.raw, input.regionIso, locale)) {
                            is LookupOutcome.Success -> {
                                val selectedCode = input.regions.firstOrNull { it.regionIso == input.regionIso }?.countryCode
                                if (found.number.countryCode != selectedCode) LookupOutcome.NotValidated(found.number)
                                else LookupOutcome.Success(localizeChinaDetail(found.number, locale))
                            }
                            else -> found
                        }
                    }
                }
                resultLocale = locale.toLanguageTag()
                mutableState.update { it.copy(outcome = result, loading = false) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutableState.update { it.copy(dataError = true, loading = false) }
            }
        }
    }

    fun refreshLocale(locale: Locale) {
        val changed = displayLocale.toLanguageTag() != locale.toLanguageTag()
        displayLocale = locale
        if (changed) setRegion(defaultRegionForLocale(locale))
        if (mutableState.value.ready) viewModelScope.launch {
            val regions = withContext(Dispatchers.Default) { OfflinePhoneRepository.supportedRegions(locale) }
            mutableState.update { it.copy(regions = regions) }
        }
        if (resultLocale != null && resultLocale != locale.toLanguageTag() && mutableState.value.outcome != null) lookup(locale)
    }

    private fun localizeChinaDetail(number: PhoneNumberDetails, locale: Locale): PhoneNumberDetails {
        val traditional = locale.language == "zh" && (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO"))
        if (!traditional || android.os.Build.VERSION.SDK_INT < 29 || number.chinaProvince == null || number.chinaCity == null) return number
        val converter = android.icu.text.Transliterator.getInstance("Simplified-Traditional")
        val province = converter.transliterate(number.chinaProvince)
        val city = converter.transliterate(number.chinaCity)
        return number.copy(
            geographicDescription = if (city == province || city.startsWith(province)) city else province + city,
            originalCarrier = number.originalCarrier?.let { converter.transliterate(it) },
            virtualCarrier = number.virtualCarrier?.let { converter.transliterate(it) },
            chinaProvince = province, chinaCity = city,
        )
    }
}
