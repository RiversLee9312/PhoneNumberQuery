// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 RiversLee9312 and contributors

package com.sariv.numberlookup

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.sariv.numberlookup.ui.defaultRegionForLocale

@RunWith(AndroidJUnit4::class)
class LookupDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun query(number: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("lookup").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("phone-input").performTextReplacement(number)
        compose.waitForIdle()
        compose.onNodeWithTag("lookup").performClick()
    }

    private fun waitForResult(prefix: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("result-number").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("result-number").assertTextContains(prefix, substring = true)
    }

    private fun selectRegion(iso: String) {
        compose.onNodeWithTag("region-picker").performClick()
        compose.onNodeWithTag("region-search").performTextReplacement(iso)
        compose.onNodeWithTag("region-list").performScrollToNode(hasTestTag("region-$iso"))
        compose.onNodeWithTag("region-$iso").performClick()
        compose.waitForIdle()
    }

    @Test fun appRequestsNoNetworkOrPersonalDataPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(context.packageName, 4096).requestedPermissions.orEmpty().toList()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.READ_CONTACTS"))
        assertFalse(permissions.contains("android.permission.READ_PHONE_STATE"))
    }

    @Test fun licensePagesShowProjectAndThirdPartyLicenses() {
        compose.onNodeWithTag("about").performClick()
        compose.onNodeWithTag("open-licenses").performScrollTo().performClick()
        compose.onNodeWithTag("license-app").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("license-detail").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("license-detail").assertTextContains("Apache License", substring = true)
        compose.onNodeWithTag("license-back").performClick()
        compose.onNodeWithTag("license-list").performScrollToNode(hasTestTag("license-china"))
        compose.onNodeWithTag("license-china").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("license-detail").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("license-detail").assertTextContains("MIT License", substring = true)
        compose.onNodeWithTag("license-detail").assertTextContains("2026 Jieeit", substring = true)
        saveScreenshot("licenses.png")
    }

    @Test fun chinaNationalNumberUsesSelectedRegionAndCanBeCleared() {
        selectRegion("CN")
        query("13800138000")
        waitForResult("+86")
        compose.onNodeWithTag("clear").performClick()
        compose.onNodeWithTag("phone-input").assertTextContains("")
        compose.onNodeWithTag("result").assertDoesNotExist()
    }

    @Test fun ukNationalNumberUsesSelectedCountry() {
        selectRegion("GB")
        query("020 7946 0018")
        waitForResult("+44")
        saveScreenshot("international.png")
    }

    @Test fun nationalUsNumberUsesChosenCountry() {
        selectRegion("US")
        query("6502530000")
        waitForResult("+1")
    }

    @Test fun unicodeArabicDigitsResolve() {
        selectRegion("IN")
        query("٩٨١٢٣٤٥٦٧٨")
        waitForResult("+91")
    }

    @Test fun explicitCountryPrefixIsRejectedByNationalInput() {
        selectRegion("CN")
        query("+1 6502530000")
        compose.waitForIdle()
        compose.onNodeWithTag("result").assertDoesNotExist()
    }

    @Test fun languageSettingsDetermineTheDefaultRegion() {
        val cases = mapOf("en" to "US", "en-GB" to "GB", "zh-CN" to "CN", "zh-TW" to "TW", "ja" to "JP",
            "ko" to "KR", "de" to "DE", "fr" to "FR", "pt-BR" to "BR", "ar-EG" to "EG")
        cases.forEach { (tag, region) -> assertEquals(tag, region, defaultRegionForLocale(Locale.forLanguageTag(tag))) }
    }

    @Test fun regionSearchAcceptsNonLatinDigits() {
        compose.onNodeWithTag("region-picker").performClick()
        compose.onNodeWithTag("region-search").performTextReplacement("+٩١")
        compose.onNodeWithTag("region-IN").assertExists()
    }

    @Test fun invalidInputDoesNotProduceAResult() {
        query("+999123")
        compose.waitForIdle()
        compose.onNodeWithTag("result").assertDoesNotExist()
        compose.onNodeWithTag("phone-input").assertExists()
    }

    @Test fun everyDeclaredLocaleLoadsAndFormatsResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locales = listOf("en", "zh-CN", "zh-TW", "ja", "ko", "es", "fr", "de", "it", "pt-BR",
            "ru", "uk", "ar", "hi", "bn", "id", "vi", "th", "tr", "fa", "pl", "nl", "sv", "ms", "fil")
        locales.forEach { tag ->
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocales(LocaleList.forLanguageTags(tag))
            val localized = context.createConfigurationContext(configuration)
            assertTrue(tag, localized.getString(R.string.screen_title).isNotBlank())
            assertTrue(tag, localized.getString(R.string.about_version, "1.0.0").contains("1.0.0"))
            assertFalse(tag, localized.getString(R.string.non_geographic_number).isBlank())
            if (tag != "en") assertNotEquals(tag, "Look up", localized.getString(R.string.lookup))
            if (tag == "ar" || tag == "fa") assertEquals(tag, android.view.View.LAYOUT_DIRECTION_RTL, configuration.layoutDirection)
        }
    }

    @Test fun adaptiveIconProvidesAnExplicitMonochromeLayer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val monochrome = (icon as AdaptiveIconDrawable).monochrome
            assertNotNull(monochrome)
            val image = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
            monochrome!!.setBounds(0, 0, 216, 216)
            monochrome.draw(Canvas(image))
            assertEquals(0, android.graphics.Color.alpha(image.getPixel(0, 0)))
            assertTrue((0 until 216).any { y -> (0 until 216).any { x -> android.graphics.Color.alpha(image.getPixel(x, y)) > 128 } })
            val path = File(context.getExternalFilesDir(null), "themed-icon.png")
            path.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screen = instrumentation.uiAutomation.takeScreenshot()
        val path = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        path.outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
