package com.example.helloplugin;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class HelloPlugin extends JavaPlugin implements Listener {
    private static final String DATA_SECTION = "players";
    private static final String KEY_TOTAL_SECONDS = "totalSeconds";
    private static final String KEY_LAST_SEEN = "lastSeen";
    private static final String KEY_LAST_NAME = "lastName";
    private static final String BAN_SECTION = "bans";
    private static final String BAN_REASON = "reason";
    private static final String BAN_EXPIRES_AT = "expiresAt";
    private static final String IP_SECTION = "ips";
    private static final String IP_PLAYERS = "players";
    private static final String REGION_SECTION = "regions";
    private static final String REGION_OWNER_UUID = "ownerUuid";
    private static final String REGION_OWNER_NAME = "ownerName";
    private static final String REGION_HOME = "home";
    private static final String REGION_WORLD = "world";

    private final Map<UUID, Long> sessionStartMillis = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        getServer().getPluginManager().registerEvents(this, this);
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().warning("WorldGuard nem található. A levédés parancsok nem fognak működni.");
        }
        getLogger().info("HelloPlugin enabled.");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            trackSessionEnd(player.getUniqueId());
        }
        saveData();
        getLogger().info("HelloPlugin disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        sessionStartMillis.put(player.getUniqueId(), System.currentTimeMillis());
        updatePlayerName(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        trackSessionEnd(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        BanEntry banEntry = getBanEntry(player.getUniqueId());
        if (banEntry == null) {
            trackPlayerIp(player);
            return;
        }
        if (banEntry.expiresAt != null && banEntry.expiresAt.isBefore(Instant.now())) {
            clearBan(player.getUniqueId());
            trackPlayerIp(player);
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append(ChatColor.RED).append("Ki vagy tiltva a szerverről.");
        if (banEntry.reason != null && !banEntry.reason.isBlank()) {
            message.append("\n").append(ChatColor.YELLOW).append("Indok: ").append(banEntry.reason);
        }
        if (banEntry.expiresAt != null) {
            message.append("\n").append(ChatColor.GOLD)
                    .append("Hátralévő idő: ")
                    .append(formatDuration(Duration.between(Instant.now(), banEntry.expiresAt)));
        }
        event.disallow(PlayerLoginEvent.Result.KICK_BANNED, message.toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("hello")) {
            handleHello(sender);
            return true;
        }
        if (command.getName().equalsIgnoreCase("online")) {
            handleOnline(sender);
            return true;
        }
        if (command.getName().equalsIgnoreCase("ora")) {
            handleOra(sender, args);
            return true;
        }
        if (command.getName().equalsIgnoreCase("ban")) {
            handleBan(sender, args);
            return true;
        }
        if (command.getName().equalsIgnoreCase("tempban")) {
            handleTempBan(sender, args);
            return true;
        }
        if (command.getName().equalsIgnoreCase("jatekos")) {
            handleJatekos(sender, args);
            return true;
        }
        if (command.getName().equalsIgnoreCase("leved")) {
            handleLeved(sender);
            return true;
        }
        if (command.getName().equalsIgnoreCase("torles")) {
            handleTorles(sender);
            return true;
        }
        if (command.getName().equalsIgnoreCase("setotthon")) {
            handleSetOtthon(sender);
            return true;
        }
        if (command.getName().equalsIgnoreCase("otthon")) {
            handleOtthon(sender, args);
            return true;
        }
        if (command.getName().equalsIgnoreCase("barat")) {
            handleBarat(sender, args);
            return true;
        }
        return false;
    }

    private void handleHello(CommandSender sender) {
        String message = ChatColor.GREEN + "Szia! Köszönöm, hogy használod a szervert.";
        if (sender instanceof Player) {
            sender.sendMessage(message);
        } else {
            sender.sendMessage("[HelloPlugin] " + ChatColor.stripColor(message));
        }
    }

    private void handleOnline(CommandSender sender) {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Jelenleg nincs online játékos.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Online játékosok és eltöltött idő:");
        for (Player player : Bukkit.getOnlinePlayers()) {
            long totalSeconds = getTotalSeconds(player.getUniqueId())
                    + getCurrentSessionSeconds(player.getUniqueId());
            sender.sendMessage(ChatColor.AQUA + "- " + player.getName() + ": "
                    + ChatColor.WHITE + formatHours(totalSeconds));
        }
    }

    private void handleOra(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Használat: /ora <játékosnév>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID uuid = target.getUniqueId();
        long totalSeconds = getTotalSeconds(uuid);
        if (target.isOnline()) {
            totalSeconds += getCurrentSessionSeconds(uuid);
            sender.sendMessage(ChatColor.GREEN + target.getName() + " eddig "
                    + formatHours(totalSeconds) + " órát töltött a szerveren.");
            return;
        }
        Long lastSeen = getLastSeen(uuid);
        if (lastSeen == null) {
            sender.sendMessage(ChatColor.YELLOW + "Nincs adat erről a játékosról: " + args[0]);
            return;
        }
        sender.sendMessage(ChatColor.GREEN + target.getName() + " eddig "
                + formatHours(totalSeconds) + " órát töltött a szerveren.");
        sender.sendMessage(ChatColor.GRAY + "Utoljára ekkor volt fent: "
                + dateTimeFormatter.format(Instant.ofEpochMilli(lastSeen)));
    }

    private void handleBan(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Használat: /ban <játékosnév> [indok]");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String reason = args.length > 1 ? joinArgs(args, 1) : "";
        setBan(target.getUniqueId(), reason, null);
        sender.sendMessage(ChatColor.GREEN + target.getName() + " kitiltva.");
        if (target.isOnline()) {
            Player online = (Player) target;
            String message = ChatColor.RED + "Ki vagy tiltva a szerverről.";
            if (!reason.isBlank()) {
                message += "\n" + ChatColor.YELLOW + "Indok: " + reason;
            }
            online.kickPlayer(message);
        }
    }

    private void handleTempBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Használat: /tempban <játékosnév> <idő> [indok]");
            sender.sendMessage(ChatColor.GRAY + "Példa: /tempban Notch 2h Spam");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        Duration duration = parseDuration(args[1]);
        if (duration == null || duration.isZero() || duration.isNegative()) {
            sender.sendMessage(ChatColor.RED + "Érvénytelen idő formátum. Példa: 30m, 2h, 7d, 1mo");
            return;
        }
        String reason = args.length > 2 ? joinArgs(args, 2) : "";
        Instant expiresAt = Instant.now().plus(duration);
        setBan(target.getUniqueId(), reason, expiresAt);
        sender.sendMessage(ChatColor.GREEN + target.getName() + " kitiltva "
                + formatDuration(duration) + " időre.");
        if (target.isOnline()) {
            Player online = (Player) target;
            String message = ChatColor.RED + "Ki vagy tiltva a szerverről.";
            if (!reason.isBlank()) {
                message += "\n" + ChatColor.YELLOW + "Indok: " + reason;
            }
            message += "\n" + ChatColor.GOLD + "Hátralévő idő: " + formatDuration(duration);
            online.kickPlayer(message);
        }
    }

    private void handleJatekos(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Használat: /jatekos <játékosnév>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID uuid = target.getUniqueId();
        String lastKnownName = getLastKnownName(uuid);
        if (lastKnownName == null) {
            sender.sendMessage(ChatColor.YELLOW + "Nincs adat erről a játékosról: " + args[0]);
            return;
        }
        String ip = getPlayerIp(uuid);
        if (ip == null || ip.isBlank()) {
            sender.sendMessage(ChatColor.YELLOW + "Ehhez a játékoshoz nincs IP adat eltárolva.");
            return;
        }
        List<String> playerIds = dataConfig.getStringList(IP_SECTION + "." + ip + "." + IP_PLAYERS);
        if (playerIds.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Nem találtam másik karaktert ehhez az IP-hez.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Az alábbi karakterek játszottak erről az IP-ről: " + ip);
        for (String playerId : playerIds) {
            UUID playerUuid;
            try {
                playerUuid = UUID.fromString(playerId);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String name = getLastKnownName(playerUuid);
            if (name == null) {
                name = playerId;
            }
            BanStatus status = getBanStatus(playerUuid);
            String statusText = status == BanStatus.PERMANENT
                    ? ChatColor.RED + "BAN"
                    : status == BanStatus.TEMPORARY
                            ? ChatColor.GOLD + "TEMPBAN"
                            : ChatColor.GREEN + "NINCS BAN";
            sender.sendMessage(ChatColor.AQUA + "- " + name + " " + statusText);
        }
    }

    private void handleLeved(CommandSender sender) {
        if (!ensureWorldGuard(sender)) {
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Csak játékos használhatja ezt a parancsot.");
            return;
        }
        Player player = (Player) sender;
        int maxRegions = getConfig().getInt("region.max-per-player", 1);
        int ownedRegions = countOwnedRegions(player.getUniqueId());
        if (ownedRegions >= maxRegions) {
            sender.sendMessage(ChatColor.RED + "Elérted a maximális levédések számát: " + maxRegions);
            return;
        }
        RegionManager manager = getRegionManager(player.getWorld());
        if (manager == null) {
            sender.sendMessage(ChatColor.RED + "Nem található WorldGuard régiókezelő ehhez a világhoz.");
            return;
        }
        int radius = getConfig().getInt("region.radius", 10);
        BlockVector3 center = BukkitAdapter.asBlockVector(player.getLocation());
        int minY = player.getWorld().getMinHeight();
        int maxY = player.getWorld().getMaxHeight() - 1;
        BlockVector3 min = BlockVector3.at(center.getX() - radius, minY, center.getZ() - radius);
        BlockVector3 max = BlockVector3.at(center.getX() + radius, maxY, center.getZ() + radius);
        String regionId = buildRegionId(player.getName(), ownedRegions + 1);
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);
        if (isOverlapping(manager, region)) {
            sender.sendMessage(ChatColor.RED + "Nem lehetséges mert bele lóg egy másik területbe.");
            return;
        }
        region.getOwners().addPlayer(player.getUniqueId());
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);
        region.setFlag(Flags.BUILD.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);
        manager.addRegion(region);
        saveRegionMetadata(regionId, player, player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Terület levédve: " + regionId);
    }

    private void handleTorles(CommandSender sender) {
        if (!ensureWorldGuard(sender)) {
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Csak játékos használhatja ezt a parancsot.");
            return;
        }
        Player player = (Player) sender;
        RegionManager manager = getRegionManager(player.getWorld());
        if (manager == null) {
            sender.sendMessage(ChatColor.RED + "Nem található WorldGuard régiókezelő ehhez a világhoz.");
            return;
        }
        ProtectedRegion region = findOwnedRegionAt(player, manager);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Nem állsz a saját levédett területeden.");
            return;
        }
        manager.removeRegion(region.getId());
        dataConfig.set(REGION_SECTION + "." + region.getId(), null);
        saveData();
        sender.sendMessage(ChatColor.GREEN + "Terület törölve: " + region.getId());
    }

    private void handleSetOtthon(CommandSender sender) {
        if (!ensureWorldGuard(sender)) {
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Csak játékos használhatja ezt a parancsot.");
            return;
        }
        Player player = (Player) sender;
        RegionManager manager = getRegionManager(player.getWorld());
        if (manager == null) {
            sender.sendMessage(ChatColor.RED + "Nem található WorldGuard régiókezelő ehhez a világhoz.");
            return;
        }
        ProtectedRegion region = findOwnedRegionAt(player, manager);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Az otthon csak a saját területen állítható be.");
            return;
        }
        saveRegionHome(region.getId(), player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Otthon beállítva a területen.");
    }

    private void handleOtthon(CommandSender sender, String[] args) {
        if (!ensureWorldGuard(sender)) {
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Csak játékos használhatja ezt a parancsot.");
            return;
        }
        Player player = (Player) sender;
        String regionId;
        if (args.length > 0) {
            regionId = findFriendRegionId(player.getUniqueId(), args[0]);
            if (regionId == null) {
                sender.sendMessage(ChatColor.RED + "Nem találtam ilyen barát területet: " + args[0]);
                return;
            }
        } else {
            regionId = findOwnedRegionId(player.getUniqueId(), player.getLocation());
            if (regionId == null) {
                sender.sendMessage(ChatColor.RED + "Nincs saját levédett területed.");
                return;
            }
        }
        Location home = getRegionHome(regionId);
        if (home == null) {
            sender.sendMessage(ChatColor.RED + "Ehhez a területhez nincs otthon beállítva.");
            return;
        }
        player.teleport(home);
        sender.sendMessage(ChatColor.GREEN + "Teleportálás kész: " + regionId);
    }

    private void handleBarat(CommandSender sender, String[] args) {
        if (!ensureWorldGuard(sender)) {
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Csak játékos használhatja ezt a parancsot.");
            return;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            listFriendRegions(player);
            return;
        }
        if (!args[0].equalsIgnoreCase("hozzaadas")) {
            sender.sendMessage(ChatColor.RED + "Használat: /barat hozzaadas <név>");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Használat: /barat hozzaadas <név>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        RegionManager manager = getRegionManager(player.getWorld());
        if (manager == null) {
            sender.sendMessage(ChatColor.RED + "Nem található WorldGuard régiókezelő ehhez a világhoz.");
            return;
        }
        ProtectedRegion region = findOwnedRegionAt(player, manager);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Csak a saját területeden adhatsz hozzá barátot.");
            return;
        }
        region.getMembers().addPlayer(target.getUniqueId());
        if (target.getName() != null) {
            updatePlayerName(target.getUniqueId(), target.getName());
        }
        sender.sendMessage(ChatColor.GREEN + target.getName() + " hozzáadva a területhez.");
    }

    private void listFriendRegions(Player player) {
        List<String> regions = new ArrayList<>();
        UUID uuid = player.getUniqueId();
        for (World world : Bukkit.getWorlds()) {
            RegionManager manager = getRegionManager(world);
            if (manager == null) {
                continue;
            }
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (region.getId().equalsIgnoreCase("__global__")) {
                    continue;
                }
                if (region.isMember(uuid) && !region.isOwner(uuid)) {
                    String ownerName = dataConfig.getString(REGION_SECTION + "." + region.getId() + "." + REGION_OWNER_NAME);
                    if (ownerName == null) {
                        ownerName = region.getId();
                    }
                    regions.add(ownerName + " (" + region.getId() + ")");
                }
            }
        }
        if (regions.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Nem vagy hozzáadva egyetlen területhez sem.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "Hozzáadott területek:");
        for (String entry : regions) {
            player.sendMessage(ChatColor.AQUA + "- " + entry);
        }
    }

    private void trackPlayerIp(Player player) {
        InetAddress address = player.getAddress() == null ? null : player.getAddress().getAddress();
        if (address == null) {
            return;
        }
        String ip = address.getHostAddress();
        String uuid = player.getUniqueId().toString();
        String basePath = IP_SECTION + "." + ip + "." + IP_PLAYERS;
        List<String> knownPlayers = dataConfig.getStringList(basePath);
        if (!knownPlayers.contains(uuid)) {
            knownPlayers.add(uuid);
            dataConfig.set(basePath, knownPlayers);
            saveData();
        }
        dataConfig.set(DATA_SECTION + "." + uuid + ".lastIp", ip);
        saveData();
    }

    private void updatePlayerName(UUID uuid, String name) {
        String basePath = DATA_SECTION + "." + uuid + "." + KEY_LAST_NAME;
        dataConfig.set(basePath, name);
        saveData();
    }

    private String getLastKnownName(UUID uuid) {
        return dataConfig.getString(DATA_SECTION + "." + uuid + "." + KEY_LAST_NAME, null);
    }

    private String getPlayerIp(UUID uuid) {
        return dataConfig.getString(DATA_SECTION + "." + uuid + ".lastIp", null);
    }

    private BanStatus getBanStatus(UUID uuid) {
        BanEntry entry = getBanEntry(uuid);
        if (entry == null) {
            return BanStatus.NONE;
        }
        if (entry.expiresAt != null && entry.expiresAt.isBefore(Instant.now())) {
            clearBan(uuid);
            return BanStatus.NONE;
        }
        return entry.expiresAt == null ? BanStatus.PERMANENT : BanStatus.TEMPORARY;
    }

    private long getCurrentSessionSeconds(UUID uuid) {
        Long start = sessionStartMillis.get(uuid);
        if (start == null) {
            return 0L;
        }
        return Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
    }

    private long getTotalSeconds(UUID uuid) {
        String basePath = DATA_SECTION + "." + uuid;
        return dataConfig.getLong(basePath + "." + KEY_TOTAL_SECONDS, 0L);
    }

    private Long getLastSeen(UUID uuid) {
        String basePath = DATA_SECTION + "." + uuid;
        if (!dataConfig.contains(basePath + "." + KEY_LAST_SEEN)) {
            return null;
        }
        return dataConfig.getLong(basePath + "." + KEY_LAST_SEEN);
    }

    private void trackSessionEnd(UUID uuid) {
        Long start = sessionStartMillis.remove(uuid);
        if (start == null) {
            return;
        }
        long sessionSeconds = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
        String basePath = DATA_SECTION + "." + uuid;
        long totalSeconds = dataConfig.getLong(basePath + "." + KEY_TOTAL_SECONDS, 0L);
        dataConfig.set(basePath + "." + KEY_TOTAL_SECONDS, totalSeconds + sessionSeconds);
        dataConfig.set(basePath + "." + KEY_LAST_SEEN, System.currentTimeMillis());
        saveData();
    }

    private void saveData() {
        if (dataFile == null || dataConfig == null) {
            return;
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Nem sikerült elmenteni a data.yml fájlt: " + e.getMessage());
        }
    }

    private String formatHours(long totalSeconds) {
        double hours = totalSeconds / 3600.0;
        return String.format(Locale.ROOT, "%.2f", hours);
    }

    private Duration parseDuration(String input) {
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.length() < 2) {
            return null;
        }
        long multiplier;
        String numberPart;
        if (value.endsWith("mo")) {
            multiplier = 30L * 24L * 60L * 60L;
            numberPart = value.substring(0, value.length() - 2);
        } else {
            char suffix = value.charAt(value.length() - 1);
            numberPart = value.substring(0, value.length() - 1);
            if (suffix == 'm') {
                multiplier = 60L;
            } else if (suffix == 'h') {
                multiplier = 60L * 60L;
            } else if (suffix == 'd') {
                multiplier = 24L * 60L * 60L;
            } else {
                return null;
            }
        }
        try {
            long amount = Long.parseLong(numberPart);
            if (amount <= 0) {
                return null;
            }
            return Duration.ofSeconds(amount * multiplier);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatDuration(Duration duration) {
        long seconds = Math.max(0L, duration.getSeconds());
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;
        long remainingHours = hours % 24L;
        long remainingMinutes = minutes % 60L;
        if (days > 0) {
            return String.format(Locale.ROOT, "%d nap %d óra %d perc", days, remainingHours, remainingMinutes);
        }
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d óra %d perc", hours, remainingMinutes);
        }
        return String.format(Locale.ROOT, "%d perc", Math.max(1L, remainingMinutes));
    }

    private String joinArgs(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private void setBan(UUID uuid, String reason, Instant expiresAt) {
        String basePath = BAN_SECTION + "." + uuid;
        dataConfig.set(basePath + "." + BAN_REASON, reason == null ? "" : reason);
        if (expiresAt == null) {
            dataConfig.set(basePath + "." + BAN_EXPIRES_AT, null);
        } else {
            dataConfig.set(basePath + "." + BAN_EXPIRES_AT, expiresAt.toEpochMilli());
        }
        saveData();
    }

    private void clearBan(UUID uuid) {
        String basePath = BAN_SECTION + "." + uuid;
        dataConfig.set(basePath, null);
        saveData();
    }

    private BanEntry getBanEntry(UUID uuid) {
        String basePath = BAN_SECTION + "." + uuid;
        if (!dataConfig.contains(basePath)) {
            return null;
        }
        String reason = dataConfig.getString(basePath + "." + BAN_REASON, "");
        Long expiresAtValue = dataConfig.contains(basePath + "." + BAN_EXPIRES_AT)
                ? dataConfig.getLong(basePath + "." + BAN_EXPIRES_AT)
                : null;
        Instant expiresAt = expiresAtValue == null ? null : Instant.ofEpochMilli(expiresAtValue);
        return new BanEntry(reason, expiresAt);
    }

    private boolean ensureWorldGuard(CommandSender sender) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            sender.sendMessage(ChatColor.RED + "A WorldGuard plugin nincs telepítve.");
            return false;
        }
        return true;
    }

    private RegionManager getRegionManager(World world) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    }

    private int countOwnedRegions(UUID owner) {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            RegionManager manager = getRegionManager(world);
            if (manager == null) {
                continue;
            }
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (region.isOwner(owner)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean isOverlapping(RegionManager manager, ProtectedRegion candidate) {
        BlockVector3 min = candidate.getMinimumPoint();
        BlockVector3 max = candidate.getMaximumPoint();
        for (ProtectedRegion existing : manager.getRegions().values()) {
            if (existing.getId().equalsIgnoreCase("__global__")) {
                continue;
            }
            if (intersects(min, max, existing.getMinimumPoint(), existing.getMaximumPoint())) {
                return true;
            }
        }
        return false;
    }

    private boolean intersects(BlockVector3 minA, BlockVector3 maxA, BlockVector3 minB, BlockVector3 maxB) {
        return minA.getX() <= maxB.getX() && maxA.getX() >= minB.getX()
                && minA.getY() <= maxB.getY() && maxA.getY() >= minB.getY()
                && minA.getZ() <= maxB.getZ() && maxA.getZ() >= minB.getZ();
    }

    private String buildRegionId(String playerName, int index) {
        return "leved_" + playerName.toLowerCase(Locale.ROOT) + "_" + index;
    }

    private ProtectedRegion findOwnedRegionAt(Player player, RegionManager manager) {
        ApplicableRegionSet regions = manager.getApplicableRegions(BukkitAdapter.asBlockVector(player.getLocation()));
        for (ProtectedRegion region : regions) {
            if (region.getId().equalsIgnoreCase("__global__")) {
                continue;
            }
            if (region.isOwner(player.getUniqueId())) {
                return region;
            }
        }
        return null;
    }

    private void saveRegionMetadata(String regionId, Player owner, Location home) {
        String base = REGION_SECTION + "." + regionId;
        dataConfig.set(base + "." + REGION_OWNER_UUID, owner.getUniqueId().toString());
        dataConfig.set(base + "." + REGION_OWNER_NAME, owner.getName());
        dataConfig.set(base + "." + REGION_WORLD, owner.getWorld().getName());
        saveRegionHome(regionId, home);
    }

    private void saveRegionHome(String regionId, Location location) {
        String base = REGION_SECTION + "." + regionId + "." + REGION_HOME;
        dataConfig.set(base + ".world", location.getWorld().getName());
        dataConfig.set(base + ".x", location.getX());
        dataConfig.set(base + ".y", location.getY());
        dataConfig.set(base + ".z", location.getZ());
        dataConfig.set(base + ".yaw", location.getYaw());
        dataConfig.set(base + ".pitch", location.getPitch());
        saveData();
    }

    private Location getRegionHome(String regionId) {
        String base = REGION_SECTION + "." + regionId + "." + REGION_HOME;
        String worldName = dataConfig.getString(base + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = dataConfig.getDouble(base + ".x");
        double y = dataConfig.getDouble(base + ".y");
        double z = dataConfig.getDouble(base + ".z");
        float yaw = (float) dataConfig.getDouble(base + ".yaw");
        float pitch = (float) dataConfig.getDouble(base + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private String findOwnedRegionId(UUID owner, Location fallbackLocation) {
        for (World world : Bukkit.getWorlds()) {
            RegionManager manager = getRegionManager(world);
            if (manager == null) {
                continue;
            }
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (region.isOwner(owner)) {
                    if (fallbackLocation != null && world.equals(fallbackLocation.getWorld())) {
                        ApplicableRegionSet regions = manager.getApplicableRegions(BukkitAdapter.asBlockVector(fallbackLocation));
                        for (ProtectedRegion applicable : regions) {
                            if (applicable.getId().equals(region.getId())) {
                                return region.getId();
                            }
                        }
                    }
                    return region.getId();
                }
            }
        }
        return null;
    }

    private String findFriendRegionId(UUID uuid, String ownerName) {
        for (World world : Bukkit.getWorlds()) {
            RegionManager manager = getRegionManager(world);
            if (manager == null) {
                continue;
            }
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (!region.isMember(uuid)) {
                    continue;
                }
                String storedOwner = dataConfig.getString(REGION_SECTION + "." + region.getId() + "." + REGION_OWNER_NAME);
                if (storedOwner != null && storedOwner.equalsIgnoreCase(ownerName)) {
                    return region.getId();
                }
            }
        }
        return null;
    }

    private static class BanEntry {
        private final String reason;
        private final Instant expiresAt;

        private BanEntry(String reason, Instant expiresAt) {
            this.reason = reason;
            this.expiresAt = expiresAt;
        }
    }

    private enum BanStatus {
        NONE,
        TEMPORARY,
        PERMANENT
    }
}
