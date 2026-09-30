package com.apothinfuser.network;

import com.apothinfuser.config.ArcaneStats;
import com.apothinfuser.world.inventory.ArcaneEnchantmentMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步奥术附魔台的三维上限与当前值。
 * <p>
 * <b>为什么带上限：</b>客户端扫不到书架（那是服务端的世界查询），没有上限就画不出正确比例的属性条，
 * 也不知道拖动范围到哪里为止。
 * <p>
 * <b>为什么不用神化自己的 {@code StatsMessage}：</b>那个包会把书架原始值写进 {@code menu.stats}。
 * 我们要的是拨定后的值，而两者走不同通道、不保证先后，混用会出现"属性条跳回书架满值"。
 * 这里用自己的包一次性带全，客户端不再依赖神化的同步。
 */
public record SyncArcaneStatsMessage(int containerId, ArcaneStats stats) {

    public SyncArcaneStatsMessage(FriendlyByteBuf buf) {
        this(buf.readVarInt(), ArcaneStats.read(buf));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.containerId);
        this.stats.write(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 这个包只发往客户端；服务端收到时直接忽略
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientHandler.apply(this);
        });
        context.setPacketHandled(true);
    }

    /** 隔离在一个嵌套类里，避免服务端类加载时碰到客户端专属的 Minecraft 类 */
    private static final class ClientHandler {
        static void apply(SyncArcaneStatsMessage message) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;
            if (minecraft.player.containerMenu.containerId != message.containerId) return;
            if (!(minecraft.player.containerMenu instanceof ArcaneEnchantmentMenu menu)) return;
            menu.applyClientStats(message.stats);
        }
    }
}
