package me.abhigya.mappinggenerator.instrumentation

import me.abhigya.mappinggenerator.AbstractSerializer
import me.abhigya.mappinggenerator.KVStringTransformer
import me.abhigya.mappinggenerator.Transformer
import me.abhigya.mappinggenerator.writeToFile
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.implementation.MethodCall
import net.bytebuddy.implementation.SuperMethodCall
import net.bytebuddy.matcher.ElementMatchers

object LegacyRegistryInterceptor : Transformer {

    private val VER_REGEX = "net\\.minecraft\\.server\\.v1_\\d+_R\\d\\."

    fun versionRegex(clazz: String): Regex {
        return Regex(VER_REGEX + clazz)
    }

    private val registries: MutableList<Pair<String, String>> = mutableListOf()
    private var dataWatcher: DataWatcher? = null

    override fun configure(builder: AgentBuilder): AgentBuilder.Identified.Narrowable {
        DedicatedServerDelegate.addStringTransformer(KVStringTransformer("vector3f", "rotation"))

        return builder.type(ElementMatchers.nameStartsWith("net.minecraft"))
    }

    override fun install(builder: DynamicType.Builder<*>, type: TypeDescription): DynamicType.Builder<*> {
        return if (type.matches(versionRegex("DataWatcher"))) {
            builder.field(ElementMatchers.fieldType<FieldDescription>(Map::class.java)
                .and(ElementMatchers.isStatic())
                .and(ElementMatchers.isPrivate())
                .and(ElementMatchers.isFinal())
                .and { type.name.contains(Regex("\\.v1_8_R\\d\\.")) }) // only in 1.8
                .transform { type, target ->
                    dataWatcher = DataWatcher(type.name, target.name)
                    target
                }
        } else if (type.matches(versionRegex("(MinecraftServer|DedicatedServer)"))) {
            builder.method(ElementMatchers.named("init"))
                .intercept(SuperMethodCall.INSTANCE.andThen(MethodCall.run(DedicatedServerDelegate)))
        } else if (type.matches(versionRegex("RegistrySimple"))) {
            builder.defineMethod("getMap", Map::class.java, Visibility.PUBLIC)
                .intercept(RegistrySimpleInstrument)
        }
//        else if (type.matches(versionRegex("RegistryMaterials"))
//            && type.declaredMethods.filter(ElementMatchers.named("getId")).isEmpty()) {
//            builder
//                .defineMethod("getId", Int::class.javaPrimitiveType!!, Visibility.PUBLIC)
//                .withParameters(Any::class.java)
//                .intercept(RegistryMaterialsInstrument)
//        }
        else if (type.matches(versionRegex("RegistryID"))) {
            builder
                .defineMethod("getMap", Map::class.java, Visibility.PUBLIC)
                .intercept(RegistryIdInstrument)
        } else {
            builder.field(ElementMatchers.fieldType<FieldDescription> { it.matches(versionRegex("Registry\\w+?")) }
                .and(ElementMatchers.isStatic()))
                .transform { type, target ->
                    registries.add(type.name to target.name)
                    target
                }
        }
    }

    object DedicatedServerDelegate : AbstractSerializer("mappings"), Runnable {
        override fun run() {
            dataWatcher?.let {
                println("Serializing entity data type")
                writeToFile(
                    it.entries.asSequence()
                        .sortedBy { it.value }
                        .map { it.key.simpleName.lowercase() }
                        .toList()
                        .transform()
                        .also { println(it) },
                    "entity_data_types.json"
                )
            }

            println("Serializing registries")
            for ((type, target) in registries) {
                val field = Class.forName(type).getField(target).get(null)
                val typeName = field.javaClass.name
                println("Found $target in $type with type $typeName")
                val entries = if (typeName.endsWith("ID")) {
                    val registry = RegistryId(field)
                    registry.map.map {
                        it.value to it.key.toString().replace("minecraft:", "")
                    }.sortedBy { it.first }
                        .map { it.second }
                } else if (typeName.endsWith("Simple") || typeName.endsWith("Default")) {
                    val registry = RegistrySimple(field)
                    registry.map.map {
                        it.value.toString() to it.key.toString().replace("minecraft:", "")
                    }.sortedBy { it.first }
                        .map { it.second }
                } else {
                    val registry = RegistryMaterials(field)
                    registry.map.map {
                        registry.registryId.map[it.value] to it.key.toString().replace("minecraft:", "")
                    }.sortedBy { it.first }
                        .map { it.second }
                }

                writeToFile(entries, "${type.takeLastWhile { it != '.' }}_${typeName.takeLastWhile { it != '.' }}.json")
            }
        }
    }

}