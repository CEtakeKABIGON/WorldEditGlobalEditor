package org.cetake.worldEditGlobalEditorPaper;

import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldedit.session.SessionOwner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WorldEditGlobalEditor extends JavaPlugin implements Listener, PluginMessageListener {

    @Override
    public void onEnable() {
        // プラグインメッセージチャネルの登録
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "worldeditglobaleditor:main");
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "worldeditglobaleditor:main", this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "worldeditglobaleditor:request");
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "worldeditglobaleditor:request", this);

        // イベントリスナーの登録
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, @NotNull Player Z, byte @NotNull [] message) {
        if (channel.equals("worldeditglobaleditor:main")){

            String receivedMessage = new String(message, StandardCharsets.UTF_8);
            String[] parts = receivedMessage.split("\\|", 2);

            if (parts.length < 2) return; // データが不足している場合は処理しない

            String playerName = parts[0];
            String editData = parts[1];

            Clipboard clipData = stringToClipboard(editData);
            if (clipData == null) return; // データが無効なら処理しない

            Player target = Bukkit.getPlayer(playerName);
            if (target != null) {
                setClipboard(target, clipData);
            }
        }else if(channel.equals("worldeditglobaleditor:request")){
            String playerName = new String(message, StandardCharsets.UTF_8);
            Player player = Bukkit.getPlayer(playerName);
            Clipboard clip = getClipboard(player);
            if (clip == null) return; // クリップボードがない場合は送信しない
            String editData = clipboardToString(clip);
            if (editData == null) return; // 変換に失敗した場合も送信しない
            sendToVelocity(playerName, editData);

        }
    }

    private void sendToVelocity(String player, String data) {
        String message = player + "|" + data;
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        Bukkit.getServer().sendPluginMessage(this, "worldeditglobaleditor:main", messageBytes);
    }


    public static Clipboard getClipboard(Player player) {
        WorldEditPlugin worldEdit = getWorldEdit();
        if (worldEdit == null) {
            return null; // WorldEditがインストールされていない
        }

        SessionOwner owner = worldEdit.wrapPlayer(player);
        SessionManager sessionManager = worldEdit.getWorldEdit().getSessionManager();
        LocalSession session = sessionManager.get(owner);

        if (session == null) {
            return null; // セッションが見つからない
        }

        try {
            return session.getClipboard().getClipboard();
        } catch (EmptyClipboardException e) {
            return null; // クリップボードが空
        }
    }


    public static String clipboardToString(Clipboard clipboard) {
        if (clipboard == null) {
            return null;
        }

        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(byteStream);
            writer.write(clipboard);
            writer.close();

            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Clipboard stringToClipboard(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
            ClipboardReader reader = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(byteStream);
            return reader.read();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void setClipboard(Player player, Clipboard clipboard) {
        if (clipboard == null) return;

        SessionManager sessionManager = WorldEdit.getInstance().getSessionManager();
        LocalSession session = sessionManager.get(BukkitAdapter.adapt(player));
        if (session == null) return;

        Component message = Component.text("クリップボードを転送しました" , NamedTextColor.AQUA);

        session.setClipboard(new ClipboardHolder(clipboard));
        player.sendMessage(message);
    }

    public static WorldEditPlugin getWorldEdit() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (plugin instanceof WorldEditPlugin) {
            return (WorldEditPlugin) plugin;
        }
        return null;
    }
}