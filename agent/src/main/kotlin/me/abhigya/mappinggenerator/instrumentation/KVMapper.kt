package me.abhigya.mappinggenerator.instrumentation

import java.lang.reflect.ParameterizedType

fun interface KVMapper {

    fun map(key: Any?, value: Any?): Pair<Any?, Any?>

}

object DataWatcherRegistryMapper : KVMapper {

    override fun map(key: Any?, value: Any?): Pair<Any?, Any?> {
        if (value != null && value.javaClass.name.contains("DataWatcherRegistry")) {
            return key to (value.javaClass.genericInterfaces[0] as ParameterizedType).actualTypeArguments[0].typeName
                .takeLastWhile { it != '.' }
                .drop(1)
                .lowercase()
        }

        return key to value
    }

}

val KVMappers = listOf(
    DataWatcherRegistryMapper
)