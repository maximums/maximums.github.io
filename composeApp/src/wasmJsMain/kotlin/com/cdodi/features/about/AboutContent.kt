package com.cdodi.features.about

/**
 * What the About page says. The page's layout lives in `pages/AboutPage.kt`; the words live here, so changing them
 * never means touching layout code. `index.html` has the same text in plain HTML, for screen readers and search
 * engines: keep the two in step.
 */
object AboutContent {
    const val NAME = "Dodi Cristian-Dumitru"

    // TODO(content): the bio, from its author.
    val paragraphs = listOf(
        "[Bio: waiting for the author's text.]",
    )

    val links = listOf(
        ContactLink("GitHub", "https://github.com/maximums"),
        // TODO(content): the email address and LinkedIn profile to publish.
    )
}

class ContactLink(val label: String, val url: String)
