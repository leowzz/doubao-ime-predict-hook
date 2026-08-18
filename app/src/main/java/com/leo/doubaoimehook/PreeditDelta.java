package com.leo.doubaoimehook;

final class PreeditDelta {
    private PreeditDelta() {
    }

    static Delta between(String previous, String current) {
        String oldValue = previous == null ? "" : previous;
        String newValue = current == null ? "" : current;
        int commonLength = 0;
        int maxCommonLength = Math.min(oldValue.length(), newValue.length());
        while (commonLength < maxCommonLength
                && oldValue.charAt(commonLength) == newValue.charAt(commonLength)) {
            commonLength++;
        }
        return new Delta(
                oldValue.length() - commonLength,
                newValue.substring(commonLength));
    }

    static boolean isAsciiLetters(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) {
                return false;
            }
        }
        return true;
    }

    static final class Delta {
        final int deleteBeforeCursor;
        final String commitAfterCursor;

        Delta(int deleteBeforeCursor, String commitAfterCursor) {
            this.deleteBeforeCursor = deleteBeforeCursor;
            this.commitAfterCursor = commitAfterCursor;
        }
    }
}
