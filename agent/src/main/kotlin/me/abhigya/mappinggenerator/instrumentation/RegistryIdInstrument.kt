package me.abhigya.mappinggenerator.instrumentation

import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.scaffold.InstrumentedType
import net.bytebuddy.implementation.Implementation
import net.bytebuddy.implementation.bytecode.ByteCodeAppender
import net.bytebuddy.implementation.bytecode.Duplication
import net.bytebuddy.implementation.bytecode.Removal
import net.bytebuddy.implementation.bytecode.StackManipulation
import net.bytebuddy.implementation.bytecode.StackManipulation.Size
import net.bytebuddy.implementation.bytecode.TypeCreation
import net.bytebuddy.implementation.bytecode.collection.ArrayAccess
import net.bytebuddy.implementation.bytecode.collection.ArrayLength
import net.bytebuddy.implementation.bytecode.constant.IntegerConstant
import net.bytebuddy.implementation.bytecode.member.FieldAccess
import net.bytebuddy.implementation.bytecode.member.MethodInvocation
import net.bytebuddy.implementation.bytecode.member.MethodReturn
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess
import net.bytebuddy.jar.asm.Label
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.matcher.ElementMatchers
import java.util.IdentityHashMap

object RegistryIdInstrument : Implementation {

    private val IMAP_TYPE = TypeDescription.ForLoadedType.of(IdentityHashMap::class.java)
    private val IMAP_CONSTRUCTOR = MethodDescription.ForLoadedConstructor(IdentityHashMap::class.java.getConstructor())
    private val IMAP_PUT = MethodDescription.ForLoadedMethod(IdentityHashMap::class.java.getDeclaredMethod("put", Any::class.java, Any::class.java))
    private val INT_VALUE_OF = MethodDescription.ForLoadedMethod(Int::class.javaObjectType.getDeclaredMethod("valueOf", Int::class.javaPrimitiveType!!))

    override fun prepare(instrumentedType: InstrumentedType): InstrumentedType {
        return instrumentedType
    }

    override fun appender(implementationTarget: Implementation.Target): ByteCodeAppender {
        val thisType = implementationTarget.instrumentedType

        val fields = thisType.declaredFields
            .filter(ElementMatchers.fieldType(IdentityHashMap::class.java))

        val isMap = fields.isNotEmpty()
        val field = FieldAccess.forField(if (isMap) {
            fields.only
        } else {
            thisType.declaredFields
                .filter(ElementMatchers.fieldType<FieldDescription.InDefinedShape>(ElementMatchers.isArray())
                    .and(ElementMatchers.not(ElementMatchers.fieldType(Int::class.javaPrimitiveType!!))))[1]
        })

        val loop = Label()
        val end = Label()

        return if (isMap) {
            ByteCodeAppender.Simple(
                MethodVariableAccess.loadThis(),
                field.read(),
                MethodReturn.REFERENCE
            )
        } else {
            ByteCodeAppender.Simple(
                TypeCreation.of(IMAP_TYPE),
                Duplication.of(IMAP_TYPE),
                MethodInvocation.invoke(IMAP_CONSTRUCTOR),
                MethodVariableAccess.REFERENCE.storeAt(1), // 1 - map
                IntegerConstant.ZERO,
                MethodVariableAccess.INTEGER.storeAt(2), // 2 - i
                MethodVariableAccess.loadThis(),
                field.read(),
                ArrayLength.INSTANCE,
                MethodVariableAccess.INTEGER.storeAt(3), // 3 - length
                StackManipulation.Simple { visitor, _ ->
                    visitor.visitLabel(loop)
                    Size.ZERO
                },
                MethodVariableAccess.INTEGER.loadFrom(2),
                MethodVariableAccess.INTEGER.loadFrom(3),
                StackManipulation.Simple { visitor, _ ->
                    visitor.visitJumpInsn(Opcodes.IF_ICMPGE, end)
                    Size.ZERO
                },
                MethodVariableAccess.INTEGER.loadFrom(2),
                MethodInvocation.invoke(INT_VALUE_OF),
                MethodVariableAccess.REFERENCE.storeAt(4), // 4 - index
                MethodVariableAccess.REFERENCE.loadFrom(1),
                StackManipulation.Simple { visitor, _ ->
                    visitor.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Map")
                    Size.ZERO
                },
                MethodVariableAccess.loadThis(),
                field.read(),
                MethodVariableAccess.INTEGER.loadFrom(2),
                ArrayAccess.REFERENCE.load(),
                MethodVariableAccess.REFERENCE.loadFrom(4),
                MethodInvocation.invoke(IMAP_PUT),
                Removal.SINGLE,
                StackManipulation.Simple { visitor, _ ->
                    visitor.visitIincInsn(2, 1)
                    visitor.visitJumpInsn(Opcodes.GOTO, loop)
                    visitor.visitLabel(end)
                    Size.ZERO
                }
            )
        }
    }
}