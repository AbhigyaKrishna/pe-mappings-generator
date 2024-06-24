@file:JvmName("AgentMain")

package me.abhigya.mappinggenerator

import java.lang.instrument.Instrumentation

fun premain(arguments: String?, instrumentation: Instrumentation) {
    println("Mapping Generator Agent Loaded")
    instrumentation.addTransformer(RegistryTransformer(), true)

//    for (clazz in instrumentation.allLoadedClasses) {
//        try {
//            instrumentation.retransformClasses(clazz)
//        } catch (e: Exception) {
//            println("Cannot transform " + clazz.name)
//        }
//    }
}