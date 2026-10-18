// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 RiversLee9312 and contributors

package com.sariv.numberlookup.ui

import android.icu.util.ULocale
import java.util.Locale

/** Uses CLDR language defaults only when the language setting has no explicit region. */
fun defaultRegionForLocale(locale: Locale): String {
    return locale.country.ifBlank {
        ULocale.addLikelySubtags(ULocale.forLocale(locale)).country
    }.ifBlank { "US" }
}
