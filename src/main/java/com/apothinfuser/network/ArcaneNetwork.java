package com.apothinfuser.network;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.config.ArcaneStats;
import com.apothinfuser.config.TableStat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 奥术附魔台的网络通道。
 * <p>
 * 与终极奥术附魔台各用一个通道，而不是合并成一个：两者的消息在语义上互不相干
 * （那边传整套灌注台配置 + 灌注产物，这边只传三个数），合并只会让注册表变长、
 * 且任一台子的协议变更都会波及另一台。
 */
public final class ArcaneNetwork {

    private ArcaneNetwork() {
    }

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.tryBuild(ApothInfuser.MOD_ID, "arcane"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    /** 在模组构造期调用一次；只做包注册 */
    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SetArcaneStatMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetArcaneStatMessage::write)
                .decoder(SetArcaneStatMessage::new)
                .consumerNetworkThread(SetArcaneStatMessage::handle)
                .add();
        CHANNEL.messageBuilder(SyncArcaneStatsMessage.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncArcaneStatsMessage::write)
                .decoder(SyncArcaneStatsMessage::new)
                .consumerNetworkThread(SyncArcaneStatsMessage::handle)
                .add();
    }

    /** 客户端：请求把某一条三维拖到指定值 */
    public static void sendSetStat(int containerId, TableStat stat, float value) {
        CHANNEL.sendToServer(SetArcaneStatMessage.of(containerId, stat, value));
    }

    /** 服务端：把三维上限与当前值同步给打开界面的那位玩家 */
    public static void sendStats(Player player, int containerId, ArcaneStats stats) {
        if (player instanceof ServerPlayer serverPlayer) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SyncArcaneStatsMessage(containerId, stats));
        }
    }
}
