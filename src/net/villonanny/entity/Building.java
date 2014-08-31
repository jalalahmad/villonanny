package net.villonanny.entity;



import net.villonanny.Translator;

public class Building extends UpgradeableSite {

	public Building(String name, String urlString, Translator translator) {
		super(name, urlString, translator);
	}

	public boolean needsFieldsQueue() {
		return false;
	}


}
