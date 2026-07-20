package com.springwater.easybot.commands.handlers;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.springwater.easybot.bridge.packet.BindStatusAccount;
import com.springwater.easybot.bridge.packet.ConfirmBindResultPacket;
import com.springwater.easybot.bridge.packet.QueryBindStatusResultPacket;
import com.springwater.easybot.commands.ICommandHandler;
import com.springwater.easybot.config.ConfigLoader;
import com.springwater.easybot.impl.ComponentAdapterImpl;
import com.springwater.easybot.platforms.EasyBotModImpl;
import com.springwater.easybot.platforms.ModData;
import com.springwater.easybot.threading.EasyBotNetworkingThreadPool;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class BindCommandHandler implements ICommandHandler {

    private static final Set<UUID> bindingPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> stack) {
        stack.then(
                LiteralArgumentBuilder.<CommandSourceStack>literal("bind")
                        .executes(context -> {
                            if (!context.getSource().isPlayer()) {
                                ModData.LOGGER.warn("温馨提示: 无法给控制台绑定账号哦。");
                                return 1;
                            }
                            if (!ConfigLoader.get().getCommand().isAllowBind()) {
                                context.getSource().sendFailure(Component.literal("当前服务器不允许绑定"));
                                return 0;
                            }

                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String playerName = player.getName().getString();
                            UUID uuid = player.getUUID();

                            if (bindingPlayers.contains(uuid)) {
                                sendFeedback(uuid, Component.literal(
                                        ConfigLoader.get().getMessage().getBindFail()
                                                .replace("#why", "您已有绑定任务正在进行，请稍后再试")
                                                .replace("&", "§")
                                ));
                                return 1;
                            }

                            bindingPlayers.add(uuid);

                            EasyBotNetworkingThreadPool.getInstance().addTask(() -> {
                                try {
                                    if (!EasyBotModImpl.INSTANCE.getBridgeClient().isReady()) {
                                        sendFailAndCleanup(uuid, "当前服务器不在线");
                                        return;
                                    }

                                    var account = EasyBotModImpl.INSTANCE.getBridgeClient().getSocialAccount(playerName);
                                    if (!Objects.equals(account.getName(), "")) {
                                        handleAlreadyBound(uuid, playerName);
                                        return;
                                    }

                                    var pack = EasyBotModImpl.INSTANCE.getBridgeClient().startBind(playerName);
                                    sendFeedback(uuid, Component.literal(
                                            ConfigLoader.get().getMessage().getBindStart()
                                                    .replace("#code", pack.getCode())
                                                    .replace("#time", pack.getTime())
                                                    .replace("&", "§")
                                    ));
                                    bindingPlayers.remove(uuid);
                                } catch (Exception e) {
                                    ModData.LOGGER.error("绑定任务失败: {}", e.getMessage(), e);
                                    sendFailAndCleanup(uuid, "服务器内部异常");
                                }
                            }, "绑定任务");

                            return 1;
                        })
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("confirm")
                                .executes(context -> {
                                    if (!context.getSource().isPlayer()) {
                                        ModData.LOGGER.warn("温馨提示: 无法给控制台绑定账号哦。");
                                        return 1;
                                    }
                                    if (!ConfigLoader.get().getCommand().isAllowBind()) {
                                        context.getSource().sendFailure(Component.literal("当前服务器不允许绑定"));
                                        return 0;
                                    }

                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String playerName = player.getName().getString();
                                    UUID uuid = player.getUUID();

                                    EasyBotNetworkingThreadPool.getInstance().addTask(() -> {
                                        try {
                                            if (!EasyBotModImpl.INSTANCE.getBridgeClient().isReady()) {
                                                sendFailAndCleanup(uuid, "当前服务器不在线");
                                                return;
                                            }

                                            var pack = EasyBotModImpl.INSTANCE.getBridgeClient().startBind(playerName);
                                            sendFeedback(uuid, Component.literal(
                                                    ConfigLoader.get().getMessage().getBindStart()
                                                            .replace("#code", pack.getCode())
                                                            .replace("#time", pack.getTime())
                                                            .replace("&", "§")
                                            ));
                                        } catch (Exception e) {
                                            ModData.LOGGER.error("绑定任务失败: {}", e.getMessage(), e);
                                            sendFailAndCleanup(uuid, "服务器内部异常");
                                        }
                                    }, "强制绑定任务");

                                    return 1;
                                })
                        )
        );

        stack.then(
                LiteralArgumentBuilder.<CommandSourceStack>literal("confirm")
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal("用法: /easybot confirm <code>").withStyle(ChatFormatting.RED));
                            return 0;
                        })
                        .then(Commands.argument("code", StringArgumentType.string())
                                .executes(context -> {
                                    if (!context.getSource().isPlayer()) {
                                        ModData.LOGGER.warn("温馨提示: 无法给控制台确认绑定。");
                                        return 1;
                                    }

                                    String code = StringArgumentType.getString(context, "code");
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String playerName = player.getName().getString();

                                    EasyBotNetworkingThreadPool.getInstance().addTask(() -> {
                                        try {
                                            ConfirmBindResultPacket packet = EasyBotModImpl.INSTANCE.getBridgeClient().confirmBind(playerName, code);
                                            EasyBotModImpl.INSTANCE.getServer().execute(() -> {
                                                ServerPlayer p = EasyBotModImpl.INSTANCE.getServer().getPlayerList().getPlayerByName(playerName);
                                                if (p != null) {
                                                    p.sendSystemMessage(Component.literal(packet.getMessage()), false);
                                                    if (packet.isSuccess() && packet.getBoundPlatforms() != null && !packet.getBoundPlatforms().isEmpty()) {
                                                        p.sendSystemMessage(Component.literal("已绑定平台: " + packet.getBoundPlatforms()).withStyle(ChatFormatting.GREEN), false);
                                                    }
                                                }
                                            });
                                        } catch (Exception ex) {
                                            Throwable cause = ex.getCause();
                                            EasyBotModImpl.INSTANCE.getServer().execute(() -> {
                                                ServerPlayer p = EasyBotModImpl.INSTANCE.getServer().getPlayerList().getPlayerByName(playerName);
                                                if (p != null) {
                                                    if (cause instanceof TimeoutException) {
                                                        p.sendSystemMessage(Component.literal("操作超时，请稍后再试").withStyle(ChatFormatting.RED), false);
                                                    } else {
                                                        ModData.LOGGER.error("确认绑定失败", ex);
                                                        p.sendSystemMessage(Component.literal("操作失败，请稍后再试").withStyle(ChatFormatting.RED), false);
                                                    }
                                                }
                                            });
                                        }
                                    }, "确认绑定任务");

                                    return 1;
                                })
                        )
        );

        stack.then(
                LiteralArgumentBuilder.<CommandSourceStack>literal("status")
                        .executes(context -> {
                            if (!context.getSource().isPlayer()) {
                                ModData.LOGGER.warn("温馨提示: 无法查询控制台绑定状态。");
                                return 1;
                            }

                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String playerName = player.getName().getString();

                            EasyBotNetworkingThreadPool.getInstance().addTask(() -> {
                                try {
                                    QueryBindStatusResultPacket packet = EasyBotModImpl.INSTANCE.getBridgeClient().queryBindStatus(playerName);
                                    EasyBotModImpl.INSTANCE.getServer().execute(() -> {
                                        ServerPlayer p = EasyBotModImpl.INSTANCE.getServer().getPlayerList().getPlayerByName(playerName);
                                        if (p != null) {
                                            if (!packet.isBound() || packet.getSocialAccounts() == null || packet.getSocialAccounts().isEmpty()) {
                                                p.sendSystemMessage(Component.literal("你尚未绑定任何社交平台"), false);
                                                return;
                                            }
                                            p.sendSystemMessage(Component.literal("已绑定的社交平台:").withStyle(ChatFormatting.GREEN), false);
                                            for (BindStatusAccount account : packet.getSocialAccounts()) {
                                                p.sendSystemMessage(
                                                        Component.literal("  " + account.getPlatform() + " - " + account.getName() + " (" + account.getUuid() + ")")
                                                                .withStyle(ChatFormatting.GOLD),
                                                        false
                                                );
                                            }
                                        }
                                    });
                                } catch (Exception ex) {
                                    Throwable cause = ex.getCause();
                                    EasyBotModImpl.INSTANCE.getServer().execute(() -> {
                                        ServerPlayer p = EasyBotModImpl.INSTANCE.getServer().getPlayerList().getPlayerByName(playerName);
                                        if (p != null) {
                                            if (cause instanceof TimeoutException) {
                                                p.sendSystemMessage(Component.literal("操作超时，请稍后再试").withStyle(ChatFormatting.RED), false);
                                            } else {
                                                ModData.LOGGER.error("查询绑定状态失败", ex);
                                                p.sendSystemMessage(Component.literal("操作失败，请稍后再试").withStyle(ChatFormatting.RED), false);
                                            }
                                        }
                                    });
                                }
                            }, "查询绑定状态任务");

                            return 1;
                        })
        );
    }

    /**
     * 处理玩家已有绑定记录的情况
     * 异步查询完整绑定状态，切回主线程展示已绑定平台列表并提供强制绑定按钮
     */
    private void handleAlreadyBound(UUID uuid, String playerName) {
        try {
            QueryBindStatusResultPacket packet = EasyBotModImpl.INSTANCE.getBridgeClient().queryBindStatus(playerName);
            EasyBotModImpl.INSTANCE.getServer().execute(() -> {
                bindingPlayers.remove(uuid);
                ServerPlayer player = EasyBotModImpl.INSTANCE.getServer().getPlayerList().getPlayer(uuid);
                if (player == null) return;

                player.sendSystemMessage(Component.literal("[EasyBot] 你已绑定以下社交平台："), false);
                if (packet.isBound() && packet.getSocialAccounts() != null) {
                    for (BindStatusAccount account : packet.getSocialAccounts()) {
                        player.sendSystemMessage(
                                Component.literal("  " + account.getPlatform() + " - " + account.getName() + " (" + account.getUuid() + ")")
                                        .withStyle(ChatFormatting.RED),
                                false
                        );
                    }
                }
                player.sendSystemMessage(Component.literal("[EasyBot] 验证码只能用于绑定新平台，无法重复绑定已有平台"), false);

                Style confirmStyle = Style.EMPTY;
                confirmStyle = ComponentAdapterImpl.withRunCommand(confirmStyle, "/easybot bind confirm");
                confirmStyle = ComponentAdapterImpl.withHoverText(confirmStyle, Component.literal("点击后将继续生成绑定验证码"));
                confirmStyle = confirmStyle.withColor(ChatFormatting.GREEN);
                player.sendSystemMessage(
                        Component.literal("[点我确认]").withStyle(confirmStyle),
                        false
                );
            });
        } catch (Exception ex) {
            ModData.LOGGER.error("查询绑定状态失败", ex);
            sendFailAndCleanup(uuid, "查询绑定状态失败");
        }
    }

    /**
     * 向玩家发送失败消息并清理绑定状态
     */
    private void sendFailAndCleanup(UUID uuid, String reason) {
        bindingPlayers.remove(uuid);
        sendFeedback(uuid, Component.literal(
                ConfigLoader.get().getMessage().getBindFail()
                        .replace("#why", reason)
                        .replace("&", "§")
        ));
    }

    /**
     * 在主线程向玩家发送消息（检查玩家是否在线）
     */
    private void sendFeedback(UUID uuid, Component component) {
        EasyBotModImpl.INSTANCE.getServer().execute(() -> {
            ServerPlayer player = EasyBotModImpl.INSTANCE.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(component, false);
            }
        });
    }
}
