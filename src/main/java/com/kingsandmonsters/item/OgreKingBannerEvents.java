package com.kingsandmonsters.item;

import com.kingsandmonsters.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class OgreKingBannerEvents {
    private OgreKingBannerEvents() {
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBlockEntity() instanceof BannerBlockEntity banner)) {
            return;
        }

        ItemStack replacement = banner.getPatterns().layers().stream()
                .map(layer -> layer.pattern().value().assetId().getPath())
                .map(path -> switch (path) {
                    case "ogre_brute" -> new ItemStack(ModItems.OGRE_BRUTE_BANNER.get());
                    case "grunt_captain" -> new ItemStack(ModItems.GRUNT_CAPTAIN_BANNER.get());
                    case "ogre_king" -> new ItemStack(ModItems.OGRE_KINGS_BANNER.get());
                    case "ogre_mage" -> new ItemStack(ModItems.OGRE_MAGE_BANNER.get());
                    default -> ItemStack.EMPTY;
                })
                .filter(stack -> !stack.isEmpty())
                .findFirst()
                .orElse(ItemStack.EMPTY);
        if (replacement.isEmpty()) {
            return;
        }

        event.getDrops().clear();
        event.getDrops().add(new ItemEntity(
                event.getLevel(),
                event.getPos().getX() + 0.5,
                event.getPos().getY() + 0.5,
                event.getPos().getZ() + 0.5,
                replacement));
    }
}
