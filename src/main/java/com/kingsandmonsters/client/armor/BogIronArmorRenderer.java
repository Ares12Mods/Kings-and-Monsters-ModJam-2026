package com.kingsandmonsters.client.armor;

import com.kingsandmonsters.item.BogIronArmorItem;
import net.minecraft.world.entity.EquipmentSlot;
import com.geckolib.renderer.GeoArmorRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import java.util.List;

public class BogIronArmorRenderer extends GeoArmorRenderer<BogIronArmorItem, net.minecraft.client.renderer.entity.state.HumanoidRenderState> {
    public BogIronArmorRenderer() {
        super(new BogIronArmorModel());
    }

    @Override
    public List<ArmorSegment> getSegmentsForSlot(HumanoidRenderState state, EquipmentSlot currentSlot) {
        if (currentSlot == EquipmentSlot.LEGS) {
            return List.of(ArmorSegment.CHEST, ArmorSegment.LEFT_LEG, ArmorSegment.RIGHT_LEG);
        }
        return super.getSegmentsForSlot(state, currentSlot);
    }
}
