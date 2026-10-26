package me.nicktroll;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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

    private static class SelectorHolder implements InventoryHolder {
        final int page;
        final List<UUID> shown = new ArrayList<>();
        Inventory inv;
        SelectorHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inv; }
    }

    private static class ActionHolder implements InventoryHolder {
        final UUID target;
        Inventory inv;
        ActionHolder(UUID target) { this.target = target; }
        @Override public Inventory getInventory() { return inv; }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда только для игроков.", NamedTextColor.RED));
            return true;
        }
        openSelector(player, 0);
        return true;
    }

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
        Inventory inv = Bukkit.createInventory(holder, 45,
                Component.text("Игрок: " + target.getName()));
        holder.inv = inv;

        // Базовые действия
        inv.setItem(10, item(Material.ENDER_PEARL, "Телепортироваться к игроку", NamedTextColor.LIGHT_PURPLE));
        inv.setItem(11, item(Material.TNT, "Убить", NamedTextColor.RED));
        inv.setItem(12, item(Material.DIAMOND, "Выдать 1 алмаз", NamedTextColor.AQUA));
        inv.setItem(13, item(Material.LIGHTNING_ROD, "Ударить молнией", NamedTextColor.YELLOW));
        inv.setItem(14, item(Material.FIREWORK_ROCKET, "Подбросить вверх", NamedTextColor.GOLD));

        // Новые действия
        inv.setItem(19, item(Material.COBWEB, "Заморозить (эффект Slowness 255)", NamedTextColor.DARK_AQUA,
                "Накладывает сильное замедление на 10 секунд"));
        inv.setItem(20, item(Material.ROTTEN_FLESH, "Отравить (эффект Poison)", NamedTextColor.DARK_GREEN,
                "Накладывает отравление на 8 секунд"));
        inv.setItem(21, item(Material.FEATHER, "Подбросить с эффектом Levitation", NamedTextColor.WHITE,
                "Медленно поднимает игрока вверх на 5 секунд"));
        inv.setItem(22, item(Material.CHEST, "Открыть инвентарь игрока", NamedTextColor.GRAY,
                "Позволяет заглянуть в инвентарь цели"));
        inv.setItem(23, item(Material.PUFFERFISH, "Случайный эффект", NamedTextColor.DARK_PURPLE,
                "Накладывает случайный негативный эффект"));

        // Кнопка возврата
        inv.setItem(40, item(Material.ARROW, "← К списку игроков", NamedTextColor.GRAY));

        viewer.openInventory(inv);
    }

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
            player.sendMessage(Component.text("Введи ник игрока в чат (или «отмена»):", NamedTextColor.AQUA));
        } else if (slot == 53 && sh.page < ((Bukkit.getOnlinePlayers().size() - 1) / PER_PAGE)) {
            openSelector(player, sh.page + 1);
        }
    }

    private void handleAction(Player player, ActionHolder ah, int slot) {
        Player target = Bukkit.getPlayer(ah.target);
        if (target == null) {
            player.sendMessage(Component.text("Игрок уже вышел.", NamedTextColor.RED));
            openSelector(player, 0);
            return;
        }

        switch (slot) {
            case 10 -> player.teleport(target.getLocation());
            case 11 -> target.setHealth(0);
            case 12 -> target.getInventory().addItem(new ItemStack(Material.DIAMOND, 1));
            case 13 -> target.getWorld().strikeLightning(target.getLocation());
            case 14 -> target.setVelocity(new Vector(0, 1.5, 0));
            case 19 -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 255));
                player.sendMessage(Component.text("Игрок заморожен.", NamedTextColor.GREEN));
            }
            case 20 -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 160, 1));
                player.sendMessage(Component.text("Игрок отравлен.", NamedTextColor.GREEN));
            }
            case 21 -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 1));
                player.sendMessage(Component.text("Игрок подброшен.", NamedTextColor.GREEN));
            }
            case 22 -> player.openInventory(target.getInventory());
            case 23 -> {
                PotionEffectType[] effects = {PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA,
                        PotionEffectType.WEAKNESS, PotionEffectType.MINING_FATIGUE};
                PotionEffectType random = effects[(int) (Math.random() * effects.length)];
                target.addPotionEffect(new PotionEffect(random, 200, 1));
                player.sendMessage(Component.text("Наложен случайный эффект: " + random.getKey().getKey(), NamedTextColor.GREEN));
            }
            case 40 -> openSelector(player, 0);
        }
        // После действия оставляем меню открытым (кроме возврата)
        if (slot != 40) {
            // Можно оставить открытым, но для удобства перерисовываем
            // openActions(player, target); // если нужно обновить
        }
    }

    // Обработка ручного ввода ника
    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player player = e.getPlayer();
        if (!awaitingInput.remove(player.getUniqueId())) return;
        e.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (message.equalsIgnoreCase("отмена")) {
            player.sendMessage(Component.text("Отменено.", NamedTextColor.GRAY));
            return;
        }

        // Ищем игрока в основном потоке
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player target = nicks.findOnline(message);
            if (target == null) {
                player.sendMessage(Component.text("Игрок не найден (он должен быть онлайн).", NamedTextColor.RED));
            } else {
                openActions(player, target);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        awaitingInput.remove(e.getPlayer().getUniqueId());
    }
}ponent.text("Отменено.", NamedTextColor.YELLOW));
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
