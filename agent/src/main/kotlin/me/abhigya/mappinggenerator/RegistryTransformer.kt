package me.abhigya.mappinggenerator

import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

class RegistryTransformer: ClassFileTransformer {

    companion object {
        val CLASS_REGEX = Regex("net\\.minecraft\\.server\\.v\\d_\\d+_R\\d\\.(\\w+)")
    }

    override fun transform(
        loader: ClassLoader,
        className: String,
        classBeingRedefined: Class<*>,
        protectionDomain: ProtectionDomain,
        classfileBuffer: ByteArray
    ): ByteArray {
        println("PreMainAgent transform Class: $className");
//        val classIdentifier = className.replace("/", ".")
//        println(classIdentifier)
//        if (targets.none { it.matches(classIdentifier) }) return classfileBuffer
//        println("Transforming $classIdentifier")
//
//        if (classIdentifier.endsWith("Blocks.class")) {
//            return classfileBuffer
//        }

        return classfileBuffer
    }
}