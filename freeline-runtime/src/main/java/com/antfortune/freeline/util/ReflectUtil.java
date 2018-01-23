package com.antfortune.freeline.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public class ReflectUtil {

    public static Object invokeMethod(String className, String methodName, Class[] cls,
                                      Object[] args) throws Exception {
        Class clazz = Class.forName(className);
        return invokeMethod(clazz, null, methodName, cls, args);
    }

    public static Object invokeMethod(Object object, String methodName, Class[] cls,
                                      Object[] args) throws Exception {
        Class clazz = object.getClass();
        return invokeMethod(clazz, object, methodName, cls, args);
    }

    public static Object invokeMethod(Object object, String methodName) throws Exception {
        Class clazz = object.getClass();
        return invokeMethod(clazz, object, methodName, null, null);
    }

    public static Object invokeMethod(Class clazz, Object object, String methodName, Class[] cls,
                                      Object[] args) throws Exception {
        Method method;
        if (null == cls) {
            method = clazz.getDeclaredMethod(methodName);
        } else {
            method = clazz.getDeclaredMethod(methodName, cls);
        }

        method.setAccessible(true);

        if (null == args) {
            return method.invoke(object);
        } else {
            return method.invoke(object, args);
        }
    }

    public static Field fieldGetOrg(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static Field fieldGetOrg(Object object, Class<?> clazz, String name) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static void fieldSet(Object object, String fieldName, Object value)
            throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, value);
    }

    public static void fieldSet(Object object, Class<?> clazz, String fieldName,
                                Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, value);
    }

    public static Object fieldGet(Object object, String fieldName) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }


    public static Object fieldGet(Object object, Class<?> clazz, String fieldName)
            throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }

    public static Object getStaticFieldValue(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(fieldName);
    }

    public static Object getField(Object obj, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        return prepareField(obj.getClass(), fieldName).get(obj);
    }

    public static void setField(Object obj, String fieldName, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        prepareField(obj.getClass(), fieldName).set(obj, value);
    }

    private static Field prepareField(Class c, String fieldName)
            throws NoSuchFieldException {
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (Exception e) {
            } finally {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException();
    }

    public static Field findField(Object instance, String name) throws NoSuchFieldException {
        Class clazz = instance.getClass();

        while(clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                if(!field.isAccessible()) {
                    field.setAccessible(true);
                }

                return field;
            } catch (NoSuchFieldException var4) {
                clazz = clazz.getSuperclass();
            }
        }

        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    public static Method findMethod(Object instance, String name, Class... parameterTypes) throws NoSuchMethodException {
        Class clazz = instance.getClass();

        while(clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name, parameterTypes);
                if(!method.isAccessible()) {
                    method.setAccessible(true);
                }

                return method;
            } catch (NoSuchMethodException var5) {
                clazz = clazz.getSuperclass();
            }
        }

        throw new NoSuchMethodException("Method " + name + " with parameters " + Arrays.asList(parameterTypes) + " not found in " + instance.getClass());
    }

    public static void expandFieldArray(Object instance, String fieldName, Object[] extraElements) throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field jlrField = findField(instance, fieldName);
        Object[] original = (Object[])((Object[])jlrField.get(instance));
        Object[] combined = (Object[])((Object[]) Array.newInstance(original.getClass().getComponentType(), original.length + extraElements.length));
        System.arraycopy(extraElements, 0, combined, 0, extraElements.length);
        System.arraycopy(original, 0, combined, extraElements.length, original.length);
        jlrField.set(instance, combined);
    }
}
