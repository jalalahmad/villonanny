package net.villonanny.type;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import net.villonanny.entity.Building;

/**
 * A map of Building with BuildingType as key.
 * For each building type, holds a list of Buildings of that type.
 */
public class BuildingTypeToBuildingMap extends EnumMap<BuildingType, List<Building>> {

	public BuildingTypeToBuildingMap() {
		super(BuildingType.class);
	}

	public BuildingTypeToBuildingMap(BuildingTypeToBuildingMap m) {
		super(m);
	}

	public Building getOne(BuildingType type) {
		List<Building> all = super.get(type);
		if (all!=null && all.size()>0) {
			return all.get(0);
		}
		return null;
	}

	public List<Building> getAll(BuildingType type) {
		List<Building> all = super.get(type);
		if (all!=null && all.size()>0) {
			return all;
		}
		return null;
	}
	
	public void put(BuildingType type, Building item) {
		List<Building> all = super.get(type);
		if (all == null) {
			all = new ArrayList<Building>();
			super.put(type, all);
		}
		all.add(item);
	}
}
