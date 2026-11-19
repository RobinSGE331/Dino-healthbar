package de.dinobar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Einfache JSON-Config unter config/dinobar.json. */
public final class DinoBarConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dinobar.json");

    /** Bossbar überhaupt anzeigen. */
    public boolean enabled = true;
    /** Bis zu wie vielen Blöcken Entfernung ein angeschautes Mob angezeigt wird. */
    public double lookRange = 24.0;
    /** Wie lange (Sekunden) ein getroffenes Mob "gemerkt" wird, auch wenn man wegschaut. */
    public int lockSeconds = 10;
    /** Wie lange (Sekunden) die Bar nach dem Wegschauen noch sichtbar bleibt. */
    public int hideAfterSeconds = 3;
    /** Prozentanzeige hinter den Herzen. */
    public boolean showPercent = true;
    /** Namen des Mobs vor den Herzen anzeigen. */
    public boolean showName = true;
    /** Auch für andere Spieler anzeigen. */
    public boolean showPlayers = false;
    /** Nur Mobs mit mindestens so viel Max-Leben (Lebenspunkte, nicht Herzen). 0 = alle. */
    public float minMaxHealth = 0f;
    /** Maximale Distanz (Blöcke), in der ein Hitbox-/Modell-Entity einem echten Mob zugeordnet wird. */
    public double resolveRadius = 2.5;

    public static DinoBarConfig load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    DinoBarConfig c = GSON.fromJson(r, DinoBarConfig.class);
                    if (c != null) { c.save(); return c; }
                }
            }
        } catch (Exception e) {
            DinoBarClient.LOGGER.warn("[DinoBar] Config konnte nicht gelesen werden, nutze Standardwerte", e);
        }
        DinoBarConfig c = new DinoBarConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) { GSON.toJson(this, w); }
        } catch (Exception e) {
            DinoBarClient.LOGGER.warn("[DinoBar] Config konnte nicht gespeichert werden", e);
        }
    }
}
