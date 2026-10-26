package me.nicktroll;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NickManager {

    private final NickTrollPlugin plugin;
    private final File file;
    private final Map<UUID, String> originals = new HashMap<>();
    private final Map<UUID, String> nicks = new HashMap<>();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public NickManager(NickTrollPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nicks.yml");
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String original = sec.getString(key + ".original");
                String nick = sec.getString(key + ".nick");
                if (original != null && nick != null) {
                    originals.put(id, original);
                    nicks.put(id, nick);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (UUID id : nicks.keySet()) {
            yml.set("players." + id + ".original", originals.get(id));
            yml.set("players." + id + ".nick", nicks.get(id));
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить nicks.yml: " + e.getMessage());
        }
    }

    public boolean hasNick(UUID id) {
        return nicks.containsKey(id);
    }

    public String getOriginal(UUID id) {
        return originals.get(id);
    }

    public Player findOnline(String name) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (p.getName().equalsIgnoreCase(name)
                    || name.equalsIgnoreCase(originals.get(id))
                    || name.equalsIgnoreCase(nicks.get(id))) {
                return p;
            }
        }
        return null;
    }

    public boolean isNameTaken(String name, UUID except) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.getUniqueId().equals(except) && o.getName().equalsIgnoreCase(name)) return true;
        }
        for (Map.Entry<UUID, String> e : nicks.entrySet()) {
            if (!e.getKey().equals(except) && e.getValue().equalsIgnoreCase(name)) return true;
        }
        for (Map.Entry<UUID, String> e : originals.entrySet()) {
            if (!e.getKey().equals(except) && e.getValue().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public void setNick(Player player, String newName) {
        UUID id = player.getUniqueId();
        originals.putIfAbsent(id, player.getName());
        nicks.put(id, newName);
        apply(player, newName);
        playThunder();
        save();
    }

    public boolean resetNick(Player player) {
        UUID id = player.getUniqueId();
        String original = originals.remove(id);
        nicks.remove(id);
        if (original == null) return false;
        apply(player, original);
        playThunder();
        save();
        return true;
    }

    public void applyStored(Player player) {
        String nick = nicks.get(player.getUniqueId());
        if (nick != null) apply(player, nick);
    }

    /**
     * Меняет профиль игрока и принудительно обновляет его для всех остальных.
     * Скин сохраняется. Поддерживает цветовые коды через '&'.
     */
    private void apply(Player player, String name) {
        // 1. Создаём новый профиль с тем же UUID и свойствами (скин)
        PlayerProfile oldProfile = player.getPlayerProfile();
        PlayerProfile newProfile = Bukkit.createProfile(player.getUniqueId(), name);
        for (ProfileProperty property : oldProfile.getProperties()) {
            newProfile.setProperty(property);
        }
        player.setPlayerProfile(newProfile);

        // 2. Устанавливаем отображаемое имя с поддержкой цветов
        Component display = LEGACY.deserialize(name).decoration(
                net.kyori.adventure.text.format.TextDecoration.ITALIC, false
        );
        player.displayName(display);
        player.playerListName(display);

        // 3. Принудительно обновляем игрока для всех остальных,
        //    чтобы ник над головой изменился у всех на клиенте.
        //    Простой трюк: скрыть и показать игрока всем.
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            online.hidePlayer(plugin, player);
            online.showPlayer(plugin, player);
        }
    }

    private void playThunder() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        }
    }
}
