package com.antfortune.freeline

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Created by huangyong on 16/7/18.
 */
class FreelineClassVisitor extends ClassVisitor implements Opcodes {

    public boolean isHack = true;

    public FreelineClassVisitor(int api, ClassWriter cw) {
        super(api, cw)
    }

    @Override
    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if ("android/app/Application".equals(superName)) {
            isHack = false;
        }
        super.visit(version, access, name, signature, superName, interfaces)
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        mv = new MethodVisitor(ASM4, mv) {
            @Override
            void visitInsn(int opcode) {
                if (isHack) {
                    if ("<init>".equals(name) && opcode == RETURN) {
                        //org.micro.freeline.hackload.ClassVerifier
                        super.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
                        super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                        Label l0 = new Label();
                        super.visitJumpInsn(IFEQ, l0);
                        super.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                        super.visitLdcInsn(Type.getType("Lorg/micro/freeline/hackload/ClassVerifier;"));
                        super.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/Object;)V", false);
                        super.visitLabel(l0);
                    }
                }
                super.visitInsn(opcode);
            }

            @Override
            void visitMaxs(int i, int i1) {
                if ("<init>".equals(name)) {
                    if (isHack) {
                        super.visitMaxs(i + 2, i1)
                    } else {
                        super.visitMaxs(i, i1)
                    }
                    return
                }
                super.visitMaxs(i, i1)
            }
        }
        return mv;
    }

}
