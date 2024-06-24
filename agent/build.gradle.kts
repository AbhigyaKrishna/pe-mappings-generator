plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation("org.javassist:javassist:3.30.0-GA")
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