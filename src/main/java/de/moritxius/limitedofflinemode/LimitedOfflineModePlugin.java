package de.moritxius.limitedofflinemode;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Plugin(
        id = "offlinebotbypass",
        name = "OfflineBotBypass",
        version = "1.0.0",
        description = "Allows specific usernames to join in offline mode with IP whitelist support",
        authors = {"moritxius", "enhanced"}
)
public class LimitedOfflineModePlugin {

    private final Set<String> allowedUsers = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelistedIps = ConcurrentHashMap.newKeySet();
    private final Set<String> adminUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, String> pendingLogins = new ConcurrentHashMap<>();
    
    private final Logger logger;
    private final ProxyServer proxy;
    private final Path dataDirectory;

    @Inject
    public LimitedOfflineModePlugin(Logger logger, ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        printStartupBanner();
        loadAdmins();
        loadAllowedUsers();
        loadIpWhitelist();
        registerCommands();
        logger.info("OfflineBotBypass loaded successfully!");
    }

    private void printStartupBanner() {
        String[] banner = {
            "",
            " \u2584\u2584\u2584\u2584\u2584\u2584                        \u2584\u2584\u2584\u2584\u2584\u2584                                                     ",
            " \u2588\u2588\u2580\u2580\u2580\u2580\u2588\u2588              \u2588\u2588      \u2588\u2588\u2580\u2580\u2580\u2580\u2588\u2588                                                   ",
            " \u2588\u2588    \u2588\u2588   \u2584\u2588\u2588\u2588\u2588\u2584   \u2588\u2588\u2588\u2588\u2588\u2588\u2588   \u2588\u2588    \u2588\u2588  \u2580\u2588\u2588  \u2588\u2588\u2588  \u2588\u2588\u2584\u2588\u2588\u2588\u2584    \u2584\u2588\u2588\u2588\u2588\u2588\u2584  \u2584\u2584\u2588\u2588\u2588\u2588\u2588\u2584  \u2584\u2584\u2588\u2588\u2588\u2588\u2588\u2584 ",
            " \u2588\u2588\u2588\u2588\u2588\u2588\u2588   \u2588\u2588\u2580  \u2580\u2588\u2588    \u2588\u2588      \u2588\u2588\u2588\u2588\u2588\u2588\u2588    \u2588\u2588\u2584 \u2588\u2588   \u2588\u2588\u2580  \u2580\u2588\u2588   \u2580 \u2584\u2584\u2584\u2588\u2588  \u2588\u2588\u2584\u2584\u2584\u2584 \u2580  \u2588\u2588\u2584\u2584\u2584\u2584 \u2580 ",
            " \u2588\u2588    \u2588\u2588  \u2588\u2588    \u2588\u2588    \u2588\u2588      \u2588\u2588    \u2588\u2588    \u2588\u2588\u2588\u2588\u2580   \u2588\u2588    \u2588\u2588  \u2584\u2588\u2588\u2580\u2580\u2580\u2588\u2588   \u2580\u2580\u2580\u2580\u2588\u2588\u2584   \u2580\u2580\u2580\u2580\u2588\u2588\u2584 ",
            " \u2588\u2588\u2584\u2584\u2584\u2584\u2588\u2588  \u2580\u2588\u2588\u2584\u2584\u2588\u2588\u2580    \u2588\u2588\u2584\u2584\u2584   \u2588\u2588\u2584\u2584\u2584\u2584\u2588\u2588     \u2588\u2588\u2588    \u2588\u2588\u2588\u2584\u2584\u2588\u2588\u2580  \u2588\u2588\u2584\u2584\u2584\u2588\u2588\u2588  \u2588\u2584\u2584\u2584\u2584\u2584\u2588\u2588  \u2588\u2584\u2584\u2584\u2584\u2584\u2588\u2588 ",
            " \u2580\u2580\u2580\u2580\u2580\u2580\u2580     \u2580\u2580\u2580\u2580       \u2580\u2580\u2580\u2580   \u2580\u2580\u2580\u2580\u2580\u2580\u2580      \u2588\u2588     \u2588\u2588 \u2580\u2580\u2580     \u2580\u2580\u2580\u2580 \u2580\u2580   \u2580\u2580\u2580\u2580\u2580\u2580    \u2580\u2580\u2580\u2580\u2580\u2580  ",
            "                                          \u2588\u2588\u2588      \u2588\u2588                                     ",
            ""
        };
        for (String line : banner) {
            logger.info(line);
        }
    }

    private void registerCommands() {
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("lom")
                        .aliases("limitedoffline", "botbypass")
                        .build(),
                new LomCommand()
        );
    }

    // ==================== CONFIG LOADING ====================

    private void loadAdmins() {
        try {
            adminUsers.clear();
            Path adminPath = dataDirectory.resolve("admins.txt");
            
            if (Files.exists(adminPath)) {
                Files.readAllLines(adminPath, StandardCharsets.UTF_8).forEach(line -> {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        adminUsers.add(trimmed.toLowerCase(Locale.ROOT));
                    }
                });
                logger.info("Loaded {} admin users", adminUsers.size());
            } else {
                Files.createDirectories(dataDirectory);
                List<String> defaultContent = List.of(
                        "# OfflineBotBypass - Admin Users",
                        "# Add usernames who can use /lom commands in-game",
                        "# One username per line (case-insensitive)",
                        "# Console always has permission",
                        "# Example:",
                        "# YourUsername"
                );
                Files.write(adminPath, defaultContent, StandardCharsets.UTF_8);
                logger.info("Created default admins.txt - add your username to use commands in-game!");
            }
        } catch (IOException e) {
            logger.error("Failed to load admins configuration", e);
        }
    }

    public void loadAllowedUsers() {
        try {
            allowedUsers.clear();
            Path configPath = dataDirectory.resolve("allowed-users.txt");
            
            if (Files.exists(configPath)) {
                Files.readAllLines(configPath, StandardCharsets.UTF_8).forEach(line -> {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        allowedUsers.add(trimmed.toLowerCase(Locale.ROOT));
                    }
                });
                logger.info("Loaded {} allowed users", allowedUsers.size());
            } else {
                Files.createDirectories(dataDirectory);
                List<String> defaultContent = List.of(
                        "# OfflineBotBypass - Allowed Users",
                        "# Usernames listed here can join in offline mode",
                        "# One username per line (case-insensitive)",
                        "# Example:",
                        "# BotAccount1"
                );
                Files.write(configPath, defaultContent, StandardCharsets.UTF_8);
                logger.info("Created default allowed-users.txt");
            }
        } catch (IOException e) {
            logger.error("Failed to load allowed users configuration", e);
        }
    }

    public void loadIpWhitelist() {
        try {
            whitelistedIps.clear();
            Path ipPath = dataDirectory.resolve("ip-whitelist.txt");
            
            if (Files.exists(ipPath)) {
                Files.readAllLines(ipPath, StandardCharsets.UTF_8).forEach(line -> {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        whitelistedIps.add(trimmed);
                    }
                });
                logger.info("Loaded {} whitelisted IPs", whitelistedIps.size());
            } else {
                Files.createDirectories(dataDirectory);
                List<String> defaultContent = List.of(
                        "# OfflineBotBypass - IP Whitelist",
                        "# ANY user connecting from these IPs can join in offline mode",
                        "# One IP per line",
                        "# Example:",
                        "# 192.168.1.100",
                        "# 10.0.0.50"
                );
                Files.write(ipPath, defaultContent, StandardCharsets.UTF_8);
                logger.info("Created default ip-whitelist.txt");
            }
        } catch (IOException e) {
            logger.error("Failed to load IP whitelist configuration", e);
        }
    }

    // ==================== CONFIG SAVING ====================

    private void saveAdmins() {
        try {
            Path adminPath = dataDirectory.resolve("admins.txt");
            List<String> lines = new ArrayList<>();
            lines.add("# OfflineBotBypass - Admin Users");
            lines.add("# Add usernames who can use /lom commands in-game");
            lines.addAll(adminUsers.stream().sorted().toList());
            
            Files.createDirectories(dataDirectory);
            Files.write(adminPath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to save admins", e);
        }
    }

    private void saveAllowedUsers() {
        try {
            Path configPath = dataDirectory.resolve("allowed-users.txt");
            List<String> lines = new ArrayList<>();
            lines.add("# OfflineBotBypass - Allowed Users");
            lines.add("# Usernames listed here can join in offline mode");
            lines.addAll(allowedUsers.stream().sorted().toList());
            
            Files.createDirectories(dataDirectory);
            Files.write(configPath, lines, StandardCharsets.UTF_8);
            logger.info("Saved {} allowed users", allowedUsers.size());
        } catch (IOException e) {
            logger.error("Failed to save allowed users", e);
        }
    }

    private void saveIpWhitelist() {
        try {
            Path ipPath = dataDirectory.resolve("ip-whitelist.txt");
            List<String> lines = new ArrayList<>();
            lines.add("# OfflineBotBypass - IP Whitelist");
            lines.add("# ANY user connecting from these IPs can join in offline mode");
            lines.addAll(whitelistedIps.stream().sorted().toList());
            
            Files.createDirectories(dataDirectory);
            Files.write(ipPath, lines, StandardCharsets.UTF_8);
            logger.info("Saved {} whitelisted IPs", whitelistedIps.size());
        } catch (IOException e) {
            logger.error("Failed to save IP whitelist", e);
        }
    }

    // ==================== LOGIN EVENTS ====================

    @Subscribe(order = PostOrder.FIRST)
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        String normalizedUsername = normalizeUsername(username);
        
        // Get the player's IP address
        InetSocketAddress address = event.getConnection().getRemoteAddress();
        String playerIp = address.getAddress().getHostAddress();
        
        // Check if IP is whitelisted (allows ANY username from this IP)
        boolean ipAllowed = whitelistedIps.contains(playerIp);
        
        // Check if username is specifically allowed
        boolean userAllowed = allowedUsers.contains(normalizedUsername);
        
        if (ipAllowed || userAllowed) {
            pendingLogins.put(normalizedUsername, playerIp);
            event.setResult(PreLoginComponentResult.forceOfflineMode());
            
            String reason = ipAllowed ? "whitelisted IP" : "allowed username";
            logger.info("Forcing offline mode for user: {} from IP: {} ({})", username, playerIp, reason);
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        String username = event.getUsername();
        String normalizedUsername = normalizeUsername(username);
        
        if (pendingLogins.containsKey(normalizedUsername)) {
            UUID offlineUUID = generateOfflineUUID(username);
            GameProfile offlineProfile = new GameProfile(offlineUUID, username, Collections.emptyList());
            event.setGameProfile(offlineProfile);
            
            String ip = pendingLogins.remove(normalizedUsername);
            logger.info("Using offline profile for user: {} (UUID: {}, IP: {})", username, offlineUUID, ip);
        }
    }

    private UUID generateOfflineUUID(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    // ==================== COMMAND HANDLER ====================

    private class LomCommand implements SimpleCommand {
        
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();
            
            if (!hasAdminPermission(source)) {
                sendMessage(source, "No permission. Add your username to admins.txt", NamedTextColor.RED);
                return;
            }
            
            if (args.length == 0) {
                sendHelp(source);
                return;
            }
            
            String action = args[0].toLowerCase(Locale.ROOT);
            
            switch (action) {
                case "add" -> {
                    if (args.length < 2) {
                        sendMessage(source, "Usage: /lom add <username>", NamedTextColor.YELLOW);
                        return;
                    }
                    String username = normalizeUsername(args[1]);
                    if (username.isEmpty()) {
                        sendMessage(source, "Invalid username.", NamedTextColor.RED);
                        return;
                    }
                    if (allowedUsers.contains(username)) {
                        sendMessage(source, "User '" + username + "' is already in the allowed list.", NamedTextColor.YELLOW);
                        return;
                    }
                    allowedUsers.add(username);
                    saveAllowedUsers();
                    sendMessage(source, "Added '" + username + "' to allowed users.", NamedTextColor.GREEN);
                }
                
                case "remove" -> {
                    if (args.length < 2) {
                        sendMessage(source, "Usage: /lom remove <username>", NamedTextColor.YELLOW);
                        return;
                    }
                    String username = normalizeUsername(args[1]);
                    if (!allowedUsers.contains(username)) {
                        sendMessage(source, "User '" + username + "' is not in the allowed list.", NamedTextColor.YELLOW);
                        return;
                    }
                    allowedUsers.remove(username);
                    saveAllowedUsers();
                    sendMessage(source, "Removed '" + username + "' from allowed users.", NamedTextColor.GREEN);
                }
                
                case "addip" -> {
                    if (args.length < 2) {
                        sendMessage(source, "Usage: /lom addip <ip>", NamedTextColor.YELLOW);
                        return;
                    }
                    String ip = args[1].trim();
                    if (ip.isEmpty()) {
                        sendMessage(source, "Invalid IP.", NamedTextColor.RED);
                        return;
                    }
                    if (whitelistedIps.contains(ip)) {
                        sendMessage(source, "IP '" + ip + "' is already whitelisted.", NamedTextColor.YELLOW);
                        return;
                    }
                    whitelistedIps.add(ip);
                    saveIpWhitelist();
                    sendMessage(source, "Added IP '" + ip + "' to whitelist. Any user from this IP can now join offline.", NamedTextColor.GREEN);
                }
                
                case "removeip" -> {
                    if (args.length < 2) {
                        sendMessage(source, "Usage: /lom removeip <ip>", NamedTextColor.YELLOW);
                        return;
                    }
                    String ip = args[1].trim();
                    if (!whitelistedIps.contains(ip)) {
                        sendMessage(source, "IP '" + ip + "' is not in the whitelist.", NamedTextColor.YELLOW);
                        return;
                    }
                    whitelistedIps.remove(ip);
                    saveIpWhitelist();
                    sendMessage(source, "Removed IP '" + ip + "' from whitelist.", NamedTextColor.GREEN);
                }
                
                case "addadmin" -> {
                    if (args.length < 2) {
                        sendMessage(source, "Usage: /lom addadmin <username>", NamedTextColor.YELLOW);
                        return;
                    }
                    String username = normalizeUsername(args[1]);
                    if (username.isEmpty()) {
                        sendMessage(source, "Invalid username.", NamedTextColor.RED);
                        return;
                    }
                    if (adminUsers.contains(username)) {
                        sendMessage(source, "'" + username + "' is already an admin.", NamedTextColor.YELLOW);
                        return;
                    }
                    adminUsers.add(username);
                    saveAdmins();
                    sendMessage(source, "Added '" + username + "' as admin.", NamedTextColor.GREEN);
                }
                
                case "removeadmin" -> {
                    if (args.length < 2) {
                        sendMessage(source, "Usage: /lom removeadmin <username>", NamedTextColor.YELLOW);
                        return;
                    }
                    String username = normalizeUsername(args[1]);
                    if (!adminUsers.contains(username)) {
                        sendMessage(source, "'" + username + "' is not an admin.", NamedTextColor.YELLOW);
                        return;
                    }
                    adminUsers.remove(username);
                    saveAdmins();
                    sendMessage(source, "Removed '" + username + "' from admins.", NamedTextColor.GREEN);
                }
                
                case "list" -> {
                    sendMessage(source, "=== Allowed Users ===", NamedTextColor.GOLD);
                    if (allowedUsers.isEmpty()) {
                        sendMessage(source, "No users in the allowed list.", NamedTextColor.GRAY);
                    } else {
                        allowedUsers.stream().sorted().forEach(user -> 
                            sendMessage(source, "- " + user, NamedTextColor.WHITE)
                        );
                    }
                    
                    sendMessage(source, "=== Whitelisted IPs ===", NamedTextColor.GOLD);
                    if (whitelistedIps.isEmpty()) {
                        sendMessage(source, "No IPs whitelisted.", NamedTextColor.GRAY);
                    } else {
                        whitelistedIps.stream().sorted().forEach(ip -> 
                            sendMessage(source, "- " + ip, NamedTextColor.WHITE)
                        );
                    }
                    
                    sendMessage(source, "=== Admins ===", NamedTextColor.GOLD);
                    if (adminUsers.isEmpty()) {
                        sendMessage(source, "No admins configured.", NamedTextColor.GRAY);
                    } else {
                        adminUsers.stream().sorted().forEach(admin -> 
                            sendMessage(source, "- " + admin, NamedTextColor.WHITE)
                        );
                    }
                }
                
                case "reload" -> {
                    loadAdmins();
                    loadAllowedUsers();
                    loadIpWhitelist();
                    sendMessage(source, "Configuration reloaded!", NamedTextColor.GREEN);
                }
                
                case "info" -> {
                    if (args.length < 2) {
                        sendMessage(source, "Usage: /lom info <username>", NamedTextColor.YELLOW);
                        return;
                    }
                    String username = normalizeUsername(args[1]);
                    
                    sendMessage(source, "=== User Info: " + username + " ===", NamedTextColor.GOLD);
                    sendMessage(source, "Offline UUID: " + generateOfflineUUID(username), NamedTextColor.WHITE);
                    sendMessage(source, "In allowed list: " + (allowedUsers.contains(username) ? "Yes" : "No"), NamedTextColor.WHITE);
                    sendMessage(source, "Is admin: " + (adminUsers.contains(username) ? "Yes" : "No"), NamedTextColor.WHITE);
                }
                
                default -> sendHelp(source);
            }
        }
        
        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            
            if (args.length <= 1) {
                return List.of("add", "remove", "addip", "removeip", "addadmin", "removeadmin", "list", "reload", "info");
            }
            
            String action = args[0].toLowerCase(Locale.ROOT);
            
            if (args.length == 2) {
                return switch (action) {
                    case "remove", "info" -> allowedUsers.stream()
                            .filter(u -> u.startsWith(args[1].toLowerCase(Locale.ROOT)))
                            .toList();
                    case "removeip" -> whitelistedIps.stream()
                            .filter(ip -> ip.startsWith(args[1]))
                            .toList();
                    case "removeadmin" -> adminUsers.stream()
                            .filter(u -> u.startsWith(args[1].toLowerCase(Locale.ROOT)))
                            .toList();
                    default -> Collections.emptyList();
                };
            }
            
            return Collections.emptyList();
        }
        
        @Override
        public boolean hasPermission(Invocation invocation) {
            return hasAdminPermission(invocation.source());
        }
        
        private boolean hasAdminPermission(CommandSource source) {
            // Console always has permission
            if (source instanceof ConsoleCommandSource) {
                return true;
            }
            // Check if player is in admins list
            if (source instanceof Player player) {
                return adminUsers.contains(player.getUsername().toLowerCase(Locale.ROOT));
            }
            return false;
        }
        
        private void sendHelp(CommandSource source) {
            sendMessage(source, "=== OfflineBotBypass Commands ===", NamedTextColor.GOLD);
            sendMessage(source, "/lom add <username> - Allow username to join offline", NamedTextColor.WHITE);
            sendMessage(source, "/lom remove <username> - Remove from allowed list", NamedTextColor.WHITE);
            sendMessage(source, "/lom addip <ip> - Whitelist IP (any user from IP allowed)", NamedTextColor.WHITE);
            sendMessage(source, "/lom removeip <ip> - Remove IP from whitelist", NamedTextColor.WHITE);
            sendMessage(source, "/lom addadmin <username> - Add plugin admin", NamedTextColor.WHITE);
            sendMessage(source, "/lom removeadmin <username> - Remove plugin admin", NamedTextColor.WHITE);
            sendMessage(source, "/lom list - List all users, IPs, and admins", NamedTextColor.WHITE);
            sendMessage(source, "/lom info <username> - Show user details", NamedTextColor.WHITE);
            sendMessage(source, "/lom reload - Reload all config files", NamedTextColor.WHITE);
        }
        
        private void sendMessage(CommandSource source, String message, NamedTextColor color) {
            source.sendMessage(Component.text(message).color(color));
        }
    }
}
