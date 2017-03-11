package com.antfortune.freeline

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Created by huangyong on 16/7/18.
 */
class FreelineClassVisitor extends ClassVisitor implements Opcodes {

    private static final String SUFFIX_ANDROIDANNOTATION = "_"

    public boolean isHack = true

    public String className = null

    public String filePath = null

    public String entry = null

    public boolean isJar = false

    public def foundAnnos = []

    public FreelineClassVisitor(String path, String entry, boolean isJar, int api, ClassWriter cw) {
        super(api, cw)
        this.filePath = path
        this.entry = entry
        this.isJar = isJar
    }

    @Override
    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name;
        if ("android/app/Application".equals(superName)) {
            isHack = false;
        }
//        if (name.endsWith(SUFFIX_ANDROIDANNOTATION)) {
//            println "find AndroidAnnotation class name: ${name}, freeline will remove the final tag"
//            super.visit(version, access & (~ACC_FINAL), name, signature, superName, interfaces)
//        } else {
//            super.visit(version, access, name, signature, superName, interfaces)
//        }
        super.visit(version, access & (~ACC_FINAL), name, signature, superName, interfaces)
    }

    @Override
    AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        collectAnno(desc)
        return super.visitAnnotation(desc, visible)
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

            @Override
            AnnotationVisitor visitAnnotation(String annoDesc, boolean visible) {
                collectAnno(annoDesc)
                return super.visitAnnotation(annoDesc, visible)
            }
        }
        return mv;
    }

    @Override
    FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        FieldVisitor fv = super.visitField(access, name, desc, signature, value)
        return new FieldVisitor(ASM4, fv) {
            @Override
            AnnotationVisitor visitAnnotation(String annoDesc, boolean visible) {
                collectAnno(annoDesc)
                return super.visitAnnotation(annoDesc, visible)
            }
        }
    }

    private void collectAnno(String desc) {
        if (desc != null) {
            FreelineAnnotationCollector.ANNOTATION_CLASSES.each { anno ->
                if (!foundAnnos.contains(anno) && desc.contains(anno)) {
                    foundAnnos.add(anno)
                    FreelineAnnotationCollector.addNewAnno(anno, filePath, className, entry, isJar)
                }
            }
        }
    }

}
