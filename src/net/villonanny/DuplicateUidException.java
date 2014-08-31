package net.villonanny;

/**
 * Some uid is found to be duplicated. The relevant message should be logged prior to throwing this exception. 
 *
 */
public class DuplicateUidException extends Exception {
	private boolean modified;
	
	/**
	 * 
	 * @param modified true if the configuration has been modified
	 */
	public DuplicateUidException(boolean modified) {
		super();
		this.modified = modified;
	}

	public boolean isModified() {
		return modified;
	}

	@Override
	public String toString() {
		return super.toString() + " - modified=" + modified;
	}
	
	

}
