package me.abhigya.mappinggenerator.instrumentation.legacy

import me.abhigya.mappinggenerator.AbstractSerializer
import me.abhigya.mappinggenerator.Interceptor
import me.abhigya.mappinggenerator.instrumentation.MapHelper
import me.abhigya.mappinggenerator.instrumentation.doesClassMatches
import me.abhigya.mappinggenerator.writeToFile
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.implementation.MethodCall
import net.bytebuddy.implementation.SuperMethodCall
import net.bytebuddy.matcher.ElementMatchers

object LegacyRegistryInterceptor : Interceptor {

    private val registries: MutableList<Pair<String, String>> = mutableListOf()
    private var dataWatcher: DataWatcher? = null

    override fun configure(builder: AgentBuilder): AgentBuilder.Identified.Narrowable {
        return builder.type(ElementMatchers.nameStartsWith("net.minecraft"))
    }

    override fun install(builder: DynamicType.Builder<*>, type: TypeDescription, classLoader: ClassLoader): DynamicType.Builder<*> {
        return if (type.doesClassMatches("DataWatcher") && type.name.contains(Regex("\\.v1_8_R\\d\\."))) { // only in 1.8
            builder.field(ElementMatchers.fieldType<FieldDescription>(Map::class.java)
                .and(ElementMatchers.isStatic())
                .and(ElementMatchers.isPrivate())
                .and(ElementMatchers.isFinal()))
                .transform { type, target ->
                    dataWatcher = DataWatcher(type.name, target.name)
                    target
                }
        } else if (type.doesClassMatches("(MinecraftServer|DedicatedServer)")) {
            builder
                .method(ElementMatchers.named("init"))
                .intercept(SuperMethodCall.INSTANCE.andThen(MethodCall.run(DedicatedServerDelegate)))
        } else if (type.doesClassMatches("RegistrySimple")) {
            builder
                .defineMethod("getMap", Map::class.java, Visibility.PUBLIC)
                .intercept(RegistrySimpleInstrument)
        } else if (type.doesClassMatches("RegistryMaterials") && type.superClass?.asErasure()?.typeName?.endsWith("RegistrySimple") != true) { // >= 1.13.1
            builder
                .defineMethod("getMap", Map::class.java, Visibility.PUBLIC)
                .intercept(RegistrySimpleInstrument)
        } else if (type.doesClassMatches("(RegistryID|RegistryBlockID)")) {
            builder
                .defineMethod("getMap", Map::class.java, Visibility.PUBLIC)
                .intercept(RegistryIdInstrument)
        } else {
            builder
                .field(ElementMatchers.fieldType<FieldDescription> { it.doesClassMatches("I?Registry\\w*") }
                .and(ElementMatchers.isStatic()))
                .transform { _, target ->
                    registries.add(type.name to target.name)
                    target
                }
        }
    }

    object DedicatedServerDelegate : AbstractSerializer("mappings"), Runnable, MapHelper {
        override fun run() {
            dataWatcher?.let {
                println("Serializing entity data type")
                writeToFile(
                    it.entries.asSequence()
                        .sortedBy { it.value }
                        .map { it.key.simpleName.lowercase() }
                        .toList()
                        .also { println(it) },
                    "DataWatcherRegistry.json"
                )
            }

            println("Serializing registries")
            for ((type, target) in registries) {
                val field = Class.forName(type).getDeclaredField(target).apply {
                    isAccessible = true
                }.get(null)
                val typeName = field.javaClass.name
                println("Found $target in $type with type $typeName")

                val entries = if (typeName.endsWith("ID")) {
                    val registry = RegistryId(field)
                    registry.map.map {
                        it.value as Any? to it.key
                    }.runOperation()
                } else if (typeName.endsWith("Simple") || typeName.endsWith("Default")) {
                    val registry = RegistrySimple(field)
                    registry.map.map {
                        it.value to it.key
                    }.runOperation()
                } else {
                    val registry = RegistryMaterials(field)
                    registry.map.map {
                        registry.registryId.map[it.value] as Any? to it.key
                    }.runOperation()
                }

                writeToFile(entries, "${type.takeLastWhile { it != '.' }}_${target}_${typeName.takeLastWhile { it != '.' }}.json")
            }
        }
    }

}