package net.villonanny.type;

import java.util.EnumMap;

import net.villonanny.entity.Field;

/**
 * A map of Field with ResourceType as key.
 * For each resource type, holds a Field of that type.
 */
public class ResourceTypeToFieldMap extends EnumMap<ResourceType, Field> {

	public ResourceTypeToFieldMap() {
		super(ResourceType.class);
	}

	public ResourceTypeToFieldMap(ResourceTypeToFieldMap m) {
		super(m);
	}

// Not used yet
//	public Field getWoodcutter() {
//		return get(ResourceType_REF.WOOD);
//	}
//
//	public Field getClayPit() {
//		return get(ResourceType_REF.CLAY);
//	}
//	
//	public Field getIronMine() {
//		return get(ResourceType_REF.IRON);
//	}
//	
//	public Field getCropland() {
//		return get(ResourceType_REF.CROP);
//	}

	
}
