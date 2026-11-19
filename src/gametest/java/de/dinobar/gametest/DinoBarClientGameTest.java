package de.dinobar.gametest;

import de.dinobar.mixin.BossHealthOverlayAccessor;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;

public class DinoBarClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            sp.getServer().runCommand("time set day");
            sp.getServer().runCommand("weather clear");
            sp.getServer().runCommand("gamerule doMobSpawning false");
            sp.getServer().runCommand("difficulty peaceful");
            sp.getServer().runCommand("tp @p 0 ~ 0 0 0");
            context.waitTicks(20);

            // Fall 1: normaler Mob mit Namen direkt vor dem Spieler (wie ein "einfacher" Custom-Dino).
            sp.getServer().runCommand("execute at @p run summon minecraft:iron_golem ^ ^ ^6 {CustomName:'Rex',NoAI:1b,Health:45f,attributes:[{id:'minecraft:max_health',base:100d}]}");
            context.waitTicks(30);
            String name1 = barName(context);
            context.takeScreenshot("dinobar-direct");
            check(name1 != null, "Fall 1: keine Bar sichtbar");
            check(name1.contains("Rex") && name1.contains("22.5 / 50"), "Fall 1: falscher Text: " + name1);

            // Fall 2: 3D-Modell-Aufbau – unsichtbarer Basis-Mob + Interaction-Hitbox drumherum.
            sp.getServer().runCommand("kill @e[type=minecraft:iron_golem]");
            context.waitTicks(40);
            context.waitTicks(5);
            sp.getServer().runCommand("execute at @p run summon minecraft:iron_golem ^ ^ ^6 {NoAI:1b,Silent:1b,Health:60f,attributes:[{id:'minecraft:max_health',base:200d}],active_effects:[{id:'minecraft:invisibility',duration:-1,show_particles:0b}]}");
            sp.getServer().runCommand("execute at @p run summon minecraft:interaction ^ ^ ^5 {width:3f,height:3f}");
            context.waitTicks(30);
            String name2 = barName(context);
            context.takeScreenshot("dinobar-model-hitbox");
            check(name2 != null, "Fall 2: keine Bar sichtbar");
            check(name2.contains("30 / 100"), "Fall 2: falscher Text: " + name2);

            // Fall 2b: Stresstest wie ein ModelEngine-Dino: 300 Display-Bones um die Hitbox.
            for (int i = 0; i < 300; i++) {
                sp.getServer().runCommand("execute at @p run summon minecraft:item_display ^" + ((i % 10) * 0.3 - 1.5)
                        + " ^" + ((i / 10 % 10) * 0.3) + " ^" + (5 + (i / 100) * 0.5) + " {item:{id:'minecraft:bone',count:1}}");
            }
            context.runOnClient(mc -> de.dinobar.DinoBarClient.maxTickNanos = 0);
            context.waitTicks(100);
            long maxNanos = context.computeOnClient(mc -> de.dinobar.DinoBarClient.maxTickNanos);
            long lastNanos = context.computeOnClient(mc -> de.dinobar.DinoBarClient.lastTickNanos);
            System.out.println("[DinoBar-Perf] max=" + (maxNanos / 1e6) + "ms last=" + (lastNanos / 1e6) + "ms bar=" + barName(context));
            context.takeScreenshot("dinobar-stress");

            // Fall 3: Wegschauen -> Bar verschwindet nach hideAfterSeconds (3s).
            sp.getServer().runCommand("tp @p ~ ~ ~ 180 0");
            context.waitTicks(80);
            check(barName(context) == null, "Fall 3: Bar sollte verschwunden sein");
        }
    }

    private static String barName(ClientGameTestContext context) {
        return context.computeOnClient(mc -> {
            Map<UUID, LerpingBossEvent> events = ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).dinobar$getEvents();
            UUID id = UUID.nameUUIDFromBytes("dinobar:healthbar".getBytes());
            LerpingBossEvent e = events.get(id);
            return e == null ? null : e.getName().getString();
        });
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
