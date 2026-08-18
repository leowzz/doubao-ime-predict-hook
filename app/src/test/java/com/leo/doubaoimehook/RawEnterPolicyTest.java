package com.leo.doubaoimehook;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RawEnterPolicyTest {
    @Test
    public void missedRawEnterIsCompensated() {
        assertTrue(RawEnterPolicy.shouldCompensate(
                true, true, true, true, false));
    }

    @Test
    public void nativeEnterIsNotDuplicated() {
        assertFalse(RawEnterPolicy.shouldCompensate(
                true, true, true, true, true));
    }

    @Test
    public void nonRawInputIsNotCompensated() {
        assertFalse(RawEnterPolicy.shouldCompensate(
                false, true, true, true, false));
    }

    @Test
    public void otherBoardsAndGesturesAreNotCompensated() {
        assertFalse(RawEnterPolicy.shouldCompensate(
                true, false, true, true, false));
        assertFalse(RawEnterPolicy.shouldCompensate(
                true, true, false, true, false));
        assertFalse(RawEnterPolicy.shouldCompensate(
                true, true, true, false, false));
    }

    @Test
    public void enterRegionMatchesMeasuredEnglishKeyboardKey() {
        assertTrue(RawEnterPolicy.isEnterKeyRegion(1135, 748, 1220, 893));
        assertFalse(RawEnterPolicy.isEnterKeyRegion(1020, 748, 1220, 893));
        assertFalse(RawEnterPolicy.isEnterKeyRegion(1135, 600, 1220, 893));
        assertFalse(RawEnterPolicy.isEnterKeyRegion(0, 0, 0, 0));
    }
}
