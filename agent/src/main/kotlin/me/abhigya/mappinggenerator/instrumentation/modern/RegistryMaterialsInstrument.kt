package me.abhigya.mappinggenerator.instrumentation.modern

import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.dynamic.scaffold.InstrumentedType
import net.bytebuddy.implementation.Implementation
import net.bytebuddy.implementation.bytecode.ByteCodeAppender
import net.bytebuddy.jar.asm.Label
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.matcher.ElementMatchers

object RegistryMaterialsInstrument : Implementation {

    override fun prepare(instrumentedType: InstrumentedType): InstrumentedType {
        return instrumentedType
    }

    override fun appender(implementationTarget: Implementation.Target): ByteCodeAppender {
        val thisType = implementationTarget.instrumentedType
        val field = thisType.declaredFields
            .filter(ElementMatchers.fieldType<FieldDescription.InDefinedShape> { it.name.endsWith("it.unimi.dsi.fastutil.objects.ObjectList") }
                .and(ElementMatchers.isPrivate())
                .and(ElementMatchers.isFinal()))
            .only

        val loop = Label()
        val end = Label()

        return ByteCodeAppender { visitor, _, _ ->
            visitor.visitCode()
            visitor.visitTypeInsn(Opcodes.NEW, "java/util/IdentityHashMap") // 1
            visitor.visitInsn(Opcodes.DUP) // 1
            visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/IdentityHashMap", "<init>", "()V", false) // 0
            visitor.visitVarInsn(Opcodes.ASTORE, 1) // -1
            visitor.visitLdcInsn(Opcodes.ICONST_0) // 1
            visitor.visitVarInsn(Opcodes.ISTORE, 2) // -1
            visitor.visitVarInsn(Opcodes.ALOAD, 0) // 1
            visitor.visitFieldInsn(Opcodes.GETFIELD, thisType.internalName, field.internalName, field.descriptor) // 0
            visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, field.descriptor.drop(1).dropLast(1), "size", "()I", true) // 0
            visitor.visitVarInsn(Opcodes.ISTORE, 3) // -1
            visitor.visitLabel(loop)
            visitor.visitFrame(Opcodes.F_APPEND, 3, arrayOf("java/util/IdentityHashMap", Opcodes.INTEGER, Opcodes.INTEGER), 0, null)
            visitor.visitVarInsn(Opcodes.ILOAD, 2) // 1
            visitor.visitVarInsn(Opcodes.ILOAD, 3) // 1
            visitor.visitJumpInsn(Opcodes.IF_ICMPGE, end) // -2
            visitor.visitVarInsn(Opcodes.ILOAD, 2) // 1
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false) // 0
            visitor.visitVarInsn(Opcodes.ASTORE, 4) // -1
            visitor.visitVarInsn(Opcodes.ALOAD, 1) // 1
            visitor.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Map") // 0
            visitor.visitVarInsn(Opcodes.ALOAD, 4) // 1
            visitor.visitVarInsn(Opcodes.ALOAD, 0) // 1
            visitor.visitVarInsn(Opcodes.ALOAD, 0) // 1
            visitor.visitFieldInsn(Opcodes.GETFIELD, thisType.internalName, field.internalName, field.descriptor) // 0
            visitor.visitVarInsn(Opcodes.ILOAD, 2) // 1
            visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, field.descriptor.drop(1).dropLast(1), "get", "(I)Ljava/lang/Object;", true) // 0
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, thisType.internalName, "getKey", "(Ljava/lang/Object;)Lnet/minecraft/resources/MinecraftKey;", false) // 0
            visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true) // 0
            visitor.visitInsn(Opcodes.POP) // -1
            visitor.visitIincInsn(2, 1) // 0
            visitor.visitJumpInsn(Opcodes.GOTO, loop) // 0
            visitor.visitLabel(end)
            visitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            visitor.visitVarInsn(Opcodes.ALOAD, 1) // 1
            visitor.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Map") // 0
            visitor.visitInsn(Opcodes.ARETURN) // -1
            visitor.visitEnd()

            ByteCodeAppender.Size(8, 5)
        }
    }

}