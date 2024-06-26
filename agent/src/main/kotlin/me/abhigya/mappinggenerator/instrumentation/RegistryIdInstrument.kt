package me.abhigya.mappinggenerator.instrumentation

import net.bytebuddy.dynamic.scaffold.InstrumentedType
import net.bytebuddy.implementation.Implementation
import net.bytebuddy.implementation.bytecode.ByteCodeAppender
import net.bytebuddy.implementation.bytecode.member.FieldAccess
import net.bytebuddy.implementation.bytecode.member.MethodReturn
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess
import net.bytebuddy.jar.asm.Label
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.matcher.ElementMatchers
import java.util.*

object RegistryIdInstrument : Implementation {

    override fun prepare(instrumentedType: InstrumentedType): InstrumentedType {
        return instrumentedType
    }

    override fun appender(implementationTarget: Implementation.Target): ByteCodeAppender {
        val thisType = implementationTarget.instrumentedType

        val fields = thisType.declaredFields
            .filter(ElementMatchers.fieldType(IdentityHashMap::class.java))

        val isMap = fields.isNotEmpty()
        val field = if (isMap) {
            fields.only
        } else {
            thisType.declaredFields
                .filter(ElementMatchers.fieldType(ElementMatchers.isArray()))[2]
        }

        val loop = Label()
        val end = Label()

        return if (isMap) {
            ByteCodeAppender.Simple(
                MethodVariableAccess.loadThis(),
                FieldAccess.forField(field).read(),
                MethodReturn.REFERENCE
            )
        } else {
            ByteCodeAppender { visitor, _, _ ->
                visitor.visitFrame(
                    Opcodes.F_FULL,
                    5,
                    arrayOf(
                        thisType.internalName,
                        "java/util/IdentityHashMap",
                        Opcodes.INTEGER,
                        Opcodes.INTEGER,
                        "java/lang/Integer"
                    ),
                    5,
                    arrayOf(
                        thisType.internalName,
                        "java/util/IdentityHashMap",
                        Opcodes.INTEGER,
                        Opcodes.INTEGER,
                        "java/lang/Integer"
                    )
                )

                visitor.visitCode()
                visitor.visitTypeInsn(Opcodes.NEW, "java/util/IdentityHashMap") // 1
                visitor.visitInsn(Opcodes.DUP) // 1
                visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/IdentityHashMap", "<init>", "()V", false) // 0
                visitor.visitVarInsn(Opcodes.ASTORE, 1) // -1
                visitor.visitLdcInsn(Opcodes.ICONST_0) // 1
                visitor.visitVarInsn(Opcodes.ISTORE, 2) // -1
                visitor.visitVarInsn(Opcodes.ALOAD, 0) // 1
                visitor.visitFieldInsn(Opcodes.GETFIELD, thisType.internalName, field.internalName, "[Ljava/lang/Object;") // 0
                visitor.visitInsn(Opcodes.ARRAYLENGTH) // 0
                visitor.visitVarInsn(Opcodes.ISTORE, 3) // -1
                visitor.visitLabel(loop)
                visitor.visitVarInsn(Opcodes.ILOAD, 2) // 1
                visitor.visitVarInsn(Opcodes.ILOAD, 3) // 1
                visitor.visitJumpInsn(Opcodes.IF_ICMPGE, end) // -2
                visitor.visitVarInsn(Opcodes.ILOAD, 2) // 1
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false) // 0
                visitor.visitVarInsn(Opcodes.ASTORE, 4) // -1
                visitor.visitVarInsn(Opcodes.ALOAD, 1) // 1
                visitor.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Map") // 0
                visitor.visitVarInsn(Opcodes.ALOAD, 0) // 1
                visitor.visitFieldInsn(Opcodes.GETFIELD, thisType.internalName, field.internalName, "[Ljava/lang/Object;") // 0
                visitor.visitVarInsn(Opcodes.ILOAD, 2) // 1
                visitor.visitInsn(Opcodes.AALOAD) // -1
                visitor.visitVarInsn(Opcodes.ALOAD, 4) // 1
                visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true) // 0
                visitor.visitInsn(Opcodes.POP) // -1
                visitor.visitIincInsn(2, 1) // 0
                visitor.visitJumpInsn(Opcodes.GOTO, loop) // 0
                visitor.visitLabel(end)
                visitor.visitVarInsn(Opcodes.ALOAD, 1) // 1
                visitor.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Map") // 0
                visitor.visitInsn(Opcodes.ARETURN) // -1
                visitor.visitEnd()

                ByteCodeAppender.Size(4, 5)
            }
        }
    }
}