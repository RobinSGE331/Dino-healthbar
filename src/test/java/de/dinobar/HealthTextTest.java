package de.dinobar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthTextTest {
    @Test void heartsAreHalfOfHealthPoints() {
        assertEquals("50", HealthText.hearts(100f));
        assertEquals("22.5", HealthText.hearts(45f));
        assertEquals("0.5", HealthText.hearts(1f));
        assertEquals("0", HealthText.hearts(-3f));
        assertEquals("?", HealthText.hearts(Float.NaN));
    }

    @Test void progressIsClampedAndSafe() {
        assertEquals(0.5f, HealthText.progress(50f, 100f));
        assertEquals(1f, HealthText.progress(150f, 100f));
        assertEquals(0f, HealthText.progress(-1f, 100f));
        assertEquals(0f, HealthText.progress(10f, 0f));
        assertEquals(0f, HealthText.progress(Float.NaN, 100f));
    }

    @Test void tierThresholds() {
        assertEquals(HealthText.Tier.GREEN, HealthText.tier(0.51f));
        assertEquals(HealthText.Tier.YELLOW, HealthText.tier(0.5f));
        assertEquals(HealthText.Tier.YELLOW, HealthText.tier(0.26f));
        assertEquals(HealthText.Tier.RED, HealthText.tier(0.25f));
    }

    @Test void labelFormat() {
        assertEquals("22.5 / 50 ❤ (45%)", HealthText.label(45f, 100f, true));
        assertEquals("22.5 / 50 ❤", HealthText.label(45f, 100f, false));
    }
}
