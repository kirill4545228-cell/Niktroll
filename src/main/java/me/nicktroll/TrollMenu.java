package me.nicktroll;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrollMenu implements CommandExecutor, Listener {

    private static final int PER_PAGE = 45;

    private final NickTrollPlugin plugin;
    private final NickManager nicks;
    private final Set<UUID> awaitingInput = ConcurrentHashMap.newKeySet();

    public TrollMenu(NickTrollPlugin plugin, NickManager nicks) {
        this.plugin = plugin;
        this.nicks = nicks;
    }

    // ---------- Держатели инвентарей ----------

    private static class SelectorHolder implements InventoryHolder {
        final int page;
        final List<UUID> shown = new ArrayList<>();
        Inventory inv;

        SelectorHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static class ActionHolder implements InventoryHolder {
        final UUID target;
        Inventory inv;

        ActionHolder(UUID target) {
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ---------- Команда ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда только для игроков.", NamedTextColor.RED));
            return true;
        }
        openSelector(player, 0);
        return true;
    }

    // ---------- Построение меню ----------

    private ItemStack item(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore)
                    .map(l -> Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack head(Player target) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(Component.text(target.getName(), NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Нажми, чтобы выбрать", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private void openSelector(Player viewer, int page) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int maxPage = Math.max(0, (online.size() - 1) / PER_PAGE);
        page = Math.min(Math.max(page, 0), maxPage);

        SelectorHolder holder = new SelectorHolder(page);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Выбор игрока"));
        holder.inv = inv;

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < online.size(); i++) {
            Player t = online.get(start + i);
            holder.shown.add(t.getUniqueId());
            inv.setItem(i, head(t));
        }

        if (page > 0) inv.setItem(45, item(Material.ARROW, "← Назад", NamedTextColor.YELLOW));
        inv.setItem(49, item(Material.NAME_TAG, "Ввести ник вручную", NamedTextColor.AQUA,
                "Нажми и напиши ник игрока в чат", "(напиши «отмена», чтобы выйти)"));
        if (page < maxPage) inv.setItem(53, item(Material.ARROW, "Вперёд →", NamedTextColor.YELLOW));

        viewer.openInventory(inv);
    }

    private void openActions(Player viewer, Player target) {
        ActionHolder holder = new ActionHolder(target.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Игрок: " + target.getName()));
        holder.inv = inv;

        inv.setItem(10, item(Material.ENDER_PEARL, "Телепортироваться к игроку", NamedTextColor.LIGHT_PURPLE));
        inv.setItem(11, item(Material.TNT, "Убить", NamedTextColor.RED));
        inv.setItem(12, item(Material.DIAMOND, "Выдать 1 алмаз", NamedTextColor.AQUA));
        inv.setItem(13, item(Material.LIGHTNING_ROD, "Ударить молнией", NamedTextColor.YELLOW));
        inv.setItem(14, item(Material.FIREWORK_ROCKET, "Подбросить вверх", NamedTextColor.GOLD));
        inv.setItem(22, item(Material.ARROW, "← К списку игроков", NamedTextColor.GRAY));

        viewer.openInventory(inv);
    }

    // ---------- Клики ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!(holder instanceof SelectorHolder) && !(holder instanceof ActionHolder)) return;

        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        int slot = e.getSlot();
        if (holder instanceof SelectorHolder sh) {
            handleSelector(player, sh, slot);
        } else if (holder instanceof ActionHolder ah) {
            handleAction(player, ah, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder(false);
        if (holder instanceof SelectorHolder || holder instanceof ActionHolder) {
            e.setCancelled(true);
        }
    }

    private void handleSelector(Player player, SelectorHolder sh, int slot) {
        if (slot < PER_PAGE) {
            if (slot >= sh.shown.size()) return;
            Player target = Bukkit.getPlayer(sh.shown.get(slot));
            if (target == null) {
                player.sendMessage(Component.text("Игрок уже вышел.", NamedTextColor.RED));
                openSelector(player, sh.page);
                return;
            }
            openActions(player, target);
        } else if (slot == 45 && sh.page > 0) {
            openSelector(player, sh.page - 1);
        } else if (slot == 49) {
            awaitingInput.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(Component.text("Напиши в чат ник игрока (или «отмена»):", NamedTextColor.AQUA));
        } else if (slot == 53) {
            openSelector(player, sh.page + 1);
        }
    }

    private void handleAction(Player player, ActionHolder ah, int slot) {
        if (slot == 22) {
            openSelector(player, 0);
            return;
        }

        Player target = Bukkit.getPlayer(ah.target);
        if (target == null) {
            player.closeInventory();
            player.sendMessage(Component.text("Игрок уже вышел.", NamedTextColor.RED));
            return;
        }

        String name = target.getName();
        switch (slot) {
            case 10 -> {
                player.closeInventory();
                player.teleportAsync(target.getLocation());
                player.sendMessage(Component.text("Телепортация к " + name, NamedTextColor.GREEN));
            }
            case 11 -> {
                target.setHealth(0.0);
                player.sendMessage(Component.text("Игрок " + name + " убит.", NamedTextColor.GREEN));
            }
            case 12 -> {
                Map<Integer, ItemStack> left = target.getInventory().addItem(new ItemStack(Material.DIAMOND));
                left.values().forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(), i));
                player.sendMessage(Component.text("Выдан 1 алмаз игроку " + name, NamedTextColor.GREEN));
            }
            case 13 -> {
                target.getWorld().strikeLightning(target.getLocation());
                player.sendMessage(Component.text("Молния ударила в " + name, NamedTextColor.GREEN));
            }
            case 14 -> {
                target.setVelocity(new Vector(0, 2, 0));
                player.sendMessage(Component.text(name + " подброшен вверх.", NamedTextColor.GREEN));
            }
            default -> {
                return;
            }
        }
        player.closeInventory();
    }

    // ---------- Ввод ника через чат ----------

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player player = e.getPlayer();
        if (!awaitingInput.remove(player.getUniqueId())) return;

        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
                player.sendMessage(Component.text("Отменено.", NamedTextColor.YELLOW));
                return;
            }
            Player target = nicks.findOnline(text);
            if (target == null) {
                player.sendMessage(Component.text("Игрок «" + text + "» не найден онлайн.", NamedTextColor.RED));
                return;
            }
            openActions(player, target);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        awaitingInput.remove(e.getPlayer().getUniqueId());
    }
}
