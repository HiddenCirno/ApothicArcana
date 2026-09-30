package com.apothinfuser.network;

import com.apothinfuser.config.TableStat;
import com.apothinfuser.config.UltimateInfuserConfig;
import fuzs.enchantinginfuser.world.inventory.InfuserMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：把某个维度滑块拨到指定值。
 * <p>
 * 灌注台自带的 {@code C2SAddEnchantLevelMessage} 只携带 {@code boolean increase}（相对增减），
 * 滑块场景必须传绝对值。
 * <p>
 * 服务端一律以自己那份 config 为准重新夹取（{@code UltimateInfuserConfig} 的 setter 内部会 clamp），
 * 再把权威值回传，因此客户端传入越界值不会造成任何影响。
 */
public record SetSliderMessage(int containerId, TableStat stat, int wireValue) {

    /** 便捷构造：直接传四维的浮点值（内部转百分之一定点） */
    public static SetSliderMessage of(int containerId, TableStat stat, float value) {
        return new SetSliderMessage(containerId, stat, UltimateInfuserConfig.toWire(value));
    }

    public float value() {
        return this.wireValue / 100.0F;
    }

    public SetSliderMessage(FriendlyByteBuf buf) {
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
            // 只认当前打开的、且属于本模组的容器
            if (player.containerMenu.containerId != this.containerId) return;
            if (!(player.containerMenu instanceof InfuserMenu menu)) return;
            if (!(menu.config instanceof UltimateInfuserConfig config)) return;

            switch (this.stat) {
                case ETERNA -> config.setSelectedEterna(this.value());
                case QUANTA -> config.setSelectedQuanta(this.value());
                case ARCANA -> config.setSelectedArcana(this.value());
            }
            // 写回方块实体，关掉界面再打开时能恢复
            if (menu instanceof com.apothinfuser.world.inventory.UltimateInfuserMenuAccess access) {
                access.apothinfuser$persistSliderValues();
                // 三维变了 → 可能进出灌注配方的窗口，必须重新匹配并把权威值回传。
                // 这一步内部会调用 sendTableState。
                access.apothinfuser$refreshInfusion();
            } else {
                UltimateInfuserNetwork.sendTableState(player, this.containerId, config);
            }
        });
        context.setPacketHandled(true);
    }
}
