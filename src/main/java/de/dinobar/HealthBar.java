package de.dinobar;

import de.dinobar.mixin.BossHealthOverlayAccessor;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hängt eine eigene Bossbar in die Vanilla-Bossbar-Liste. Dadurch sieht sie aus wie eine echte
 * Bossbar, stapelt sich sauber mit Server-Bossbars und animiert den Balken flüssig.
 * Aktualisiert wird nur, wenn sich Werte wirklich ändern.
 */
public final class HealthBar {
    private static final UUID BAR_ID = UUID.nameUUIDFromBytes("dinobar:healthbar".getBytes());

    private final DinoBarConfig config;
    private LerpingBossEvent event;
    private int targetId = -1;
    private float lastHealth = Float.NaN;
    private float lastMax = Float.NaN;
    /** Höchstes beobachtetes Leben – Fallback, falls der Server das Max-Leben nicht korrekt sendet. */
    private float highestSeen;

    public HealthBar(DinoBarConfig config) { this.config = config; }

    public void update(Minecraft mc, LivingEntity target) {
        if (target == null || !config.enabled) { hide(mc); return; }

        if (target.getId() != targetId) {
            targetId = target.getId();
            highestSeen = 0f;
            lastHealth = Float.NaN;
            lastMax = Float.NaN;
        }

        float health = target.getHealth();
        if (Float.isFinite(health)) highestSeen = Math.max(highestSeen, health);
        float max = Math.max(target.getMaxHealth(), highestSeen);

        Map<UUID, LerpingBossEvent> events = ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).dinobar$getEvents();
        boolean created = false;
        if (event == null) {
            event = new LerpingBossEvent(BAR_ID, Component.empty(), 1f,
                    BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10, false, false, false);
            created = true;
        }
        // Server-Reset (z.B. Weltwechsel) entfernt unsere Bar – dann neu einhängen.
        if (events.get(BAR_ID) != event) events.put(BAR_ID, event);

        if (!created && health == lastHealth && max == lastMax) return;
        lastHealth = health;
        lastMax = max;

        float progress = HealthText.progress(health, max);
        event.setProgress(progress);
        event.setColor(switch (HealthText.tier(progress)) {
            case GREEN -> BossEvent.BossBarColor.GREEN;
            case YELLOW -> BossEvent.BossBarColor.YELLOW;
            case RED -> BossEvent.BossBarColor.RED;
        });
        event.setName(buildName(target, health, max));
    }

    public void hide(Minecraft mc) {
        if (event != null) {
            ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).dinobar$getEvents().remove(BAR_ID, event);
            event = null;
        }
        targetId = -1;
    }

    private Component buildName(LivingEntity target, float health, float max) {
        MutableComponent text = Component.empty();
        if (config.showName) {
            text.append(displayName(target).copy()).append(Component.literal("  "));
        }
        ChatFormatting color = switch (HealthText.tier(HealthText.progress(health, max))) {
            case GREEN -> ChatFormatting.GREEN;
            case YELLOW -> ChatFormatting.YELLOW;
            case RED -> ChatFormatting.RED;
        };
        text.append(Component.literal(HealthText.label(health, max, config.showPercent)).withStyle(color));
        return text;
    }

    /** Eigener Name > Name von Passagier/Fahrzeug (Namensschild des Modells) > Mob-Typ. */
    static Component displayName(LivingEntity target) {
        if (target.hasCustomName()) return target.getCustomName();
        for (Entity p : target.getPassengers()) if (p.hasCustomName()) return p.getCustomName();
        Entity v = target.getVehicle();
        if (v != null && v.hasCustomName()) return v.getCustomName();
        return target.getType().getDescription();
    }
}
