package com.kingsandmonsters.item;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.ModMobEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

public final class WarcallerBellEvents {
    private static final double RADIUS = 16.0;
    private static final int REQUIRED_ATTACKERS = 3;
    private static final int HOSTILE_SCAN_INTERVAL_TICKS = 5;
    private static final int BONUS_LINGER_TICKS = 2 * 20;
    // Just needs to comfortably outlast the gap between scans — the true active/linger window is
    // still governed entirely by bonusActive below, since we explicitly add/remove this effect
    // every time this method runs rather than relying on its own timer to expire.
    private static final int WARCALLED_EFFECT_DURATION_TICKS = HOSTILE_SCAN_INTERVAL_TICKS + 5;
    private static final String BONUS_UNTIL_TAG = "KingsAndMonstersWarcallerBonusUntil";
    private static final Identifier HEALTH_BONUS_ID = Identifier.fromNamespaceAndPath(
            KingsAndMonsters.MODID, "warcaller_bell_health");
    private static final Identifier ARMOR_BONUS_ID = Identifier.fromNamespaceAndPath(
            KingsAndMonsters.MODID, "warcaller_bell_armor");
    private static final AttributeModifier HEALTH_BONUS = new AttributeModifier(
            HEALTH_BONUS_ID, 2.0, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier ARMOR_BONUS = new AttributeModifier(
            ARMOR_BONUS_ID, 4.0, AttributeModifier.Operation.ADD_VALUE);
    private static final DustParticleOptions ACTIVATION_DUST =
            new DustParticleOptions(0xC79E47, 1.2F);

    private WarcallerBellEvents() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        boolean wearingBell = hasWarcallerBell(player);
        if (wearingBell && player.tickCount % HOSTILE_SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        List<Mob> nearbyHostiles = wearingBell
                ? player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RADIUS),
                        mob -> mob instanceof Enemy
                                && mob.isAlive()
                                && !mob.is(Tags.EntityTypes.BOSSES))
                : List.of();

        if (wearingBell && player.tickCount % 20 == 0) {
            drawAggro(player, nearbyHostiles);
        }

        int targetingPlayer = 0;
        for (Mob hostile : nearbyHostiles) {
            if (hostile.getTarget() == player && ++targetingPlayer >= REQUIRED_ATTACKERS) {
                break;
            }
        }
        boolean thresholdMet = wearingBell && targetingPlayer >= REQUIRED_ATTACKERS;
        long gameTime = player.level().getGameTime();
        if (thresholdMet) {
            player.getPersistentData().putLong(BONUS_UNTIL_TAG, gameTime + BONUS_LINGER_TICKS);
        } else if (!wearingBell) {
            player.getPersistentData().remove(BONUS_UNTIL_TAG);
        }
        boolean bonusActive = wearingBell
                && (thresholdMet || player.getPersistentData().getLongOr(BONUS_UNTIL_TAG, 0L) > gameTime);
        setSurroundedBonus(player, bonusActive);
    }

    private static void drawAggro(ServerPlayer wearer, List<Mob> hostiles) {
        for (Mob hostile : hostiles) {
            LivingEntity currentTarget = hostile.getTarget();
            if ((currentTarget == null || currentTarget instanceof ServerPlayer)
                    && hostile.getRandom().nextFloat() < 0.35F) {
                hostile.setTarget(wearer);
            }
        }
    }

    private static void setSurroundedBonus(ServerPlayer player, boolean active) {
        updateModifier(player.getAttribute(Attributes.MAX_HEALTH), HEALTH_BONUS, active);
        updateModifier(player.getAttribute(Attributes.ARMOR), ARMOR_BONUS, active);
        if (!active && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        updateWarcalledEffect(player, active);
    }

    private static void updateWarcalledEffect(ServerPlayer player, boolean active) {
        boolean wasActive = player.hasEffect(ModMobEffects.WARCALLED);
        if (active) {
            // Re-applied every time this runs so the icon's own timer never has to be the source
            // of truth — bonusActive already carries the linger, this just mirrors it.
            player.addEffect(new MobEffectInstance(ModMobEffects.WARCALLED,
                    WARCALLED_EFFECT_DURATION_TICKS, 0, false, true, true));
            if (!wasActive) {
                playActivationCue(player);
            }
        } else if (wasActive) {
            player.removeEffect(ModMobEffects.WARCALLED);
        }
    }

    private static void playActivationCue(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK,
                SoundSource.PLAYERS, 0.6F, 0.7F);
        serverLevel.sendParticles(ACTIVATION_DUST,
                player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                24, player.getBbWidth() * 0.6, player.getBbHeight() * 0.5, player.getBbWidth() * 0.6, 0.02);
    }

    private static void updateModifier(AttributeInstance attribute, AttributeModifier modifier, boolean active) {
        if (attribute == null) {
            return;
        }
        boolean present = attribute.getModifier(modifier.id()) != null;
        if (active && !present) {
            attribute.addTransientModifier(modifier);
        } else if (!active && present) {
            attribute.removeModifier(modifier.id());
        }
    }

    private static boolean hasWarcallerBell(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .map(inventory -> inventory.findFirstCurio(
                        stack -> stack.is(ModItems.WARCALLER_BELL.get())).isPresent())
                .orElse(false);
    }
}
