package com.apothinfuser.network;

import com.apothinfuser.config.UltimateInfuserConfig;
import com.apothinfuser.world.inventory.UltimateInfuserMenuAccess;
import fuzs.enchantinginfuser.world.inventory.InfuserMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：执行灌注。
 * <p>
 * 包里<b>不带任何配方信息</b>——服务端拿到后会用自己的三维数值重新 {@code findMatch}，
 * 因此客户端无法通过伪造包来白拿产物。
 */
public record PerformInfusionMessage(int containerId) {

    public PerformInfusionMessage(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.containerId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (player.containerMenu.containerId != this.containerId) return;
            if (!(player.containerMenu instanceof InfuserMenu menu)) return;
            if (!(menu.config instanceof UltimateInfuserConfig)) return;
            if (menu instanceof UltimateInfuserMenuAccess access) {
                access.apothinfuser$performInfusion(player);
            }
        });
        context.setPacketHandled(true);
    }
}
