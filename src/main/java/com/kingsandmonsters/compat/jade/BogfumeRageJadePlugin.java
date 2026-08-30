package com.kingsandmonsters.compat.jade;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.ModMobEffects;
import com.kingsandmonsters.entity.OgreGrunt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.ITooltip;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin(KingsAndMonsters.MODID)
public class BogfumeRageJadePlugin implements IWailaPlugin {
    private static final Identifier BOGFUME_RAGE =
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "bogfume_rage");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(BogfumeRageProvider.INSTANCE, OgreGrunt.class);
    }

    private enum BogfumeRageProvider implements IComponentProvider<EntityAccessor> {
        INSTANCE;

        @Override
        public Identifier getUid() {
            return BOGFUME_RAGE;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (accessor.getEntity() instanceof LivingEntity entity && entity.hasEffect(ModMobEffects.BOGFUME_RAGE)) {
                tooltip.add(Component.translatable("jade.kingsandmonsters.bogfume_rage").withStyle(ChatFormatting.GREEN));
            }
        }
    }
}
