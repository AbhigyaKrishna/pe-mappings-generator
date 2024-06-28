package me.abhigya.mappinggenerator.instrumentation.modern

class RegistryMaterials(
    val internal: Any
) {
    val map: Map<Int, Any?> by lazy {
        fun tryGetMethod(clazz: Class<*>) = runCatching {
            clazz.getDeclaredMethod("getMap")
        }

        var clazz: Class<*>? = internal.javaClass
        while (clazz != null) {
            tryGetMethod(clazz)
                .onSuccess {
                    return@lazy it.invoke(internal) as Map<Int, Any?>
                }
            clazz = clazz.superclass
        }

        throw NoSuchMethodException("getMap in class ${internal.javaClass}")
    }

    val getKey: (Any) -> Any? by lazy {
        val fn = internal.javaClass
            .declaredMethods
            .first { it.returnType.name.endsWith("MinecraftKey") };

        { key -> fn.invoke(internal, key) }
    }
}