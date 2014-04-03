package org.eclipse.linuxtools.tmf.totalads.ui;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.eclipse.linuxtools.tmf.totalads.ui.ksm.KernelStateModeling;
import org.eclipse.linuxtools.tmf.totalads.ui.slidingwindow.SlidingWindow;


public class ModelTypeFactory {
private static ModelTypeFactory modelTypes=null;
public static enum ModelTypes {Anomaly, Classification};
private HashMap<ModelTypes,HashSet<String>> modelList=null;
private HashMap<String,IDetectionModels> acronymModels=null;
/**
 * 
 */

private ModelTypeFactory( ){
	modelList=new HashMap<ModelTypes,HashSet<String>>();
	acronymModels=new HashMap<String, IDetectionModels>();
}
/**
 * Creates the instance if ModelTypeFactory
 * @return ModelTypeFactory
 */
public static ModelTypeFactory getInstance(){
	if (modelTypes==null)
		modelTypes=new ModelTypeFactory();
	return modelTypes;
}

/**
 * Destroys the instance of factory if already exists
 * This code is necessary because when Eclipse is running and TotalADS window is closed and reopened, the static
 * object is not recreated on the creation of new Object of TotalADS
 */
public static void destroyInstance(){
	if (modelTypes!=null)
		modelTypes=null;
}
/**
 * Gets the list of models by a type; e.g., classifiaction, clustering, etc.
 * @return
 */
public IDetectionModels[] getModels(ModelTypes modTypes){
	HashSet<String> list= modelList.get(modTypes);
	if (list==null)
		return null;
	else{	
		IDetectionModels []models=new IDetectionModels[list.size()];
		Iterator<String> it=list.iterator();
		int count=0;
		while (it.hasNext()){
			models[count++]= getModelyByAcronym(it.next());
		}
		return models;
	}
}
/**
 * 
 * @param key
 * @param detectionModel
 * @throws TotalADSUiException
 */
private void registerModelWithAcronym(String key, IDetectionModels detectionModel) throws TotalADSUiException{
	
	if (key.isEmpty())
		throw new TotalADSUiException("Empty key/acronym!");
	else if (key.contains("_"))
			throw new TotalADSUiException("Acronym cannot contain underscore");
	else {
		
		IDetectionModels model =acronymModels.get(key);
		if (model==null) 
			acronymModels.put(key, detectionModel);
		else
			throw new TotalADSUiException("Duplicate key/acronym!");
	}
		
		
}
/**
 * Registers a model with the factory
 * @param detectionModel
 * @param modelType
 */
public void registerModelWithFactory(ModelTypes modelType,  IDetectionModels detectionModel)
																	throws TotalADSUiException{
	
	registerModelWithAcronym(detectionModel.getAcronym(), detectionModel);
	HashSet<String>  list=modelList.get(modelType);
	
	if (list==null)
		list=new HashSet<String>();
	
	list.add(detectionModel.getAcronym());
	
	modelList.put(modelType, list);
	
		
}
/**
 * Get a model by acronym
 * @param key
 * @return
 */
public IDetectionModels getModelyByAcronym(String key){
	IDetectionModels model= acronymModels.get(key);
	if (model==null)
		return null;
	else
		return model.createInstance();
}
/**
 * Get all models to register with the factory
 */
public void initialize() throws TotalADSUiException{
	
 //Reflections reflections = new Reflections("org.eclipse.linuxtools.tmf.totalads.ui");
 ////java.util.Set<Class<? extends IDetectionModels>> modules = reflections.getSubTypesOf
	//		 							(org.eclipse.linuxtools.tmf.totalads.ui.IDetectionModels.class);
	// The following code needs to be replaced with reflection in future versions
	KernelStateModeling.registerModel();
	SlidingWindow.registerModel();
	//DecisionTree.registerModel();
	HiddenMarkovModel.registerModel();
	
	
}



}
