import buildsrc.DependencyInfo
import buildsrc.ProjectInfo
import com.jfrog.bintray.gradle.BintrayExtension
import java.io.ByteArrayOutputStream
import org.gradle.api.internal.HasConvention
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
  id("com.gradle.build-scan") version "2.0.2"
  id("com.github.ben-manes.versions") version "0.20.0"
  id("org.jetbrains.dokka") version "0.9.17" apply false
  kotlin("jvm") version "1.2.71" apply false
  id("com.jfrog.bintray") version "1.8.4" apply false
}

description = "Assertion library extensions for testing with Gradle"

buildScan {
  fun env(key: String): String? = System.getenv(key)

  setTermsOfServiceAgree("yes")
  setTermsOfServiceUrl("https://gradle.com/terms-of-service")

  // Env variables from https://circleci.com/docs/2.0/env-vars/
  if (env("CI") != null) {
    logger.lifecycle("Running in CI environment, setting build scan attributes.")
    tag("CI")
    env("CIRCLE_BRANCH")?.let { tag(it) }
    env("CIRCLE_BUILD_NUM")?.let { value("Circle CI Build Number", it) }
    env("CIRCLE_BUILD_URL")?.let { link("Build URL", it) }
    env("CIRCLE_SHA1")?.let { value("Revision", it) }
//    Issue with Circle CI/Gradle with caret (^) in URLs
//    see: https://discuss.gradle.org/t/build-scan-plugin-1-10-3-issue-when-using-a-url-with-a-caret/24965
//    see: https://discuss.circleci.com/t/circle-compare-url-does-not-url-escape-caret/18464
//    env("CIRCLE_COMPARE_URL")?.let { link("Diff", it) }
    env("CIRCLE_REPOSITORY_URL")?.let { value("Repository", it) }
    env("CIRCLE_PR_NUMBER")?.let { value("Pull Request Number", it) }
    link("Repository", ProjectInfo.projectUrl)
  }
}

val gitCommitSha: String by lazy {
  ByteArrayOutputStream().use {
    rootProject.exec {
      commandLine("git", "rev-parse", "HEAD")
      standardOutput = it
    }
    it.toString(Charsets.UTF_8.name()).trim()
  }
}

tasks {
  wrapper {
    gradleVersion = "5.0"
  }

  val gitDirtyCheck by creating {
    doFirst {
      val output = ByteArrayOutputStream().use {
        exec {
          commandLine("git", "status", "--porcelain")
          standardOutput = it
        }
        it.toString(Charsets.UTF_8.name()).trim()
      }
      if (output.isNotBlank()) {
        throw GradleException("Workspace is dirty:\n$output")
      }
    }
  }

  val docVersionChecks by creating {
    group = PublishingPlugin.PUBLISH_TASK_GROUP
    description = "Checks if the repository documentation is up-to-date for the version $version"
    val changeLog = file("CHANGELOG.adoc")
    inputs.file(changeLog)
    // Output is just used for up-to-date checking
    outputs.dir(file("$buildDir/repositoryDocumentation"))
    doFirst {
      changeLog.bufferedReader().use { it.readLines() }.let { lines ->
        val changelogLineRegex = Regex("^== ${version.toString().replace(".", "\\.")} \\(\\d{4}\\/\\d{2}\\/\\d{2}\\)\$")
        val changelogSectionMatch = lines.any { line -> changelogLineRegex.matches(line) }
        if (!changelogSectionMatch) {
          throw GradleException("$changeLog does not contain section for $version")
        }
      }
    }
  }

  val gitTag by creating(Exec::class) {
    description = "Tags the local repository with version ${project.version}"
    group = PublishingPlugin.PUBLISH_TASK_GROUP
    commandLine("git", "tag", "-a", project.version, "-m", "Gradle created tag for ${project.version}")
    mustRunAfter(docVersionChecks)
  }
//
//  val bintrayUpload by getting {
//    dependsOn(gitDirtyCheck)
//    mustRunAfter(gitTag, docVersionChecks)
//  }

  val pushGitTag by creating(Exec::class) {
    description = "Pushes Git tag ${project.version} to origin"
    group = PublishingPlugin.PUBLISH_TASK_GROUP
    mustRunAfter(gitTag, docVersionChecks)
    commandLine("git", "push", "origin", "refs/tags/${project.version}")
  }
//
//  "release" {
//    group = PublishingPlugin.PUBLISH_TASK_GROUP
//    description = "Publishes the library and pushes up a Git tag for the current commit"
//    dependsOn(docVersionChecks, bintrayUpload, pushGitTag, gitTag, gitDirtyCheck, "build")
//  }
}

allprojects {
  group = "com.mkobit.gradle.test"
  version = "0.2.0"
}

subprojects {
  pluginManager.withPlugin("java-library") {
//    withConvention(JavaPluginConvention::class) {
//      sourceCompatibility = JavaVersion.VERSION_1_8
//      targetCompatibility = JavaVersion.VERSION_1_8
//    }

    dependencies {
      "api"(gradleApi())
      "api"(gradleTestKit())

      "testImplementation"(kotlin("stdlib-jdk8"))
      "testImplementation"(kotlin("reflect"))
      "testImplementation"(DependencyInfo.assertJCore)
      "testImplementation"(DependencyInfo.assertk)
      "testImplementation"(DependencyInfo.mockitoCore)
      "testImplementation"(DependencyInfo.mockitoKotlin)
      DependencyInfo.junitTestImplementationArtifacts.forEach {
        "testImplementation"(it)
      }
      DependencyInfo.junitTestRuntimeOnlyArtifacts.forEach {
        "testRuntimeOnly"(it)
      }
    }
  }

  repositories {
    jcenter()
    mavenCentral()
  }

  tasks {
    withType<Jar> {
      from(rootProject.projectDir) {
        include("LICENSE.txt")
        into("META-INF")
      }
      manifest {
        attributes(mapOf(
          "Build-Revision" to gitCommitSha,
          "Implementation-Version" to project.version
          // TODO: include Gradle version?
        ))
      }
    }

    withType<Test>().configureEach {
      useJUnitPlatform()
      systemProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")
    }

    withType<Javadoc>().configureEach {
      options {
        header = project.name
        encoding = "UTF-8"
      }
    }

    withType<KotlinCompile>().configureEach {
      kotlinOptions.jvmTarget = "1.8"
    }
  }
}
