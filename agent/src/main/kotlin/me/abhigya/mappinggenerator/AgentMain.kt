@file:JvmName("AgentMain")

package me.abhigya.mappinggenerator

import java.lang.instrument.Instrumentation

fun premain(arguments: String?, instrumentation: Instrumentation) {
    println("Hello from AgentX 'premain'!")
}

fun main(args: Array<String>) {
    println("Hello from AgentX 'main'!")
}