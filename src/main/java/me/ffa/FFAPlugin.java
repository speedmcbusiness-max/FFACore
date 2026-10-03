package me.ffa;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FFAPlugin extends JavaPlugin {

    public NamespacedKey itemKey;
    public final Map<String, Arena> arenas = new LinkedHashMap<>();   // key = lowercase name
    public final Map<UUID, String> players = new HashMap<>();         // player -> arena name
    public CombatManager combat;
    public RegenManager regen;
    public Menus menus;

    private File kitFile, dataFile;
    private YamlConfiguration kits, data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "ffa_item");
        loadAll();
        combat = new CombatManager(this);
        regen = new RegenManager(this);
        menus = new Menus(this);
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(combat, this);
        pm.registerEvents(regen, this);
        pm.registerEvents(new FFAListener(this), this);
        FFACommand cmd = new FFACommand(this);
        for (String n : List.of("ffa", "ffasetspawn", "ffaeditor")) {
            getCommand(n).setExecutor(cmd);
            getCommand(n).setTabCompleter(cmd);
        }
    }

    @Override
    public void onDisable() {
        if (regen != null) regen.restoreAll();
    }

    // ---------- util ----------
    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public String text(String key, String... rep) {
        String m = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < rep.length; i += 2) m = m.replace(rep[i], rep[i + 1]);
        return color(m);
    }

    public void msg(CommandSender s, String key, String... rep) {
        s.sendMessage(color(getConfig().getString("messages.prefix", "")) + text(key, rep));
    }

    // ---------- storage ----------
    public void loadAll() {
        reloadConfig();
        kitFile = new File(getDataFolder(), "kits.yml");
        dataFile = new File(getDataFolder(), "data.yml");
        kits = YamlConfiguration.loadConfiguration(kitFile);
        data = YamlConfiguration.loadConfiguration(dataFile);
        arenas.clear();
        ConfigurationSection s = data.getConfigurationSection("arenas");
        if (s != null) {
            for (String n : s.getKeys(false)) {
                Location l = s.getLocation(n + ".spawn");
                if (l == null || l.getWorld() == null) continue;
                Arena a = new Arena(n, l, s.getInt(n + ".radius", getConfig().getInt("regen.default-radius", 60)));
                a.kit = s.getString(n + ".kit");
                arenas.put(n.toLowerCase(), a);
            }
        }
    }

    public void saveData() {
        data.set("arenas", null);
        for (Arena a : arenas.values()) {
            data.set("arenas." + a.name + ".spawn", a.spawn);
            data.set("arenas." + a.name + ".kit", a.kit);
            data.set("arenas." + a.name + ".radius", a.radius);
        }
        try { data.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void saveKits() {
        try { kits.save(kitFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // ---------- arenas ----------
    public Arena arena(String name) {
        return name == null ? null : arenas.get(name.toLowerCase());
    }

    public void join(Player p, String name) {
        Arena a = arena(name);
        if (a == null) { msg(p, "unknown-arena", "%arena%", name); return; }
        if (a.kit == null || findKit(a.kit) == null) { msg(p, "arena-no-kit", "%arena%", a.name); return; }
        players.put(p.getUniqueId(), a.name);
        combat.clear(p.getUniqueId());
        reset(p);
        giveKit(p, findKit(a.kit));
        p.teleport(a.spawn);
        msg(p, "joined", "%arena%", a.name);
    }

    public void toLobby(Player p) {
        players.remove(p.getUniqueId());
        combat.clear(p.getUniqueId());
        p.getInventory().clear();
        reset(p);
        p.teleport(lobby());
        giveItem(p);
    }

    public Location lobby() {
        Location l = data.getLocation("spawn");
        if (l != null && l.getWorld() != null) return l;
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void setLobby(Location l) {
        data.set("spawn", l);
        try { data.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // ---------- kits ----------
    public List<String> kitNames() {
        ConfigurationSection s = kits.getConfigurationSection("kits");
        return s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false));
    }

    public String findKit(String name) {
        if (name == null) return null;
        for (String k : kitNames()) if (k.equalsIgnoreCase(name)) return k;
        return null;
    }

    public void saveKit(Player p, String name) {
        String existing = findKit(name);
        String path = "kits." + (existing != null ? existing : name);
        PlayerInventory inv = p.getInventory();
        kits.set(path + ".contents", Arrays.asList(inv.getStorageContents()));
        kits.set(path + ".armor", Arrays.asList(inv.getArmorContents()));
        kits.set(path + ".offhand", inv.getItemInOffHand());
        saveKits();
    }

    public void deleteKit(String name) {
        kits.set("kits." + name, null);
        saveKits();
    }

    public void giveKit(Player p, String kit) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        String path = "kits." + kit;
        inv.setStorageContents(read(path + ".contents", 36));
        inv.setArmorContents(read(path + ".armor", 4));
        ItemStack off = kits.getItemStack(path + ".offhand");
        inv.setItemInOffHand(off == null ? new ItemStack(Material.AIR) : off);
    }

    private ItemStack[] read(String path, int size) {
        ItemStack[] arr = new ItemStack[size];
        List<?> l = kits.getList(path);
        if (l != null)
            for (int i = 0; i < Math.min(size, l.size()); i++)
                arr[i] = l.get(i) instanceof ItemStack s ? s : null;
        return arr;
    }

    @SuppressWarnings("deprecation")
    public void reset(Player p) {
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
    }

    // ---------- FFA item ----------
    public ItemStack ffaItem() {
        FileConfiguration c = getConfig();
        Material m = Material.matchMaterial(c.getString("ffa-item.material", "GOLDEN_SWORD"));
        if (m == null) m = Material.GOLDEN_SWORD;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(c.getString("ffa-item.name", "&7FFA")));
        List<String> lore = new ArrayList<>();
        for (String s : c.getStringList("ffa-item.lore")) lore.add(color(s));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isFfaItem(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public void giveItem(Player p) {
        if (!getConfig().getBoolean("ffa-item.enabled", true)) return;
        for (ItemStack s : p.getInventory().getContents()) if (isFfaItem(s)) return;
        p.getInventory().setItem(getConfig().getInt("ffa-item.slot", 4), ffaItem());
    }
}
