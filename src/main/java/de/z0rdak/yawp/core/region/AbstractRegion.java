package de.z0rdak.yawp.core.region;

import de.z0rdak.yawp.commands.RegionCommands;
import de.z0rdak.yawp.core.group.PlayerContainer;
import de.z0rdak.yawp.core.flag.FlagContainer;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.flag.RegionFlag;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.*;
import java.util.stream.Collectors;

import static de.z0rdak.yawp.util.constants.RegionNBT.*;

/**
 * An abstract region represents the basic implementation of a IProtectedRegion.
 * This abstraction can be used for markable regions as well as regions without
 * an area (dimensions). <br>
 */
public abstract class AbstractRegion implements IProtectedRegion {
    protected String name;
    protected ResourceKey<Level> dimension;
    protected RegionType regionType;
    protected FlagContainer flags;
    protected Map<String, PlayerContainer> groups;
    protected boolean isActive;
    protected boolean isMuted;
    @Nullable
    protected IProtectedRegion parent;
    protected String parentName;
    protected Map<String, IProtectedRegion> children;
    protected Set<String> childrenNames;

    protected AbstractRegion(CompoundTag nbt) {
        this.childrenNames = new HashSet<>(0);
        this.children = new HashMap<>(0);
        this.parentName = null;
        this.parent = null;
        this.flags = new FlagContainer();
        this.groups = new HashMap<>();
        this.groups.put(MEMBERS, new PlayerContainer());
        this.groups.put(OWNERS, new PlayerContainer());
        this.deserializeNBT(nbt);
    }

    protected AbstractRegion(String name, RegionType type) {
        this.name = name;
        this.regionType = type;
        this.flags = new FlagContainer();
        this.groups = new HashMap<>();
        this.groups.put(MEMBERS, new PlayerContainer());
        this.groups.put(OWNERS, new PlayerContainer());
        this.isActive = true;
        this.children = new HashMap<>();
    }


    protected AbstractRegion(String name, ResourceKey<Level> dimension, RegionType type) {
        this.name = name;
        this.dimension = dimension;
        this.regionType = type;
        this.flags = new FlagContainer();
        this.groups = new HashMap<>();
        this.groups.put(MEMBERS, new PlayerContainer());
        this.groups.put(OWNERS, new PlayerContainer());
        this.children = new HashMap<>();
        this.isActive = true;
    }

    /**
     * Minimal constructor to create an abstract region by supplying a name, type and an owner.
     *
     * @param name  name of the region
     * @param owner region owner
     */
    protected AbstractRegion(String name, RegionType regionType, Player owner) {
        this(name, regionType);
        if (owner != null) {
            this.groups.get(OWNERS).addPlayer(owner.getUUID(), owner.getScoreboardName());
        }
    }

    protected AbstractRegion(String name, ResourceKey<Level> dimension, RegionType regionType, Player owner) {
        this(name, dimension, regionType);
        if (owner != null) {
            this.groups.get(OWNERS).addPlayer(owner.getUUID(), owner.getScoreboardName());
        }
    }

    @Nullable
    @Override
    public String getParentName() {
        return parentName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ResourceKey<Level> getDim() {
        return dimension;
    }

    @Override
    public void addFlag(IFlag flag) {
        this.flags.put(flag);
    }

    @Override
    public void removeFlag(String flag) {
        this.flags.remove(flag);
    }

    public boolean containsFlag(RegionFlag flag) {
        return this.flags.contains(flag);
    }

    @Override
    public boolean containsFlag(String flag) {
        return this.flags.contains(flag);
    }

    @Override
    public Collection<IFlag> getFlags() {
        return Collections.unmodifiableList(new ArrayList<>(this.flags.values()));
    }

    @Override
    public FlagContainer getFlagContainer() {
        return flags;
    }

    @Nullable
    @Override
    public IFlag getFlag(String flagName) {
        if (this.flags.contains(flagName)) {
            return this.flags.get(flagName);
        } else {
            return null;
        }
    }

    public void updateFlag(IFlag flag) {
        this.flags.put(flag);
    }

    public void toggleFlag(String flag, boolean enable) {
        if (this.containsFlag(flag)) {
            this.flags.get(flag).setIsActive(enable);
        }
    }

    public void invertFlag(String flag) {
        if (this.containsFlag(flag)) {
            this.flags.get(flag).setOverride(this.flags.get(flag).doesOverride());
        }
    }

    @Override
    public boolean isActive() {
        return this.isActive;
    }

    @Override
    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public boolean isMuted() {
        return false;
    }

    @Override
    public void setIsMuted(boolean isMuted) {
        this.isMuted = isMuted;
    }

    @Override
    public void addPlayer(Player player, String group) {
        this.getGroup(group).addPlayer(player.getUUID(), player.getScoreboardName());
    }

    @Override
    public void addTeam(String teamName, String group) {
        this.getGroup(group).addTeam(teamName);
    }

    @Override
    public void removeTeam(String teamName, String group) {
        this.getGroup(group).removeTeam(teamName);
    }

    @Override
    public void removePlayer(UUID playerUuid, String group) {
        this.getGroup(group).removePlayer(playerUuid);
    }

    @Override
    public boolean hasTeam(String teamName, String group) {
        return this.getGroup(group).hasTeam(teamName);
    }

    @Override
    public boolean hasPlayer(UUID playerUuid, String group) {
        return this.getGroup(group).hasPlayer(playerUuid);
    }

    /**
     * Gets the container for the provided group. Creates a new one if none is existent.
     * @param group
     * @return
     */
    @Override
    public PlayerContainer getGroup(String group) {
        if (!this.groups.containsKey(group)) {
            // FIXME: return null instead to signal non-existing group? or manage them properly
           return this.groups.put(group, new PlayerContainer());
        }
        return this.groups.get(group);
    }

    /**
     * Checks if the player is defined in the regions player list OR whether the player is an operator.
     * Usually this check is needed when an event occurs, and it needs to be checked whether
     * the player has a specific permission to perform an action in the region.
     *
     * @param player to be checked
     * @return true if player is in region list or is an operator, false otherwise
     */
    @Override
    public boolean permits(Player player) {
        return isInGroup(player, RegionCommands.OWNER) || isInGroup(player, RegionCommands.MEMBER);
    }

    public boolean isInGroup(Player player, String group) {
        return this.groups.get(group).hasPlayer(player.getUUID())
                || (player.getTeam() != null && this.groups.get(group).hasTeam(player.getTeam().getName()));
    }


    @Override
    public boolean disallows(Player player) {
        return !permits(player);
    }

    /**
     * Will always be called by IMarkableRegion to remove child of type IMarkableRegion
     *
     * @param child
     */
    @Override
    public void removeChild(IProtectedRegion child) {
        this.children.remove(child.getName());
    }

    /**
     * TODO: Global region stuff
     * Most error handling and consistency checks are done beforehand by the ArgumentTypes for add and removing children.
     * Try to add a child region to this region. <br>
     * 1. The child already has a parent which is a local region  or <br>
     * 2. The child is the same regions as this or <br>
     * 3. The child is the parent of this region <br>
     * 4. The child area is not completely contained by the parent area. (done beforehand)
     * Also removes child from dimension region
     * FIXME: since this method does manage more than adding children, it should be renamed
     *
     * @param child child to add to this region.
     */
    @Override
    public void addChild(@Nonnull IProtectedRegion child) {
        IProtectedRegion childParent = child.getParent();
        if (childParent != null) {
            boolean hasDimRegionParent = childParent instanceof DimensionalRegion;
            boolean hasLocalRegionParent = childParent instanceof IMarkableRegion;
            if (hasLocalRegionParent) {
                if (!childParent.equals(this) && this instanceof IMarkableRegion) {
                    // TODO: Why not allow this, as long as the owner of both regions are the same?
                    throw new IllegalRegionStateException("Not allowed to \"steal\" child from other parent than a dimensional region");
                }
                if (this instanceof DimensionalRegion) {
                    childParent.removeChild(child);
                }
            }
            if (hasDimRegionParent) {
                childParent.removeChild(child);
            }
        }
        child.setParent(this);
        this.children.put(child.getName(), child);
    }

    @Override
    public Map<String, IProtectedRegion> getChildren() {
        return Collections.unmodifiableMap(this.children);
    }

    @Override
    public Set<String> getChildrenNames() {
        return this.childrenNames;
    }

    @Override
    public boolean hasChild(IProtectedRegion maybeChild) {
        return this.children.containsKey(maybeChild.getName());
    }

    /**
     * FIXME: setParent should not be used directly. Use addChild instead
     * Contains common consistency checks for setting a parent region.
     * More specific checks and assignments need to be implemented in subclasses.
     *
     * @param parent the parent to set for this region.
     * @throws IllegalRegionStateException when consistency checks are failing.
     */
    public boolean setParent(IProtectedRegion parent) {
        if (parent instanceof DimensionalRegion) {
            this.parent = parent;
            return true;
        }
        if (!parent.getDim().location().equals(GlobalRegion.GLOBAL) || !(parent instanceof GlobalRegion)) {
            if (!parent.getDim().location().equals(this.dimension.location())) {
                throw new IllegalRegionStateException("Region '" + parent.getName() + "' is not in the same dimension!");
            }
        }
        if (parent.equals(this)) {
            throw new IllegalRegionStateException("Region '" + parent.getName() + "' can't be its own parent!");
        }
        if (children.containsKey(parent.getName())) {
            throw new IllegalRegionStateException("Parent '" + parent.getName() + "' is already set as child for region '" + this.getName() + "'!");
        }
        return parent.hasChild(this);
    }

    @Nullable
    public IProtectedRegion getParent() {
        return parent;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(NAME, this.name);
        nbt.putString(DIM, this.dimension.location().toString());
        nbt.putString(REGION_TYPE, this.regionType.type);
        nbt.putBoolean(ACTIVE, this.isActive);
        nbt.putBoolean(MUTED, this.isMuted);
        nbt.put(FLAGS, this.flags.serializeNBT());
        nbt.put(OWNERS, this.groups.get(OWNERS).serializeNBT());
        nbt.put(MEMBERS, this.groups.get(MEMBERS).serializeNBT());
        if (this.parent != null) {
            nbt.putString(PARENT, this.parent.getName());
        } else {
            nbt.putString(PARENT, "");
        }
        if (this.children != null) {
            ListTag childrenList = new ListTag();
            childrenList.addAll(this.children.keySet().stream()
                    .map(StringTag::valueOf)
                    .collect(Collectors.toSet()));
            nbt.put(CHILDREN, childrenList);
        } else {
            nbt.put(CHILDREN, new ListTag());
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.name = nbt.getString(NAME);
        this.dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY,
                new ResourceLocation(nbt.getString(DIM)));
        this.isActive = nbt.getBoolean(ACTIVE);
        this.isMuted = nbt.getBoolean(MUTED);
        this.regionType = RegionType.of(nbt.getString(REGION_TYPE));
        this.flags = new FlagContainer(nbt.getCompound(FLAGS));
        this.groups = new HashMap<>();
        this.groups.put(OWNERS, new PlayerContainer(nbt.getCompound(OWNERS)));
        this.groups.put(MEMBERS, new PlayerContainer(nbt.getCompound(MEMBERS)));
        if (this.parent == null) {
            // deserialize parent only if present and if this is no instance of GlobalRegion
            if (nbt.contains(PARENT, Tag.TAG_STRING) && !(this instanceof GlobalRegion)) {
                String parentName = nbt.getString(PARENT);
                if (!parentName.equals("")) {
                    this.parentName = nbt.getString(PARENT);
                } else {
                    this.parentName = null;
                }
            }
        }
        if (this.children != null && this.children.isEmpty()) {
            if (nbt.contains(CHILDREN, Tag.TAG_LIST)) {
                ListTag childrenNbt = nbt.getList(CHILDREN, Tag.TAG_STRING);
                if (childrenNbt.size() > 0) {
                    this.children = new HashMap<>(childrenNbt.size());
                    this.childrenNames = new HashSet<>(childrenNbt.size());
                    for (int i = 0; i < childrenNbt.size(); i++) {
                        this.childrenNames.add(childrenNbt.getString(i));
                    }
                }
            }
        }
    }
}
