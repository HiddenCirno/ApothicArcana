package com.apothinfuser.network;

import com.apothinfuser.config.UltimateInfuserConfig;
import fuzs.enchantinginfuser.world.inventory.InfuserMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步整张台子的状态（四维上限、三个滑块值、宝藏解锁、命中的灌注产物）。
 * <p>
 * 用自定义包而不是 {@code DataSlot} 的原因：DataSlot 需要在菜单构造器里 {@code addDataSlot} 注册，
 * 而我们不持有 {@code InfuserMenu} 的构造器（只能通过 {@code @Invoker} 调用），
 * 注入构造器再加字段会让 Mixin 面积无谓变大。一个包把这些值一次性带过去更简单，
 * 且宝藏标志、灌注产物这类非数值状态本来也不适合塞进 int 的 DataSlot。
 */
public record SyncTableStateMessage(int containerId,
        int maxEterna, int maxQuanta, int maxArcana, int maxRectification,
        int selectedEterna, int selectedQuanta, int selectedArcana,
        boolean treasure, ItemStack infusionResult) {

    public SyncTableStateMessage(int containerId, UltimateInfuserConfig config) {
        // 四维是 float（0.25 步进、两位小数），走百分之一定点传输，既精确又比 writeFloat 省字节
        this(containerId,
                UltimateInfuserConfig.toWire(config.getMaxEterna()),
                UltimateInfuserConfig.toWire(config.getMaxQuanta()),
                UltimateInfuserConfig.toWire(config.getMaxArcana()),
                UltimateInfuserConfig.toWire(config.getMaxRectification()),
                UltimateInfuserConfig.toWire(config.getSelectedEterna()),
                UltimateInfuserConfig.toWire(config.getSelectedQuanta()),
                UltimateInfuserConfig.toWire(config.getSelectedArcana()),
                config.isTreasureUnlocked(),
                config.getInfusionResult());
    }

    public SyncTableStateMessage(FriendlyByteBuf buf) {
        this(buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(),
                buf.readItem());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.containerId);
        buf.writeVarInt(this.maxEterna);
        buf.writeVarInt(this.maxQuanta);
        buf.writeVarInt(this.maxArcana);
        buf.writeVarInt(this.maxRectification);
        buf.writeVarInt(this.selectedEterna);
        buf.writeVarInt(this.selectedQuanta);
        buf.writeVarInt(this.selectedArcana);
        buf.writeBoolean(this.treasure);
        buf.writeItem(this.infusionResult);
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
        static void apply(SyncTableStateMessage message) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;
            if (minecraft.player.containerMenu.containerId != message.containerId) return;
            if (!(minecraft.player.containerMenu instanceof InfuserMenu menu)) return;
            if (!(menu.config instanceof UltimateInfuserConfig config)) return;

            config.applySyncedState(message.maxEterna, message.maxQuanta, message.maxArcana,
                    message.maxRectification, message.selectedEterna, message.selectedQuanta,
                    message.selectedArcana, message.treasure);
            config.setInfusionResult(message.infusionResult);
        }
    }
}
