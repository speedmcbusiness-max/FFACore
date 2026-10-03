package me.ffa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Rebuilds anything broken / placed / blown up inside an FFA's radius (crystal pvp friendly). */
public class RegenManager implements Listener {

    private record Entry(BlockState state, long at) {}

    private final FFAPlugin pl;
    private final Map<Location, Entry> pending = new LinkedHashMap<>();

    public RegenManager(FFAPlugin pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 10L, 10L);
    }

    private boolean enabled() { return pl.getConfig().getBoolean("regen.enabled", true); }

    private void record(BlockState s) {
        if (!enabled()) return;
        boolean inside = false;
        for (Arena a : pl.arenas.values()) if (a.contains(s.getLocation())) { inside = true; break; }
        if (!inside) return;
        Entry old = pending.get(s.getLocation());
        pending.put(s.getLocation(), new Entry(old != null ? old.state() : s, System.currentTimeMillis()));
    }

    private void tick() {
        long delay = pl.getConfig().getLong("regen.delay-seconds", 10) * 1000L;
        int max = pl.getConfig().getInt("regen.blocks-per-run", 300);
        long now = System.currentTimeMillis();
        int done = 0;
        for (Iterator<Entry> it = pending.values().iterator(); it.hasNext() && done < max; ) {
            Entry e = it.next();
            if (now - e.at() >= delay) {
                e.state().update(true, false);
                it.remove();
                done++;
            }
        }
    }

    public void restoreAll() {
        for (Entry e : new ArrayList<>(pending.values())) e.state().update(true, false);
        pending.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        record(e.getBlock().getState());
        if (enabled() && pl.getConfig().getBoolean("regen.no-drops", true)) e.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { record(e.getBlockReplacedState()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) { record(e.getBlock().getState()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) record(b.getState());
        if (enabled() && pl.getConfig().getBoolean("regen.no-drops", true)) e.setYield(0f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) record(b.getState());
        if (enabled() && pl.getConfig().getBoolean("regen.no-drops", true)) e.setYield(0f);
    }
}
