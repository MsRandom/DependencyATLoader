plugins {
    java
    `maven-publish`
}

val build = System.getenv("BUILD_NUMBER") ?: "0"

version = "1.0.0${if (build == "0") "" else "-$build"}"
group = "net.msrandom.atload"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

repositories {
    maven(url = "https://libraries.minecraft.net/")
    maven(url = "https://maven.minecraftforge.net/")
    mavenCentral()
}

dependencies {
    implementation(group = "net.minecraft", name = "launchwrapper", version = "1.12")
    implementation(group = "commons-io", name = "commons-io", version = "2.5")
    implementation(group = "com.google.guava", name = "guava", version = "21.0")
    implementation(group = "net.minecraftforge", name = "legacydev", version = "0.2.3.+", classifier = "fatjar")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        val mavenUsername = System.getenv("MAVEN_USERNAME")
        val mavenPassword = System.getenv("MAVEN_PASSWORD")

        if (mavenUsername != null && mavenPassword != null) {
            maven(url = "https://maven.msrandom.net/repository/root/") {
                credentials {
                    username = mavenUsername
                    password = mavenPassword
                }
            }
        }
    }
}
