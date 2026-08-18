package de.robv.android.xposed;

import java.lang.reflect.Field;

public final class XposedHelpers {
    private XposedHelpers() {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        throw new UnsupportedOperationException();
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return new XC_MethodHook.Unhook();
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        throw new UnsupportedOperationException();
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        throw new UnsupportedOperationException();
    }

    public static Field findField(Class<?> clazz, String fieldName) {
        throw new UnsupportedOperationException();
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        throw new UnsupportedOperationException();
    }
}
