package net.villonanny.entity;

import net.villonanny.Translator;
import net.villonanny.type.BuildingType;

public class RallyPointEmptySite extends EmptySite {

	public RallyPointEmptySite(String name, String urlString, Translator translator) {
		super(name, urlString, translator);
		super.setOneChoiceOnly(true);
		super.setType(BuildingType.EMPTYSITE);
	}

	public boolean needsFieldsQueue() {
			return false;
	}
}
