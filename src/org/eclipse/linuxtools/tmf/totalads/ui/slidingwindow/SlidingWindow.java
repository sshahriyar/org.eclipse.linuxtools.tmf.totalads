/**
 * 
 */
package org.eclipse.linuxtools.tmf.totalads.ui.slidingwindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.linuxtools.tmf.totalads.ui.Configuration;
import org.eclipse.linuxtools.tmf.totalads.ui.DBMS;
import org.eclipse.linuxtools.tmf.totalads.ui.IDetectionModels;
import org.eclipse.linuxtools.tmf.totalads.ui.ITraceIterator;
import org.eclipse.linuxtools.tmf.totalads.ui.ModelTypeFactory;
import org.eclipse.linuxtools.tmf.totalads.ui.ProgressConsole;
import org.eclipse.linuxtools.tmf.totalads.ui.TotalADSUiException;
import org.eclipse.linuxtools.tmf.totalads.ui.IDetectionModels.Results;
import org.eclipse.linuxtools.tmf.totalads.ui.ModelTypeFactory.ModelTypes;
import org.swtchart.Chart;

/**
 * @author Syed Shariyar Murtaza
 * This class models a sliding window algorithm over traces of events
 */
public class SlidingWindow implements IDetectionModels {
	 
	String TRACE_COLLECTION=Configuration.traceCollection;
	String SETTINGS_COLLECTION=Configuration.settingsCollection;
	class Event{ String event; ArrayList<Event[]> branches= null;  }
	HashMap<String, Event[]> sysCallSequences= new HashMap<String, Event[]>();
	/**
	 * Constructor
	 * 	 */
	public SlidingWindow() {
		
	}
	
	/**
	 * Creates a database to store models
	 */
	@Override
	public void createDatabase(String databaseName, DBMS connection) throws Exception{
		String []collectionNames={TRACE_COLLECTION, SETTINGS_COLLECTION};
		connection.createDatabase(databaseName, collectionNames);
	}
	
	/* 
	 * 
	 */
	@Override
	public void train (ITraceIterator trace, Boolean isLastTrace, String database, DBMS connection, ProgressConsole console)  throws Exception {
	      sysCallSequences.clear();
		  int totalLines=0, winWidth=0;
	      int maxWin=5;
	      //String [] newSequence=new String[5];
	      LinkedList<String> newSequence=new LinkedList<String>();
	      
	      
	      while (trace.advance()) {
	    	  totalLines++;
	    	  
	    	  //newSequence[winWidth]=trace.getCurrentEvent();
	    	 // console.printText(newSequence[winWidth]+" ");
	    	  newSequence.add(trace.getCurrentEvent());
	    	  //console.printText(newSequence.get(winWidth)+" ");
	    	  winWidth++;
	    	      	  
	    	  if(winWidth >= maxWin){
	    		  		
	    		  winWidth--;
	    		  //console.printNewLine();
	    		  String[] seq=new String[maxWin];
	    		  seq=newSequence.toArray(seq);
	    		  searchAndAddSequence(seq);
	    		  newSequence.remove(0);
	    	  }
	    		  
	     }
	     // for those sequences which are less than the window width for a trace smaller than window width
	      //if( totalLines< maxWin){
		  	//	winWidth--;
			  //System.out.println(sequence.toString());
		// }
	      		
		//console.printTextLn("");

	}

	/* 
	 * 
	 */
	@Override
	public  void validate (ITraceIterator trace, String database, DBMS connection, Boolean isLastTrace, ProgressConsole console) throws Exception {
		printSequence(console);

	}
	

	/* (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.ui.IDetectionModels#test(char[], java.lang.String)
	 */
	@Override
	public Results test (ITraceIterator trace,  String database, DBMS connection) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.ui.IDetectionModels#isValidationAllowed()
	 */
	

	/* (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.ui.IDetectionModels#textResult()
	 */
	@Override
	public String textResult() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.ui.IDetectionModels#graphicalResults()
	 */
	@Override
	public Chart graphicalResults() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.linuxtools.tmf.totalads.ui.IDetectionModels#createInstance()
	 */
	@Override
	public IDetectionModels createInstance() {
		
		return new SlidingWindow();
	}

	/** 
	 * Returns the name
	 */
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Sliding Window";
	}
	
	 /**
     * Returns the acronym of the model
     */
    public String getAcronym(){
    	
    	return "SWN";
    }
	/**
	 *  Self registration of the model with the modelFactory 
	 */
	public static void registerModel() throws TotalADSUiException{
		ModelTypeFactory modelFactory= ModelTypeFactory.getInstance();
		SlidingWindow sldWin=new SlidingWindow();
		modelFactory.registerModelWithFactory( ModelTypeFactory.ModelTypes.Anomaly,  sldWin);
	}
	
	/**
	 * Searches and adds a sequence in the training database. 
	 * If a sequence already exists, it updates the counter
	 * @param sequence sequence to add
	 */
	private void searchAndAddSequence(String []newSequence){
		Integer seqSize=newSequence.length;
		Event[] eventSequence= sysCallSequences.get(newSequence[0]);
		if (eventSequence==null){ // if there is no such starting event then add sequence to such an event
			
			eventSequence=new Event[seqSize+1];
			for (int j=0;j<seqSize;j++){
				eventSequence[j]=new Event();
				eventSequence[j].event=newSequence[j];
			}
			eventSequence[seqSize]=new Event();
			eventSequence[seqSize].event="1";
			
		}else{
			
			Boolean isFound=searchAndAppendSequence(eventSequence, newSequence);// search in graph from this node/sequence
			
			if (isFound==false){// if not found then add on 0 branch 
				eventSequence[0].branches=new ArrayList<Event[]>();
				Event[] newBranchSeq=new Event[seqSize+1];
				for (int j=0;j<seqSize-1;j++){
					newBranchSeq[j]=new Event();
					newBranchSeq[j].event=newSequence[j];
				}
				newBranchSeq[seqSize]=new Event();
				newBranchSeq[seqSize].event="1";
				eventSequence[0].branches.add(newBranchSeq);
			}
		}
		// putting the sequence (graph actually) to the starting event (node) as a key
		sysCallSequences.put(newSequence[0],eventSequence);
	}
	
	/**
	 * Searches and appends a sequence to an already existing graph of sequences. 
	 * If a sequence already exists, it updates the counter 
	 * Use the following graph-example to understand the code
	 * 
	 * 108-106-5-55-45
	 * 90
	 * |
	 * 3---9-6
	 * | |-3-10
	 * | |-3-6
	 * |
	 * 106
	 * |
	 * 5
	 * @param eventSeq an already existing sequence of events 
	 * @param newSeq   a sequence of events that requires to be appended
	 * @return
	 */
	private Boolean searchAndAppendSequence(Event[] eventSeq, String []newSeq){
		
	Integer seqSize=newSeq.length;
	Integer j;
	for (j=0; j < seqSize;j++){
	 	
		if (!eventSeq[j].event.equals(newSeq[j]))
				break;
	}
	 
	Integer matchIdx=j-1;
	 
	if (matchIdx>=seqSize-1){
	 	Integer counter= Integer.parseInt(eventSeq[seqSize].event)+1;
	 	eventSeq[seqSize].event=counter.toString();
		return true;	
	}
	else if (matchIdx <0){
			return false; // return null if mismatch on the first idx
	}
	
	else {
		 Event []newEventSeq= new Event[seqSize-matchIdx];//+1 for the count
		 String [] newTmpSeq= new String [seqSize-matchIdx-1];
	     Boolean isFound=false;      
	     Integer i;
		 for ( i=0; i <newEventSeq.length-1; i++){
			 		newEventSeq[i]=new Event();
					newEventSeq[i].event=newSeq[matchIdx+i+1];// that is copy from the next index of matched index
					newTmpSeq[i]=newSeq[matchIdx+i+1];
		 }
		 newEventSeq[i]=new Event();
		 newEventSeq[i].event="1";// add 1 as a counter at the leaf
			
		ArrayList<Event[]> branches= eventSeq[matchIdx].branches;
	//      When there are no more branches then we shall automatically 
	//       add a new branch by skipping the if block
		if (branches!=null){
	          //if the branches exist then we need to recursively go through the remaining branches to find 
				// a possible location to append the new sequence
				
				for (int bCount=0; bCount<branches.size(); bCount++){
					Event []branchEventSeq=branches.get(bCount);
					/// ****** recursive call
					isFound=searchAndAppendSequence(branchEventSeq,newTmpSeq);
					/// ****** recursive call
					if (isFound==true){//{// there is no need to iterate more branches, we have found a match
						branches.set(bCount, branchEventSeq);
						//branches.add(returnSeq);
						break;
					}
		        }
		}else{
			branches= new ArrayList<Event[]>();
		}
	    //We have just found out where to append a branch in the graph
	 	  //add a new branch to the event and return the eventSeq.   
		
		if (isFound==false)
			branches.add(newEventSeq);
	
		eventSeq[matchIdx].branches=branches;
		return true;
		   
	}
	// End of function searchAndAddSequence
   }
	
	/**
	 * Prints the graph of sequence
	 * @param console
	 */
	private void printSequence(ProgressConsole console){
		for(Map.Entry<String, Event[]>nodes:  sysCallSequences.entrySet()){
			printRecursive(nodes.getValue(),"", console);
			
		}
	}
	/**
	 * This function goes through the graph ov Events and print the sequences in a human readable
	 * manner
	 * @param nodes
	 * @param prefix
	 * @param console
	 */
	private void printRecursive(Event[] nodes,String prefix, ProgressConsole console){
	      
		Boolean isPrefixPrinted=false;
		   for (int nodeCount=0; nodeCount<nodes.length; nodeCount++){
			      
				  ArrayList<Event[]> branches=nodes[nodeCount].branches;
				   if (branches!= null){
					 prefix=prefix+nodes[nodeCount].event+"-";
					 for(int i=0;i<branches.size(); i++){
							printRecursive(branches.get(i),prefix,console);
					   }   
					}
				    else{
				     
					    if (!isPrefixPrinted){
							  console.printText(prefix);
							  isPrefixPrinted=true;	
				    		}
					 		console.printText(nodes[nodeCount].event+"-");
				}
		}
	  	console.printNewLine();	    
	}
	
	@Override
	public void crossValidate(Integer folds, String database, DBMS connection, ProgressConsole console) throws Exception{
		
	}
}
