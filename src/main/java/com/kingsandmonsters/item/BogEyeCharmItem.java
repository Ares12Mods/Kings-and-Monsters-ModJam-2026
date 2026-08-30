package com.kingsandmonsters.item;

import com.kingsandmonsters.ClientConfig;

import com.kingsandmonsters.network.BogEyeTargetPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Consumer;

public class BogEyeCharmItem extends Item {
    private static final int HORIZONTAL_RADIUS = 24;
    private static final int VERTICAL_RADIUS = 12;
    private static final int SUCCESS_COOLDOWN_TICKS = 5 * 20;
    private static final int MISS_COOLDOWN_TICKS = 2 * 20;

    public BogEyeCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos target = findNearestUnopenedContainer(serverLevel, player.blockPosition());
        if (target == null) {
            player.getCooldowns().addCooldown(stack, MISS_COOLDOWN_TICKS);
            player.sendOverlayMessage(Component.translatable("message.kingsandmonsters.bog_eye.none")
                    .withStyle(ChatFormatting.DARK_GREEN));
            return InteractionResult.SUCCESS;
        }

        double distance = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(target)));
        player.sendOverlayMessage(Component.translatable(
                "message.kingsandmonsters.bog_eye.found", Math.round(distance))
                .withStyle(ChatFormatting.GREEN));
        player.getCooldowns().addCooldown(stack, SUCCESS_COOLDOWN_TICKS);
        stack.hurtAndBreak(1, player,
                hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new BogEyeTargetPayload(target));
        }
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 0.75F);
        return InteractionResult.SUCCESS;
    }

    private static BlockPos findNearestUnopenedContainer(ServerLevel level, BlockPos origin) {
        BlockPos nearest = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        int minChunkX = (origin.getX() - HORIZONTAL_RADIUS) >> 4;
        int maxChunkX = (origin.getX() + HORIZONTAL_RADIUS) >> 4;
        int minChunkZ = (origin.getZ() - HORIZONTAL_RADIUS) >> 4;
        int maxChunkZ = (origin.getZ() + HORIZONTAL_RADIUS) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof RandomizableContainerBlockEntity container)
                            || container.getLootTable() == null) {
                        continue;
                    }
                    BlockPos pos = container.getBlockPos();
                    int dx = pos.getX() - origin.getX();
                    int dy = pos.getY() - origin.getY();
                    int dz = pos.getZ() - origin.getZ();
                    if (Math.abs(dy) > VERTICAL_RADIUS
                            || dx * dx + dz * dz > HORIZONTAL_RADIUS * HORIZONTAL_RADIUS) {
                        continue;
                    }
                    double distanceSqr = dx * dx + dy * dy + dz * dz;
                    if (distanceSqr < nearestDistanceSqr) {
                        nearestDistanceSqr = distanceSqr;
                        nearest = pos.immutable();
                    }
                }
            }
        }
        return nearest;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (!ClientConfig.showCustomTooltips()) return;
        tooltip.accept(Component.empty());
        tooltip.accept(Component.translatable("tooltip.kingsandmonsters.when_used")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Right-click to locate nearby unopened loot")
                .withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.literal("Detects loot within 24 blocks")
                .withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.literal("Uses 1 durability when loot is found")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
