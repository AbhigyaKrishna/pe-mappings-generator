plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation("net.bytebuddy:byte-buddy:1.14.17")
    implementation("net.bytebuddy:byte-buddy-agent:1.14.17")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

tasks {
    shadowJar {
        manifest {
            attributes["Premain-Class"] = "me.abhigya.mappinggenerator.AgentMain"
            attributes["Can-Redefine-Classes"] = "true"
            attributes["Can-Retransform-Classes"] = "true"
        }

        archiveFileName = "agent.jar"
    }

    jar {
        enabled = false
    }

    assemble {
        dependsOn(shadowJar)
    }
}