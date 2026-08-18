package com.leo.doubaoimehook;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreeditDeltaTest {
    @Test
    public void appendCommitsOnlyNewSuffix() {
        PreeditDelta.Delta delta = PreeditDelta.between("he", "hel");

        assertEquals(0, delta.deleteBeforeCursor);
        assertEquals("l", delta.commitAfterCursor);
    }

    @Test
    public void backspaceDeletesPreviousSuffix() {
        PreeditDelta.Delta delta = PreeditDelta.between("hello", "hell");

        assertEquals(1, delta.deleteBeforeCursor);
        assertEquals("", delta.commitAfterCursor);
    }

    @Test
    public void replacementDeletesAndCommits() {
        PreeditDelta.Delta delta = PreeditDelta.between("cat", "car");

        assertEquals(1, delta.deleteBeforeCursor);
        assertEquals("r", delta.commitAfterCursor);
    }

    @Test
    public void asciiLetterCheckRejectsPunctuation() {
        assertTrue(PreeditDelta.isAsciiLetters(""));
        assertTrue(PreeditDelta.isAsciiLetters("Abc"));
        assertTrue(!PreeditDelta.isAsciiLetters("abc1"));
        assertTrue(!PreeditDelta.isAsciiLetters("abc-"));
    }
}
