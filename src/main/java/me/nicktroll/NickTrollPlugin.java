package me.nicktroll;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class NickTrollPlugin extends JavaPlugin implements Listener {

    private NickManager nickManager;

    @Override
    public void onEnable() {
        nickManager = new NickManager(this);
        nickManager.load();

        NickChangeCommand nickCmd = new NickChangeCommand(nickManager);
        PluginCommand nc = getCommand("nickchange");
        if (nc != null) {
            nc.setExecutor(nickCmd);
            nc.setTabCompleter(nickCmd);
        }

        TrollMenu menu = new TrollMenu(this, nickManager);
        PluginCommand pt = getCommand("playertroll");
        if (pt != null) {
            pt.setExecutor(menu);
        }

        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    // При входе на сервер заново применяем сохранённый ник
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!nickManager.hasNick(player.getUniqueId())) return;
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) nickManager.applyStored(player);
        }, 2L);
    }
}
