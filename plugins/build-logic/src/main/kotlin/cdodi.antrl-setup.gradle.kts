import com.cdodi.buildlogic.DownloadWebGrammarTask
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named

plugins {
    id("antlr")
    id("idea")
}

idea {
    module {
        sourceDirs = sourceDirs + file("src/main/antlr")
        generatedSourceDirs = generatedSourceDirs + layout.buildDirectory.dir("generated-src/antlr/main").get().asFile
    }
}

val sourceSets = extensions.getByType<SourceSetContainer>()
val mainSourceSet = sourceSets.named<SourceSet>(SourceSet.MAIN_SOURCE_SET_NAME)
val grammarProvider = mainSourceSet.map { set -> set.antlr.srcDirs.first().resolve("WebIDL.g4") }

val downloadWebIdlGrammar = tasks.register<DownloadWebGrammarTask>("downloadWebIdlGrammar") {
    group = "webidl init"
    grammarUrl.set(providers.gradleProperty("webIdlGrammarUrl"))
    grammarFile.fileProvider(grammarProvider)
}

tasks.generateGrammarSource {
    dependsOn(downloadWebIdlGrammar)
    arguments = arguments + listOf("-visitor", "-long-messages")
}

tasks.configureEach {
    if (name == "compileKotlin") dependsOn(tasks.generateGrammarSource)
}

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.1")
    implementation("org.antlr:antlr4-runtime:4.13.1")
}
