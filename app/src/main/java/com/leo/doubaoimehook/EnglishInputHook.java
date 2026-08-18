package com.leo.doubaoimehook;

import android.view.inputmethod.InputConnection;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class EnglishInputHook {
    private static final String KEYBOARD_JNI = "com.bytedance.android.doubaoime.KeyboardJni";
    private static final String IME_SERVICE = "com.bytedance.android.doubaoime.ImeService";
    private static final String TAG = "[doubao-ime-hook] ";

    private static String lastPreedit = "";
    private static String pendingNativeCommit;

    private EnglishInputHook() {
    }

    static void install(ClassLoader classLoader) {
        try {
            Class<?> keyboardJni = XposedHelpers.findClass(KEYBOARD_JNI, classLoader);
            hookPreedit(keyboardJni);
            hookCommits(keyboardJni);
            hookCandidateCallbacks(keyboardJni);
            hookLifecycle(keyboardJni, classLoader);
            XposedBridge.log(TAG + "installed");
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + "install failed: " + throwable);
        }
    }

    private static void hookPreedit(Class<?> keyboardJni) {
        XposedHelpers.findAndHookMethod(keyboardJni, "UpdatePreedit", String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isPasswordInput(keyboardJni)) {
                            String current = (String) param.args[0];
                            if (handlePasswordPreedit(keyboardJni, current)) {
                                param.setResult(null);
                            }
                            resetPreedit();
                            return;
                        }
                        if (!isEnglishKeyboard(keyboardJni, param.thisObject)) {
                            resetPreedit();
                            return;
                        }

                        String current = (String) param.args[0];
                        if (!EnglishInputPolicy.shouldInterceptPreedit(
                                true, false, current)) {
                            resetPreedit();
                            return;
                        }

                        if (commitPreeditDelta(keyboardJni, current)) {
                            param.setResult(null);
                        }
                    }
                });
    }

    private static void hookCommits(Class<?> keyboardJni) {
        XposedHelpers.findAndHookMethod(keyboardJni, "commitForSpace",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!isEnglishKeyboard(keyboardJni, param.thisObject)) {
                            return;
                        }
                        if (isPasswordInput(keyboardJni)) {
                            return;
                        }
                        pendingNativeCommit = " ";
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        finishPendingNativeCommit(keyboardJni, param);
                    }
                });

        XposedHelpers.findAndHookMethod(keyboardJni, "commitForSpaceDoubleClick",
                boolean.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!isEnglishKeyboard(keyboardJni, param.thisObject)) {
                            return;
                        }
                        if (isPasswordInput(keyboardJni)) {
                            return;
                        }
                        boolean punctuationMode = (Boolean) param.args[0];
                        pendingNativeCommit = punctuationMode ? ". " : " ";
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        finishPendingNativeCommit(keyboardJni, param);
                    }
                });

        XposedBridge.hookAllMethods(keyboardJni, "DoCommit", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String text = param.args[0] instanceof String ? (String) param.args[0] : null;
                if (isPasswordInput(keyboardJni)) {
                    String source = param.args.length > 2 && param.args[2] instanceof String
                            ? (String) param.args[2] : "";
                    if (EnglishInputPolicy.shouldSuppressPasswordCommit(true, source, text)) {
                        resetPreedit();
                        param.setResult(null);
                    }
                    return;
                }
                if (pendingNativeCommit != null) {
                    String pendingCommit = pendingNativeCommit;
                    pendingNativeCommit = null;
                    if (isAsciiWordWithOptionalTrailingSpaces(text)
                            && commitText(keyboardJni, pendingCommit)) {
                        resetPreedit();
                        param.setResult(null);
                    }
                    return;
                }

                String source = param.args.length > 2 && param.args[2] instanceof String
                        ? (String) param.args[2] : "";
                if (!EnglishInputPolicy.shouldFilterKeyboardCallback(
                        isEnglishKeyboard(keyboardJni, param.thisObject), false, source, text)) {
                    return;
                }

                if (!isAsciiWordWithOptionalTrailingSpaces(text)) {
                    return;
                }

                int trailingSpaces = countTrailingSpaces(text);
                boolean handled = trailingSpaces == 0
                        || commitText(keyboardJni, spaces(trailingSpaces));
                if (handled) {
                    resetPreedit();
                    param.setResult(null);
                }
            }
        });
    }

    private static void hookCandidateCallbacks(Class<?> keyboardJni) {
        XC_MethodHook callback = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                boolean password = isPasswordInput(keyboardJni);
                boolean english = isEnglishKeyboard(keyboardJni, param.thisObject);
                if (EnglishInputPolicy.shouldSuppressCandidateCallbacks(english, password)) {
                    param.setResult(null);
                }
            }
        };
        XposedBridge.hookAllMethods(keyboardJni, "notifyCandidateBarSnapshot", callback);
        XposedBridge.hookAllMethods(keyboardJni, "notifyMoreCandidateSnapshot", callback);
        XposedBridge.hookAllMethods(keyboardJni, "notifyCandidateBarPinyin", callback);
        XposedBridge.hookAllMethods(keyboardJni, "notifyCandidateBarPinyin9Symbols", callback);
        XposedBridge.hookAllMethods(keyboardJni, "notifyCandidateBarSymbolRecent", callback);
    }

    private static void hookLifecycle(Class<?> keyboardJni, ClassLoader classLoader) {
        XC_MethodHook resetCallback = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                resetPreedit();
            }
        };
        XposedBridge.hookAllMethods(keyboardJni, "onFinishInput", resetCallback);
        XposedBridge.hookAllMethods(keyboardJni, "onFinishInputView", resetCallback);

        Class<?> imeService = XposedHelpers.findClass(IME_SERVICE, classLoader);
        XposedBridge.hookAllMethods(imeService, "onStartInput", resetCallback);
        XposedBridge.hookAllMethods(imeService, "onStartInputView", resetCallback);
        XposedBridge.hookAllMethods(imeService, "onFinishInput", resetCallback);
        XposedBridge.hookAllMethods(imeService, "onFinishInputView", resetCallback);
    }

    private static boolean commitPreeditDelta(Class<?> keyboardJni, String current) {
        PreeditDelta.Delta delta = PreeditDelta.between(lastPreedit, current);
        if (delta.deleteBeforeCursor == 0 && delta.commitAfterCursor.isEmpty()) {
            lastPreedit = current;
            return true;
        }
        if (!applyEdit(keyboardJni, delta.deleteBeforeCursor, delta.commitAfterCursor)) {
            return false;
        }
        lastPreedit = current;
        return true;
    }

    private static boolean commitText(Class<?> keyboardJni, String text) {
        return applyEdit(keyboardJni, 0, text);
    }

    private static boolean handlePasswordPreedit(Class<?> keyboardJni, String current) {
        if (!EnglishInputPolicy.hasAsciiLetterPrefixWithSuffix(current)) {
            return false;
        }

        InputConnection inputConnection = getInputConnection(keyboardJni);
        if (inputConnection == null) {
            return false;
        }

        try {
            CharSequence beforeCursor = inputConnection.getTextBeforeCursor(current.length(), 0);
            String suffix = EnglishInputPolicy.replayedPreeditSuffix(
                    beforeCursor == null ? null : beforeCursor.toString(), current);
            if (suffix == null) {
                return false;
            }
            return suffix.isEmpty() || commitText(keyboardJni, suffix);
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + "password preedit handling failed: " + throwable);
            return false;
        }
    }

    private static void finishPendingNativeCommit(Class<?> keyboardJni, MethodHookParam param) {
        String pendingCommit = pendingNativeCommit;
        if (pendingCommit == null) {
            return;
        }
        pendingNativeCommit = null;
        if (commitText(keyboardJni, pendingCommit)) {
            resetPreedit();
            param.setResult(Boolean.TRUE);
        }
    }

    private static boolean applyEdit(Class<?> keyboardJni, int deleteBeforeCursor, String commitText) {
        InputConnection inputConnection = getInputConnection(keyboardJni);
        if (inputConnection == null) {
            return false;
        }

        try {
            inputConnection.beginBatchEdit();
            if (deleteBeforeCursor > 0
                    && !inputConnection.deleteSurroundingText(deleteBeforeCursor, 0)) {
                return false;
            }
            if (!commitText.isEmpty() && !inputConnection.commitText(commitText, 1)) {
                return false;
            }
            return true;
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + "InputConnection edit failed: " + throwable);
            return false;
        } finally {
            try {
                inputConnection.endBatchEdit();
            } catch (Throwable throwable) {
                XposedBridge.log(TAG + "endBatchEdit failed: " + throwable);
            }
        }
    }

    private static InputConnection getInputConnection(Class<?> keyboardJni) {
        try {
            Object imeService = XposedHelpers.getStaticObjectField(keyboardJni, "mImeService");
            if (imeService == null) {
                return null;
            }
            Object wrapper = XposedHelpers.callMethod(imeService, "r");
            return wrapper instanceof InputConnection ? (InputConnection) wrapper : null;
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + "InputConnection lookup failed: " + throwable);
            return null;
        }
    }

    private static boolean isEnglishKeyboard(Class<?> keyboardJni, Object thisObject) {
        try {
            Object keyboard = thisObject;
            if (keyboard == null) {
                keyboard = XposedHelpers.callStaticMethod(keyboardJni, "getKeyboardJni");
            }
            Object result = XposedHelpers.callMethod(keyboard, "IsEnglishKeyboard");
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isPasswordInput(Class<?> keyboardJni) {
        try {
            Object value = XposedHelpers.getStaticObjectField(
                    keyboardJni, "mCurrentEditboxIsPasswordType");
            return Boolean.TRUE.equals(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isAsciiWordWithOptionalTrailingSpaces(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                hasLetter = true;
            } else if (ch != ' ') {
                return false;
            }
        }
        return hasLetter;
    }

    private static int countTrailingSpaces(String text) {
        int count = 0;
        for (int i = text.length() - 1; i >= 0 && text.charAt(i) == ' '; i--) {
            count++;
        }
        return count;
    }

    private static String spaces(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private static void resetPreedit() {
        lastPreedit = "";
        pendingNativeCommit = null;
    }
}
