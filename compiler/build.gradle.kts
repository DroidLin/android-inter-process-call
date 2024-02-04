import org.jetbrains.kotlin.fir.declarations.builder.buildTypeAlias

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.22-1.0.17")
    implementation(project(":annotation"))
}

publishing {
    publications {
        register<MavenPublication>("java") {
            groupId = "com.github.DroidLin"
            artifactId = "android-inter-process-call-compiler"
            version = "1.0.0"

            afterEvaluate {
                from(components["java"])
            }
        }
    }
    repositories {
        maven {
            name = "repositoryLocalRepo"
            url = uri("${rootProject.projectDir}/repo")
        }
    }
}