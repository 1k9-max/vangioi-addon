plugins {
    id("fabric-loom") version "1.14.10"
    id("maven-publish")
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

base {
    archivesName = properties["archives_base_name"] as String
    group = properties["maven_group"] as String

    val buildNum = project.findProperty("build_number")?.toString() ?: "1"
    version = "v$buildNum"
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "Terraformers"
        url = uri("https://maven.terraformersmc.com")
    }
    maven {
        name = "ViaVersion"
        url = uri("https://repo.viaversion.com")
    }
    mavenCentral()

    exclusiveContent {
        forRepository {
            maven {
                name = "modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

val modInclude: Configuration by configurations.creating
val library: Configuration by configurations.creating
val modId = providers.gradleProperty("mod_id")

sourceSets.main {
    resources.exclude("assets/meteor-client/**")
}

configurations {
    modImplementation.configure {
        extendsFrom(modInclude)
    }
    include.configure {
        extendsFrom(modInclude)
    }

    implementation.configure {
        extendsFrom(library)
    }
    shadow.configure {
        extendsFrom(library)
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${properties["minecraft_version"] as String}")
    mappings("net.fabricmc:yarn:${properties["yarn_mappings"] as String}:v2")
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"] as String}")
    modInclude(fabricApi.module("fabric-resource-loader-v0", properties["fapi_version"] as String))

    modCompileOnly(fabricApi.module("fabric-renderer-indigo", properties["fapi_version"] as String))
    modCompileOnly("maven.modrinth:sodium:${properties["sodium_version"] as String}") { isTransitive = false }
    modCompileOnly("maven.modrinth:lithium:${properties["lithium_version"] as String}") { isTransitive = false }
    modCompileOnly("maven.modrinth:iris:${properties["iris_version"] as String}") { isTransitive = false }
    modCompileOnly("com.viaversion:viafabricplus:${properties["viafabricplus_version"] as String}") { isTransitive = false }
    modCompileOnly("com.viaversion:viafabricplus-api:${properties["viafabricplus_version"] as String}") { isTransitive = false }

    modCompileOnly("meteordevelopment:baritone:${properties["baritone_version"] as String}-SNAPSHOT")
    modCompileOnly("com.terraformersmc:modmenu:${properties["modmenu_version"] as String}")

    modCompileOnly(files("libs/litematica-fabric-1_21_4-0_21_7.jar"))
    modCompileOnly(files("libs/malilib-fabric-1_21_4-0_23_5.jar"))

    library("meteordevelopment:orbit:${properties["orbit_version"] as String}")
    // Đã sửa lại đúng biến starscript_version ở đây:
    library("meteordevelopment:starscript:${properties["starscript_version"] as String}")
    library("org.reflections:reflections:${properties["reflections_version"] as String}")
    library("io.netty:netty-handler-proxy:${properties["netty_version"] as String}") { isTransitive = false }
    library("io.netty:netty-codec-socks:${properties["netty_version"] as String}") { isTransitive = false }
    library("de.florianmichael:WaybackAuthLib:${properties["waybackauthlib_version"] as String}")

    shadow(project(":launch"))
}

loom {
    accessWidenerPath = file("src/main/resources/meteor-client.accesswidener")
    mixin {
        defaultRefmapName.set("${modId.get()}.refmap.json")
    }
}

afterEvaluate {
    tasks.migrateMappings.configure {
        outputDir.set(project.file("src/main/java"))
    }
}

tasks {
    processResources {
        val configuredModId = modId.get()
        val buildNumber = project.findProperty("build_number")?.toString() ?: "1"
        val commit = project.findProperty("commit")?.toString() ?: ""

        val propertyMap = mapOf(
            "version" to project.version,
            "mod_id" to configuredModId,
            "build_number" to buildNumber,
            "commit" to commit,
            "minecraft_version" to project.property("minecraft_version"),
            "loader_version" to project.property("loader_version")
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
        filesMatching("van-gioi-fork.properties") {
            expand(propertyMap)
        }

        from("src/main/resources/assets/meteor-client") {
            into("assets/$configuredModId")
        }
    }

    jar {
        val licenseSuffix = project.base.archivesName.get()
        from("LICENSE") {
            rename { "${it}_${licenseSuffix}" }
        }

        manifest {
            attributes["Main-Class"] = "meteordevelopment.meteorclient.Main"
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.release = 21
    }

    shadowJar {
        configurations = listOf(project.configurations.shadow.get())
        archiveClassifier.set("all-dev") // Đặt hậu tố riêng cho bản shadow tạm thời để tránh xung đột

        val licenseSuffix = project.base.archivesName.get()
        from("LICENSE") {
            rename { "${it}_${licenseSuffix}" }
        }

        dependencies {
            exclude {
                it.moduleGroup == "org.slf4j"
            }
        }
    }

    remapJar {
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)
        archiveClassifier.set("") // Đưa file cuối cùng về dạng sạch sẽ không có hậu tố thừa
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "meteor-client"
            version = project.version.toString()
        }
    }
}
