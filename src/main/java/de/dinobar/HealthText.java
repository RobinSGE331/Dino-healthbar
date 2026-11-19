package de.dinobar;

import java.util.Locale;

/** Reine Rechen-/Formatierungslogik ohne Minecraft-Abhängigkeit (testbar). */
public final class HealthText {
    private HealthText() {}

    public enum Tier { GREEN, YELLOW, RED }

    /** Fortschritt 0..1, robust gegen NaN, negative Werte und fehlendes Max-Leben. */
    public static float progress(float health, float maxHealth) {
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0f) return 0f;
        float p = health / maxHealth;
        return Math.max(0f, Math.min(1f, p));
    }

    public static Tier tier(float progress) {
        if (progress > 0.5f) return Tier.GREEN;
        if (progress > 0.25f) return Tier.YELLOW;
        return Tier.RED;
    }

    /** 1 Herz = 2 Lebenspunkte. Eine Nachkommastelle, ".0" wird weggelassen. */
    public static String hearts(float healthPoints) {
        if (!Float.isFinite(healthPoints)) return "?";
        return number(Math.max(0f, healthPoints) / 2f);
    }

    public static String number(float v) {
        float rounded = Math.round(v * 10f) / 10f;
        if (rounded == (long) rounded) return Long.toString((long) rounded);
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    /** z.B. "45.5 / 100 ❤ (91%)" */
    public static String label(float health, float maxHealth, boolean showPercent) {
        StringBuilder sb = new StringBuilder();
        sb.append(hearts(health)).append(" / ").append(hearts(maxHealth)).append(" ❤");
        if (showPercent) {
            sb.append(" (").append(Math.round(progress(health, maxHealth) * 100f)).append("%)");
        }
        return sb.toString();
    }
}
