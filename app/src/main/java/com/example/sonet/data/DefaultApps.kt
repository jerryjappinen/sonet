package com.example.sonet.data

/** Out-of-the-box organisation. Users can change all of it from the long-press menus. */
object DefaultApps {
    /** Always start in More, on top of the "untouched system app" heuristic. */
    val MORE_PACKAGES = setOf(
        "com.android.settings",                             // Settings
        "com.google.android.apps.safetyhub",                // Safety
        "com.google.android.apps.accessibility.voiceaccess", // Voice Access
        "com.android.stk",                                  // SIM toolkit
    )

    const val GOOGLE_GROUP_NAME = "Google"

    /** Put into a "Google" group the first time the launcher runs (if at least two are installed). */
    val GOOGLE_GROUP_PACKAGES = setOf(
        "com.google.android.apps.docs",            // Drive
        "com.android.vending",                     // Play Store
        "com.google.android.apps.youtube.music",   // YouTube Music
        "com.google.android.youtube",              // YouTube
        "com.google.android.gm",                   // Gmail
        "com.google.android.apps.maps",            // Maps
        "com.google.android.apps.photos",          // Photos
        "com.google.android.googlequicksearchbox", // Google
        "com.google.android.calendar",             // Calendar
        "com.google.android.keep",                 // Keep
        "com.google.android.apps.tachyon",         // Meet
        "com.google.android.apps.bard",            // Gemini
        "com.google.android.apps.translate",       // Translate
        "com.google.android.apps.docs.editors.docs",   // Docs
        "com.google.android.apps.docs.editors.sheets", // Sheets
        "com.google.android.apps.docs.editors.slides", // Slides
    )

    /** Wallet apps for the floating button, in order of preference. */
    val WALLET_PACKAGES = listOf(
        "com.google.android.apps.walletnfcrel", // Google Wallet
        "com.samsung.android.spay",             // Samsung Wallet
    )
}
