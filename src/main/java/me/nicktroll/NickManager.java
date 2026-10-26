package me.nicktroll;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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
            } catch (IllegalArgumentException ignored) {}
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

    public boolean hasNick(UUID id) { return nicks.containsKey(id); }
    public String getOriginal(UUID id) { return originals.get(id); }

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
     * Меняет профиль (таб/чат) и создаёт невидимый TextDisplay для наместника.
     * Поддерживает цветовые коды через '&'.
     */
    private void apply(Player player, String name) {
        // 1. Обновляем профиль (таб, чат)
        PlayerProfile oldProfile = player.getPlayerProfile();
        PlayerProfile newProfile = Bukkit.createProfile(player.getUniqueId(), name);
        for (ProfileProperty property : oldProfile.getProperties()) {
            newProfile.setProperty(property);
        }
        player.setPlayerProfile(newProfile);

        // 2. Отображаемое имя
        Component display = LEGACY.deserialize(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        player.displayName(display);
        player.playerListName(display);

        // 3. Скрываем старый наместник через Scoreboard Team
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam("nick_" + player.getName());
        if (team == null) team = board.registerNewTeam("nick_" + player.getName());
        team.addEntry(player.getName());
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        // 4. Создаём TextDisplay над головой (следует за игроком)
        for (TextDisplay td : player.getWorld().getEntitiesByClass(TextDisplay.class)) {
            if (td.getScoreboardTags().contains("nick_" + player.getUniqueId())) td.remove();
        }
        TextDisplay textDisplay = player.getWorld().spawn(player.getLocation().add(0, 2.2, 0), TextDisplay.class);
        textDisplay.text(display);
        textDisplay.setBillboard(Display.Billboard.CENTER);
        textDisplay.addScoreboardTag("nick_" + player.getUniqueId());

        // Запускаем таск для следования за игроком
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !textDisplay.isValid()) {
                textDisplay.remove();
                return;
            }
            textDisplay.teleport(player.getLocation().add(0, 2.2, 0));
        }, 1L, 1L);
    }

    private void playThunder() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        }
    }
}
