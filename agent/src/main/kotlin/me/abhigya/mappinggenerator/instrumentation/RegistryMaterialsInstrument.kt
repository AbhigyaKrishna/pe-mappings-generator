package me.abhigya.mappinggenerator.instrumentation

import me.abhigya.mappinggenerator.instrumentation.LegacyRegistryInterceptor.versionRegex
import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.scaffold.InstrumentedType
import net.bytebuddy.implementation.Implementation
import net.bytebuddy.implementation.bytecode.ByteCodeAppender
import net.bytebuddy.implementation.bytecode.member.FieldAccess
import net.bytebuddy.implementation.bytecode.member.MethodInvocation
import net.bytebuddy.implementation.bytecode.member.MethodReturn
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess
import net.bytebuddy.matcher.ElementMatchers

object RegistryMaterialsInstrument : Implementation {

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
            .filter(
                ElementMatchers.returns<MethodDescription.InGenericShape>(Int::class.javaPrimitiveType!!)
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

}