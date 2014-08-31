package net.villonanny.type;


public enum ResourceType {
   // String values must match language configuration keys, like in "key.wood = legno"
   WOOD("wood", "1"),
   CLAY("clay", "2"),
   IRON("iron", "3"),
   CROP("crop", "4"),
   FOOD("food", "5")
   ;
   // HOURLY_CROP("hourly crop");

   private final String stringValue;
   private final String imageClassOrPath;
   
   private ResourceType(String name, String imageClassOrPath) {
      this.stringValue = name;
      this.imageClassOrPath = imageClassOrPath;
   }
   
   public String toString() {
      return stringValue;
   }

   public static ResourceType fromInt(int num) {
      return ResourceType.values()[num]; // Starts at 0
   }
   
   public int toInt() {
      return ordinal(); // Starts at 0
   }

   public String getImageClassOrPath() {
      return imageClassOrPath;
   }
   
   public static ResourceType fromString(String name) {
        return ResourceType.valueOf(name.toUpperCase());
    }
}
