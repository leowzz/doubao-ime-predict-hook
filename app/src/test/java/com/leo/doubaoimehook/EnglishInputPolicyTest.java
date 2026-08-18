package com.leo.doubaoimehook;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EnglishInputPolicyTest {
    @Test
    public void passwordPreeditStaysOnNativePath() {
        assertFalse(EnglishInputPolicy.shouldInterceptPreedit(true, true, "secret"));
    }

    @Test
    public void passwordKeyboardCommitStaysOnNativePath() {
        assertFalse(EnglishInputPolicy.shouldFilterKeyboardCallback(
                true, true, "keyboard_callback", "secret"));
    }

    @Test
    public void regularEnglishPreeditStillUsesDirectCommit() {
        assertTrue(EnglishInputPolicy.shouldInterceptPreedit(true, false, "secret"));
    }

    @Test
    public void regularEnglishCandidateCommitIsStillFiltered() {
        assertTrue(EnglishInputPolicy.shouldFilterKeyboardCallback(
                true, false, "keyboard_callback", "secret"));
    }

    @Test
    public void passwordCandidateCallbacksAreSuppressed() {
        assertTrue(EnglishInputPolicy.shouldSuppressCandidateCallbacks(false, true));
    }

    @Test
    public void regularEnglishCandidateCallbacksAreSuppressed() {
        assertTrue(EnglishInputPolicy.shouldSuppressCandidateCallbacks(true, false));
    }

    @Test
    public void nonEnglishNonPasswordCandidateCallbacksAreAllowed() {
        assertFalse(EnglishInputPolicy.shouldSuppressCandidateCallbacks(false, false));
    }

    @Test
    public void passwordNumberPreeditDropsAlreadyCommittedEnglishPrefix() {
        assertEquals("1", EnglishInputPolicy.replayedPreeditSuffix("abc", "abc1"));
    }

    @Test
    public void passwordPreeditWithoutCommittedPrefixStaysOnNativePath() {
        assertNull(EnglishInputPolicy.replayedPreeditSuffix("xyz", "abc1"));
    }

    @Test
    public void onlyLetterPrefixWithNonLetterSuffixIsRepairable() {
        assertTrue(EnglishInputPolicy.hasAsciiLetterPrefixWithSuffix("abc1"));
        assertFalse(EnglishInputPolicy.hasAsciiLetterPrefixWithSuffix("1"));
        assertFalse(EnglishInputPolicy.hasAsciiLetterPrefixWithSuffix("abc"));
    }
}
