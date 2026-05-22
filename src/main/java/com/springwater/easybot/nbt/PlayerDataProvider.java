package com.springwater.easybot.nbt;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.springwater.easybot.placeholder.handlers.StatisticHandler;
import com.springwater.easybot.platforms.EasyBotModImpl;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class PlayerDataProvider {
    private static final Gson GSON = new Gson();
    @Nullable
    public static JsonObject getPlayerData(UUID uuid) {
        MinecraftServer server = EasyBotModImpl.INSTANCE.getServer();
        if (server == null) {
            return null;
        }

        JsonElement jsonElement;
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);

        if (serverPlayer != null) {
            //? >= 1.21.6 {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    new net.minecraft.util.ProblemReporter.ScopedCollector(EasyBotModImpl.INSTANCE.getLogger()),
                    server.registryAccess()
            );
            serverPlayer.saveWithoutId(output);
            jsonElement = NbtOps.INSTANCE.convertTo(
                    JsonOps.INSTANCE,
                    output.buildResult()
            );
            //?} else {
            /*net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            serverPlayer.saveWithoutId(tag);
            jsonElement = NbtOps.INSTANCE.convertTo(
                    JsonOps.INSTANCE,
                    tag
            );
            *///?}

        } else {
            Path playerDataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR);
            Path playerFile = playerDataDir.resolve(uuid.toString() + ".dat");
            if (!Files.exists(playerFile)) {
                return null; // 玩家不在线
            }

            try {
                //? <= 1.20.2 {
                /*net.minecraft.nbt.CompoundTag offlineTag = net.minecraft.nbt.NbtIo.readCompressed(playerFile.toFile());
                *///?} else {
                net.minecraft.nbt.CompoundTag offlineTag = net.minecraft.nbt.NbtIo.readCompressed(
                        playerFile.toFile().toPath(),
                        net.minecraft.nbt.NbtAccounter.unlimitedHeap()
                );
                //?}
                jsonElement = NbtOps.INSTANCE.convertTo(
                        JsonOps.INSTANCE,
                        offlineTag
                );
            } catch (Exception e) {
                EasyBotModImpl.INSTANCE.getLogger().error("读取离线玩家的NBT数据失败: {}", uuid, e);
                throw new RuntimeException(e);
            }
        }
        if (jsonElement.isJsonObject()) {
            return jsonElement.getAsJsonObject();
        }
        return null;
    }

    @Nullable
    public  static JsonObject getPlayerStats(UUID uuid) { 
        MinecraftServer server = EasyBotModImpl.INSTANCE.getServer();
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);
        if(serverPlayer != null)
            return GSON.fromJson(StatisticHandler.realTimeStatToJson(serverPlayer), JsonObject.class);
        Path playerStatPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_STATS_DIR);
        Path playerStatFile = playerStatPath.resolve(uuid.toString() + ".json");
        if(!Files.exists(playerStatFile))
            return null;
        try {
            return GSON.fromJson(Files.newBufferedReader(playerStatFile), JsonObject.class);
        } catch (Exception e) {
            EasyBotModImpl.INSTANCE.getLogger().error("读取玩家统计数据失败: {}", uuid, e);
            throw new RuntimeException(e);
        }
    }
}