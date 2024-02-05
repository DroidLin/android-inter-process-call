plugins {
    `java-gradle-plugin`
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
    implementation(gradleApi())
    implementation(localGroovy())
    implementation(gradleKotlinDsl())
    implementation("com.android.tools.build:gradle:8.2.1")
    implementation("com.android.tools.build:gradle-api:8.2.1")
}

gradlePlugin {
    plugins {
        create("interProcessTransformPlugin") {
            id = "InterProcessTransform"
            implementationClass = "com.lza.android.inter.process.transform.plugin.InterProcessPlugin"
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("java") {
            groupId = "com.github.DroidLin"
            artifactId = "android-inter-process-transform-plugin"
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