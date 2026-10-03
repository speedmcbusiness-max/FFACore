package me.ffa;

import org.bukkit.Location;

public class Arena {
    public final String name;
    public Location spawn;
    public String kit;
    public int radius;

    public Arena(String name, Location spawn, int radius) {
        this.name = name;
        this.spawn = spawn;
        this.radius = radius;
    }

    public boolean contains(Location l) {
        return l.getWorld() != null && spawn.getWorld() != null
                && l.getWorld().equals(spawn.getWorld())
                && l.distanceSquared(spawn) <= (double) radius * radius;
    }
}
