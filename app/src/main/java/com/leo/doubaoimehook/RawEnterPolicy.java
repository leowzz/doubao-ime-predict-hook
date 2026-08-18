package com.leo.doubaoimehook;

final class RawEnterPolicy {
    // Ratios follow the bottom-right enter key in the resizable English layout.
    private static final float ENTER_LEFT_RATIO = 0.86f;
    private static final float ENTER_TOP_RATIO = 0.72f;

    private RawEnterPolicy() {
    }

    static boolean shouldCompensate(
            boolean rawInput,
            boolean englishKeyboard,
            boolean touchStartedOnEnter,
            boolean touchEndedOnEnter,
            boolean enterDispatched) {
        return rawInput
                && englishKeyboard
                && touchStartedOnEnter
                && touchEndedOnEnter
                && !enterDispatched;
    }

    static boolean isEnterKeyRegion(float x, float y, int width, int height) {
        return width > 0
                && height > 0
                && x >= width * ENTER_LEFT_RATIO
                && x <= width
                && y >= height * ENTER_TOP_RATIO
                && y <= height;
    }
}
