package com.cdodi.features.about

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
}

class Role(val title: String, val employer: String, val years: String)

class ContactLink(val label: String, val url: String)
