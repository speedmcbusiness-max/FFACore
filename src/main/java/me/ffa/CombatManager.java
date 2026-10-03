package me.ffa;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Anti free-hit: two players who fight are locked to each other, nobody else can hit them. */
public class CombatManager implements Listener {

    private record Lock(UUID opponent, long until) {}

    private final FFAPlugin pl;
    private final Map<UUID, Lock> locks = new HashMap<>();
    private final NamespacedKey ownerKey;

    public CombatManager(FFAPlugin pl) {
        this.pl = pl;
        this.ownerKey = new NamespacedKey(pl, "crystal_owner");
    }

    private Lock active(UUID id) {
        Lock l = locks.get(id);
        if (l == null) return null;
        if (System.currentTimeMillis() > l.until() || Bukkit.getPlayer(l.opponent()) == null) {
            locks.remove(id);
            return null;
        }
        return l;
    }

    public boolean isFighting(UUID id) { return active(id) != null; }

    public void clear(UUID id) {
        Lock l = locks.remove(id);
        if (l != null) {
            Lock o = locks.get(l.opponent());
            if (o != null && o.opponent().equals(id)) locks.remove(l.opponent());
        }
    }

    public void quit(Player p) {
        if (isFighting(p.getUniqueId()) && pl.getConfig().getBoolean("combat.kill-on-quit", true)) p.setHealth(0);
        clear(p.getUniqueId());
    }

    private Player resolve(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        if (d instanceof EnderCrystal c) {
            String s = c.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (s != null) {
                try { return Bukkit.getPlayer(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    private void bar(Player p, String key, String... rep) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(pl.text(key, rep)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        FileConfiguration c = pl.getConfig();
        if (!c.getBoolean("combat.enabled", true)) return;

        // remember who touched a crystal so its explosion can be attributed
        if (e.getEntity() instanceof EnderCrystal cr) {
            Player owner = resolve(e.getDamager());
            if (owner != null)
                cr.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            return;
        }
        if (!(e.getEntity() instanceof Player victim)) return;
        Player att = resolve(e.getDamager());
        if (att == null || att.equals(victim)) return;

        if (c.getBoolean("combat.only-in-ffa", true)
                && !pl.players.containsKey(att.getUniqueId()) && !pl.players.containsKey(victim.getUniqueId())) return;

        Lock vl = active(victim.getUniqueId());
        Lock al = active(att.getUniqueId());

        if (vl != null && !vl.opponent().equals(att.getUniqueId()) && c.getBoolean("combat.block-third-party", true)) {
            e.setCancelled(true);
            bar(att, "victim-busy", "%player%", victim.getName());
            return;
        }
        if (al != null && !al.opponent().equals(victim.getUniqueId()) && c.getBoolean("combat.block-switching", true)) {
            e.setCancelled(true);
            bar(att, "attacker-busy");
            return;
        }

        long until = System.currentTimeMillis() + c.getLong("combat.duration-seconds", 15) * 1000L;
        boolean fresh = vl == null && al == null;
        locks.put(att.getUniqueId(), new Lock(victim.getUniqueId(), until));
        locks.put(victim.getUniqueId(), new Lock(att.getUniqueId(), until));
        if (fresh) {
            bar(att, "fight-started", "%player%", victim.getName());
            bar(victim, "fight-started", "%player%", att.getName());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        clear(e.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!isFighting(e.getPlayer().getUniqueId())) return;
        String first = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        for (String b : pl.getConfig().getStringList("combat.blocked-commands")) {
            if (first.equals(b.toLowerCase())) {
                e.setCancelled(true);
                pl.msg(e.getPlayer(), "cant-command");
                return;
            }
        }
    }
}
