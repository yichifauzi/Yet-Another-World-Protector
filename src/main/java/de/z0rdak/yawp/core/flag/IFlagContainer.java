package de.z0rdak.yawp.core.flag;

public interface IFlagContainer {

   void put(IFlag flag);

   boolean contains(RegionFlag flag);

   boolean contains(String flag);

   void updateFlag(IFlag flag);

   void toggleFlag(String flag, boolean enable);

   void invertFlag(String flag);
}
