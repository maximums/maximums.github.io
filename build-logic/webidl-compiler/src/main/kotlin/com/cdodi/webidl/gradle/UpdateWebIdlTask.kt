package com.cdodi.webidl.gradle

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

private const val WEBREF_CDN = "https://cdn.jsdelivr.net/npm/@webref/idl"
private const val HEADER_PREFIX = "// Source: @webref/idl@"

/**
 * Replaces each committed `.idl` file with the file of the same name from a pinned `@webref/idl` release.
 * No other task depends on it, so normal builds never touch the network; the result is committed like any change.
 */
@UntrackedTask(because = "Overwrites committed sources on request; must never be skipped or cached")
abstract class UpdateWebIdlTask : DefaultTask() {

    @get:Internal
    abstract val idlFiles: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val webrefVersion: Property<String>

    @TaskAction
    fun update() {
        val version = webrefVersion.orNull
            ?: throw GradleException("Pass the @webref/idl version to pin, e.g. ./gradlew $path -PwebrefVersion=3.84.0")

        for (file in idlFiles) {
            val url = "$WEBREF_CDN@$version/${file.name}"
            val previous = file.takeIf(File::exists)?.useLines { lines -> lines.firstOrNull()?.removePrefix(HEADER_PREFIX) }
            val body = download(url)

            file.parentFile.mkdirs()
            file.writeText("$HEADER_PREFIX$version (${file.name}) — updated by $path\n$body")
            logger.lifecycle("${file.name}: ${previous ?: "none"} -> $version (${body.length} chars). Review the diff and the golden tests, then commit.")
        }
    }

    private fun download(url: String): String {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw GradleException("HTTP ${connection.responseCode} when downloading $url")
            }
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }
}
