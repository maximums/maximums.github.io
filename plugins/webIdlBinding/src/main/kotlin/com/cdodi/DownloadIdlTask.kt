package com.cdodi

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@CacheableTask
abstract class DownloadIdlTask : DefaultTask() {

    @get:Input
    abstract val idlUrl: Property<String>

    @get:OutputFile
    abstract val idlFile: RegularFileProperty

    @TaskAction
    operator fun invoke() {
        val stringUrl = idlUrl.get()
        val file = idlFile.get().asFile

        logger.lifecycle("IDL download started: $stringUrl")
        file.parentFile.mkdirs()

        try {
            val connection = URI.create(stringUrl).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000

            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw GradleException("HTTP $status when downloading WebIDL from $stringUrl")
            }

            connection.inputStream.use { input ->
                Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            logger.lifecycle("IDL download completed: ${file.length()} bytes")
        } catch (e: GradleException) {
            throw e
        } catch (e: Exception) {
            throw GradleException("Failed to download WebIDL from $stringUrl: ${e.message}", e)
        }
    }
}
