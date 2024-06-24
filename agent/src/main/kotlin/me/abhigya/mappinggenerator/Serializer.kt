package me.abhigya.mappinggenerator

import kotlinx.serialization.encodeToString
import java.io.File

interface Serializer {

    fun addStringTransformer(transformer: StringTransformer)

}

fun interface StringTransformer {

    fun transform(str: String): String

}

data class KVStringTransformer(
    val from: String,
    val to: String
) : StringTransformer {

    override fun transform(str: String): String {
        return when (str) {
            from -> to
            else -> str
        }
    }

}

abstract class AbstractSerializer : Serializer {

    private val transformers: MutableList<StringTransformer> = mutableListOf()

    override fun addStringTransformer(transformer: StringTransformer) {
        transformers.add(transformer)
    }

    protected fun String.transform(): String {
        return transformers.fold(this) { acc, transformer ->
            transformer.transform(acc)
        }
    }

    protected fun List<String>.transform(): List<String> {
        return map { it.transform() }
    }

    protected fun Map<String, String>.transform(): Map<String, String> {
        return mapKeys { it.key.transform() }
            .mapValues { it.value.transform() }
    }

}

inline fun <reified T> Serializer.serializeToString(obj: T): String {
    return PrettyJson.encodeToString<T>(obj)
}

inline fun <reified T> Serializer.writeToFile(obj: T, path: String) {
    val file = File(path)
    if (!file.exists()) {
        file.createNewFile()
    }

    file.writeText(serializeToString(obj))
}