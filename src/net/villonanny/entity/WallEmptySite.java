package net.villonanny.entity;

import net.villonanny.Translator;
import net.villonanny.type.BuildingType;

public class WallEmptySite extends EmptySite {

	public WallEmptySite(String name, String urlString, Translator translator) {
		super(name, urlString, translator);
		super.setOneChoiceOnly(true);
		super.setType(BuildingType.EMPTYSITE);
	}

	public boolean needsFieldsQueue() {
		return false;
	}

}
