package org.cetake.worldEditGlobalEditorVelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Plugin(id = "worldeditglobaleditor", name = "WorldEditGlobalEditor", version = "1.0")
public class WorldEditGlobalEditor {
    private static final String CHANNEL = "worldeditglobaleditor:main";
    private final Logger logger;
    private final ProxyServer server;

    @Inject
    public WorldEditGlobalEditor(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;

    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("WorldEditGlobalEditor Plugin Enabled");

        server.getChannelRegistrar().register(MinecraftChannelIdentifier.create("worldeditglobaleditor", "main"));
        server.getChannelRegistrar().register(MinecraftChannelIdentifier.create("worldeditglobaleditor", "request"));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {

        if (!event.getIdentifier().getId().equals(CHANNEL)) {
            return;
        }

        String receivedMessage = new String(event.getData(), StandardCharsets.UTF_8);
        try {

            // 送信されたデータを解析
            String[] parts = receivedMessage.split("\\|", 2);
            if (parts.length < 2) {
                logger.warn("Invalid message format received: {}", receivedMessage);
                return;
            }
            String playerName = parts[0];

            sendPluginMessage(playerName, receivedMessage);



        } catch (Exception e) {
            logger.error("Failed to handle plugin message", e);
        }
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(currentServer -> {
            MinecraftChannelIdentifier channelIdentifier = MinecraftChannelIdentifier.create("worldeditglobaleditor", "request");

            byte[] message = (player.getUsername()).getBytes(StandardCharsets.UTF_8);
            currentServer.sendPluginMessage(channelIdentifier, message);
        });
    }

    private void sendPluginMessage(String playerName, String receivedMessage) {

        getServerByPlayer(playerName).thenAccept(optionalServer -> {
            if (optionalServer.isEmpty()) {
                logger.warn("Player {} is not connected to any server.", playerName);
                return;
            }

            RegisteredServer paperServer = optionalServer.get();
            MinecraftChannelIdentifier channelIdentifier = MinecraftChannelIdentifier.create("worldeditglobaleditor", "main");
            byte[] messageBytes = receivedMessage.getBytes(StandardCharsets.UTF_8);

            paperServer.sendPluginMessage(channelIdentifier, messageBytes);

        });
    }


    public CompletableFuture<Optional<RegisteredServer>> getServerByPlayer(String playerName) {
        CompletableFuture<Optional<RegisteredServer>> future = new CompletableFuture<>();

        server.getScheduler().buildTask(this, () -> {
            Optional<RegisteredServer> serverOptional = server.getAllPlayers().stream()
                    .filter(player -> player.getUsername().equalsIgnoreCase(playerName))
                    .findFirst()
                    .flatMap(Player::getCurrentServer)
                    .map(serverConnection -> {
                        logger.info("Found player {} on server: {}", playerName, serverConnection.getServer().getServerInfo().getName());
                        return serverConnection.getServer();
                    });

            future.complete(serverOptional); // 取得した情報を `CompletableFuture` にセット
        }).delay(2, TimeUnit.SECONDS).schedule();

        return future;
    }
}