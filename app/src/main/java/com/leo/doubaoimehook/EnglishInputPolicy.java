package com.leo.doubaoimehook;

final class EnglishInputPolicy {
    private static final String KEYBOARD_CALLBACK = "keyboard_callback";

    private EnglishInputPolicy() {
    }

    static boolean shouldInterceptPreedit(boolean english, boolean password, String current) {
        return english && !password && PreeditDelta.isAsciiLetters(current);
    }

    static boolean shouldFilterKeyboardCallback(
            boolean english, boolean password, String source, String text) {
        return english
                && !password
                && KEYBOARD_CALLBACK.equals(source)
                && isAsciiWordWithOptionalTrailingSpaces(text);
    }

    static boolean shouldSuppressCandidateCallbacks(boolean english, boolean password) {
        return password || english;
    }

    static boolean hasAsciiLetterPrefixWithSuffix(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int index = 0;
        while (index < text.length() && isAsciiLetter(text.charAt(index))) {
            index++;
        }
        return index > 0 && index < text.length();
    }

    static String replayedPreeditSuffix(String textBeforeCursor, String current) {
        if (textBeforeCursor == null || current == null || current.isEmpty()) {
            return null;
        }
        int maxPrefixLength = Math.min(textBeforeCursor.length(), current.length());
        for (int prefixLength = maxPrefixLength; prefixLength > 0; prefixLength--) {
            if (textBeforeCursor.endsWith(current.substring(0, prefixLength))) {
                return current.substring(prefixLength);
            }
        }
        return null;
    }

    private static boolean isAsciiWordWithOptionalTrailingSpaces(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isAsciiLetter(ch)) {
                hasLetter = true;
            } else if (ch != ' ') {
                return false;
            }
        }
        return hasLetter;
    }

    private static boolean isAsciiLetter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }
}
