package me.ffa;

import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FFACommand implements CommandExecutor, TabCompleter {

    private final FFAPlugin pl;

    public FFACommand(FFAPlugin pl) { this.pl = pl; }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("ffa.admin")) return true;
        pl.msg(s, "no-permission");
        return false;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        pl.msg(s, "players-only");
        return null;
    }

    private boolean validName(String n) { return n.matches("[A-Za-z0-9_-]+"); }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String c = cmd.getName().toLowerCase();
        if (c.equals("ffasetspawn")) { setSpawn(s); return true; }
        if (c.equals("ffaeditor")) { editor(s); return true; }

        if (a.length == 0 || a[0].equalsIgnoreCase("help")) { help(s); return true; }
        Player p;
        switch (a[0].toLowerCase()) {
            case "editor" -> editor(s);
            case "kiteditor", "kits" -> {
                if (!admin(s) || (p = player(s)) == null) return true;
                pl.menus.openKitEditor(p);
            }
            case "setspawn" -> setSpawn(s);
            case "setkit" -> {
                if (!admin(s) || (p = player(s)) == null) return true;
                if (a.length < 2) { usage(s, "/ffa setkit <kit-name>"); return true; }
                if (!validName(a[1])) { pl.msg(s, "invalid-name"); return true; }
                pl.saveKit(p, a[1]);
                pl.msg(s, "kit-saved", "%kit%", a[1]);
            }
            case "delkit" -> {
                if (!admin(s)) return true;
                if (a.length < 2) { usage(s, "/ffa delkit <kit-name>"); return true; }
                String k = pl.findKit(a[1]);
                if (k == null) { pl.msg(s, "unknown-kit", "%kit%", a[1]); return true; }
                pl.deleteKit(k);
                pl.msg(s, "kit-deleted", "%kit%", k);
            }
            case "spawnset" -> {
                if (!admin(s) || (p = player(s)) == null) return true;
                if (a.length < 2) { usage(s, "/ffa spawnset <ffa-name>"); return true; }
                if (!validName(a[1])) { pl.msg(s, "invalid-name"); return true; }
                Location loc = p.getLocation();
                Arena ar = pl.arena(a[1]);
                if (ar == null) {
                    ar = new Arena(a[1], loc, pl.getConfig().getInt("regen.default-radius", 60));
                    pl.arenas.put(a[1].toLowerCase(), ar);
                } else ar.spawn = loc;
                pl.saveData();
                pl.msg(s, "ffa-spawn-set", "%arena%", ar.name);
            }
            case "setradius" -> {
                if (!admin(s)) return true;
                if (a.length < 3) { usage(s, "/ffa setradius <ffa-name> <blocks>"); return true; }
                Arena ar = pl.arena(a[1]);
                if (ar == null) { pl.msg(s, "unknown-arena", "%arena%", a[1]); return true; }
                try { ar.radius = Math.max(1, Integer.parseInt(a[2])); }
                catch (NumberFormatException ex) { usage(s, "/ffa setradius <ffa-name> <blocks>"); return true; }
                pl.saveData();
                pl.msg(s, "radius-set", "%arena%", ar.name, "%radius%", String.valueOf(ar.radius));
            }
            case "delete" -> {
                if (!admin(s)) return true;
                if (a.length < 2) { usage(s, "/ffa delete <ffa-name>"); return true; }
                Arena ar = pl.arenas.remove(a[1].toLowerCase());
                if (ar == null) { pl.msg(s, "unknown-arena", "%arena%", a[1]); return true; }
                pl.saveData();
                pl.msg(s, "arena-deleted", "%arena%", ar.name);
            }
            case "item" -> {
                if (!admin(s) || (p = player(s)) == null) return true;
                p.getInventory().addItem(pl.ffaItem());
                pl.msg(s, "item-given");
            }
            case "join" -> {
                if ((p = player(s)) == null) return true;
                if (a.length < 2) { pl.menus.openSelector(p); return true; }
                pl.join(p, a[1]);
            }
            case "leave" -> {
                if ((p = player(s)) == null) return true;
                if (!pl.players.containsKey(p.getUniqueId())) { pl.msg(s, "not-in-ffa"); return true; }
                if (pl.getConfig().getBoolean("combat.block-leave", true) && pl.combat.isFighting(p.getUniqueId())) {
                    pl.msg(s, "cant-leave");
                    return true;
                }
                pl.toLobby(p);
                pl.msg(s, "left");
            }
            case "list" -> s.sendMessage(FFAPlugin.color("&7FFAs: &f" + String.join("&7, &f",
                    pl.arenas.values().stream().map(x -> x.name).toList())));
            case "reload" -> {
                if (!admin(s)) return true;
                pl.loadAll();
                pl.msg(s, "reloaded");
            }
            default -> help(s);
        }
        return true;
    }

    private void editor(CommandSender s) {
        Player p;
        if (!admin(s) || (p = player(s)) == null) return;
        pl.menus.openEditor(p);
    }

    private void setSpawn(CommandSender s) {
        Player p;
        if (!admin(s) || (p = player(s)) == null) return;
        pl.setLobby(p.getLocation());
        pl.msg(s, "spawn-set");
    }

    private void usage(CommandSender s, String u) { pl.msg(s, "usage", "%usage%", u); }

    private void help(CommandSender s) {
        s.sendMessage(FFAPlugin.color("&7&m      &r &7FFA &7&m      "));
        String[] l = {
                "&f/ffa join [ffa] &7- join an FFA", "&f/ffa leave &7- leave the FFA", "&f/ffa list",
                "&e/ffasetspawn &7- set main spawn (death/leave)", "&e/ffa setkit <kit> &7- save your inventory as a kit",
                "&e/ffa delkit <kit>", "&e/ffa kiteditor &7- load/delete kits",
                "&e/ffa spawnset <ffa> &7- create FFA / set its spawn",
                "&e/ffa setradius <ffa> <n> &7- rebuild radius", "&e/ffa delete <ffa>",
                "&e/ffaeditor &7(or /ffa editor) - link kits to FFAs", "&e/ffa item &7- get the FFA item", "&e/ffa reload"};
        for (String x : l) s.sendMessage(FFAPlugin.color(x));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!cmd.getName().equalsIgnoreCase("ffa")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("join", "leave", "list"));
            if (s.hasPermission("ffa.admin"))
                out.addAll(List.of("setkit", "delkit", "kiteditor", "spawnset", "setradius", "delete", "editor", "setspawn", "item", "reload"));
        } else if (a.length == 2) {
            switch (a[0].toLowerCase()) {
                case "join", "spawnset", "setradius", "delete" -> pl.arenas.values().forEach(x -> out.add(x.name));
                case "setkit", "delkit" -> out.addAll(pl.kitNames());
            }
        }
        return out.stream().filter(x -> x.toLowerCase().startsWith(a[a.length - 1].toLowerCase())).collect(Collectors.toList());
    }
}
