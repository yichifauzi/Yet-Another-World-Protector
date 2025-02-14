package de.z0rdak.yawp.core.flag;

import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;

/**
 *
 * ListFlag
 * Blacklist or Whitelist
 *
 * Usable for placing/breaking blocks, item usage
 * Granular entity spawn control
 *
 * /rs flag add <region> <flag>
 * /rs flag add <region> block-blacklist <modid:block> [... <modid:block>]
 * Remove all blocks from blacklist and blacklist itself
 * /rs flag remove <region> block-blacklist
 * Removes all given blocks from blacklist
 * /rs flag remove <region> block-blacklist <modid:block> [... <modid:block>]
 * Removes all blocks from blacklist
 * /rs flag remove <region> block-blacklist clear
 *
 * /wp flag ... max-snow-layer-height 4
 * NumberFlag
 * /rs flag add <region> max-level 30
 * /rs flag add <region>
 *
 * // Trigger
 * /rs trigger <region> on-leave clear-xp
 *
 */
public interface IFlag extends INBTSerializable<CompoundNBT>, Comparable<IFlag> {

    /**
     * Get the unique identifier for the flag. <br>
     * The valid flags are currently stored as an enum. <br>
     * Mod:Name -> ResourceLocation in the future.
     *
     * @return unique name for flag.
     * @see RegionFlag
     */
    String getName();

    /**
     * Returns the flag type of the flag.     *
     * @return the flag type enum value of the flag.
     * @see FlagType
     */
    FlagType getType();

    /**
     * Returns whether the flag does override the same flag defined in child regions. <br>
     * @return true if the flag overrides the same flag in child regions
     */
    boolean doesOverride();

    /**
     * Set the override state of the flag. <br>
     * When true, it overrides the same flag in child regions.
     * @param doesOverride overrides the same flag in child regions if set to true
     */
    void setOverride(boolean doesOverride);

    /**
     * Returns whether the flag is active in the region. <br></br>
     * This means the flag state is either ALLOWED or DENIED. <br></br>
     * Disabled flags are not considered for flag checks.
     * @return true if flag is active, false otherwise.
     */
    boolean isActive();

    FlagState getState();

    void setState(FlagState state);

    FlagMessage getFlagMsg();

    void setFlagMsg(FlagMessage msg);
}
