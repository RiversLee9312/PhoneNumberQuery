// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 RiversLee9312 and contributors

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.sariv.numberlookup.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sariv.numberlookup.BuildConfig
import com.sariv.numberlookup.R
import com.sariv.numberlookup.data.LookupOutcome
import com.sariv.numberlookup.data.NumberType
import com.sariv.numberlookup.data.PhoneNumberDetails
import com.sariv.numberlookup.data.SupportedRegion
import java.util.Locale
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
fun NumberLookupApp(model: LookupViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val regions = state.regions
    val selectedRegion = regions.firstOrNull { it.regionIso == state.regionIso }
    var showRegions by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)

    LaunchedEffect(locale.toLanguageTag()) { model.refreshLocale(locale) }

    fun search() {
        focus.clearFocus()
        keyboard?.hide()
        model.lookup(locale)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.screen_title)) },
                actions = {
                    IconButton(onClick = { showAbout = true }, modifier = Modifier.testTag("about")) {
                        Symbol(R.drawable.ic_info, stringResource(R.string.info))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = { showRegions = true },
                enabled = state.ready,
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().testTag("region-picker"),
            ) {
                Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Symbol(R.drawable.ic_language, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.country_or_region), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedRegion?.countryName ?: state.regionIso,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Text(selectedRegion?.countryCode?.let { "+$it" }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Symbol(R.drawable.ic_arrow_drop_down, null)
                }
            }

            val errorText = when {
                state.dataError -> stringResource(R.string.data_load_error)
                state.outcome is LookupOutcome.InvalidInput -> if (state.raw.isBlank())
                    stringResource(R.string.empty_number) else stringResource(R.string.invalid_number)
                state.outcome is LookupOutcome.NotValidated -> stringResource(R.string.number_not_found)
                else -> null
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = state.raw, onValueChange = model::setRaw,
                    modifier = Modifier.fillMaxWidth().testTag("phone-input"),
                    label = { Text(stringResource(R.string.phone_number)) },
                    placeholder = { Text(stringResource(R.string.phone_placeholder), style = MaterialTheme.typography.bodyLarge) },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr,
                        fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    isError = errorText != null,
                    supportingText = if (errorText != null) { {
                        Text(errorText, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    } } else null,
                    trailingIcon = {
                        if (state.raw.isNotEmpty()) {
                            IconButton(onClick = model::clear, modifier = Modifier.testTag("clear")) {
                                Symbol(R.drawable.ic_close, stringResource(R.string.clear_number))
                            }
                        } else {
                            val clipboardEmpty = stringResource(R.string.clipboard_empty)
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context).toString() else ""
                                if (text.isBlank()) scope.launch { snackbar.showSnackbar(clipboardEmpty) }
                                else model.setRaw(text)
                            }) { Symbol(R.drawable.ic_content_paste, stringResource(R.string.paste_number)) }
                        }
                    },
                )
                Button(
                    onClick = { if (state.dataError) model.initialize() else search() },
                    enabled = state.ready && !state.loading || state.dataError,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight).testTag("lookup"),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) {
                    if (!state.ready && !state.dataError || state.loading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    } else Symbol(R.drawable.ic_search, null, Modifier.size(ButtonDefaults.MediumIconSize))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(if (state.dataError) R.string.retry else if (!state.ready || state.loading) R.string.loading else R.string.lookup),
                        style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }

            val rawResult = (state.outcome as? LookupOutcome.Success)?.number
            val result = rawResult?.let {
                if (it.regionIso == "001") it.copy(countryName = stringResource(R.string.non_geographic_number)) else it
            }
            Crossfade(targetState = result, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "result") { number ->
                if (number != null) {
                    val copiedText = stringResource(R.string.result_copied)
                    val copyText = resultClipboardText(number)
                    ResultCard(number, onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(appName, copyText))
                        if (Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar(copiedText) }
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        }
    }
    if (showRegions) RegionSheet(regions, state.regionIso, { showRegions = false }) {
        model.setRegion(it)
        showRegions = false
    }
    if (showAbout) AboutSheet(onDismiss = { showAbout = false }, onLicenses = { showLicenses = true })
    if (showLicenses) LicenseSheet(onDismiss = { showLicenses = false })
}

@Composable
private fun ResultCard(number: PhoneNumberDetails, onCopy: () -> Unit) {
    val hasDetail = !number.geographicDescription.isNullOrBlank() && number.geographicDescription != number.countryName
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("result").semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Symbol(R.drawable.ic_location_on, null, Modifier.size(26.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(if (hasDetail) number.geographicDescription.orEmpty() else number.countryName,
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("result-location").semantics { heading() })
                    Text(number.international, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("result-number"))
                }
                IconButton(onClick = onCopy, modifier = Modifier.testTag("copy-result")) {
                    Symbol(R.drawable.ic_content_copy, stringResource(R.string.copy_result))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ResultDetail(stringResource(R.string.result_country), number.countryName)
                ResultDetail(stringResource(R.string.result_carrier), number.originalCarrier?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.unknown_carrier))
                if (!number.virtualCarrier.isNullOrBlank()) ResultDetail(stringResource(R.string.result_virtual_carrier), number.virtualCarrier.orEmpty())
                ResultDetail(stringResource(R.string.result_number_type), stringResource(typeLabel(number.type)))
            }
            if (!hasDetail && number.regionIso != "001") Text(stringResource(R.string.region_country_only),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@StringRes
private fun typeLabel(type: NumberType): Int = when (type) {
    NumberType.MOBILE -> R.string.number_type_mobile
    NumberType.FIXED_LINE -> R.string.number_type_fixed_line
    NumberType.FIXED_LINE_OR_MOBILE -> R.string.number_type_fixed_or_mobile
    NumberType.TOLL_FREE -> R.string.number_type_toll_free
    NumberType.PREMIUM_RATE -> R.string.number_type_premium
    NumberType.SHARED_COST -> R.string.number_type_shared_cost
    NumberType.PERSONAL_NUMBER -> R.string.number_type_personal
    NumberType.PAGER -> R.string.number_type_pager
    NumberType.UAN -> R.string.number_type_uan
    NumberType.VOIP -> R.string.number_type_voip
    NumberType.VOICEMAIL -> R.string.number_type_voicemail
    else -> R.string.number_type_other
}

@Composable
private fun resultClipboardText(number: PhoneNumberDetails): String = buildString {
    append(number.international)
    append('\n'); append(stringResource(R.string.result_region)); append(": ")
    append(number.geographicDescription?.takeIf { it.isNotBlank() } ?: number.countryName)
    append('\n'); append(stringResource(R.string.result_country)); append(": "); append(number.countryName)
    append('\n'); append(stringResource(R.string.result_carrier)); append(": ")
    append(number.originalCarrier?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown_carrier))
}

@Composable
private fun RegionSheet(regions: List<SupportedRegion>, current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(search, regions) {
        val query = normalizeRegionSearch(search).trim().removePrefix("+")
        regions.filter { region ->
            query.isEmpty() || normalizeRegionSearch(region.countryName).contains(query) ||
                region.regionIso.lowercase(Locale.ROOT).contains(query) || region.countryCode.toString().startsWith(query) ||
                normalizeRegionSearch(Locale.Builder().setRegion(region.regionIso).build().getDisplayCountry(Locale.ENGLISH)).contains(query)
        }.sortedByDescending { it.regionIso.equals(query, ignoreCase = true) || normalizeRegionSearch(it.countryName) == query }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            SheetHeading(stringResource(R.string.choose_region), onDismiss)
            OutlinedTextField(value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("region-search"),
                label = { Text(stringResource(R.string.search_regions)) }, singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                leadingIcon = { Symbol(R.drawable.ic_search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) Text(stringResource(R.string.no_regions), Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f).testTag("region-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.regionIso }) { region ->
                    Surface(onClick = { onSelect(region.regionIso) },
                        color = if (current == region.regionIso) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth()
                            .semantics { selected = current == region.regionIso }.testTag("region-${region.regionIso}")) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(region.countryName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("+${region.countryCode}", style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (current == region.regionIso) Symbol(R.drawable.ic_check, null)
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeRegionSearch(value: String): String = buildString {
    Normalizer.normalize(value, Normalizer.Form.NFKD).forEach { character ->
        if (Character.getType(character) != Character.NON_SPACING_MARK.toInt()) {
            val digit = Character.digit(character, 10)
            append(if (digit >= 0) ('0'.code + digit).toChar() else character)
        }
    }
}.lowercase(Locale.ROOT)

@Composable
private fun AboutSheet(onDismiss: () -> Unit, onLicenses: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SheetHeading(stringResource(R.string.about_title), onDismiss)
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AboutSection(R.string.about_offline_title, R.string.about_offline_body)
                AboutSection(R.string.about_accuracy_title, R.string.about_accuracy_body)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.about_sources_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.about_global_source), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_china_source), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onLicenses, modifier = Modifier.align(Alignment.Start).testTag("open-licenses")) {
                    Text(stringResource(R.string.about_licenses))
                }
            }
        }
    }
}

@Composable
private fun AboutSection(@StringRes title: Int, @StringRes body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LicenseSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entries = listOf(
        LicenseEntry("app", stringResource(R.string.app_name), "Apache-2.0", "number-lookup.txt"),
        LicenseEntry("libphonenumber", "Google libphonenumber", "Apache-2.0", "libphonenumber.txt"),
        LicenseEntry("androidx", "AndroidX", "Apache-2.0", "libphonenumber.txt"),
        LicenseEntry("kotlin", "Kotlin · kotlinx", "Apache-2.0", "libphonenumber.txt"),
        LicenseEntry("annotations", "JetBrains Annotations", "Apache-2.0", "libphonenumber.txt"),
        LicenseEntry("guava", "Guava ListenableFuture", "Apache-2.0", "libphonenumber.txt"),
        LicenseEntry("jspecify", "JSpecify", "Apache-2.0", "libphonenumber.txt"),
        LicenseEntry("symbols", "Material Symbols", "Apache-2.0", "material-symbols.txt"),
        LicenseEntry("china", stringResource(R.string.china_phone_data), "MIT", "jieeit-phone.txt"),
    )
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = entries.firstOrNull { it.id == selectedId }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            if (selected == null) {
                SheetHeading(stringResource(R.string.licenses_title), onDismiss)
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("license-list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { LicenseGroupLabel(stringResource(R.string.app_license)) }
                    item { LicenseRow(entries.first()) { selectedId = it.id } }
                    item { LicenseGroupLabel(stringResource(R.string.third_party_licenses)) }
                    items(entries.drop(1), key = { it.id }) { entry ->
                        LicenseRow(entry) { selectedId = it.id }
                    }
                }
            } else {
                BackHandler { selectedId = null }
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedId = null }, modifier = Modifier.testTag("license-back")) {
                        Symbol(R.drawable.ic_arrow_back, stringResource(R.string.licenses_title))
                    }
                    Text(selected.name, style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onDismiss) { Symbol(R.drawable.ic_close, stringResource(R.string.close)) }
                }
                var text by remember(selected.id) { mutableStateOf<String?>(null) }
                var loadError by remember(selected.id) { mutableStateOf(false) }
                var attempt by remember(selected.id) { mutableIntStateOf(0) }
                LaunchedEffect(selected.id, attempt) {
                    loadError = false
                    try {
                        text = withContext(Dispatchers.IO) {
                            context.assets.open("licenses/${selected.asset}").bufferedReader(Charsets.UTF_8).use { it.readText() }
                        }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        loadError = true
                    }
                }
                when {
                    loadError -> Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.license_load_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
                    }
                    text == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> SelectionContainer(Modifier.weight(1f)) {
                        Text(text.orEmpty(), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState()).padding(bottom = 24.dp).testTag("license-detail"),
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                    }
                }
            }
        }
    }
}

private data class LicenseEntry(val id: String, val name: String, val spdx: String, val asset: String)

@Composable
private fun LicenseGroupLabel(label: String) {
    Text(label, Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LicenseRow(entry: LicenseEntry, onClick: (LicenseEntry) -> Unit) {
    Surface(onClick = { onClick(entry) }, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("license-${entry.id}")) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                Text(entry.spdx, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Symbol(R.drawable.ic_chevron_right, null)
        }
    }
}

@Composable
private fun SheetHeading(title: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
        IconButton(onClick = onDismiss) { Symbol(R.drawable.ic_close, stringResource(R.string.close)) }
    }
}

@Composable
private fun Symbol(@DrawableRes resource: Int, description: String?, modifier: Modifier = Modifier.size(24.dp),
    tint: androidx.compose.ui.graphics.Color = LocalContentColor.current) {
    Icon(painterResource(resource), description, modifier, tint)
}
