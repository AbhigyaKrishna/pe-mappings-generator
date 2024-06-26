package me.abhigya.mappinggenerator.instrumentation

import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.dynamic.scaffold.InstrumentedType
import net.bytebuddy.implementation.Implementation
import net.bytebuddy.implementation.bytecode.ByteCodeAppender
import net.bytebuddy.implementation.bytecode.member.FieldAccess
import net.bytebuddy.implementation.bytecode.member.MethodReturn
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess
import net.bytebuddy.matcher.ElementMatchers

object RegistrySimpleInstrument : Implementation {

    override fun prepare(instrumentedType: InstrumentedType): InstrumentedType {
        return instrumentedType
    }

    override fun appender(implementationTarget: Implementation.Target): ByteCodeAppender {
        val thisType = implementationTarget.instrumentedType
        val field = thisType.declaredFields
            .filter(
                ElementMatchers.fieldType<FieldDescription.InDefinedShape>(Map::class.java)
                .and(ElementMatchers.isProtected())
                .and(ElementMatchers.isFinal()))
            .only
        return ByteCodeAppender.Simple(listOf(
            MethodVariableAccess.loadThis(),
            FieldAccess.forField(field).read(),
            MethodReturn.REFERENCE
        ))
    }

}