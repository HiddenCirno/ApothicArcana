package com.apothinfuser.client;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.world.level.block.entity.ArcaneEnchantTile;
import com.apothinfuser.world.level.block.entity.UltimateInfuserBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity;

/**
 * 两个奥术台子上方那本悬浮的书（终极台子还会额外浮起待附魔的物品）。
 * <p>
 * <b>为什么要自己写，不能复用原版渲染器：</b>原版 {@code EnchantTableRenderer} 的书册贴图是
 * <b>硬编码的单例</b>，而且它只认 {@code EnchantmentTableBlockEntity} 这一个类型参数
 * （Java 泛型不变，我们的方块实体类型变体用不了它）。我们要给两个台子各自的专属书册，
 * 所以只能自己实现。
 * <p>
 * <b>变换与动画完全照原版实现</b>（逐条对照原版渲染方法的字节码写成）：
 * 平移 (0.5, 0.75, 0.5)、正弦悬浮、绕 Y 轴转向玩家、绕 Z 轴固定倾斜 80°、
 * 以及由 {@code flip}/{@code open} 驱动的翻页。动画数值由方块的 ticker
 * （{@code EnchantmentTableBlockEntity::bookAnimationTick}）推进，
 * 见两个方块类的 {@code getTicker}——少了那个覆写，这本书会静止不动且不报错。
 * <p>
 * <b>贴图为什么不走方块图集：</b>原版只把 {@code entity/enchanting_table_book} 单独收进了方块图集
 * （见 {@code minecraft:atlases/blocks.json} 里的 {@code single} 条目），我们自己的书册不在其中。
 * 与其去改图集配置，不如用 {@code RenderType.entitySolid(贴图路径)} 直接读文件——
 * 少一层依赖，也不用担心图集拼接问题。
 */
public class ArcaneTableRenderer<T extends EnchantmentTableBlockEntity> implements BlockEntityRenderer<T> {

    /** 奥术附魔台的书册 */
    public static final ResourceLocation ARCANE_BOOK =
            ResourceLocation.tryBuild(ApothInfuser.MOD_ID, "textures/entity/arcane_enchanting_table_book.png");

    /** 终极奥术附魔台的书册 */
    public static final ResourceLocation ULTIMATE_BOOK =
            ResourceLocation.tryBuild(ApothInfuser.MOD_ID, "textures/entity/ultimate_arcane_enchanting_table_book.png");

    private final BookModel bookModel;
    private final ResourceLocation bookTexture;
    private final boolean renderHeldItem;

    private ArcaneTableRenderer(BlockEntityRendererProvider.Context context, ResourceLocation bookTexture,
            boolean renderHeldItem) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
        this.bookTexture = bookTexture;
        this.renderHeldItem = renderHeldItem;
    }

    /**
     * 奥术附魔台：只有一本悬浮的书。
     * <p>
     * 注册时传方法引用 {@code ArcaneTableRenderer::arcaneTable}——写静态工厂而不是直接暴露构造器，
     * 是因为注册接口只给一个 {@code Context} 参数，无处传贴图。
     */
    public static ArcaneTableRenderer<ArcaneEnchantTile> arcaneTable(BlockEntityRendererProvider.Context context) {
        return new ArcaneTableRenderer<>(context, ARCANE_BOOK, false);
    }

    /** 终极奥术附魔台：多一层悬浮的待附魔物品（这是它继承自附魔灌注台的表现） */
    public static ArcaneTableRenderer<UltimateInfuserBlockEntity> ultimateTable(BlockEntityRendererProvider.Context context) {
        return new ArcaneTableRenderer<>(context, ULTIMATE_BOOK, true);
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay) {
        this.renderBook(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        if (this.renderHeldItem) {
            this.renderHeldItem(blockEntity, partialTick, poseStack, bufferSource, packedLight);
        }
    }

    private void renderBook(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.75F, 0.5F);

        float age = blockEntity.time + partialTick;
        poseStack.translate(0.0F, 0.1F + Mth.sin(age * 0.1F) * 0.01F, 0.0F);

        // 朝向：把 rot-oRot 这个角度差归一化到 [-π, π]，避免跨过正负 180° 时整本书猛地转一圈
        float deltaRot = blockEntity.rot - blockEntity.oRot;
        while (deltaRot >= Math.PI) {
            deltaRot -= (float) (Math.PI * 2);
        }
        while (deltaRot < -Math.PI) {
            deltaRot += (float) (Math.PI * 2);
        }
        poseStack.mulPose(Axis.YP.rotation(-(blockEntity.oRot + deltaRot * partialTick)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));

        float flip = Mth.lerp(partialTick, blockEntity.oFlip, blockEntity.flip);
        float rightPage = Mth.clamp(Mth.frac(flip + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float leftPage = Mth.clamp(Mth.frac(flip + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float open = Mth.lerp(partialTick, blockEntity.oOpen, blockEntity.open);
        this.bookModel.setupAnim(age, rightPage, leftPage, open);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entitySolid(this.bookTexture));
        this.bookModel.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    /**
     * 悬浮在台子上方的待附魔物品。
     * <p>
     * 书没打开时不画（{@code open == 0}），与附魔灌注台的表现一致——物品是"从书里浮起来"的，
     * 书合着的时候不该有东西悬在那儿。
     */
    private void renderHeldItem(T blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        if (blockEntity.open == 0.0F && blockEntity.oOpen == 0.0F) return;
        if (!(blockEntity instanceof Container container)) return;

        ItemStack stack = container.getItem(0);
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(0.5F, 1.0F, 0.5F);

        BakedModel model = Minecraft.getInstance().getItemRenderer()
                .getModel(stack, blockEntity.getLevel(), null, 0);
        float hoverOffset = Mth.sin((blockEntity.time + partialTick) / 10.0F) * 0.1F + 0.1F;
        float modelYScale = model.getTransforms().getTransform(ItemDisplayContext.GROUND).scale.y();
        float open = Mth.lerp(partialTick, blockEntity.oOpen, blockEntity.open);
        // 书越开，物品浮得越高；合上时落到台面里
        poseStack.translate(0.0F, hoverOffset + 0.25F * modelYScale * open - 0.15F * (1.0F - open), 0.0F);

        float scale = open * 0.8F + 0.2F;
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotation((blockEntity.time + partialTick) / 20.0F));
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GROUND, false, poseStack,
                bufferSource, packedLight, OverlayTexture.NO_OVERLAY, model);
        poseStack.popPose();
    }
}
