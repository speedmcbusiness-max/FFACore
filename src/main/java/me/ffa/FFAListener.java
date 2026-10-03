package me.ffa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FFAListener implements Listener {

    private final FFAPlugin pl;
    private final Set<UUID> dead = new HashSet<>();

    public FFAListener(FFAPlugin pl) { this.pl = pl; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        FileConfiguration c = pl.getConfig();
        if (c.getBoolean("join.teleport-to-spawn", true)) p.teleport(pl.lobby());
        if (c.getBoolean("join.give-item", true)) pl.giveItem(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        pl.combat.quit(p);                                   // combat log = death
        if (pl.players.remove(p.getUniqueId()) != null) p.getInventory().clear(); // kit items are not theirs
        dead.remove(p.getUniqueId());
    }

    // ---- death: clear items, send to spawn ----
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!pl.players.containsKey(p.getUniqueId())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepInventory(false);
        e.setKeepLevel(false);
        dead.add(p.getUniqueId());
        if (pl.getConfig().getBoolean("death.instant-respawn", true))
            Bukkit.getScheduler().runTask(pl, () -> { if (p.isDead()) p.spigot().respawn(); });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!dead.remove(p.getUniqueId())) return;
        pl.players.remove(p.getUniqueId());
        e.setRespawnLocation(pl.lobby());
        Bukkit.getScheduler().runTask(pl, () -> pl.toLobby(p));
    }

    // ---- FFA item ----
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!pl.isFfaItem(e.getItem())) return;
        e.setCancelled(true);
        pl.menus.openSelector(e.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (pl.isFfaItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    // ---- GUIs ----
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menus.Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menus.Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta() || !it.getItemMeta().hasDisplayName()) return;
        String name = ChatColor.stripColor(it.getItemMeta().getDisplayName());

        switch (h.type) {
            case SELECTOR -> {
                p.closeInventory();
                pl.join(p, name);
            }
            case KIT_EDITOR -> {
                if (!p.hasPermission("ffa.admin")) return;
                if (e.isRightClick()) {
                    pl.deleteKit(name);
                    pl.msg(p, "kit-deleted", "%kit%", name);
                    pl.menus.openKitEditor(p);
                } else {
                    p.closeInventory();
                    pl.giveKit(p, name);
                    pl.msg(p, "kit-loaded", "%kit%", name);
                }
            }
            case EDITOR -> {
                if (!p.hasPermission("ffa.admin")) return;
                pl.menus.openKitPick(p, name);
            }
            case KIT_PICK -> {
                if (!p.hasPermission("ffa.admin")) return;
                Arena a = pl.arena(h.data);
                if (a == null) { p.closeInventory(); return; }
                a.kit = name;
                pl.saveData();
                pl.msg(p, "kit-linked", "%arena%", a.name, "%kit%", name);
                pl.menus.openEditor(p);
            }
        }
    }
}
