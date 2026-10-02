package com.dogpound.buddybuilder;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A friend who lives in the world save: keeps her name, skin, friendship and memories across restarts. */
public class EntityBuddy extends EntityCreature {
    private static final DataParameter<String> SKIN = EntityDataManager.createKey(EntityBuddy.class, DataSerializers.STRING);
    private static final DataParameter<Integer> FRIENDSHIP = EntityDataManager.createKey(EntityBuddy.class, DataSerializers.VARINT);
    private static final DataParameter<String> MODE = EntityDataManager.createKey(EntityBuddy.class, DataSerializers.STRING);
    public final List<String> memories = new ArrayList<>();
    public UUID owner;
    public double goX, goY, goZ;
    public boolean hasGoal;

    public EntityBuddy(World w) {
        super(w);
        setSize(0.6F, 1.8F);
        enablePersistence();
        setAlwaysRenderNameTag(true);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(SKIN, "");
        dataManager.register(FRIENDSHIP, 0);
        dataManager.register(MODE, "follow");
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(6, new EntityAIWanderAvoidWater(this, 0.6D) {
            @Override public boolean shouldExecute() { return "wander".equals(getMode()) && super.shouldExecute(); }
        });
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(64.0D);
    }

    public String getSkin() { return dataManager.get(SKIN); }
    public void setSkin(String s) { dataManager.set(SKIN, s); }
    public int getFriendship() { return dataManager.get(FRIENDSHIP); }
    public void addFriendship(int d) { dataManager.set(FRIENDSHIP, Math.max(-100, Math.min(1000, getFriendship() + d))); }
    public String getMode() { return dataManager.get(MODE); }
    public void setMode(String m) { dataManager.set(MODE, m); hasGoal = false; }

    public String friendTitle() {
        int f = getFriendship();
        return f >= 400 ? "best friend" : f >= 150 ? "close friend" : f >= 50 ? "friend" : f >= 10 ? "getting to know you" : "just met";
    }

    public void remember(String fact) {
        memories.add(fact);
        while (memories.size() > 40) memories.remove(0);
    }

    public void goTo(double x, double y, double z) {
        goX = x; goY = y; goZ = z; hasGoal = true;
        dataManager.set(MODE, "stay");
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (world.isRemote || ticksExisted % 10 != 0) return;
        if (hasGoal) {
            if (getDistanceSq(goX, goY, goZ) < 4) hasGoal = false;
            else if (getNavigator().noPath() && !getNavigator().tryMoveToXYZ(goX, goY, goZ, 1.0D) && getDistanceSq(goX, goY, goZ) > 900)
                setPositionAndUpdate(goX, goY, goZ); // too far to walk: hop there
            return;
        }
        EntityPlayer p = owner == null ? null : world.getPlayerEntityByUUID(owner);
        if (p != null && "follow".equals(getMode())) {
            double d = getDistanceSq(p);
            if (d > 32 * 32) setPositionAndUpdate(p.posX + 1, p.posY, p.posZ + 1);
            else if (d > 16) getNavigator().tryMoveToEntityLiving(p, 1.0D);
            else getNavigator().clearPath();
        }
    }

    /** Right-click with something to give it to her as a gift. */
    @Override
    protected boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) return false;
        if (!world.isRemote) {
            String name = held.getDisplayName();
            if (!player.capabilities.isCreativeMode) held.shrink(1);
            addFriendship(5);
            remember("You gave me a " + name + " as a gift.");
            player.sendMessage(new TextComponentString("§d" + getName() + ": §fAww, a " + name + "! Thank you ❤ (" + friendTitle() + ")"));
        } else {
            for (int i = 0; i < 7; i++)
                world.spawnParticle(EnumParticleTypes.HEART, posX + (rand.nextDouble() - 0.5), posY + 2.0, posZ + (rand.nextDouble() - 0.5), 0, 0.1, 0);
        }
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (src.getTrueSource() instanceof EntityPlayer && !world.isRemote) {
            addFriendship(-10);
            remember("You hit me once. That hurt.");
        }
        return super.attackEntityFrom(src, amount);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setString("BuddySkin", getSkin());
        nbt.setInteger("Friendship", getFriendship());
        nbt.setString("Mode", getMode());
        if (owner != null) nbt.setUniqueId("Owner", owner);
        NBTTagList list = new NBTTagList();
        for (String m : memories) list.appendTag(new NBTTagString(m));
        nbt.setTag("Memories", list);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        setSkin(nbt.getString("BuddySkin"));
        dataManager.set(FRIENDSHIP, nbt.getInteger("Friendship"));
        if (!nbt.getString("Mode").isEmpty()) dataManager.set(MODE, nbt.getString("Mode"));
        if (nbt.hasUniqueId("Owner")) owner = nbt.getUniqueId("Owner");
        memories.clear();
        NBTTagList list = nbt.getTagList("Memories", 8);
        for (int i = 0; i < list.tagCount(); i++) memories.add(list.getStringTagAt(i));
    }
}
