/*********************************************************************************************
 * Copyright (c) 2014  Software Behaviour Analysis Lab, Concordia University, Montreal, Canada
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of XYZ License which
 * accompanies this distribution, and is available at xyz.com/license
 *
 * Contributors:
 *    Syed Shariyar Murtaza
 **********************************************************************************************/
package org.eclipse.linuxtools.tmf.totalads.readers.ctfreaders;


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
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSReaderException;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSUIException;
import org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator;
import org.eclipse.linuxtools.tmf.totalads.readers.ITraceTypeReader;
import org.eclipse.linuxtools.tmf.totalads.readers.TraceTypeFactory;

/**
 * Class to read system calls from LTTng traces by using CtfTmfTrace class.
 * @author <p> Syed Shariyar Murtaza justsshary@hotmail.com</p> 
 */
public class CTFLTTngSysCallTraceReader implements ITraceTypeReader   {
	 // ------------------------------------------------------------------------
     // Inner class: Implements and iterator 
	 // ------------------------------------------------------------------------
     private class CTFSystemCallIterator implements ITraceIterator {   
    	 private CtfIterator traceIterator=null;
    	 private CtfTmfTrace  trace=null;
    	 private Boolean isDispose=false;
    	  
    	 public CTFSystemCallIterator(CtfTmfTrace  tmfTrace){
    		   trace=tmfTrace;
    		   traceIterator=tmfTrace.createIterator();
    	   }
    	  /** Moves Iterator to the next event, and returns true if the iterator can advance or false if the iterator cannot advance **/ 
    	   @Override
    	    public boolean advance(){
    			boolean isAdvance=traceIterator.advance();
    		
    			if (!isAdvance){
    				isDispose=true;
    				trace.dispose();
    			}
    				
    			return isAdvance;
    					
    		}
    		/** Returns the event for the location of the iterator  **/ 
    		@Override
    	    public String getCurrentEvent(){
    			
    			String syscall="";
    			do{
    				CtfTmfEvent event = traceIterator.getCurrentEvent();
    				syscall=handleSysEntryEvent(event);
        		} while (syscall.isEmpty() && advance());
    			
    			if (syscall.isEmpty())
    				return null;
    			else 
    				return syscall;
    			
    		}
    	
    		/** Closes the iterator stream **/
    		@Override
    		public void close(){
    			if (!isDispose)
    				trace.dispose();
    		}
    		/**
    		 * Returns System Call
    		 * @param event Event object of type CtfTmfEvent
    		 * @return Event  as a String
    		 */
    		private String handleSysEntryEvent(CtfTmfEvent event) {
    			String eventName=event.getEventName();
    			String syscall="";
    			//System.out.println(eventName);
    			if (eventName.startsWith(LttngStrings.SYSCALL_PREFIX)){
    				//Integer id=MapSysCallIDToName.getSysCallID(eventName.trim());
    				//if (id==null) id=-1;
    				syscall=eventName.trim();
    			 }
    			return syscall;
    			
    		} 
    

     }
     
    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor: Instantiate a new trace reader
     *
     */
    public CTFLTTngSysCallTraceReader() {
          //this.trace=trace;
          //this.traceBuffer=buffer;
    }
    /**
     * Creates an instance through the use of ITraceTypeReader
     */
   @Override
    public ITraceTypeReader createInstance(){
    	return new CTFLTTngSysCallTraceReader();
    }
   /**
    * Registers itself with the TraceTypeFactory
    * @throws TotalADSUIException
    */
    
    public static void registerTraceTypeReader() throws TotalADSUIException{
    	TraceTypeFactory trcTypFactory=TraceTypeFactory.getInstance();
    	CTFLTTngSysCallTraceReader kernelTraceReader=new CTFLTTngSysCallTraceReader();
    	trcTypFactory.registerTraceReaderWithFactory(kernelTraceReader.getName(), kernelTraceReader);
    }
    /**
     * Returns the name of the model
     * @return Name
     */
    @Override
    public String getName(){
    	return "LTTng System Call Reader";
    }
    /**
     * Returns the acronym of the Kernel space reader
     * @return Acronym
     */
    public String getAcronym(){
    	
    	return "SYS";
    }
	/**
	 * Return the iterator to go over the trace file
	 * @param file The file object
	 * @return Iterator on a trace
	 */
    public ITraceIterator getTraceIterator(File file) throws TotalADSReaderException{
    	
    	 String filePath=file.getPath();
		 CtfTmfTrace  fTrace = new CtfTmfTrace();
	
		 try {
	            fTrace.initTrace(null, filePath, CtfTmfEvent.class);
	            
	      } catch (TmfTraceException e) {
	            /* Should not happen if tracesExist() passed */
	            throw new TotalADSReaderException(e.getMessage());
	      }
		 
		 return new CTFSystemCallIterator(fTrace);
    }
  
    
}
