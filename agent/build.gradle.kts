plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(8)
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "me.abhigya.mappinggenerator.AgentMain"
        }
    }
}