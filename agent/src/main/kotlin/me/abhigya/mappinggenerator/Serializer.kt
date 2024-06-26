package me.abhigya.mappinggenerator

import kotlinx.serialization.encodeToString
import java.io.File

interface Serializer

abstract class AbstractSerializer(
    val defaultDir: String = ""
) : Serializer

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

inline fun <reified T> AbstractSerializer.writeToFile(obj: T, path: String) {
    if (defaultDir.isNotBlank()) {
        File(defaultDir).mkdirs()
    }

    val file = File(defaultDir, path)
    if (!file.exists()) {
        file.createNewFile()
    }

    file.writeText(serializeToString(obj))
}