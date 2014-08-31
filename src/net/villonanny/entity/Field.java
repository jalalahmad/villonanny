package net.villonanny.entity;

import net.villonanny.InvalidConfigurationException;
import net.villonanny.Translator;
import net.villonanny.type.BuildingType;
import net.villonanny.type.ResourceType;

import org.apache.log4j.Logger;

public class Field extends Building {
	private final Logger log = Logger.getLogger(this.getClass());

	public Field(String name, String urlString, Translator translator) {
		super(name, urlString, translator);
	}

	public ResourceType getResourceType() throws InvalidConfigurationException {
		if (isWood()) {
			return ResourceType.WOOD;
		}
		if (isClay()) {
			return ResourceType.CLAY;
		}
		if (isIron()) {
			return ResourceType.IRON;
		}
		if (isCrop()) {
			return ResourceType.CROP;
		}
		throw new InvalidConfigurationException("Was expecting a resource type but found " + getType());
//		return ((FieldType)getType()).getResourceType();
	}

	public boolean isWood() {
		return getType() == BuildingType.WOODCUTTER;
	}

	public boolean isClay() {
		return getType() == BuildingType.CLAY_PIT;
	}

	public boolean isIron() {
		return getType() == BuildingType.IRON_MINE;
	}

	public boolean isCrop() {
		return getType() == BuildingType.CROPLAND;
	}
	
	public boolean needsFieldsQueue() {
		return true;
	}
}
