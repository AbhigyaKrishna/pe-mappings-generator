package me.abhigya.mappinggenerator

import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.SuperCall
import net.bytebuddy.matcher.ElementMatchers

object LegacyRegistryInterceptor : Transformer {

    private val VER_REGEX = "net\\.minecraft\\.server\\.v1_\\d+_R\\d\\."

    @JvmStatic
    val registriesType: MutableList<String> = mutableListOf()
    lateinit var dataWatcher: String

    override fun configure(builder: AgentBuilder): AgentBuilder.Identified.Narrowable {
        return builder.type(ElementMatchers.any())
    }

    override fun install(builder: DynamicType.Builder<*>, type: TypeDescription): DynamicType.Builder<*> {
        if (type.name.matches(versionRegex("DataWatcher"))) {
            dataWatcher = type.name
        }

        return builder
            .field(ElementMatchers.named<FieldDescription>("REGISTRY")
                .and(ElementMatchers.fieldType { it.name.matches(versionRegex("Registry\\w+?")) }))
            .transform { type, target ->
                registriesType.add(target.name)
                target
            }
            .method(ElementMatchers.named<MethodDescription>("init")
                .and { type.name.matches(versionRegex("(MinecraftServer|DedicatedServer)")) })
            .intercept(MethodDelegation.to(DedicatedServerDelegate::class.java))
    }

    object DedicatedServerDelegate {
        @JvmStatic
        fun intercept(
            @SuperCall callable: Runnable
        ) {
            println("MinecraftServer#B called")

            registriesType.forEach {
                println("Found registry: $it")
            }
        }
    }

    private fun versionRegex(clazz: String): Regex {
        return Regex(VER_REGEX + clazz)
    }

}