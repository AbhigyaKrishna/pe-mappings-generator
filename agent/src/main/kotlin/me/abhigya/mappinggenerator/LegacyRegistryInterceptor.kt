package me.abhigya.mappinggenerator

import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.dynamic.scaffold.InstrumentedType
import net.bytebuddy.implementation.Implementation
import net.bytebuddy.implementation.MethodCall
import net.bytebuddy.implementation.SuperMethodCall
import net.bytebuddy.implementation.bytecode.ByteCodeAppender
import net.bytebuddy.implementation.bytecode.member.FieldAccess
import net.bytebuddy.implementation.bytecode.member.MethodInvocation
import net.bytebuddy.implementation.bytecode.member.MethodReturn
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess
import net.bytebuddy.matcher.ElementMatchers

object LegacyRegistryInterceptor : Transformer {

    private val VER_REGEX = "net\\.minecraft\\.server\\.v1_\\d+_R\\d\\."

    private val registriesType: MutableList<Registry> = mutableListOf()
    private lateinit var dataWatcher: DataWatcher

    override fun configure(builder: AgentBuilder): AgentBuilder.Identified.Narrowable {
        DedicatedServerDelegate.addStringTransformer(KVStringTransformer("vector3f", "rotation"))

        return builder.type(ElementMatchers.nameStartsWith("net.minecraft"))
    }

    override fun install(builder: DynamicType.Builder<*>, type: TypeDescription): DynamicType.Builder<*> {
        return if (type.matches(versionRegex("DataWatcher"))) {
            builder.field(ElementMatchers.fieldType<FieldDescription>(Map::class.java)
                .and(ElementMatchers.isStatic())
                .and(ElementMatchers.isPrivate()))
                .transform { type, target ->
                    dataWatcher = DataWatcher(type.name, target.name)
                    target
                }
        } else if (type.matches(versionRegex("(MinecraftServer|DedicatedServer)"))) {
            builder.method(ElementMatchers.named("init"))
                .intercept(SuperMethodCall.INSTANCE.andThen(MethodCall.run(DedicatedServerDelegate)))
        } else if (type.matches(versionRegex("RegistrySimple"))) {
            builder.defineMethod("getMap", Map::class.java, Visibility.PUBLIC)
                .intercept(object: Implementation {
                    override fun prepare(instrumentedType: InstrumentedType): InstrumentedType {
                        return instrumentedType
                    }

                    override fun appender(implementationTarget: Implementation.Target): ByteCodeAppender {
                        val thisType = implementationTarget.instrumentedType
                        val field = thisType.declaredFields
                            .filter(ElementMatchers.fieldType<FieldDescription.InDefinedShape>(Map::class.java)
                                .and(ElementMatchers.isProtected())
                                .and(ElementMatchers.isFinal()))
                            .only
                        return ByteCodeAppender.Simple(listOf(
                            MethodVariableAccess.loadThis(),
                            FieldAccess.forField(field).read(),
                            MethodReturn.REFERENCE
                        ))
                    }
                })
        } else if (type.matches(versionRegex("Registry\\w+?")) && !type.declaredFields
            .filter(ElementMatchers.fieldType { it.matches(versionRegex("RegistryID")) })
                .isEmpty()) {
            builder
                .defineMethod("getId", Int::class.javaPrimitiveType!!, Visibility.PUBLIC)
                .withParameters(Any::class.java)
                .intercept(object : Implementation {
                    override fun prepare(instrumentedType: InstrumentedType): InstrumentedType {
                        return instrumentedType
                    }

                    override fun appender(implementationTarget: Implementation.Target): ByteCodeAppender {
                        val thisType = implementationTarget.instrumentedType
                        val field = thisType.declaredFields
                            .filter(ElementMatchers.fieldType<FieldDescription.InDefinedShape> { it.matches(versionRegex("RegistryID")) }
                                .and(ElementMatchers.isProtected())
                                .and(ElementMatchers.isFinal()))
                            .only

                        val method = field.type.declaredMethods
                            .filter(ElementMatchers.returns<MethodDescription.InGenericShape>(Int::class.javaPrimitiveType!!)
                                .and(ElementMatchers.takesArguments(Any::class.java))
                                .and(ElementMatchers.isPublic()))
                            .only
                        return ByteCodeAppender.Simple(listOf(
                            MethodVariableAccess.loadThis(),
                            FieldAccess.forField(field).read(),
                            MethodVariableAccess.REFERENCE.loadFrom(1),
                            MethodInvocation.invoke(method),
                            MethodReturn.INTEGER
                        ))
                    }
                })
        } else {
            builder.field(ElementMatchers.fieldType { it.matches(versionRegex("I?Registry\\w+?"))
                    && !it.matches(versionRegex("(RegistryID|RegistryDefault)")) })
                .transform { type, target ->
                    registriesType.add(Registry(type.name, target.name))
                    target
                }
        }
    }

    object DedicatedServerDelegate : AbstractSerializer(), Runnable {
        override fun run() {
            println("Serializing entity data type")
            writeToFile(
                dataWatcher.entries.asSequence()
                    .sortedBy { it.value }
                    .map { it.key.simpleName.lowercase() }
                    .toList()
                    .transform()
                    .also { println(it) },
                "entity_data_types.json"
            )

            println("Serializing registries")
            for (registry in registriesType) {
                println("Found ${registry.field} in ${registry.clazz}")
                val entries = registry.map.map { (k, v) ->
                    registry.getId(v) to k.toString().replace("minecraft:", "")
                }.associate { it }
                    .toSortedMap()
                    .values
                    .toList()
                    .also { println(it) }

                writeToFile(entries, "${registry.clazz.takeLastWhile { it != '.' }}.json")
            }
        }
    }

    data class DataWatcher(
        val clazz: String,
        val field: String
    ) {
        val entries: Map<Class<*>, Int> get() = Class.forName(clazz)
            .getDeclaredField(this.field)
            .apply {
                isAccessible = true
            }
            .get(null) as Map<Class<*>, Int>
    }

    class Registry(
        val clazz: String,
        val field: String
    ) {
        companion object {
            lateinit var mapField: String
        }

        val registry: Any by lazy {
            Class.forName(clazz)
                .getDeclaredField(field)
                .apply {
                    isAccessible = true
                }
                .get(null)
        }

        val map: Map<Any, Any> by lazy {
            registry.javaClass
                .getMethod("getMap")
                .apply {
                    isAccessible = true
                }
                .invoke(registry) as Map<Any, Any>
        }

        val getId: (Any) -> Int by lazy {
            val method = registry.javaClass.getMethod("getId", Any::class.java)
            method.isAccessible = true
            { any -> method.invoke(registry, any) as Int }
        }
    }

    private fun versionRegex(clazz: String): Regex {
        return Regex(VER_REGEX + clazz)
    }

    private fun TypeDescription.matches(regex: Regex): Boolean {
        return regex.matches(name)
    }

}