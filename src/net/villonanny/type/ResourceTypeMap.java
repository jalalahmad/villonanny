package net.villonanny.type;

import java.util.EnumMap;

/**
 * A map of Integers with ResourceType as key.
 * For each type of resource, holds the amount.
 */
public class ResourceTypeMap extends EnumMap<ResourceType, Integer> {

	public ResourceTypeMap() {
		super(ResourceType.class);
		init();
	}

	public ResourceTypeMap(ResourceTypeMap m) {
		super(m);
	}

	public ResourceTypeMap(int r0, int r1, int r2, int r3, int r4) {
		super(ResourceType.class);
		put(ResourceType.fromInt(0), r0);
		put(ResourceType.fromInt(1), r1);
		put(ResourceType.fromInt(2), r2);
		put(ResourceType.fromInt(3), r3);
		put(ResourceType.fromInt(4), r4);
	}
	
	private void init() {
		// Initialise with zero to avoid nullpointers when auto(un)boxing from Integer
		for (ResourceType type : ResourceType.values()) {
			put(type, 0);
		}
	}
	
	/**
	 * @return the sum of all values, which is the total resources contained in this map
	 */
	public int getSumOfValues() {
		int tot = 0;
		for (int val : values()) {
			tot += val;
		}
		return tot;
	}

	public int getWood() {
		return get(ResourceType.WOOD);
	}

	public int getClay() {
		return get(ResourceType.CLAY);
	}
	
	public int getIron() {
		return get(ResourceType.IRON);
	}
	
	public int getCrop() {
		return get(ResourceType.CROP);
	}

	public int getFood() {
		return get(ResourceType.FOOD);
	}
	
	/**
	 * Return true if all elements in the first map are lower or equal to the same elements in the second map
	 * @param other
	 * @return
	 */
	public boolean lowerThanOrEqual(ResourceTypeMap other) {
		for (ResourceType type : ResourceType.values()) {
			int first = this.get(type);
			int second = other.get(type);
			if (first > second) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return String.format("%s/%s/%s/%s/%s", getWood(), getClay(), getIron(), getCrop(), getFood());
	}

	public String toStringNoFood() {
		return String.format("%s/%s/%s/%s", getWood(), getClay(), getIron(), getCrop());
	}
	
	public void addResources(ResourceTypeMap toAdd) {
		for (ResourceType type : ResourceType.values()) {
			int currentValue = get(type);
			int newValue = currentValue + toAdd.get(type);
			put(type, newValue);
		}
	}
	
	public ResourceTypeMap getAverage(int tot) {
		ResourceTypeMap result = new ResourceTypeMap();
		for (ResourceType type : ResourceType.values()) {
			int currentValue = get(type);
			int average = currentValue/tot;
			result.put(type, average);
		}
		return result;
	}
	
	public ResourceTypeMap getDifference(ResourceTypeMap second) {
		ResourceTypeMap result = new ResourceTypeMap();
		for (ResourceType type : ResourceType.values()) {
			int currentValue = get(type);
			int difference = currentValue - second.get(type);
			result.put(type, difference);
		}
		return result;
	}

	public void removeResources(ResourceTypeMap resources) {
		for (ResourceType type : ResourceType.values()) {
			int currentValue = get(type);
			int newValue = currentValue - resources.get(type);
			put(type, newValue);
		}
	}

	/**
	 * mulitply by given factor
	 * @param mutiplier
	 * @author GAC
	 */
	public void multiply(int m) {
		for (ResourceType type : ResourceType.values()) {
			int currentValue = get(type);
			int newValue = currentValue * m;
			put(type, newValue);
		}
	}
	public void multiplyResources(ResourceTypeMap resources) {
		for (ResourceType type : ResourceType.values()) {
			int currentValue = get(type);
			int newValue = currentValue * resources.get(type);
			put(type, newValue);
		}
	}
	
}
