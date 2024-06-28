package me.abhigya.mappinggenerator.instrumentation

import net.bytebuddy.description.type.TypeDescription

fun TypeDescription.matches(regex: Regex): Boolean {
    return regex.matches(name)
}

fun TypeDescription.doesClassMatches(regex: String): Boolean {
    return Regex(regex).matches(name.takeLastWhile { it != '.' })
}