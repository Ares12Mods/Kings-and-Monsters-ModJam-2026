package com.kingsandmonsters.entity;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.ModEntities;
import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.ModSoundEvents;
import com.kingsandmonsters.api.patrol.PatrolTier;
import com.kingsandmonsters.entity.animation.SynchronizedAnimationController;
import com.kingsandmonsters.entity.animation.CanonicalOneShotState;
import com.kingsandmonsters.tribute.TributeManager;
import com.kingsandmonsters.world.OgreMerchantSpawnManager;
import com.kingsandmonsters.world.FortMapFactory;
import com.kingsandmonsters.world.FortTargetAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.network.chat.Component;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.network.syncher.*;
import org.jetbrains.annotations.Nullable;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.*;
import com.geckolib.animation.object.LoopType;
import com.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** A cowardly travelling trader accompanied by a neutral, anger-scaled Ogre escort. */
public class OgreMerchant extends AbstractVillager implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_RUNNING = SynchedEntityData.defineId(OgreMerchant.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WALKING = SynchedEntityData.defineId(OgreMerchant.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> DATA_TRADE_UNTIL = SynchedEntityData.defineId(OgreMerchant.class, EntityDataSerializers.LONG);
    private static final String GUARDS_SPAWNED_TAG = "MerchantGuardsSpawned";
    private static final String NATURAL_ENCOUNTER_TAG = "NaturalMerchantEncounter";
    private static final String NATURAL_SPAWN_TIME_TAG = "NaturalMerchantSpawnTime";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation WALK_2 = RawAnimation.begin().thenLoop("walk2");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation TRADE = RawAnimation.begin().then("Trade", LoopType.PLAY_ONCE);
    private static final String BRIBE_RECEIPT_NAME = "Ogre Truce";
    private static final double BASE_MOVEMENT_SPEED = 0.22;
    private static final double WANDER_MOVEMENT_SPEED = 0.14;
    private static final double PANIC_MOVEMENT_SPEED = 0.35;
    private static final int RUN_ANIMATION_GRACE_TICKS = 8;
    private static final int TRADE_APPROVAL_SOUND_COOLDOWN_TICKS = 50;
    private static final float TRADE_APPROVAL_MIN_PITCH = 0.96F;
    private static final float TRADE_APPROVAL_PITCH_VARIATION = 0.08F;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private boolean guardsSpawned;
    private boolean panicRunning;
    private int runAnimationGraceTicks;
    @Nullable private LivingEntity merchantThreat;
    private boolean offersInitialized;
    private double lastLocomotionX = Double.NaN;
    private double lastLocomotionZ = Double.NaN;
    private int movementAnimationGraceTicks;
    private int walkCycleTicks;
    private int lastTradeApprovalSoundTick = -TRADE_APPROVAL_SOUND_COOLDOWN_TICKS;
    private boolean alternateWalkCycle;
    private boolean naturalEncounter;
    private long naturalSpawnGameTime;
    private final CanonicalOneShotState visualOneShot = new CanonicalOneShotState();
    public OgreMerchant(EntityType<? extends OgreMerchant> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractVillager.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(DATA_RUNNING, false); builder.define(DATA_WALKING, false);
        builder.define(DATA_TRADE_UNTIL, 0L);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MerchantThreatAvoidGoal());
        goalSelector.addGoal(2, new TradeWithCustomerGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this,
                WANDER_MOVEMENT_SPEED / BASE_MOVEMENT_SPEED));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override protected void updateTrades(ServerLevel serverLevel) {
        List<Supplier<MerchantOffer>> cheap = List.of(
                () -> singleCost(Items.GOLD_INGOT, TradePrices.STEW_GOLD,
                        ModItems.IRONBELLY_STEW.get().getDefaultInstance(), 6));

        List<Supplier<MerchantOffer>> medium = List.of(
                () -> singleCost(Items.GOLD_INGOT, TradePrices.HOOKBLADE_GOLD, ModItems.OGRE_HOOKBLADE.get().getDefaultInstance(), 1),
                () -> singleCost(Items.GOLD_INGOT, TradePrices.BOG_EYE_GOLD, ModItems.BOG_EYE_CHARM.get().getDefaultInstance(), 1),
                () -> singleCost(Items.GOLD_INGOT, TradePrices.TRIBUTE_GOLD, ModItems.KINGS_TRIBUTE_BUNDLE.get().getDefaultInstance(), 1));
        List<Supplier<MerchantOffer>> premium = List.of(
                () -> doubleCost(Items.DIAMOND, 5, Items.GOLD_INGOT, 24, ModItems.RATTLEBONE_RING.get().getDefaultInstance()),
                () -> doubleCost(Items.DIAMOND, 5, Items.GOLD_INGOT, 28, ModItems.WARCALLER_BELL.get().getDefaultInstance()));
        List<Supplier<MerchantOffer>> rare = List.of(
                () -> doubleCost(ModItems.GOLDEN_OGRE_TOOTH.get(), 1, Items.DIAMOND, 8, ModItems.OGRE_MERCHANT_BACKPACK.get().getDefaultInstance()),
                () -> doubleCost(ModItems.GOLDEN_OGRE_TOOTH.get(), 2, Items.DIAMOND, 6, ModItems.OGREBLOOD_TOTEM.get().getDefaultInstance()),
                premium.get(0), premium.get(1));

        Set<net.minecraft.world.item.Item> results = new HashSet<>();
        addRandomOffers(cheap, 1, results);
        addRandomOffers(medium, 2, results);
        addRandomOffers(premium, 1, results);
        addRandomOffers(rare, 1, results);
        // The peace service is separate from merchandise so every Merchant can accept a bribe.
        getOffers().add(createBribeOffer());
        // The Fort Map is a dedicated guaranteed utility trade, never part of the random cheap-tier
        // pool above — see ensureFortMapOffer().
        ensureFortMapOffer();
    }

    private void addRandomOffers(List<Supplier<MerchantOffer>> pool, int count, Set<net.minecraft.world.item.Item> results) {
        List<Supplier<MerchantOffer>> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, new java.util.Random(random.nextLong()));
        for (Supplier<MerchantOffer> supplier : shuffled) {
            if (count <= 0) break;
            MerchantOffer offer = supplier.get();
            if (results.add(offer.getResult().getItem())) { getOffers().add(offer); count--; }
        }
    }

    /**
     * The Fort Map is a dedicated, guaranteed utility trade — never a member of the random
     * cheap/medium/premium/rare pools, so it can never lose out to shuffle/dedup logic or a pool
     * sized smaller than expected. Safe to call repeatedly (initial {@link #updateTrades()} and
     * every retry tick): it always scans the live offer list first, so it can never append a
     * second Fort Map trade no matter how many times it is invoked.
     */
    private void ensureFortMapOffer() {
        int existingCount = countFortMapOffers();
        if (existingCount > 0) {
            return;
        }

        MerchantOffer fortMap = createFortMapOffer();
        boolean appended = fortMap != null;
        if (appended) {
            getOffers().add(0, fortMap);
        }

    }

    private MerchantOffer createFortMapOffer() {
        if (!(level() instanceof ServerLevel serverLevel)) return null;
        var target = FortTargetAuthority.findNearestGuaranteedFort(serverLevel, blockPosition());
        return target
                .map(t -> FortMapFactory.createFortMap(serverLevel, t))
                .map(map -> singleCost(Items.GOLD_INGOT, TradePrices.FORT_MAP_GOLD, map, 1))
                .orElse(null);
    }

    /**
     * Item-identity duplicate scan, per the design requirement that duplicate protection must not
     * {@code Items.FILLED_MAP} is unique to the Fort Map among every offer this Merchant can ever
     * generate, so a plain item-type check is precise here.
     */
    private int countFortMapOffers() {
        int count = 0;
        for (MerchantOffer offer : getOffers()) {
            if (offer.getResult().is(Items.FILLED_MAP)) {
                count++;
            }
        }
        return count;
    }

    private MerchantOffer createBribeOffer() {
        ItemStack receipt = new ItemStack(Items.WRITABLE_BOOK);
        receipt.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(BRIBE_RECEIPT_NAME));
        receipt.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.translatable("tooltip.kingsandmonsters.ogre_truce.mechanic").withStyle(net.minecraft.ChatFormatting.DARK_GRAY),
                Component.translatable("tooltip.kingsandmonsters.ogre_truce.flavor_1").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.ITALIC),
                Component.translatable("tooltip.kingsandmonsters.ogre_truce.flavor_2").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.ITALIC))));
        return singleCost(ModItems.GOLDEN_OGRE_TOOTH.get(), 1, receipt, 3);
    }

    private static MerchantOffer singleCost(net.minecraft.world.level.ItemLike cost, int amount, ItemStack result, int uses) {
        return new MerchantOffer(new ItemCost(cost.asItem(), amount), result, uses, 1, 0.05F);
    }

    private static MerchantOffer doubleCost(net.minecraft.world.level.ItemLike first, int firstAmount,
            net.minecraft.world.level.ItemLike second, int secondAmount, ItemStack result) {
        return new MerchantOffer(new ItemCost(first.asItem(), firstAmount),
                java.util.Optional.of(new ItemCost(second.asItem(), secondAmount)), result, 1, 1, 0.05F);
    }

    @Override protected void rewardTradeXp(MerchantOffer offer) { }

    @Override public boolean showProgressBar() { return false; }

    @Override public void notifyTradeUpdated(ItemStack stack) { }

    @Override public SoundEvent getNotifyTradeSound() { return SoundEvents.EMPTY; }

    @Override protected SoundEvent getTradeUpdatedSound(boolean isYesSound) { return SoundEvents.EMPTY; }

    @Override public void playCelebrateSound() { }

    @Override protected SoundEvent getHurtSound(DamageSource source) {
        return ModSoundEvents.OGRE_MERCHANT_HURT.get();
    }

    @Nullable @Override public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) { return null; }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || isBaby() || player.isSecondaryUseActive()) {
            return super.mobInteract(player, hand);
        }
        if (isTrading()) {
            // The active customer already has the screen; everyone else must wait for it to close.
            return getTradingPlayer() == player ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (getOffers().isEmpty()) return InteractionResult.PASS;
        setTradingPlayer(player);
        openTradingScreen(player, getDisplayName(), 1);
        return InteractionResult.SUCCESS;
    }

    @Override public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);
        if (tickCount - lastTradeApprovalSoundTick >= TRADE_APPROVAL_SOUND_COOLDOWN_TICKS) {
            lastTradeApprovalSoundTick = tickCount;
            float pitch = TRADE_APPROVAL_MIN_PITCH
                    + getRandom().nextFloat() * TRADE_APPROVAL_PITCH_VARIATION;
            playSound(ModSoundEvents.OGRE_MERCHANT_TRADE.get(), 1.0F, pitch);
        }
        entityData.set(DATA_TRADE_UNTIL, level().getGameTime() + 25L);
        if (offer.getResult().is(Items.WRITABLE_BOOK)
                && offer.getResult().getHoverName().getString().equals(BRIBE_RECEIPT_NAME)
                && getTradingPlayer() instanceof ServerPlayer player) {
            int before = TributeManager.getFactionAngerLevel((ServerLevel) player.level());
            TributeManager.decayFactionAnger((ServerLevel) player.level(), TradePrices.TOOTH_BRIBE_ANGER_REDUCTION);
            int reduced = before - TributeManager.getFactionAngerLevel((ServerLevel) player.level());
            player.sendSystemMessage(Component.literal(reduced > 0
                    ? "Ogre anger eased by " + reduced + "." : "The Ogre Tribe bears you no anger."));
        }
    }

    private static final class TradePrices {
        private static final int STEW_GOLD = 8;
        private static final int FORT_MAP_GOLD = 12;
        private static final int HOOKBLADE_GOLD = 28;
        private static final int BOG_EYE_GOLD = 32;
        private static final int TRIBUTE_GOLD = 40;
        private static final int TOOTH_BRIBE_ANGER_REDUCTION = 10;
    }

    @Override public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
        boolean result = super.hurtServer(serverLevel, source, amount);
        if (result && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof OgreGrunt)) {
            merchantThreat = attacker;
            if (!level().isClientSide()) {
                for (OgreGrunt guard : level().getEntitiesOfClass(OgreGrunt.class, getBoundingBox().inflate(48),
                        candidate -> candidate.isMerchantGuardFor(getUUID()))) {
                    guard.defendMerchantFrom(attacker);
                }
            }
        }
        return result;
    }

    @Override public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            long tradeUntil = entityData.get(DATA_TRADE_UNTIL);
            if (tradeUntil > 0L && level().getGameTime() >= tradeUntil) {
                entityData.set(DATA_TRADE_UNTIL, 0L);
            }
            if (!offersInitialized) {
                // AbstractVillager normally creates offers lazily from the first GUI
                // interaction. Build/cache them during the entity's first server tick.
                getOffers();
                repairFortMapOffer();
                offersInitialized = true;
            }
            boolean moved = hasMeaningfulHorizontalMovement();
            if (moved) movementAnimationGraceTicks = 4;
            else if (movementAnimationGraceTicks > 0) movementAnimationGraceTicks--;
            boolean movementVisible = movementAnimationGraceTicks > 0;
            if (panicRunning) runAnimationGraceTicks = RUN_ANIMATION_GRACE_TICKS;
            else if (runAnimationGraceTicks > 0) runAnimationGraceTicks--;
            boolean runningVisual = runAnimationGraceTicks > 0;
            boolean trading = isTrading();
            entityData.set(DATA_RUNNING, !trading && runningVisual);
            entityData.set(DATA_WALKING,
                    !trading && !runningVisual && movementVisible && !navigation.isDone());
        }
        if (entityData.get(DATA_WALKING) && level().getGameTime() >= entityData.get(DATA_TRADE_UNTIL)) {
            if (++walkCycleTicks >= 40) {
                walkCycleTicks = 0;
                alternateWalkCycle = !alternateWalkCycle;
            }
        } else {
            walkCycleTicks = 0;
        }
        if (!level().isClientSide() && naturalEncounter
                && level().getGameTime() - naturalSpawnGameTime >= OgreMerchantSpawnManager.NATURAL_LIFETIME
                && !isTrading() && level().getGameTime() >= entityData.get(DATA_TRADE_UNTIL)
                && level().getNearestPlayer(this, 16) == null) {
            removeNaturalEncounter();
        }
    }

    /** Replaces any persisted development-era Fort Map offer so obsolete reservations are never sold. */
    private void repairFortMapOffer() {
        getOffers().removeIf(offer -> offer.getResult().is(Items.FILLED_MAP));
        ensureFortMapOffer();
    }

    @Nullable @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (!level().isClientSide()) {
            getOffers();
            offersInitialized = true;
        }
        if (reason == EntitySpawnReason.NATURAL && !guardsSpawned && level instanceof ServerLevel server) {
            naturalEncounter = true;
            naturalSpawnGameTime = server.getGameTime();
            guardsSpawned = true;
            PatrolTier guardTier = TributeManager.findNearestCamp(server, blockPosition(),
                            OgreMerchantSpawnManager.PREFERRED_STRUCTURE_MAX_DISTANCE)
                    .map(camp -> TributeManager.getOrCreate(server, camp).getCurrentPatrolTier())
                    .orElse(PatrolTier.SMALL);
            List<EntityType<? extends OgreGrunt>> escort = new ArrayList<>();
            escort.add(ModEntities.OGRE_GRUNT.get());
            escort.add(ModEntities.OGRE_GRUNT.get());
            if (guardTier == PatrolTier.MEDIUM || guardTier == PatrolTier.LARGE) {
                escort.add(ModEntities.OGRE_ARCHER.get());
            }
            if (guardTier == PatrolTier.LARGE) {
                escort.add(ModEntities.OGRE_GRUNT_CAPTAIN.get());
            }
            BlockPos[] offsets = {
                    new BlockPos(2, 0, 1),
                    new BlockPos(-2, 0, -1),
                    new BlockPos(-1, 0, 2),
                    new BlockPos(1, 0, -2)
            };
            for (int i = 0; i < escort.size(); i++) {
                BlockPos pos = blockPosition().offset(offsets[i]);
                OgreGrunt guard = escort.get(i).spawn(server, pos, EntitySpawnReason.MOB_SUMMONED);
                if (guard == null) continue;
                guard.assignMerchantGuard(getUUID());
            }
        }
        return result;
    }

    @Override public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(GUARDS_SPAWNED_TAG, guardsSpawned);
        tag.putBoolean(NATURAL_ENCOUNTER_TAG, naturalEncounter);
        tag.putLong(NATURAL_SPAWN_TIME_TAG, naturalSpawnGameTime);
    }
    @Override public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        guardsSpawned = tag.getBooleanOr(GUARDS_SPAWNED_TAG, false);
        naturalEncounter = tag.getBooleanOr(NATURAL_ENCOUNTER_TAG, false);
        naturalSpawnGameTime = tag.getLongOr(NATURAL_SPAWN_TIME_TAG, 0L);
    }

    public boolean isNaturalEncounter() { return naturalEncounter; }

    private void removeNaturalEncounter() {
        if (level() instanceof ServerLevel server) {
            java.util.List<OgreGrunt> guards = new java.util.ArrayList<>();
            for (Entity entity : server.getAllEntities()) {
                if (entity instanceof OgreGrunt guard && guard.isMerchantGuardFor(getUUID())) guards.add(guard);
            }
            guards.forEach(Entity::discard);
        }
        discard();
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SynchronizedAnimationController<>(this, "Merchant Movement", 4, state -> {
            long now = level().getGameTime();
            long tradeUntil = entityData.get(DATA_TRADE_UNTIL);
            int visualType = visualOneShot.update(state.controller(), tradeUntil > 0L ? 1 : 0,
                    tradeUntil > 0L ? tradeUntil - 25L : 0L, ignored -> 25, now);
            if (visualType > 0) {
                return state.setAndContinue(TRADE);
            }
            if (entityData.get(DATA_RUNNING)) return state.setAndContinue(RUN);
            if (entityData.get(DATA_WALKING)) return state.setAndContinue(alternateWalkCycle ? WALK_2 : WALK);
            return state.setAndContinue(IDLE);
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }

    private boolean hasMeaningfulHorizontalMovement() {
        if (Double.isNaN(lastLocomotionX)) {
            lastLocomotionX = getX();
            lastLocomotionZ = getZ();
            return false;
        }
        double dx = getX() - lastLocomotionX;
        double dz = getZ() - lastLocomotionZ;
        lastLocomotionX = getX();
        lastLocomotionZ = getZ();
        return dx * dx + dz * dz > 0.00001;
    }

    private class TradeWithCustomerGoal extends Goal {
        private TradeWithCustomerGoal() {
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isTrading() && getTradingPlayer() != null && getTradingPlayer().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            Player customer = getTradingPlayer();
            getNavigation().stop();
            setXxa(0.0F);
            setZza(0.0F);
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            if (customer != null) {
                getLookControl().setLookAt(customer, 30.0F, 30.0F);
            }
        }
    }

    private class MerchantThreatAvoidGoal extends AvoidEntityGoal<LivingEntity> {
        MerchantThreatAvoidGoal() {
            super(OgreMerchant.this,
                    LivingEntity.class,
                    candidate -> candidate == merchantThreat,
                    24.0F,
                    PANIC_MOVEMENT_SPEED / BASE_MOVEMENT_SPEED,
                    PANIC_MOVEMENT_SPEED / BASE_MOVEMENT_SPEED,
                    candidate -> candidate == merchantThreat);
        }

        @Override public boolean canUse() {
            if (merchantThreat == null || !merchantThreat.isAlive() || isTrading()) return false;
            return super.canUse();
        }

        @Override public void start() {
            panicRunning = true;
            // MOVE flag and priority 1 immediately preempt eager approach and wandering.
            super.start();
        }

        @Override public void stop() {
            super.stop();
            panicRunning = false;
            if (merchantThreat != null
                    && (!merchantThreat.isAlive() || distanceToSqr(merchantThreat) >= 24.0 * 24.0)) {
                merchantThreat = null;
            }
        }
    }
}
