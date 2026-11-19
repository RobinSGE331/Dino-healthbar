package de.dinobar;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bestimmt, welches Mob gerade angezeigt wird.
 *
 * <p>Performance: pro Tick wird höchstens ein Raycast (alle 2 Ticks) gemacht und nur die Entities
 * rund um das angeschaute/getroffene Entity betrachtet. Ergebnisse der Zuordnung
 * "Hitbox-Entity -> echtes Mob" werden gecacht.
 */
public final class TargetTracker {
    private static final int NONE = -1;
    private static final float VANILLA_ARMOR_STAND_HP = 20f;

    private final DinoBarConfig config;

    private int lockedId = NONE;
    private long lockUntil;
    private int shownId = NONE;
    private long showUntil;

    private int lookCacheTick = -1;
    private Entity lookCache;

    private int resolveSourceId = NONE;
    private int resolveResultId = NONE;

    public TargetTracker(DinoBarConfig config) { this.config = config; }

    /** Aufruf, wenn der Spieler ein Entity schlägt. */
    public void onAttack(Entity hit, long gameTick) {
        LivingEntity target = resolve(hit);
        if (target != null) {
            lockedId = target.getId();
            lockUntil = gameTick + config.lockSeconds * 20L;
            show(target.getId(), gameTick);
        }
    }

    /** Liefert das anzuzeigende Mob oder null. Einmal pro Client-Tick aufrufen. */
    public LivingEntity tick(Minecraft mc, long gameTick) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) { reset(); return null; }

        // 1) Angeschautes Mob hat Vorrang.
        Entity looked = lookedAt(mc, player, gameTick);
        LivingEntity lookTarget = looked == null ? null : resolve(looked);
        if (lookTarget != null) {
            show(lookTarget.getId(), gameTick);
            return lookTarget;
        }

        // 2) Zuletzt getroffenes Mob bleibt eine Weile sichtbar.
        if (lockedId != NONE && gameTick <= lockUntil) {
            LivingEntity locked = valid(level.getEntity(lockedId));
            if (locked != null && inRange(player, locked)) {
                show(locked.getId(), gameTick);
                return locked;
            }
            lockedId = NONE;
        }

        // 3) Kurzes Nachleuchten nach dem Wegschauen.
        if (shownId != NONE && gameTick <= showUntil) {
            LivingEntity shown = valid(level.getEntity(shownId));
            if (shown != null && inRange(player, shown)) return shown;
        }
        shownId = NONE;
        return null;
    }

    public void reset() {
        lockedId = NONE;
        shownId = NONE;
        lookCache = null;
        lookCacheTick = -1;
        resolveSourceId = NONE;
        resolveResultId = NONE;
    }

    private void show(int id, long gameTick) {
        shownId = id;
        showUntil = gameTick + config.hideAfterSeconds * 20L;
    }

    private boolean inRange(Player player, Entity e) {
        double max = Math.max(config.lookRange, 16.0) + 16.0;
        return player.distanceToSqr(e) <= max * max;
    }

    /** Was schaut der Spieler an? Vanilla-Pick (3 Blöcke) oder eigener, gedrosselter Raycast. */
    public Entity lookedAt(Minecraft mc, LocalPlayer player, long gameTick) {
        if (mc.crosshairPickEntity != null) return mc.crosshairPickEntity;
        if (config.lookRange <= 3.5) return null;

        // Nur alle 2 Ticks raycasten.
        int t = (int) (gameTick >> 1);
        if (t == lookCacheTick) return lookCache != null && !lookCache.isRemoved() ? lookCache : null;
        lookCacheTick = t;
        lookCache = raycast(player, config.lookRange);
        return lookCache;
    }

    public static Entity raycast(LocalPlayer player, double range) {
        Vec3 eye = player.getEyePosition(1f);
        Vec3 view = player.getViewVector(1f);
        // Blöcke dürfen die Sicht verdecken.
        HitResult block = player.pick(range, 1f, false);
        double dist = range;
        if (block.getType() != HitResult.Type.MISS) dist = block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(view.scale(dist));
        AABB box = player.getBoundingBox().expandTowards(view.scale(dist)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eye, end, box, e -> !e.isSpectator() && e.isPickable() && e != player, dist * dist);
        return hit == null ? null : hit.getEntity();
    }

    /**
     * Ordnet ein angeschautes/getroffenes Entity einem echten Mob mit Leben zu.
     * 3D-Modell-Plugins (z.B. ModelEngine) nutzen oft unsichtbare Basis-Mobs, Interaction-,
     * Display- oder Armorstand-Entities. Dann suchen wir Fahrzeug/Passagiere und die nähere Umgebung ab.
     */
    public LivingEntity resolve(Entity e) {
        if (e == null) return null;
        LivingEntity direct = valid(e);
        if (direct != null) return direct;

        if (e.getId() == resolveSourceId) {
            LivingEntity cached = valid(e.level().getEntity(resolveResultId));
            if (cached != null) return cached;
        }

        LivingEntity found = null;
        for (Entity v = e.getVehicle(); v != null && found == null; v = v.getVehicle()) found = valid(v);
        if (found == null) {
            for (Entity p : e.getPassengers()) { found = valid(p); if (found != null) break; }
        }
        if (found == null) found = nearest(e);

        resolveSourceId = e.getId();
        resolveResultId = found == null ? NONE : found.getId();
        return found;
    }

    /**
     * Sucht in der Umgebung den Mob, der am wahrscheinlichsten die echten Lebenspunkte trägt:
     * höchstes Max-Leben gewinnt (Hilfs-Entities haben meist Standardwerte wie 20), bei Gleichstand der nächste.
     */
    private LivingEntity nearest(Entity source) {
        AABB area = source.getBoundingBox().inflate(config.resolveRadius);
        List<LivingEntity> list = source.level().getEntitiesOfClass(LivingEntity.class, area, le -> valid(le) != null);
        Vec3 c = source.getBoundingBox().getCenter();
        LivingEntity best = null;
        float bestMax = -1f;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity le : list) {
            float max = le.getMaxHealth();
            double d = le.position().distanceToSqr(c);
            if (max > bestMax || (max == bestMax && d < bestD)) { bestMax = max; bestD = d; best = le; }
        }
        return best;
    }

    /** Gibt das Entity zurück, wenn es ein anzeigbares Mob ist, sonst null. */
    public LivingEntity valid(Entity e) {
        if (!(e instanceof LivingEntity le)) return null;
        if (le.isRemoved()) return null;
        // Normale Armorstände (immer 20 LP) sind Deko. 3D-Modell-Plugins legen das echte Leben aber oft auf
        // einen unsichtbaren Marker-Armorstand mit erhöhtem Max-Leben – den wollen wir.
        if (le instanceof ArmorStand && !(le.getMaxHealth() > VANILLA_ARMOR_STAND_HP)) return null;
        if (le == Minecraft.getInstance().player) return null;
        if (le instanceof Player && !config.showPlayers) return null;
        if (config.minMaxHealth > 0f && le.getMaxHealth() < config.minMaxHealth) return null;
        return le;
    }
}
