package com.apothinfuser.network;

import com.apothinfuser.config.StatMath;
import com.apothinfuser.config.TableStat;
import com.apothinfuser.world.inventory.ArcaneEnchantmentMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：把奥术附魔台的某一条三维拖到指定值。
 * <p>
 * 传的是<b>绝对值</b>而不是增量：拖动会产生大量事件，增量会因为丢包或事件合并而累积误差，
 * 绝对值天然幂等——最后一个包就是最终状态。
 * <p>
 * 服务端一律以自己那份状态为准重新夹取（{@code ArcaneStats.setSelected} 内部会 clamp 到书架上限），
 * 再把权威值回传，因此客户端传入越界值不会有任何影响。
 */
public record SetArcaneStatMessage(int containerId, TableStat stat, int wireValue) {

    public static SetArcaneStatMessage of(int containerId, TableStat stat, float value) {
        return new SetArcaneStatMessage(containerId, stat, StatMath.toWire(value));
    }

    public float value() {
        return StatMath.fromWire(this.wireValue);
    }

    public SetArcaneStatMessage(FriendlyByteBuf buf) {
        this(buf.readVarInt(), TableStat.byOrdinal(buf.readByte()), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.containerId);
        buf.writeByte(this.stat.ordinal());
        buf.writeVarInt(this.wireValue);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            // 只认当前打开的那一个容器，防止拖动途中界面被关掉后误改
            if (player.containerMenu.containerId != this.containerId) return;
            if (!(player.containerMenu instanceof ArcaneEnchantmentMenu menu)) return;
            menu.applySelection(this.stat, this.value());
        });
        context.setPacketHandled(true);
    }
}
