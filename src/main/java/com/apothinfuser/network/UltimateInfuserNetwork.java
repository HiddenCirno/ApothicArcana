package com.apothinfuser.network;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.config.UltimateInfuserConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class UltimateInfuserNetwork {

    private UltimateInfuserNetwork() {
    }

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.tryBuild(ApothInfuser.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    /** 在模组构造期调用一次；只做类初始化与包注册 */
    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SetSliderMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetSliderMessage::write)
                .decoder(SetSliderMessage::new)
                .consumerNetworkThread(SetSliderMessage::handle)
                .add();
        CHANNEL.messageBuilder(SyncTableStateMessage.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncTableStateMessage::write)
                .decoder(SyncTableStateMessage::new)
                .consumerNetworkThread(SyncTableStateMessage::handle)
                .add();
        CHANNEL.messageBuilder(PerformInfusionMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PerformInfusionMessage::write)
                .decoder(PerformInfusionMessage::new)
                .consumerNetworkThread(PerformInfusionMessage::handle)
                .add();
    }

    /** 客户端：请求执行灌注（服务端会重新匹配，包里不带配方信息） */
    public static void sendPerformInfusion(int containerId) {
        CHANNEL.sendToServer(new PerformInfusionMessage(containerId));
    }

    /** 把整张台子的状态同步给打开界面的那位玩家 */
    public static void sendTableState(Player player, int containerId, UltimateInfuserConfig config) {
        if (player instanceof ServerPlayer serverPlayer) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SyncTableStateMessage(containerId, config));
        }
    }

    /** 客户端：请求把某个滑块拨到指定值 */
    public static void sendSlider(int containerId, com.apothinfuser.config.TableStat stat, float value) {
        CHANNEL.sendToServer(SetSliderMessage.of(containerId, stat, value));
    }
}
