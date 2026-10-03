package me.ffa;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class Menus {

    public enum Type { SELECTOR, KIT_EDITOR, EDITOR, KIT_PICK }

    public static class Holder implements InventoryHolder {
        public final Type type;
        public final String data;
        Inventory inv;
        Holder(Type t, String d) { type = t; data = d; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final FFAPlugin pl;

    public Menus(FFAPlugin pl) { this.pl = pl; }

    private Inventory make(Type t, String data, String title, int count) {
        Holder h = new Holder(t, data);
        int size = Math.min(54, Math.max(9, ((count - 1) / 9 + 1) * 9));
        Inventory inv = Bukkit.createInventory(h, size, FFAPlugin.color(title));
        h.inv = inv;
        return inv;
    }

    private ItemStack icon(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(FFAPlugin.color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(FFAPlugin.color(s));
        im.setLore(l);
        it.setItemMeta(im);
        return it;
    }

    private Material mat(String path, Material def) {
        Material m = Material.matchMaterial(pl.getConfig().getString(path, def.name()));
        return m == null ? def : m;
    }

    private String title(String key) { return pl.getConfig().getString("gui." + key, key); }

    /** Player-facing FFA list (opened by the gold sword). */
    public void openSelector(Player p) {
        Inventory inv = make(Type.SELECTOR, null, title("selector-title"), pl.arenas.size());
        int i = 0;
        for (Arena a : pl.arenas.values()) {
            if (i >= 54) break;
            long n = pl.players.values().stream().filter(v -> v.equalsIgnoreCase(a.name)).count();
            inv.setItem(i++, icon(mat("gui.arena-icon", Material.DIAMOND_SWORD), "&e" + a.name,
                    "&7Kit: &f" + (a.kit == null ? "none" : a.kit), "&7Playing: &f" + n, "", "&aClick to join"));
        }
        p.openInventory(inv);
    }

    /** /ffa kiteditor: load / delete kits. */
    public void openKitEditor(Player p) {
        List<String> kits = pl.kitNames();
        Inventory inv = make(Type.KIT_EDITOR, null, title("kit-editor-title"), kits.size());
        int i = 0;
        for (String k : kits) {
            if (i >= 54) break;
            inv.setItem(i++, icon(mat("gui.kit-icon", Material.CHEST), "&b" + k,
                    "&aLeft-click: &7load into your inventory", "&cRight-click: &7delete kit"));
        }
        p.openInventory(inv);
    }

    /** /ffaeditor: choose an FFA to connect a kit to. */
    public void openEditor(Player p) {
        Inventory inv = make(Type.EDITOR, null, title("editor-title"), pl.arenas.size());
        int i = 0;
        for (Arena a : pl.arenas.values()) {
            if (i >= 54) break;
            inv.setItem(i++, icon(mat("gui.arena-icon", Material.DIAMOND_SWORD), "&e" + a.name,
                    "&7Kit: &f" + (a.kit == null ? "none" : a.kit), "", "&eClick to change kit"));
        }
        p.openInventory(inv);
    }

    public void openKitPick(Player p, String arena) {
        List<String> kits = pl.kitNames();
        Inventory inv = make(Type.KIT_PICK, arena, title("kit-pick-title").replace("%arena%", arena), kits.size());
        int i = 0;
        for (String k : kits) {
            if (i >= 54) break;
            inv.setItem(i++, icon(mat("gui.kit-icon", Material.CHEST), "&b" + k, "&aClick to link to &f" + arena));
        }
        p.openInventory(inv);
    }
}
