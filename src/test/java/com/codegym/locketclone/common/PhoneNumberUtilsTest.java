package com.codegym.locketclone.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneNumberUtilsTest {

    @Test
    void normalize_convertsVietnameseLocalNumberToCanonicalFormat() {
        assertEquals("+84901234567", PhoneNumberUtils.normalize("0901234567"));
    }

    @Test
    void lookupCandidates_containsCanonicalAndLegacyVariants() {
        var candidates = PhoneNumberUtils.lookupCandidates("0901234567");

        assertTrue(candidates.contains("+84901234567"));
        assertTrue(candidates.contains("0901234567"));
        assertTrue(candidates.contains("84901234567"));
    }

    @Test
    void normalize_rejectsUnsupportedPhoneNumber() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNumberUtils.normalize("abc"));
    }
}

