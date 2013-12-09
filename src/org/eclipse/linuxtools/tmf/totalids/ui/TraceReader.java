package org.eclipse.linuxtools.tmf.totalids.ui;


import java.io.File;

import org.eclipse.linuxtools.lttng2.kernel.core.*;
import org.eclipse.linuxtools.ctf.core.trace.CTFReaderException;
import org.eclipse.linuxtools.ctf.core.trace.CTFTrace;
import org.eclipse.linuxtools.internal.lttng2.kernel.core.Attributes;
import org.eclipse.linuxtools.internal.lttng2.kernel.core.LttngStrings;
import org.eclipse.linuxtools.tmf.core.ctfadaptor.CtfIterator;
import org.eclipse.linuxtools.tmf.core.ctfadaptor.CtfTmfEvent;
import org.eclipse.linuxtools.tmf.core.ctfadaptor.CtfTmfTrace;
import org.eclipse.linuxtools.tmf.core.event.ITmfEventField;
import org.eclipse.linuxtools.tmf.core.exceptions.TmfTraceException;
import org.eclipse.linuxtools.tmf.core.tests.shared.CtfTmfTestTrace;

/**
 * Class to read CTF traces by using CtfTmfTrace class.
 * @author Syed Shariyar Murtaza 
 */
public class TraceReader   {
	
           
    
    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Instantiate a new trace reader
     *
     */
    public TraceReader() {
          //this.trace=trace;
          //this.traceBuffer=buffer;
    }

	/**
	 * 
	 * @param file
	 * @return
	 */
	public StringBuilder getTrace(File file) throws Exception{
		
		 String filePath=file.getPath();
		 StringBuilder traceBuffer= new StringBuilder();
		 CtfTmfTrace  fTrace = new CtfTmfTrace();
	
		 try {
	            fTrace.initTrace(null, filePath, CtfTmfEvent.class);
	      } catch (TmfTraceException e) {
	            /* Should not happen if tracesExist() passed */
	            throw new RuntimeException(e);
	      }
	      
		 	 
    	 //TraceReader input = new TraceReader(fTrace,traceBuffer);
    	 readTrace(fTrace,traceBuffer);
    	 fTrace.dispose();
    	 return traceBuffer;
	}

    
/**
 * Reads the trace
 * 
 */
   private void readTrace(CtfTmfTrace  trace,StringBuilder traceBuffer){
    	
    	CtfIterator traceIterator=trace.createIterator();
    	 
    	while (traceIterator.advance()){
    		
    		CtfTmfEvent event = traceIterator.getCurrentEvent();
    		handleEvents(event, traceBuffer);
    		   		 
    		
    	}
   
    }
   /**
    * This function dispatches each event to its appropriate handle
    * @param event
    */			

public void handleEvents(CtfTmfEvent event, StringBuilder traceBuffer) {
	String eventName=event.getEventName();
	
	if (eventName.startsWith(LttngStrings.SYSCALL_PREFIX)){
          //  || eventName.startsWith(LttngStrings.COMPAT_SYSCALL_PREFIX)) {
					handleSysCallEntryEvent(event, traceBuffer);
					
	 }
	/*not needed right now, may be in the future it will be uncommented
	 * else if (eventName.equals(LttngStrings.EXIT_SYSCALL)){
					handleSysExitEvent(event);
	}*/
	
}

/**
 * This is an event handler for system call exit event
 * @param event
 */
private void handleSysExitEvent(CtfTmfEvent event, StringBuilder traceBuffer) {
	ITmfEventField content = event.getContent();
	ITmfEventField returnVal=content.getField("ret");
	System.out.println("Ret: "+returnVal.getValue()); 
  //  accumulator.add(event.getTimestamp(), field);// whatever internal storage you want to use.
}

/**
 * This is an event handler for System call events 
 * @param event
 */
private void handleSysCallEntryEvent(CtfTmfEvent event, StringBuilder traceBuffer) {
	String eventName=event.getEventName();
	//System.out.println(eventName);
	Integer id=MapSysCallNameToID.getSysCallID(eventName.trim());
	if (id==null){
		//throw new Exception("System call not found in the map: "+eventName);
		id=-1;
	}
	traceBuffer.append(id).append("\n");
}


    
}

