package me.abhigya.mappinggenerator.instrumentation.modern

import me.abhigya.mappinggenerator.AbstractSerializer
import me.abhigya.mappinggenerator.Interceptor
import me.abhigya.mappinggenerator.instrumentation.MapHelper
import me.abhigya.mappinggenerator.instrumentation.doesClassMatches
import me.abhigya.mappinggenerator.writeToFile
import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.implementation.MethodCall
import net.bytebuddy.implementation.SuperMethodCall
import net.bytebuddy.matcher.ElementMatchers
import java.io.IOException

object ModernRegistryInterceptor : Interceptor {

    private lateinit var registryOfRegistries: Pair<String, String>
    private lateinit var loader: ClassLoader
    private val registries: MutableList<Pair<String, String>> = mutableListOf()

    override fun shouldBind(type: TypeDescription, loader: ClassLoader): Boolean {
        return type.`package`?.name?.startsWith("net.minecraft.server") == true
                && type.`package`?.name?.startsWith("net.minecraft.server.v1_") != true
    }

    override fun install(builder: DynamicType.Builder<*>, type: TypeDescription, classLoader: ClassLoader): DynamicType.Builder<*> {
        return if (type.doesClassMatches("RegistryMaterials")) {
            builder
                .defineMethod("getMap", Map::class.java, Visibility.PUBLIC)
                .intercept(RegistryMaterialsInstrument)
        } else if (type.doesClassMatches("(MinecraftServer|DedicatedServer)")) {
            builder
                .method(ElementMatchers.isOverriddenFrom<MethodDescription> { it.doesClassMatches("MinecraftServer") }
                    .and(ElementMatchers.isPublic())
                    .and(ElementMatchers.returns(Boolean::class.javaPrimitiveType!!))
                    .and(ElementMatchers.canThrow(IOException::class.java))
                    .and(ElementMatchers.hasParameters { it.toList().isEmpty() }))
                .intercept(SuperMethodCall.INSTANCE.andThen(MethodCall.run(DedicatedServerDelegate)))
        } else if (type.doesClassMatches("IRegistry")) {
            builder
                .field(ElementMatchers.fieldType<FieldDescription> { it.doesClassMatches("(IRegistry|RegistryBlocks|RegistryMaterials)") }
                    .and(ElementMatchers.isStatic())
                    .and(ElementMatchers.isFinal())
                    .and(ElementMatchers.isPublic()))
                .transform { type, target ->
                    if (!::registryOfRegistries.isInitialized) {
                        registryOfRegistries = type.name to target.name // first entry
                        loader = classLoader
                    } else {
                        registries += type.name to target.name
                    }
                    target
                }
        } else {
            builder
        }
    }

    object DedicatedServerDelegate : AbstractSerializer("mappings"), Runnable, MapHelper {
        override fun run() {
            val registryOfRegistries = RegistryMaterials(loader.loadClass(registryOfRegistries.first).getDeclaredField(registryOfRegistries.second).apply {
                isAccessible = true
            }.get(null))

            println("Serializing registries")
            for ((type, target) in registries) {
                val field = loader.loadClass(type).getDeclaredField(target).apply {
                    isAccessible = true
                }.get(null)
                val name = registryOfRegistries.getKey(field)!!.toString()
                    .replace("minecraft:", "")
                    .replace('/', '_')
                val typeName = field.javaClass.name
                println("Found $target for $name with type $typeName")

                val registry = RegistryMaterials(field)
                val entries = registry.map.map {
                    it.key as Any? to it.value
                }.runOperation()

                writeToFile(entries, "${type.takeLastWhile { it != '.' }}_${name}_${typeName.takeLastWhile { it != '.' }}.json")
            }
        }
    }

}