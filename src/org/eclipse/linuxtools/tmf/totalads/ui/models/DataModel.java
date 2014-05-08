/**
 * 
 */
package org.eclipse.linuxtools.tmf.totalads.ui.models;

/**
 * @author efraimlopez
 *
 */
public class DataModel {

	private final String id;
	private final String description;
	
	public DataModel(String id, String description){
		this.id = id; this.description = description;
	}
	
	public String getId() {
		return id;
	}

	public String getDescription() {
		return description;
	}

}