package de.dinobar;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DinoBarClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("dinobar");

    private static DinoBarConfig config;
    private static TargetTracker tracker;
    private static HealthBar bar;
    private static long tickCounter;
    /** Messwerte: Zeit, die der Mod pro Tick braucht (Nanosekunden). */
    public static long lastTickNanos, maxTickNanos;

    private static KeyMapping toggleKey;
    private static KeyMapping debugKey;

    @Override
    public void onInitializeClient() {
        config = DinoBarConfig.load();
        tracker = new TargetTracker(config);
        bar = new HealthBar(config);

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("dinobar", "dinobar"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.dinobar.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        debugKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.dinobar.debug", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide() && config.enabled) tracker.onAttack(entity, tickCounter);
            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(DinoBarClient::onTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
                ClientCommandManager.literal("dinobar")
                        .executes(c -> status(c.getSource()))
                        .then(ClientCommandManager.literal("toggle").executes(c -> { toggle(); return 1; }))
                        .then(ClientCommandManager.literal("debug").executes(c -> { debug(Minecraft.getInstance()); return 1; }))
                        .then(ClientCommandManager.literal("percent").executes(c -> {
                            config.showPercent = !config.showPercent; changed(c, "Prozent: " + onOff(config.showPercent)); return 1; }))
                        .then(ClientCommandManager.literal("name").executes(c -> {
                            config.showName = !config.showName; changed(c, "Name: " + onOff(config.showName)); return 1; }))
                        .then(ClientCommandManager.literal("players").executes(c -> {
                            config.showPlayers = !config.showPlayers; changed(c, "Spieler: " + onOff(config.showPlayers)); return 1; }))
                        .then(ClientCommandManager.literal("range")
                                .then(ClientCommandManager.argument("bloecke", DoubleArgumentType.doubleArg(0, 64)).executes(c -> {
                                    config.lookRange = DoubleArgumentType.getDouble(c, "bloecke");
                                    changed(c, "Reichweite: " + config.lookRange + " Blöcke"); return 1; })))
                        .then(ClientCommandManager.literal("minhp")
                                .then(ClientCommandManager.argument("lebenspunkte", FloatArgumentType.floatArg(0)).executes(c -> {
                                    config.minMaxHealth = FloatArgumentType.getFloat(c, "lebenspunkte");
                                    changed(c, "Mindest-Max-Leben: " + config.minMaxHealth + " LP"); return 1; })))
        ));
    }

    private static void onTick(Minecraft mc) {
        tickCounter++;
        while (toggleKey.consumeClick()) toggle();
        while (debugKey.consumeClick()) debug(mc);

        if (!config.enabled || mc.level == null || mc.player == null) {
            bar.hide(mc);
            if (mc.level == null) tracker.reset();
            return;
        }
        long t0 = System.nanoTime();
        bar.update(mc, tracker.tick(mc, tickCounter));
        lastTickNanos = System.nanoTime() - t0;
        if (lastTickNanos > maxTickNanos) maxTickNanos = lastTickNanos;
    }

    private static void toggle() {
        config.enabled = !config.enabled;
        config.save();
        msg(Component.literal("Dino Health Bar: " + onOff(config.enabled)));
    }

    private static int status(FabricClientCommandSource src) {
        src.sendFeedback(prefix().append("an=" + onOff(config.enabled) + ", Reichweite=" + config.lookRange
                + ", Merken=" + config.lockSeconds + "s, minHP=" + config.minMaxHealth
                + "\nBefehle: toggle, debug, percent, name, players, range <n>, minhp <n>"));
        return 1;
    }

    private static void changed(CommandContext<FabricClientCommandSource> c, String what) {
        config.save();
        c.getSource().sendFeedback(prefix().append(what));
    }

    /** Zeigt, was der Mod "sieht" – hilft herauszufinden, wie die Dinos auf dem Server gebaut sind. */
    private static void debug(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        msg(Component.literal(String.format(java.util.Locale.ROOT, "Mod-Zeit/Tick: zuletzt %.3f ms, max %.3f ms",
                lastTickNanos / 1e6, maxTickNanos / 1e6)).withStyle(ChatFormatting.GRAY));
        maxTickNanos = 0;
        Entity looked = mc.crosshairPickEntity != null ? mc.crosshairPickEntity : TargetTracker.raycast(mc.player, 32);
        if (looked == null) { msg(Component.literal("Debug: Du schaust kein Entity an.").withStyle(ChatFormatting.GRAY)); return; }

        msg(Component.literal("Angeschaut: ").withStyle(ChatFormatting.GOLD).append(describe(looked)));
        if (looked.getVehicle() != null) msg(Component.literal("  Fahrzeug: ").append(describe(looked.getVehicle())));
        for (Entity p : looked.getPassengers()) msg(Component.literal("  Passagier: ").append(describe(p)));

        LivingEntity resolved = tracker.resolve(looked);
        msg(Component.literal("Zugeordnet: ").withStyle(ChatFormatting.GOLD)
                .append(resolved == null ? Component.literal("nichts gefunden").withStyle(ChatFormatting.RED) : describe(resolved)));

        List<LivingEntity> near = mc.level.getEntitiesOfClass(LivingEntity.class, looked.getBoundingBox().inflate(4),
                e -> e != mc.player && e != looked);
        int n = 0;
        for (LivingEntity le : near) {
            if (n++ >= 5) break;
            msg(Component.literal("  In der Nähe: ").append(describe(le)));
        }
    }

    private static Component describe(Entity e) {
        StringBuilder sb = new StringBuilder();
        sb.append(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())).append(" #").append(e.getId());
        if (e.isInvisible()) sb.append(" [unsichtbar]");
        if (e instanceof LivingEntity le) {
            sb.append(" HP ").append(HealthText.number(le.getHealth())).append('/').append(HealthText.number(le.getMaxHealth()));
        } else {
            sb.append(" (kein Leben)");
        }
        sb.append(String.format(java.util.Locale.ROOT, " %.1fx%.1f", e.getBbWidth(), e.getBbHeight()));
        Component out = Component.literal(sb.toString()).withStyle(ChatFormatting.WHITE);
        if (e.hasCustomName()) out = out.copy().append(Component.literal(" Name: ")).append(e.getCustomName());
        return out;
    }

    private static void msg(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(prefix().append(c), false);
    }

    private static net.minecraft.network.chat.MutableComponent prefix() {
        return Component.literal("[DinoBar] ").withStyle(ChatFormatting.DARK_GREEN);
    }

    private static String onOff(boolean b) { return b ? "an" : "aus"; }
}
