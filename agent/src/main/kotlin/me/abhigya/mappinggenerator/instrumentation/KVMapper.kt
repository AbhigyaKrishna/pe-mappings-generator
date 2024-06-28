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
                .dropLast(1)
                .lowercase()
        }

        return key to value
    }

}

interface MapHelper {

    fun List<Pair<Any?, Any?>>.runOperation(): Map<String, String> {
        return run {
            KVMappers.fold(this) { acc, mapper -> acc.map { DataWatcherRegistryMapper.map(it.first, it.second) } }
        }
            .map { it.first.toString() to it.second.toString() }
            .sortedBy { it.first }
            .toMap()
    }

}

val KVMappers = listOf(
    DataWatcherRegistryMapper
)