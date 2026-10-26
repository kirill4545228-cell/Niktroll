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
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrollMenu implements CommandExecutor, Listener {

    private static final int PER_PAGE = 45;
    private final NickTrollPlugin plugin;
    private final NickManager nicks;
    private final Set<UUID> awaitingInput = ConcurrentHashMap.newKeySet();
    private final Set<UUID> fallImmune = ConcurrentHashMap.newKeySet(); // Для отмены урона при падении

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
        if (meta != null) {
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                meta.lore(Arrays.stream(lore)
                        .map(l -> Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                        .toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack head(Player target) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(), NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Нажми, чтобы выбрать", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            stack.setItemMeta(meta);
        }
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
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Игрок: " + target.getName()));
        holder.inv = inv;

        // Базовые
        inv.setItem(10, item(Material.ENDER_PEARL, "Телепортироваться к игроку", NamedTextColor.LIGHT_PURPLE));
        inv.setItem(11, item(Material.TNT, "Убить", NamedTextColor.RED));
        inv.setItem(12, item(Material.DIAMOND, "Выдать 1 алмаз", NamedTextColor.AQUA));
        inv.setItem(13, item(Material.LIGHTNING_ROD, "Ударить молнией", NamedTextColor.YELLOW));
        inv.setItem(14, item(Material.FIREWORK_ROCKET, "Подбросить вверх", NamedTextColor.GOLD, "Урон при падении отключён"));

        // Эффекты
        inv.setItem(19, item(Material.COBWEB, "Заморозить (Slowness 255)", NamedTextColor.DARK_AQUA));
        inv.setItem(20, item(Material.ROTTEN_FLESH, "Отравить (Poison)", NamedTextColor.DARK_GREEN));
        inv.setItem(21, item(Material.FEATHER, "Левитация", NamedTextColor.WHITE));
        inv.setItem(22, item(Material.CHEST, "Открыть инвентарь игрока", NamedTextColor.GRAY));
        inv.setItem(23, item(Material.PUFFERFISH, "Случайный эффект", NamedTextColor.DARK_PURPLE));

        // Новые функции
        inv.setItem(28, item(Material.CREEPER_HEAD, "Спавн заряженного крипера", NamedTextColor.GREEN, "Спавнит крипера сразу за игроком"));
        inv.setItem(29, item(Material.BEDROCK, "Заточить в бедроковую коробку", NamedTextColor.DARK_GRAY, "Создаёт клетку 3x3 вокруг игрока"));
        inv.setItem(30, item(Material.DRAGON_EGG, "Убрать чанк над игроком", NamedTextColor.LIGHT_PURPLE, "Удаляет все блоки над игроком (осторожно!)"));

        inv.setItem(49, item(Material.ARROW, "← К списку игроков", NamedTextColor.GRAY));
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
            case 14 -> { // Подброс с защитой от урона
                target.setVelocity(new Vector(0, 1.5, 0));
                fallImmune.add(target.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> fallImmune.remove(target.getUniqueId()), 200L); // 10 сек
                player.sendMessage(Component.text("Игрок подброшен (урон при падении отключён).", NamedTextColor.GREEN));
            }
            case 19 -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 255));
            case 20 -> target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 160, 1));
            case 21 -> target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 1));
            case 22 -> player.openInventory(target.getInventory());
            case 23 -> {
                PotionEffectType[] effects = {PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA,
                        PotionEffectType.WEAKNESS, PotionEffectType.MINING_FATIGUE};
                PotionEffectType random = effects[(int) (Math.random() * effects.length)];
                target.addPotionEffect(new PotionEffect(random, 200, 1));
                player.sendMessage(Component.text("Наложен эффект: " + random.getKey().getKey(), NamedTextColor.GREEN));
            }
            case 28 -> { // Заряженный крипер
                Creeper creeper = (Creeper) target.getWorld().spawnEntity(target.getLocation().add(0, 0, 1), EntityType.CREEPER);
                creeper.setPowered(true);
                player.sendMessage(Component.text("Заряженный крипер заспавнен.", NamedTextColor.GREEN));
            }
            case 29 -> { // Бедроковая коробка
                createBedrockBox(target);
                player.sendMessage(Component.text("Игрок заточён в бедроковую коробку.", NamedTextColor.GREEN));
            }
            case 30 -> { // Удаление чанка над игроком
                removeChunkAbove(target);
                player.sendMessage(Component.text("Чанк над игроком очищен.", NamedTextColor.GREEN));
            }
            case 49 -> openSelector(player, 0);
        }
    }

    private void createBedrockBox(Player target) {
        var world = target.getWorld();
        var loc = target.getLocation();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        // Создаём коробку 3x3x3 из бедрока
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue; // Внутри воздух
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.BEDROCK);
                }
            }
        }
    }

    private void removeChunkAbove(Player target) {
        var world = target.getWorld();
        var loc = target.getLocation();
        int startY = loc.getBlockY() + 1;
        int maxY = world.getMaxHeight();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        // Проходим по области 16x16 над игроком
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int y = startY; y < maxY; y++) {
                    if (world.getBlockAt(x + dx, y, z + dz).getType() != Material.AIR) {
                        world.getBlockAt(x + dx, y, z + dz).setType(Material.AIR);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player p
                && fallImmune.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

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
        fallImmune.remove(e.getPlayer().getUniqueId());
    }
}
