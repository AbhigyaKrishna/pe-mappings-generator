package me.abhigya.mappinggenerator.instrumentation

import net.bytebuddy.description.type.TypeDescription
import java.lang.reflect.Field
import java.util.IdentityHashMap

data class DataWatcher(
    val clazz: String,
    val field: String
) {
    val entries: Map<Class<*>, Int> by lazy {
        Class.forName(clazz)
            .getDeclaredField(this.field)
            .apply {
                isAccessible = true
            }
            .get(null) as Map<Class<*>, Int>
    }
}

class RegistryId(
    private val internal: Any
) {
    val map: IdentityHashMap<Any, Int> by lazy {
        internal.javaClass
            .getMethod("getMap")
            .apply {
                isAccessible = true
            }
            .invoke(internal) as IdentityHashMap<Any, Int>
    }
}

open class RegistrySimple(
    protected val internal: Any
) {
    val map: Map<Any, Any> by lazy {
        internal.javaClass
            .getMethod("getMap")
            .apply {
                isAccessible = true
            }
            .invoke(internal) as Map<Any, Any>
    }
}

class RegistryMaterials(
    internal: Any
) : RegistrySimple(internal) {
    val registryId: RegistryId by lazy {
        sequence<Field> {
            var clazz: Class<*>? = internal.javaClass
            while (clazz != null) {
                yieldAll(clazz.declaredFields.iterator())
                clazz = clazz.superclass
            }
        }
            .first {
                it.type.name.matches(LegacyRegistryInterceptor.versionRegex("RegistryID"))
            }
            .apply {
                isAccessible = true
            }
            .get(internal)
            .run {
                RegistryId(this)
            }
    }
}

fun TypeDescription.matches(regex: Regex): Boolean {
    return regex.matches(name)
}
