package com.cdodi.features.about

import com.cdodi.design.theme.Credit

/**
 * What the About page says. The page's layout lives in `pages/AboutPage.kt`; the words live here, so changing them
 * never means touching layout code. `index.html` has the same bio in plain HTML, for screen readers and search
 * engines: keep the two in step.
 */
object AboutContent {
    const val NAME = "Dodi Cristian-Dumitru"

    const val ROLE = "Software engineer · Android · Gradle builds"

    val paragraphs = listOf(
        "Android engineer with 5+ years building production Android applications and libraries in Kotlin. I work mostly " +
            "on Jetpack Compose, Gradle and modularization, and on keeping large codebases maintainable through static " +
            "analysis and custom lint tooling.",
        "I have delivered work for GlobalPayments, Bloomberg and BMW across the payments and automotive domains, from " +
            "modernizing existing Android codebases and leading technology migrations to taking Android Automotive and " +
            "Android Auto projects from proof of concept to production-ready libraries.",
    )

    val work = listOf(
        Role("Android Engineer", "Appsfactory GmbH", "2024 – now"),
        Role("Android Developer", "Endava PLC", "2021 – 2024"),
        Role("Android Developer", "Fanatico Inc", "2021"),
    )

    val skills = listOf(
        "Kotlin", "Java", "Jetpack Compose", "Compose Multiplatform", "Kotlin Multiplatform", "Android Automotive",
        "Android Auto", "Coroutines & Flow", "MVI / MVVM", "Gradle", "Dagger Hilt", "Koin", "Retrofit", "Ktor", "Room",
        "Detekt", "Android Lint", "Robolectric", "AGSL", "GitHub Actions",
    )

    val links = listOf(
        ContactLink("GitHub", "https://github.com/maximums"),
        ContactLink("LinkedIn", "https://www.linkedin.com/in/cristian-dodi-4249831b5/"),
        ContactLink("cdodi907@gmail.com", "mailto:cdodi907@gmail.com"),
    )

    /** The maple on Home (`files/models/maple-bonsai.glb`, prepared by `tools/models/prepare-maple.sh`). */
    val mapleCredit = Credit(
        "Maple Bonsai Tree",
        "NPHoa_Kagoby",
        "https://sketchfab.com/3d-models/maple-bonsai-tree-1710f5553283441ea2b041e4738314ec",
        source = "Sketchfab",
        licence = "CC BY 4.0",
        licenceUrl = "https://creativecommons.org/licenses/by/4.0/",
    )
}

class Role(val title: String, val employer: String, val years: String)

class ContactLink(val label: String, val url: String)
