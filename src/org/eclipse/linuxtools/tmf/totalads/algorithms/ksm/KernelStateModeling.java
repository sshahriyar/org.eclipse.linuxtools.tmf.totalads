/*********************************************************************************************
 * Copyright (c) 2014  Software Behaviour Analysis Lab, Concordia University, Montreal, Canada
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of XYZ License which
 * accompanies this distribution, and is available at xyz.com/license
 *
 * Contributors:
 *    Syed Shariyar Murtaza
 **********************************************************************************************/
package org.eclipse.linuxtools.tmf.totalads.algorithms.ksm;
import org.eclipse.linuxtools.tmf.totalads.algorithms.AlgorithmTypes;
import org.eclipse.linuxtools.tmf.totalads.algorithms.IAlgorithmOutStream;
import org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm;
import org.eclipse.linuxtools.tmf.totalads.algorithms.AlgorithmFactory;
import org.eclipse.linuxtools.tmf.totalads.algorithms.Results;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSDBMSException;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSReaderException;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSGeneralException;
import org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Arrays;

import com.google.gson.JsonObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

/**
 * This class implements Kernel State Modeling algorithm for the detection of anomalies
 * @author <p>Syed Shariyar Murtaza justsshary@hotmail.com</p>
 *
 */
public class KernelStateModeling implements IDetectionAlgorithm {
	
    // Variables declaration
	private List<String> ARCH_CALLS_LIST;
	private List<String> KERNEL_CALLS_LIST;
	private List<String> MM_CALLS_LIST;
	private List<String> NET_CALLS_LIST;
	private List<String> FS_CALLS_LIST;
	private List<String> IPC_CALLS_LIST;
	private List<String> SECURITY_CALLS_LIST;
	private Boolean initialize;
	private Boolean isTestStarted;
	private Integer validationTraceCount;
	private Integer validationAnomalyCount;
	private Integer testTraceCount;
	private Integer testAnomalyCount;
	private String TRACE_COLLECTION;
	private String SETTINGS_COLLECTION;
	private Double alpha;
	private Double maxAlpha;
	private String []trainingOptions={"Kernel:2.6.35-3.2.x","true", "Kernel:3.5-3.13","false"};
	private String []testingOptions={"Alpha","0.0"};
	
    /*
     * Inner class to store trace states
     * Behaves likes a structure. There is no
     * need of getter setters here because it is a 
     * private class and getter/setters adds unnecessary complexity
     */
    private class TraceStates{
	    public Double FS=0.0;
	    public Double MM=0.0;
	    public Double KL=0.0;
	    public Double AC=0.0;
	    public Double IPC=0.0;
	    public Double NT=0.0;
	    public Double SC=0.0;
	    public Double UN=0.0;
    }
     
    
	/**
	 * Constructor
	 */
    public KernelStateModeling(){
    	TRACE_COLLECTION=TraceCollection.COLLECTION_NAME.toString();
    	SETTINGS_COLLECTION=SettingsCollections.COLLECTION_NAME.toString();
    	initialize=false;
    	isTestStarted=false;
    	validationTraceCount=0;
        validationAnomalyCount=0;
        testTraceCount=0;
        testAnomalyCount=0;
        alpha=0.0;
        maxAlpha=0.10;
       
    	
    }
   /*
    * (non-Javadoc)
    * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#initializeModelAndSettings(java.lang.String, org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject, java.lang.String[])
    */
    @Override
    public void initializeModelAndSettings(String modelName, IDataAccessObject dataAccessObject, String[] trainingSettings) throws  TotalADSDBMSException, TotalADSGeneralException{
    	
    	if (trainingSettings!=null){
  		  int trueCount=0;
  		  
  		  for (int count=1; count<trainingOptions.length; count+=2)
  			 if (trainingSettings[count].equals("true")){
  			  		trainingOptions[count]="true";
  			  		trueCount++;
  			  		if (trueCount>1){
  			  			trainingOptions[count]="false";
  			  			throw new TotalADSGeneralException("Please, select only one option as true.");
  			  		}
  			 }
  			 else  if (trainingSettings[count].equals("false")) // if it is not true then it must be false
  				    trainingOptions[count]="false";
  			 else
  				 throw new TotalADSGeneralException("Please, type true or false only.");
		      
  			if (trueCount==0)	  
  		      throw new TotalADSGeneralException("Please, type true for one of the options.");
  		  	    			  	
  	   }else
  		 trainingSettings=trainingOptions;
    	
    	
    	String []collectionNames={TRACE_COLLECTION, SETTINGS_COLLECTION};
    	dataAccessObject.createDatabase(modelName, collectionNames);
    	String []fields={TraceCollection.FS.toString(),TraceCollection.MM.toString(),TraceCollection.KL.toString()};
	    dataAccessObject.createAscendingUniquesIndexes(modelName, TRACE_COLLECTION, fields);
	    saveSettingsInDatabase(alpha,modelName, dataAccessObject);
    }
    
    /*
     * (non-Javadoc)
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#getTrainingOptions()
     */
    @Override
    public String[] getTrainingSettings(){
   			return trainingOptions;
  
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#saveTestingOptions(java.lang.String[], java.lang.String, org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject)
     */
    @Override
    public void saveTestSettings(String [] options, String database, IDataAccessObject dataAccessObject) throws TotalADSGeneralException, TotalADSDBMSException
    {
    	try {
			alpha= Double.parseDouble(options[1]);
		}catch (NumberFormatException ex){
			throw new TotalADSGeneralException("Please, enter only decimal values.");
		}
    	// First read settings in a class level array-- just one row
    	getSettingsFromDatabase(database, dataAccessObject);
		// Now update using that class level array, to avoid any error
    	saveSettingsInDatabase(alpha, database, dataAccessObject);
    	
    }

   /*
    * (non-Javadoc)
    * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#getTestingOptions(java.lang.String, org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject)
    */
    @Override
    public String[] getTestSettings(String database, IDataAccessObject dataAccessObject){
    	Double alphaVal=getSettingsFromDatabase(database, dataAccessObject);
		if(alphaVal!=null)
			 alpha=alphaVal;
		testingOptions[1]=alpha.toString();
		return testingOptions;
    }
    
    /*
     * (non-Javadoc)
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#getSettingsToDisplay()
     */
    @Override
	public String[] getSettingsToDisplay(String database, IDataAccessObject dataAccessObject){
		// First read settings in a class level array-- just one row
		Double alphaVal=getSettingsFromDatabase(database, dataAccessObject);
		String []settings=new String[trainingOptions.length+2];
		int j;
		for (j=0;j<trainingOptions.length;j++)
			settings[j]=trainingOptions[j];
		settings[j]="Alpha";
		settings[j+1]=alphaVal.toString();
		return settings;
	}
    /*
     * (non-Javadoc)
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#train(org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator, java.lang.Boolean, java.lang.String, org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject, org.eclipse.linuxtools.tmf.totalads.algorithms.IAlgorithmOutStream)
     */
    @Override
    public void train(ITraceIterator trace, Boolean isLastTrace, String database, IDataAccessObject connection, IAlgorithmOutStream outStream) throws TotalADSGeneralException, TotalADSDBMSException, TotalADSReaderException {
    	 //initialized alpha to 0 during training
    	if (!initialize){
    		    alpha=getSettingsFromDatabase(database, connection);
			    initialize=true;
			    this.intializeStates();
	     }
    				
    	TraceStates states= new TraceStates();
    	
    	outStream.addOutputEvent("Measuring states' probabilities");
		outStream.addNewLine();
    	
		measureStateProbabilities(trace, states,outStream);
		// if everything is fine up till now then carry on and insert it into the database
		saveTraceData(connection, database, states);
		
		
		outStream.addOutputEvent("FS="+states.FS +" KL "+states.KL+" MM "+states.MM+
				" AC "+states.AC+" IPC "+states.IPC+" NT "+states.NT+" SC "
			    +states.SC+" UN "+states.UN);	outStream.addNewLine();
		outStream.addNewLine();
		if (isLastTrace){
			initialize=false; // may not be necessary because an instance of the algorithm is always created on every selction
			
		}
    }
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#validate(org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator, java.lang.String, org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject, java.lang.Boolean, org.eclipse.linuxtools.tmf.totalads.algorithms.IAlgorithmOutStream)
	 */
    @Override
	public void validate(ITraceIterator trace, String database, IDataAccessObject dataAccessObject, Boolean isLastTrace, IAlgorithmOutStream outStream) throws  TotalADSGeneralException, TotalADSDBMSException, TotalADSReaderException {
	  
		  validationTraceCount++;
		  TraceStates valTrcStates=new TraceStates();
		  measureStateProbabilities(trace, valTrcStates,outStream);
		  while (alpha< maxAlpha){
			    Boolean isAnomaly=evaluateKSM( alpha, valTrcStates,dataAccessObject,database);
				if (isAnomaly==false)
							 break; // no need to increment alpha as there is no anomaly
			    alpha+=0.02;
			    outStream.addOutputEvent("Increasing alpha to "+alpha);
			    outStream.addNewLine();
			    
		  }
		 if (alpha>=maxAlpha)
			 if (evaluateKSM( alpha, valTrcStates,dataAccessObject,database)==true)
				 validationAnomalyCount++;
			 
		  if (isLastTrace){
			  	outStream.addOutputEvent("Updating database");
			  	outStream.addNewLine();
			  	
				
				saveSettingsInDatabase(alpha, database, dataAccessObject);
				
				  
			  	outStream.addOutputEvent("Database updated with final alpha: "+alpha);
			  	outStream.addNewLine();
			  	Double anomalyPercentage= (validationAnomalyCount.doubleValue()/validationTraceCount)*100;
				  
				outStream.addOutputEvent("Anomalies at alpha "+alpha + " are "+anomalyPercentage);
				outStream.addNewLine();
				outStream.addOutputEvent("Total traces "+validationTraceCount);
				outStream.addNewLine();
		  }
	  
	}
	
	
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#test(org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator, java.lang.String, org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject, org.eclipse.linuxtools.tmf.totalads.algorithms.IAlgorithmOutStream)
	 */
	@Override
	public Results test(ITraceIterator trace, String database,IDataAccessObject dataAccessObject, IAlgorithmOutStream outputStream) throws TotalADSGeneralException, TotalADSDBMSException, TotalADSReaderException {
		
		if  (!isTestStarted){
			outputStream.addOutputEvent("Starting testing");
			outputStream.addNewLine();
			testTraceCount=0;
			testAnomalyCount=0;
			Double alphaVal=getSettingsFromDatabase(database, dataAccessObject);
			if(alphaVal!=null)
					 alpha=alphaVal;
			
			this.intializeStates();
			isTestStarted=true;
		}
		
		TraceStates testTrcStates= new TraceStates();
		outputStream.addOutputEvent("Measuring probabilities of states in the trace");
		
		outputStream.addNewLine();
		measureStateProbabilities(trace, testTrcStates,outputStream);
		
		outputStream.addOutputEvent("FS="+testTrcStates.FS +" KL "+testTrcStates.KL+" MM "+testTrcStates.MM+
					" AC "+testTrcStates.AC+" IPC "+testTrcStates.IPC+" NT "+testTrcStates.NT+" SC "
				    +testTrcStates.SC+" UN "+testTrcStates.UN);
		outputStream.addNewLine();
		
		Boolean isAnomaly=evaluateKSM(alpha, testTrcStates, dataAccessObject, database);
		testTraceCount++;
		
		if (isAnomaly)
			testAnomalyCount++;
		
		Results results= new Results();
		results.setAnomaly(isAnomaly);
		//results.setAnomalyType(null);
		results.setDetails("FS "+testTrcStates.FS+"\n");
		results.setDetails("KL "+testTrcStates.KL+"\n");
		results.setDetails("MM "+testTrcStates.MM+"\n");
		results.setDetails("AC "+testTrcStates.AC+"\n");
		results.setDetails("IPC "+testTrcStates.IPC+"\n");
		results.setDetails("NT "+testTrcStates.NT+"\n");
		results.setDetails("SC "+testTrcStates.SC+"\n");
		results.setDetails("UN "+testTrcStates.UN+"\n");
		
		outputStream.addOutputEvent("Finished evaluating the trace");
		outputStream.addNewLine();
		return results;
				
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#getTotalAnomalyPercentage()
	 */
    @Override
	public Double getTotalAnomalyPercentage(){

		Double anomalousPercentage=(testAnomalyCount.doubleValue()/testTraceCount.doubleValue())*100;
		return anomalousPercentage;
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#getName()
	 */
	@Override
	public String getName(){
		return "Kernel State Modeling (KSM)";
	}
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#getAcronym()
	 */
	@Override
    public String getAcronym(){
    	
    	return "KSM";
    }
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#graphicalResults(org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator)
	 */
    @Override
	public org.swtchart.Chart graphicalResults(ITraceIterator traceIterator){
		return null;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#createInstance()
	 */
	@Override
	public IDetectionAlgorithm createInstance() {
		return new KernelStateModeling();
	}
	
	/** Self registration of the model with the modelFactory **/
	public static void registerModel() throws TotalADSGeneralException{
		AlgorithmFactory modelFactory= AlgorithmFactory.getInstance();
		KernelStateModeling ksm=new KernelStateModeling();
		modelFactory.registerModelWithFactory( AlgorithmTypes.ANOMALY,ksm);
	}
	/**
	 * Evaluates KSM
	 * @param trace
	 * @param alpha
	 * @return
	 */
	private Boolean evaluateKSM(Double alpha, TraceStates testStates, IDataAccessObject connection, String database){
		Boolean isAnomalous=false;
		Double maxFS=0.0;
		
		String maxVal=connection.selectMax("FS", database, TRACE_COLLECTION);
		
		if(!maxVal.isEmpty())
			maxFS=Double.parseDouble(maxVal);
		

		  if (testStates.FS> maxFS)
		     isAnomalous=true;  
		  else  {
			  for (double incr=testStates.FS; incr<=maxFS ;incr+=0.01){
					// get all those records/documents from the Collection (DB) which match FS=incr
					DBCursor cursor=  connection.select("FS", null, incr, database, TRACE_COLLECTION);
					if (cursor!=null){ 
						 //  get the max KL and the max MM from them
						 Double []maxKLMM=getMaxKLandMM(cursor);
						 Double maxKL=maxKLMM[0];
						 Double maxMM=maxKLMM[1];
						  if   ((testStates.KL- maxKL  >alpha ) ||  (testStates.MM-maxMM > alpha)){
	                          // break because the trace is normal
	                          isAnomalous=true;
	                           break;
						  } else{
	                          // break because we don't want to increment incr further as the trace is normal
	                          break;
						  }    
					}//end if
				 
			 }//end for
		  } //end if
		
		
		return isAnomalous;
	}
	/**
	 * Gets maximum KL and MM
	 * @param cursor
	 * @param maxKL
	 * @param maxMM
	 */
	private Double[] getMaxKLandMM(DBCursor cursor ){
		Double []maxKLMM={0.0,0.0};
		while (cursor.hasNext()){
			DBObject doc=cursor.next();
			Double KL=(Double)doc.get("KL");
			Double MM=(Double)doc.get("MM");
			if (KL>maxKLMM[0])
				maxKLMM[0]=KL;	
			if (MM> maxKLMM[1])
				maxKLMM[1]=MM;
		}
	  cursor.close();		
	  return maxKLMM;	 
	}
	
	/**
	 * Measure probabilities of states
	 * @param trace
	 * @param states
	 * @param out
	 * @throws TotalADSGeneralException
	 * @throws TotalADSReaderException
	 */
	private void measureStateProbabilities(ITraceIterator trace, TraceStates states, IAlgorithmOutStream out) throws TotalADSGeneralException, TotalADSReaderException{
		
		Double totalSysCalls=0.0;
		int sysCount=1;
		while (trace.advance()){
			 	String systemCall= trace.getCurrentEvent();
			 	
			 	if (sysCount%100000==0){
			 		out.addOutputEvent("Executing "+sysCount+"th system call");
			 		out.addNewLine();
			 	}
			 		
			 	if (systemCall != null)
			 		mapStates(systemCall,states);
			 	sysCount++;
		}
			
		
		totalSysCalls=states.MM+states.FS+states.KL+states.NT+states.IPC+states.SC+states.AC+states.UN;
		// If correct system call names do not exist in the trace, throw an exception
		if (totalSysCalls<=0 || totalSysCalls.equals(states.UN))
			throw new TotalADSGeneralException("KSM: No system call names found in the last trace. Further processing aborted!");
		
		states.FS= round(states.FS/totalSysCalls,2);
		states.MM=round(states.MM/totalSysCalls,2);
		states.KL=round(states.KL/totalSysCalls,2);
		states.NT=round(states.NT/totalSysCalls,2);
		states.IPC=round(states.IPC/totalSysCalls,2);
		states.SC=round(states.SC/totalSysCalls,2);
		states.AC=round(states.AC/totalSysCalls,2);
		states.UN=round(states.UN/totalSysCalls,2);
		
		
	}
	
	/**
	 * Map system call ids to state and count frequencies
	 * @param syscallID
	 * @param states
	 */
	private void mapStates(String syscall, TraceStates states){
		  if (MM_CALLS_LIST.contains(syscall))
			  states.MM++;// keep track of the last sys_entry function id and
		  else if (FS_CALLS_LIST.contains(syscall))
			  states.FS++;    
		  else if (KERNEL_CALLS_LIST.contains(syscall))
			  states.KL++;	
		  else if (NET_CALLS_LIST.contains(syscall))
			  states.NT++;
		  else if (IPC_CALLS_LIST.contains(syscall))
			  states.IPC++;
		  else if (SECURITY_CALLS_LIST.contains(syscall))
			  states.SC++;
		  else if (ARCH_CALLS_LIST.contains(syscall))
			  states.AC++;
		  else 
			  states.UN++;
		      
	}
	/**
	 * Rounds a decimal number up to certain decimal places given in precision
	 * @param unrounded
	 * @param precision
	 * @return
	 */
	private double round(double unrounded, int precision)
	{
	    BigDecimal bd = new BigDecimal(unrounded);
	    BigDecimal rounded = bd.setScale(precision, BigDecimal.ROUND_UP);
	    return rounded.doubleValue();
	}
	
	/**
	 * 
	 * @param connection
	 * @param database
	 * @param states
	 * @throws TotalADSDBMSException 
	 */
	private void saveTraceData(IDataAccessObject connection, String database, TraceStates states) throws TotalADSDBMSException{

		try {
				JsonObject jsonDoc=new JsonObject();
				jsonDoc.addProperty(TraceCollection.FS.toString(), states.FS);
				jsonDoc.addProperty(TraceCollection.MM.toString(), states.MM);
				jsonDoc.addProperty(TraceCollection.KL.toString(), states.KL);
				jsonDoc.addProperty(TraceCollection.AC.toString(), states.AC);
				jsonDoc.addProperty(TraceCollection.IPC.toString(), states.IPC);
				jsonDoc.addProperty(TraceCollection.NT.toString(), states.NT);
				jsonDoc.addProperty(TraceCollection.SC.toString(), states.SC);
				jsonDoc.addProperty(TraceCollection.UN.toString(), states.UN);
				connection.insertUsingJSON(database, jsonDoc,  TRACE_COLLECTION);
				
				// this also works
				//connection.insert(states, database,TRACE_COLLECTION);

		}catch (TotalADSDBMSException ex){
			if (ex!=null && (!ex.getMessage().contains("E11000") || !ex.getMessage().contains("duplicate")))// if it is a duplicate name error do nothing
				throw new TotalADSDBMSException(ex);
		}
	}
	/**
	 * Saves settings in a database
	 * @param alpha
	 * @param database
	 * @param connection
	 * @throws TotalADSDBMSException 
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 * @throws Exception
	 */
	private void saveSettingsInDatabase(Double alpha, String database, IDataAccessObject connection) 
			throws  TotalADSDBMSException {
		 String settingsKey ="KSM_SETTINGS";
			
		 // Method 2
		  
		  JsonObject jsonKey=new JsonObject();
		  jsonKey.addProperty("_id",settingsKey);
				  
		  JsonObject jsonObjToUpdate= new JsonObject();
		  jsonObjToUpdate.addProperty(SettingsCollections.KEY.toString(), settingsKey);
		  jsonObjToUpdate.addProperty(SettingsCollections.ALPHA.toString(), alpha);
		  
		  String kernel="";
		  for (int count=1; count<trainingOptions.length; count+=2)
 			   if (trainingOptions[count].equalsIgnoreCase("true")){
 				   kernel=trainingOptions[count-1];
 				   break;//only one option can be true
 			   }
 				   
 				   
		  jsonObjToUpdate.addProperty(SettingsCollections.KernelVersions.toString(), kernel);
			  
		  String time= new SimpleDateFormat("ddMMyyyy_HHmmss").format(Calendar.getInstance().getTime());
		  jsonObjToUpdate.addProperty(SettingsCollections.UPDATE_TIME.toString(), time);
				
		  connection.insertOrUpdateUsingJSON(database, jsonKey, jsonObjToUpdate, this.SETTINGS_COLLECTION);
		  
	}
	
	/**
	 * Gets alpha and other settings from the database. Alpha is returned and other settings are
	 * put in a class level string array trainingOptions
	 * @param database
	 * @param connection
	 */
	private Double getSettingsFromDatabase(String database, IDataAccessObject connection){
	    String settingsKey ="KSM_SETTINGS";
	    Double alphaValue=null;
	    String kernelVersion="";
	    
		DBCursor cursor= connection.select(SettingsCollections.KEY.toString(), "",settingsKey, database, SETTINGS_COLLECTION);
		if (cursor!=null && cursor.hasNext()){
			DBObject dbObject=cursor.next();
			alphaValue=Double.parseDouble(dbObject.get(SettingsCollections.ALPHA.toString()).toString());
			kernelVersion=dbObject.get(SettingsCollections.KernelVersions.toString()).toString();
		}
		
		 for (int count=0; count<trainingOptions.length; count+=2)
			   if (trainingOptions[count].equalsIgnoreCase(kernelVersion)){
				   trainingOptions[count+1]="true";
				   //only one option can be true
			   }else// else it is false
				   trainingOptions[count+1]="false";
		
		
		return alphaValue;
	}
	
	/**
	 * Initializer of system calls to state mapper
	 */
	private void  intializeStates(){
		// Kerenel 2.6.32 Ubuntu 10.04 x86 64 and 32 both
		/*
		List<String> ARCH_CALLS_LIST=Arrays.asList( "sys_fork",  "sys_vfork", "sys_rt_sigreturn", "sys_sigreturn", "sys_uname", 
										"sys_sigaction", "sys_clone", "sys_olduname","sys_execve", "sys_sigaltstack", "sys_mmap", 
											"sys_set_thread_area", "sys_sigsuspend","sys_ipc"); 
		List<String> KERNEL_CALLS_LIST=Arrays.asList("sys_getuid16", "sys_setgroups", "sys_getpgrp", "sys_timer_settime", 
				 "sys_init_module", "sys_getpriority", "sys_lchown16", "sys_setresuid", "sys_timer_gettime", "sys_adjtimex", 
				 "sys_getresgid16", "sys_exit_group", "sys_geteuid16", "sys_times", "sys_setitimer", "sys_setgid", "sys_setsid", 
				 "sys_sched_setparam", "sys_sched_getaffinity", "sys_sched_setscheduler", "sys_waitid", "sys_gettid", "sys_getegid16",
				 "sys_setpriority", "sys_getsid", "sys_gettimeofday", "sys_setresgid", "sys_timer_delete", "sys_setreuid16", 
				 "sys_sched_setaffinity", "sys_setfsuid16", "sys_setreuid", "sys_setdomainname", "sys_timer_getoverrun", 
				 "sys_getrlimit", "sys_getppid", "sys_getresuid16", "sys_capset", "sys_setfsuid", "sys_getpid", "sys_getegid", 
				 "sys_getgroups", "sys_kexec_load", "sys_unshare", "sys_getitimer", "sys_clock_settime", "sys_old_getrlimit", 
				 "sys_stime", "sys_sigpending", "sys_nanosleep", "sys_kill", "sys_getresgid", "sys_setresuid16", 
				 "sys_sched_getscheduler", "sys_clock_getres", "sys_getgid", "sys_sched_rr_get_interval", "sys_time", 
				 "sys_futex", "sys_ptrace", "sys_sched_get_priority_min", "sys_clock_gettime", "sys_delete_module", 
				 "sys_settimeofday", "sys_tgkill", "sys_setuid", "sys_sched_getparam", "sys_sgetmask", "sys_setgroups16", 
				 "sys_sched_yield", "sys_rt_sigpending", "sys_capget", "sys_getresuid", "sys_rt_sigaction", "sys_clock_nanosleep",
				 "sys_set_robust_list", "sys_setregid", "sys_setfsgid16", "sys_set_tid_address", "sys_pause", "sys_umask", 
				 "sys_perf_event_open", "sys_exit", "sys_getuid", "sys_signal", "sys_getcpu", "sys_rt_sigtimedwait", 
				 "sys_getgid16", "sys_sysctl", "sys_syslog", "sys_rt_sigprocmask", "sys_setregid16", "sys_rt_sigsuspend", 
				 "sys_rt_sigqueueinfo", "sys_setfsgid", "sys_restart_syscall", "sys_getrusage", "sys_alarm", "sys_sysinfo", 
				 "sys_getpgid", "sys_geteuid", "sys_get_robust_list", "sys_setresgid16", "sys_sigprocmask", "sys_sched_get_priority_max",
				 "sys_setuid16", "sys_sethostname", "sys_rt_tgsigqueueinfo", "sys_setrlimit", "sys_wait4", "sys_ssetmask", "sys_acct", 
				 "sys_tkill", "sys_chown16", "sys_setgid16", "sys_waitpid", "sys_newuname", "sys_reboot", "sys_nice", "sys_fchown16", 
				 "sys_setpgid", "sys_prctl", "sys_timer_create", "sys_getgroups16", "sys_personality"); 
		 List<String> MM_CALLS_LIST= Arrays.asList("sys_swapon", "sys_mbind", "sys_munlockall", "sys_migrate_pages", "sys_mincore", 
				 "sys_mlock", "sys_move_pages", "sys_mmap_pgoff", "sys_msync", "sys_brk", "sys_mprotect", "sys_set_mempolicy", 
				 "sys_swapoff", "sys_munmap", "sys_get_mempolicy", "sys_mlockall", "sys_remap_file_pages", "sys_madvise", "sys_munlock", 
				 "sys_mremap"); 
		//not found:
		 //"sys_readahead", "sys_ioperm", "sys_vm86",  "sys_iopl", "sys_get_thread_area", "sys_sync_file_range", "sys_fallocate",  
		  //"sys_lookup_dcookie", "sys_pwrite64", "sys_semctl", "sys_truncate64", "sys_modify_ldt", "sys_ftruncate64", 
		 // "sys_fadvise64_64", "sys_arch_prctl",  "old_mmap","sys_vm86old", "sys_fadvise64", "old_select", "sys_pread64", 
		 List<String> NET_CALLS_LIST=Arrays.asList("sys_recvmsg", "sys_sendmsg", "sys_sendto", "sys_getsockname", "sys_listen", 
				 "sys_socket", "sys_accept", "sys_connect", "sys_recvfrom", "sys_socketcall", "sys_socketpair", "sys_setsockopt", 
				 "sys_accept4", "sys_getpeername", "sys_shutdown", "sys_getsockopt", "sys_bind"); 
		 List<String> FS_CALLS_LIST= Arrays.asList( "sys_readv", "sys_eventfd", "sys_stat", "sys_mkdirat", "sys_preadv", "sys_getcwd", 
				 "sys_epoll_ctl", "sys_open", "sys_mkdir", "sys_linkat", "sys_old_readdir", "sys_close", "sys_sendfile", "sys_ioctl", 
				 "sys_fdatasync", "sys_tee", "sys_chdir", "sys_symlink", "sys_unlink", "sys_signalfd", "sys_fchownat", "sys_flock",
				 "sys_statfs", "sys_sendfile64", "sys_flistxattr", "sys_fstatfs64", "sys_pipe2", "sys_lchown", "sys_stat64", "sys_fstat64"
				 , "sys_lgetxattr", "sys_readlinkat", "sys_rmdir", "sys_lstat", "sys_utimes", "sys_newfstat", "sys_fchmod", 
				 "sys_symlinkat", "sys_write", "sys_lsetxattr", "sys_fsync", "sys_vmsplice", "sys_listxattr", "sys_umount", 
				 "sys_fsetxattr", "sys_readlink", "sys_epoll_create", "sys_eventfd2", "sys_fchmodat", "sys_chroot", "sys_sysfs",
				 "sys_inotify_init1", "sys_pipe", "sys_mount", "sys_link", "sys_removexattr", "sys_statfs64", "sys_ioprio_set", "sys_getdents",
				 "sys_read", "sys_quotactl", "sys_inotify_rm_watch", "sys_mknodat", "sys_epoll_wait", "sys_epoll_pwait", "sys_ftruncate", 
				 "sys_io_cancel", "sys_lremovexattr", "sys_truncate", "sys_fstat", "sys_fstatfs", "sys_rename", "sys_faccessat", "sys_creat",
				 "sys_llseek", "sys_bdflush", "sys_inotify_add_watch", "sys_renameat", "sys_dup3", "sys_dup2", "sys_utime", "sys_uselib", 
				 "sys_io_destroy", "sys_futimesat", "sys_fchdir", "sys_unlinkat", "sys_pselect6", "sys_mknod", "sys_access", "sys_ioprio_get",
				 "sys_oldumount", "sys_inotify_init", "sys_pivot_root", "sys_utimensat", "sys_fchown", "sys_fremovexattr", "sys_splice", 
				 "sys_chown", "sys_pwritev", "sys_ppoll", "sys_poll", "sys_timerfd_create", "sys_lstat64", "sys_llistxattr", "sys_select", 
				 "sys_lseek", "sys_ustat", "sys_newfstatat", "sys_epoll_create1", "sys_dup", "sys_newlstat", "sys_fcntl64", "sys_io_setup", 
				 "sys_fgetxattr", "sys_nfsservctl", "sys_setxattr", "sys_timerfd_settime", "sys_getdents64", "sys_io_submit",
				 "sys_timerfd_gettime", "sys_openat", "sys_writev", "sys_io_getevents", "sys_signalfd4", "sys_vhangup", 
				 "sys_getxattr", "sys_sync", "sys_fcntl", "sys_fstatat64", "sys_newstat", "sys_chmod"); 
		 List<String> IPC_CALLS_LIST=Arrays.asList("sys_mq_unlink", "sys_mq_open", "sys_shmat", "sys_msgsnd", "sys_mq_notify", 
				 "sys_shmdt", "sys_shmctl", "sys_mq_timedsend", "sys_semop", "sys_semget", "sys_mq_timedreceive", "sys_msgctl", 
				 "sys_msgget", "sys_msgrcv", "sys_shmget", "sys_semtimedop", "sys_mq_getsetattr"); 
		 List<String> SECURITY_CALLS_LIST=Arrays.asList("sys_keyctl", "sys_request_key", "sys_add_key");
		 */
		 // For kernel 2.6.35( Ubuntu 10.10) to 3.2 (Ubuntu 12.10)
		
		  if (trainingOptions[1].equals("true")){
			  
				ARCH_CALLS_LIST=Arrays.asList(
				 "sys_fork", "sys_sigreturn", "sys_sigaction", "sys_execve", "sys_mmap", "sys_set_thread_area", "compat_sys_execve", "compat_sys_sigaltstack", "sys_vfork", "compat_sys_rt_sigreturn", "sys_rt_sigreturn", "sys_clone", "sys_sigaltstack", "sys_sigsuspend"
				 );
				 KERNEL_CALLS_LIST=Arrays.asList(
				 "sys_getpgrp", "sys_getpriority", "compat_sys_timer_create", "sys_timer_gettime", "sys_adjtimex", "sys_geteuid16", "sys_setitimer", "sys_sched_setscheduler", "sys_waitid", "sys_getegid16", "sys_gettid", "sys_getsid", "sys_gettimeofday", "sys_setfsuid16", "sys_setreuid", "sys_setdomainname", "sys_capset", "sys_kexec_load", "sys_getitimer", "sys_unshare", "sys_old_getrlimit", "sys_clock_settime", "sys_stime", "compat_sys_kexec_load", "sys_nanosleep", "sys_getresgid", "sys_setresuid16", "sys_sched_getscheduler", "sys_clock_getres", "sys_sched_rr_get_interval", "sys_time", "sys_futex", "sys_ptrace", "sys_clock_gettime", "sys_settimeofday", "sys_tgkill", "sys_setuid", "sys_sched_yield", "sys_rt_sigaction", "sys_clock_nanosleep", "sys_setfsgid16", "sys_pause", "sys_perf_event_open", "sys_uname", "compat_sys_rt_sigaction", "sys_getuid", "sys_rt_sigtimedwait", "sys_getgid16", "compat_sys_ptrace", "sys_rt_sigprocmask", "sys_setregid16", "sys_rt_sigqueueinfo", "sys_setfsgid", "sys_alarm", "sys_sysinfo", "sys_getpgid", "sys_setrlimit", "sys_wait4", "sys_ssetmask", "sys_newuname", "sys_reboot", "sys_prctl", "sys_getgroups16", "sys_timer_create", "sys_personality", "sys_getuid16", "sys_setgroups", "sys_timer_settime", "sys_init_module", "compat_sys_rt_sigtimedwait", "sys_lchown16", "sys_setresuid", "compat_sys_waitid", "sys_getresgid16", "sys_exit_group", "sys_times", "sys_setgid", "sys_clock_adjtime", "sys_setsid", "sys_sched_setparam", "sys_sched_getaffinity", "sys_setpriority", "sys_setresgid", "sys_timer_delete", "compat_sys_get_robust_list", "sys_setreuid16", "sys_sched_setaffinity", "sys_timer_getoverrun", "sys_getrlimit", "sys_getppid", "sys_getresuid16", "sys_setns", "sys_setfsuid", "sys_getpid", "sys_getegid", "sys_getgroups", "sys_sigpending", "compat_sys_rt_tgsigqueueinfo", "sys_kill", "sys_getgid", "sys_sched_get_priority_min", "sys_delete_module", "sys_sched_getparam", "sys_sgetmask", "sys_setgroups16", "sys_rt_sigpending", "sys_capget", "sys_getresuid", "sys_set_robust_list", "sys_setregid", "sys_set_tid_address", "sys_umask", "sys_exit", "sys_signal", "sys_getcpu", "sys_sysctl", "sys_syslog", "sys_rt_sigsuspend", "sys_restart_syscall", "sys_getrusage", "compat_sys_sigpending", "sys_geteuid", "sys_get_robust_list", "sys_setresgid16", "sys_sigprocmask", "sys_sched_get_priority_max", "sys_setuid16", "sys_olduname", "sys_sethostname", "sys_rt_tgsigqueueinfo", "sys_acct", "sys_chown16", "sys_setgid16", "sys_tkill", "sys_waitpid", "sys_prlimit64", "sys_kcmp", "sys_nice", "compat_sys_set_robust_list", "sys_fchown16", "sys_setpgid"
				 );
				 MM_CALLS_LIST= Arrays.asList(
				 "sys_mbind", "compat_sys_process_vm_readv", "compat_sys_process_vm_writev", "sys_migrate_pages", "sys_move_pages", "compat_sys_move_pages", "sys_get_mempolicy", "sys_remap_file_pages", "sys_madvise", "sys_fadvise64", "sys_swapon", "sys_readahead", "sys_process_vm_writev", "sys_munlockall", "sys_mincore", "sys_mlock", "sys_mmap_pgoff", "sys_msync", "sys_brk", "sys_mprotect", "sys_fadvise64_64", "sys_set_mempolicy", "sys_swapoff", "sys_munmap", "sys_mlockall", "sys_process_vm_readv", "sys_munlock", "sys_old_mmap", "sys_mremap"
				 );
				//not found
				 //"compat_sys_preadv64", "sys_iopl", "compat_sys_sigqueueinfo", "sys_modify_ldt", "sys_vm86old", "sys_ioperm", "sys_unused", "sys_vm86", "sys_get_thread_area", "sys_arch_prctl", "compat_sys_pwritev64", "sys_available", 
				 NET_CALLS_LIST=Arrays.asList(
				 "sys_recvmsg", "sys_sendmmsg", "sys_sendto", "sys_getsockname", "compat_sys_setsockopt", "sys_socket", "sys_recvfrom", "sys_setsockopt", "sys_shutdown", "compat_sys_recvfrom", "sys_bind", "compat_sys_getsockopt", "compat_sys_recvmmsg", "sys_sendmsg", "compat_sys_sendmsg", "sys_listen", "sys_accept", "sys_connect", "sys_socketcall", "sys_socketpair", "compat_sys_sendmmsg", "sys_recvmmsg", "sys_accept4", "sys_getpeername", "compat_sys_recvmsg", "sys_getsockopt"
				 );
				 FS_CALLS_LIST= Arrays.asList(
				 "sys_preadv", "sys_open", "sys_mkdir", "sys_linkat", "compat_sys_ioctl", "sys_old_readdir", "sys_close", "sys_sendfile", "compat_sys_vmsplice", "sys_fanotify_mark", "sys_tee", "compat_sys_writev", "sys_chdir", "sys_symlink", "sys_signalfd", "sys_fchownat", "sys_statfs", "sys_sendfile64", "sys_fstatfs64", "sys_lchown", "sys_fstat64", "sys_readlinkat", "sys_rmdir", "sys_utimes", "sys_fchmod", "compat_sys_readv", "sys_fallocate", "sys_open_by_handle_at", "sys_fsync", "sys_vmsplice", "sys_umount", "sys_epoll_create", "sys_eventfd2", "sys_sysfs", "sys_inotify_init1", "sys_pipe", "sys_mount", "sys_link", "sys_ftruncate64", "sys_statfs64", "sys_getdents", "sys_read", "sys_inotify_rm_watch", "sys_epoll_wait", "sys_name_to_handle_at", "sys_epoll_pwait", "sys_lremovexattr", "sys_fstat", "sys_faccessat", "sys_inotify_add_watch", "sys_dup3", "sys_dup2", "sys_utime", "sys_uselib", "sys_io_destroy", "sys_unlinkat", "sys_ioprio_get", "sys_oldumount", "sys_utimensat", "sys_fremovexattr", "sys_splice", "sys_chown", "sys_poll", "sys_lseek", "sys_ustat", "sys_epoll_create1", "sys_dup", "sys_newlstat", "sys_nfsservctl", "sys_fgetxattr", "sys_getdents64", "sys_old_select", "sys_io_submit", "sys_timerfd_gettime", "sys_openat", "sys_writev", "sys_io_getevents", "sys_pread64", "sys_getxattr", "sys_sync", "sys_fstatat64", "sys_readv", "sys_eventfd", "sys_stat", "sys_mkdirat", "sys_getcwd", "sys_epoll_ctl", "sys_ioctl", "sys_fdatasync", "sys_syncfs", "sys_unlink", "sys_flock", "sys_flistxattr", "sys_pipe2", "sys_stat64", "sys_lgetxattr", "sys_lstat", "sys_newfstat", "sys_sync_file_range", "sys_symlinkat", "sys_write", "sys_lsetxattr", "sys_listxattr", "sys_readlink", "sys_fsetxattr", "sys_fchmodat", "sys_lookup_dcookie", "sys_pwrite64", "sys_chroot", "sys_truncate64", "sys_removexattr", "sys_ioprio_set", "sys_quotactl", "sys_mknodat", "sys_ftruncate", "sys_io_cancel", "sys_truncate", "sys_fstatfs", "sys_rename", "sys_creat", "sys_llseek", "sys_bdflush", "sys_renameat", "sys_futimesat", "sys_fchdir", "sys_pselect6", "sys_mknod", "sys_access", "sys_inotify_init", "sys_pivot_root", "sys_fchown", "sys_pwritev", "sys_ppoll", "sys_timerfd_create", "sys_lstat64", "sys_llistxattr", "sys_select", "sys_newfstatat", "sys_fanotify_init", "sys_fcntl64", "sys_io_setup", "sys_setxattr", "sys_timerfd_settime", "sys_signalfd4", "sys_vhangup", "sys_fcntl", "sys_newstat", "sys_chmod"
				 );
				 IPC_CALLS_LIST=Arrays.asList(
				 "sys_mq_open", "sys_semctl", "sys_mq_timedsend", "sys_mq_timedreceive", "sys_msgctl", "sys_msgget", "sys_shmget", "sys_semtimedop", "sys_mq_getsetattr", "sys_mq_unlink", "sys_ipc", "sys_shmat", "sys_msgsnd", "sys_mq_notify", "sys_shmdt", "compat_sys_mq_notify", "sys_shmctl", "sys_semop", "sys_semget", "sys_msgrcv"
				 );
				 SECURITY_CALLS_LIST=Arrays.asList("sys_keyctl", "sys_request_key", "sys_add_key"); 
		  }	 
		 //need to replace with 3.5 to 3.13
		// For kernel 3.13 Ubuntu 14.04 (currently we assume it will work for Ubuntu 13.10 and 13.04
		  else  if (trainingOptions[3].equals("true")){  
			     ARCH_CALLS_LIST=Arrays.asList(
				 "sys_iopl", "sys_fallocate", "sys_ftruncate64", "sys_sigreturn", "sys_vm86old", "sys_mmap", "sys_set_thread_area", "sys_pread64", "sys_vm86", "sys_get_thread_area", "compat_sys_rt_sigreturn", "sys_pwrite64", "sys_truncate64", "sys_rt_sigreturn", "sys_llseek", "sys_sigsuspend" 
	  		     );	
			     KERNEL_CALLS_LIST=Arrays.asList(
				 "sys_getpgrp", "sys_getpriority", "compat_sys_timer_create", "sys_timer_gettime", "sys_adjtimex", "sys_geteuid16", "sys_setitimer", "sys_sched_setscheduler", "sys_waitid", "sys_getegid16", "sys_gettid", "sys_getsid", "sys_gettimeofday", "sys_setfsuid16", "sys_setreuid", "sys_setdomainname", "sys_fork", "sys_capset", "sys_kexec_load", "sys_getitimer", "sys_unshare", "sys_old_getrlimit", "sys_clock_settime", "sys_stime", "compat_sys_kexec_load", "sys_nanosleep", "sys_getresgid", "sys_setresuid16", "sys_sched_getscheduler", "sys_clock_getres", "sys_sched_rr_get_interval", "sys_time", "sys_futex", "sys_ptrace", "sys_clock_gettime", "sys_settimeofday", "sys_tgkill", "sys_setuid", "sys_sched_yield", "sys_rt_sigaction", "sys_clock_nanosleep", "sys_setfsgid16", "sys_pause", "sys_perf_event_open", "sys_uname", "compat_sys_rt_sigaction", "sys_getuid", "sys_rt_sigtimedwait", "sys_getgid16", "compat_sys_ptrace", "sys_rt_sigprocmask", "sys_setregid16", "sys_sigaction", "sys_rt_sigqueueinfo", "sys_setfsgid", "sys_alarm", "sys_sysinfo", "sys_getpgid", "sys_setrlimit", "sys_wait4", "sys_ssetmask", "sys_newuname", "sys_reboot", "sys_prctl", "sys_getgroups16", "sys_timer_create", "sys_personality", "sys_getuid16", "sys_setgroups", "sys_timer_settime", "sys_init_module", "compat_sys_rt_sigpending", "compat_sys_rt_sigtimedwait", "sys_lchown16", "sys_finit_module", "sys_setresuid", "compat_sys_waitid", "sys_getresgid16", "sys_exit_group", "sys_times", "sys_setgid", "sys_clock_adjtime", "sys_setsid", "sys_sched_setparam", "sys_sched_getaffinity", "sys_setpriority", "sys_setresgid", "sys_timer_delete", "compat_sys_get_robust_list", "sys_setreuid16", "sys_sched_setaffinity", "sys_timer_getoverrun", "compat_sys_sigaltstack", "sys_getrlimit", "compat_sys_rt_sigqueueinfo", "sys_getppid", "sys_getresuid16", "sys_setns", "sys_setfsuid", "sys_getpid", "sys_getegid", "sys_getgroups", "sys_sigpending", "compat_sys_rt_tgsigqueueinfo", "sys_kill", "sys_vfork", "sys_getgid", "sys_sched_get_priority_min", "sys_delete_module", "sys_sched_getparam", "sys_sgetmask", "sys_setgroups16", "sys_rt_sigpending", "sys_capget", "sys_getresuid", "sys_set_robust_list", "sys_setregid", "sys_set_tid_address", "sys_umask", "sys_exit", "sys_signal", "sys_getcpu", "sys_sysctl", "sys_syslog", "sys_clone", "sys_rt_sigsuspend", "sys_restart_syscall", "sys_getrusage", "sys_geteuid", "sys_get_robust_list", "sys_setresgid16", "sys_sigprocmask", "sys_sched_get_priority_max", "sys_setuid16", "sys_olduname", "sys_sethostname", "sys_rt_tgsigqueueinfo", "sys_sigaltstack", "sys_acct", "sys_chown16", "sys_setgid16", "sys_tkill", "sys_waitpid", "sys_prlimit64", "sys_kcmp", "sys_nice", "compat_sys_set_robust_list", "sys_fchown16", "sys_setpgid" 
				 );
			     MM_CALLS_LIST= Arrays.asList(
				 "sys_mbind", "compat_sys_process_vm_readv", "compat_sys_process_vm_writev", "sys_migrate_pages", "sys_move_pages", "compat_sys_move_pages", "sys_get_mempolicy", "sys_remap_file_pages", "sys_madvise", "sys_fadvise64", "sys_swapon", "sys_readahead", "sys_process_vm_writev", "sys_munlockall", "sys_mincore", "sys_mlock", "sys_mmap_pgoff", "sys_msync", "sys_brk", "sys_mprotect", "sys_fadvise64_64", "sys_set_mempolicy", "sys_swapoff", "sys_munmap", "sys_mlockall", "sys_process_vm_readv", "sys_munlock", "sys_old_mmap", "sys_mremap" 
				 );
				 //not found
				 //"sys_modify_ldt", "sys_ioperm", "sys_unused", "sys_arch_prctl", "sys_available", 
			     NET_CALLS_LIST=Arrays.asList(
				 "sys_recvmsg", "sys_sendmmsg", "sys_sendto", "sys_getsockname", "compat_sys_setsockopt", "sys_socket", "sys_recvfrom", "sys_setsockopt", "sys_shutdown", "compat_sys_recvfrom", "sys_bind", "compat_sys_getsockopt", "compat_sys_recvmmsg", "sys_sendmsg", "compat_sys_sendmsg", "sys_listen", "sys_accept", "sys_connect", "sys_socketcall", "sys_socketpair", "compat_sys_sendmmsg", "sys_recvmmsg", "sys_accept4", "sys_getpeername", "compat_sys_recvmsg", "sys_getsockopt" 
				  );
			     FS_CALLS_LIST= Arrays.asList(
				 "sys_preadv", "sys_open", "compat_sys_preadv64", "sys_mkdir", "sys_linkat", "compat_sys_ioctl", "sys_old_readdir", "sys_close", "sys_sendfile", "compat_sys_vmsplice", "sys_fanotify_mark", "sys_tee", "compat_sys_writev", "sys_chdir", "sys_symlink", "sys_signalfd", "sys_fchownat", "sys_statfs", "sys_sendfile64", "sys_fstatfs64", "sys_lchown", "sys_fstat64", "sys_readlinkat", "sys_rmdir", "sys_utimes", "sys_fchmod", "compat_sys_readv", "sys_open_by_handle_at", "sys_fsync", "sys_vmsplice", "sys_umount", "sys_epoll_create", "sys_eventfd2", "sys_sysfs", "sys_inotify_init1", "sys_pipe", "sys_mount", "sys_link", "sys_statfs64", "sys_getdents", "sys_read", "sys_inotify_rm_watch", "sys_epoll_wait", "sys_name_to_handle_at", "sys_epoll_pwait", "sys_lremovexattr", "sys_fstat", "sys_faccessat", "sys_inotify_add_watch", "sys_dup3", "sys_dup2", "sys_utime", "sys_uselib", "sys_io_destroy", "sys_unlinkat", "sys_ioprio_get", "sys_oldumount", "sys_utimensat", "sys_fremovexattr", "sys_splice", "sys_chown", "sys_execve", "sys_poll", "sys_lseek", "sys_ustat", "sys_epoll_create1", "sys_dup", "sys_newlstat", "sys_fgetxattr", "sys_getdents64", "sys_old_select", "sys_io_submit", "sys_timerfd_gettime", "sys_openat", "sys_writev", "sys_io_getevents", "sys_getxattr", "sys_sync", "sys_fstatat64", "sys_readv", "sys_eventfd", "sys_stat", "sys_mkdirat", "sys_getcwd", "sys_epoll_ctl", "sys_ioctl", "sys_fdatasync", "sys_syncfs", "compat_sys_execve", "sys_unlink", "sys_flock", "sys_flistxattr", "sys_pipe2", "sys_stat64", "sys_lgetxattr", "sys_lstat", "sys_newfstat", "sys_sync_file_range", "sys_symlinkat", "sys_write", "sys_lsetxattr", "sys_listxattr", "sys_readlink", "sys_fsetxattr", "sys_fchmodat", "sys_lookup_dcookie", "sys_chroot", "sys_removexattr", "sys_ioprio_set", "sys_quotactl", "sys_mknodat", "sys_ftruncate", "sys_io_cancel", "sys_truncate", "sys_fstatfs", "sys_rename", "sys_creat", "sys_bdflush", "sys_renameat", "compat_sys_pwritev64", "sys_futimesat", "sys_fchdir", "sys_pselect6", "sys_mknod", "sys_access", "sys_inotify_init", "sys_pivot_root", "sys_fchown", "sys_pwritev", "sys_ppoll", "sys_timerfd_create", "sys_lstat64", "sys_llistxattr", "sys_select", "sys_newfstatat", "sys_fanotify_init", "sys_fcntl64", "sys_io_setup", "sys_setxattr", "sys_timerfd_settime", "sys_signalfd4", "sys_vhangup", "sys_fcntl", "sys_newstat", "sys_chmod" 
				  );
			     IPC_CALLS_LIST=Arrays.asList(
				 "sys_mq_open", "sys_semctl", "sys_mq_timedsend", "sys_mq_timedreceive", "sys_msgctl", "sys_msgget", "sys_shmget", "sys_semtimedop", "sys_mq_getsetattr", "sys_mq_unlink", "sys_ipc", "sys_shmat", "sys_msgsnd", "sys_mq_notify", "sys_shmdt", "compat_sys_mq_notify", "sys_shmctl", "sys_semop", "sys_semget", "sys_msgrcv" 
				 );
			     SECURITY_CALLS_LIST=Arrays.asList(
				 "sys_keyctl", "sys_request_key", "sys_add_key");
		  }
	}
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#getDescription()
	 */
	@Override
	public String getDescription(){
	 return " KSM focuses on detection of anomalies by transforming system calls into states of kernel modules. "
	 		+ "A system call can be classified into eight different states: file system state, kernel state (KL), "
	 		+ " memory management state (MM), networking state (NT), inter process communication state (IPC), security state (SC), "
	 		+ "  architecture state (AC) and (a rare) unknown state (UN). KSM then identifies anomalies by comparing the probabilities of occurrences of states "
	 		+ "in normal and anomalous traces. During the training and validation phases, KSM learns the probabilities"
	 		+ " of states from traces, adjust a decision threshold alpha, and use this information to evaluate states of"
	 		+ " traces in the testing phase. State representation facilitates in reducing computation complexity and "
	 		+ "allows system wide anomaly detection with a lesser false positive rate than other techniques";
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#isOnlineLearningSupported()
	 */
	@Override
	public boolean isOnlineLearningSupported() {
		
		return true;
	}

}
