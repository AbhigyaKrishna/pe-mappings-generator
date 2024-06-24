@file:JvmName("AgentMain")

package me.abhigya.mappinggenerator

import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import java.lang.instrument.Instrumentation


val transformers = listOf(
    LegacyRegistryInterceptor
)

fun premain(arguments: String?, instrumentation: Instrumentation) {
    println("Mapping Generator Agent Loaded")
    ByteBuddyAgent.install()

    transformers.forEach {
        println("Installing ${it.javaClass.simpleName} transformer...")
        AgentBuilder.Default()
            .run {
                it.configure(this)
            }
            .transform { builder, type, _, _, _ ->
                it.install(builder, type)
            }
            .installOn(instrumentation)
    }
}

interface Transformer {

    fun configure(builder: AgentBuilder): AgentBuilder.Identified.Narrowable

    fun install(builder: DynamicType.Builder<*>, type: TypeDescription): DynamicType.Builder<*>

}