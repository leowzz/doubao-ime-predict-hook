package com.leo.doubaoimehook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.bytedance.android.doubaoime";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[doubao-ime-hook] loading target process=" + lpparam.processName);
        EnglishInputHook.install(lpparam.classLoader);
    }
}
