package com.pvpclient.config;

import com.google.gson.*;
import com.pvpclient.PvpClient;
import com.pvpclient.module.ClientModule;
import com.pvpclient.module.ModuleManager;
import com.pvpclient.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Saves everything to .minecraft/config/pvpclient.json. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> AUTHORIZED = new ArrayList<>(List.of("localhost", "127.0.0.1"));

    private ConfigManager() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvpclient.json");
    }

    /** Hosts of servers you own/administer where combat + ESP modules may run (besides singleplayer). */
    public static List<String> authorizedServers() { return AUTHORIZED; }

    public static void load(ModuleManager manager) {
        Path f = file();
        if (!Files.exists(f)) { save(manager); return; }
        try (Reader reader = Files.newBufferedReader(f)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("authorizedServers")) {
                AUTHORIZED.clear();
                for (JsonElement e : root.getAsJsonArray("authorizedServers")) AUTHORIZED.add(e.getAsString());
            }
            JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (ClientModule m : manager.all()) {
                if (!mods.has(m.getName())) continue;
                JsonObject o = mods.getAsJsonObject(m.getName());
                if (!m.isAlwaysOn() && o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
                if (o.has("key")) m.setKeyCode(o.get("key").getAsInt());
                JsonObject so = o.has("settings") ? o.getAsJsonObject("settings") : new JsonObject();
                for (Setting<?> s : m.getSettings()) {
                    if (!so.has(s.getName())) continue;
                    try { s.fromJson(so.get(s.getName())); }
                    catch (Exception ex) { PvpClient.LOGGER.warn("Bad value for {}.{}", m.getName(), s.getName()); }
                }
            }
        } catch (Exception e) {
            PvpClient.LOGGER.error("Could not read config, using defaults", e);
        }
    }

    public static void save(ModuleManager manager) {
        JsonObject root = new JsonObject();
        JsonArray servers = new JsonArray();
        AUTHORIZED.forEach(servers::add);
        root.add("authorizedServers", servers);

        JsonObject mods = new JsonObject();
        for (ClientModule m : manager.all()) {
            JsonObject o = new JsonObject();
            if (!m.isAlwaysOn()) {
                o.addProperty("enabled", m.isEnabled());
                o.addProperty("key", m.getKeyCode());
            }
            JsonObject so = new JsonObject();
            for (Setting<?> s : m.getSettings()) so.add(s.getName(), s.toJson());
            o.add("settings", so);
            mods.add(m.getName(), o);
        }
        root.add("modules", mods);

        try (Writer w = Files.newBufferedWriter(file())) {
            GSON.toJson(root, w);
        } catch (Exception e) {
            PvpClient.LOGGER.error("Could not save config", e);
        }
    }
}
