@file:JvmName("AgentMain")

package me.abhigya.mappinggenerator

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import me.abhigya.mappinggenerator.instrumentation.legacy.LegacyRegistryInterceptor
import me.abhigya.mappinggenerator.instrumentation.modern.ModernRegistryInterceptor
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatchers
import java.io.File
import java.io.PrintStream
import java.lang.instrument.Instrumentation


val transformers = listOf(
    LegacyRegistryInterceptor,
    ModernRegistryInterceptor
)

fun premain(arguments: String?, instrumentation: Instrumentation) {
    println("Mapping Generator Agent Loaded")
    ByteBuddyAgent.install()

    println("Installing transformers...")
    AgentBuilder.Default()
        .with(AgentBuilder.Listener.StreamWriting(PrintStream(File("agent.log"))))
        .type(ElementMatchers.nameStartsWith("net.minecraft"))
        .transform { builder, type, loader, _, _ ->
            transformers
                .filter { it.shouldBind(type, loader) }
                .fold(builder) { acc, it ->
                    it.install(acc, type, loader)
                }
        }
        .installOn(instrumentation)
}

interface Interceptor {

    fun shouldBind(type: TypeDescription, loader: ClassLoader): Boolean

    fun install(builder: DynamicType.Builder<*>, type: TypeDescription, classLoader: ClassLoader): DynamicType.Builder<*>

}

@OptIn(ExperimentalSerializationApi::class)
val PrettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = true
    prettyPrintIndent = "  "
}