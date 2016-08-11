package com.antfortune.freeline

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * Created by huangyong on 16/8/1.
 */
class FreelineInjector {

    public static void inject(List<String> excludeClasses, File file, List<String> modules) {
        if (file.path.endsWith(".class")
                && !isExcluded(file.path, excludeClasses)) {
            realInject(file)
        } else if (file.path.endsWith("classes.jar")) {
            println "find jar: ${file.path}"
            if (file.absolutePath.contains("intermediates" + File.separator + "exploded-aar" + File.separator)
                    && !file.absolutePath.contains("com.antfortune.android.freeline")
                    && !file.absolutePath.contains("com.android.support")
                    && isProjectModuleJar(file.absolutePath, modules)) {
                println "inject jar: ${file.path}"
                realInject(file)
            }
        }
    }

    private static boolean isExcluded(String path, List<String> excludeClasses) {
        for (String exclude : excludeClasses) {
            if (!exclude.endsWith(".class")) {
                exclude = exclude + ".class"
            }
            if (path.endsWith(exclude)) {
                println "exclude class: ${path}"
                return true
            }
        }
        return false
    }

    private static boolean isProjectModuleJar(String path, List<String> modules) {
        for (String module : modules) {
            if (path.contains(module)) {
                return true
            }
        }
        return false
    }

    private static void realInject(File file) {
        try {
            def pending = new File(file.parent, file.name + ".pending")

            if (file.path.endsWith(".class")) {
                FileInputStream fis = new FileInputStream(file)
                FileOutputStream fos = new FileOutputStream(pending)
                println "inject: ${file.path}"
                byte[] bytes = hackClass(fis);
                fos.write(bytes)
                fis.close()
                fos.close()
            } else if (file.path.endsWith(".jar")) {
                def jar = new JarFile(file)
                Enumeration enumeration = jar.entries()
                JarOutputStream jos = new JarOutputStream(new FileOutputStream(pending))
                while (enumeration.hasMoreElements()) {
                    InputStream is
                    try {
                        JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                        String entryName = jarEntry.getName();
                        ZipEntry zipEntry = new ZipEntry(entryName)

                        is = jar.getInputStream(jarEntry)
                        jos.putNextEntry(zipEntry)

                        def bytes = hackClass(is)
                        jos.write(bytes)
                    } catch (Exception e) {

                    } finally {
                        if (is != null) {
                            is.close()
                        }
                        jos.closeEntry()
                    }
                }
                jos.close()
                jar.close()
            }

            if (file.exists()) {
                file.delete()
            }
            pending.renameTo(file)
        } catch (Exception e) {
            println "inject error: ${file.path}"
        }
    }

    private static byte[] hackClass(InputStream inputStream) {
        ClassReader cr = new ClassReader(inputStream)
        ClassWriter cw = new ClassWriter(cr, 0)
        ClassVisitor cv = new FreelineClassVisitor(Opcodes.ASM4, cw)
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

}
