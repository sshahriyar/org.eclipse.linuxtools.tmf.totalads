/*********************************************************************************************
 * Copyright (c) 2014  Software Behaviour Analysis Lab, Concordia University, Montreal, Canada
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of XYZ License which
 * accompanies this distribution, and is available at xyz.com/license
 *
 * Contributors:
 *    Syed Shariyar Murtaza
 **********************************************************************************************/
package org.eclipse.linuxtools.tmf.totalads.ui.live;


import java.awt.event.MouseAdapter;

import org.eclipse.linuxtools.tmf.totalads.algorithms.AlgorithmFactory;
import org.eclipse.linuxtools.tmf.totalads.core.TMFTotalADSView;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSNetException;
import org.eclipse.linuxtools.tmf.totalads.readers.ctfreaders.CTFLTTngSysCallTraceReader;
import org.eclipse.linuxtools.tmf.totalads.ui.TotalADS;
import org.eclipse.linuxtools.tmf.totalads.ui.TraceBrowser;
import org.eclipse.linuxtools.tmf.totalads.ui.TracingTypeSelector;
import org.eclipse.linuxtools.tmf.totalads.ui.diagnosis.ModelLoader;
import org.eclipse.linuxtools.tmf.totalads.ui.diagnosis.ResultsAndFeedback;
import org.eclipse.linuxtools.tmf.totalads.ui.modeling.ProgressConsole;
import org.eclipse.linuxtools.tmf.totalads.ui.modeling.StatusBar;
import org.eclipse.linuxtools.tmf.totalads.ui.utilities.SWTResourceManager;
import org.eclipse.osgi.framework.internal.core.Msg;
import org.eclipse.swt.SWT;
//import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
//import org.eclipse.swt.widgets.Button;
//import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ProgressBar;
//import org.eclipse.swt.widgets.TabItem;
//import org.eclipse.swt.widgets.Table;
//import org.eclipse.swt.widgets.TableColumn;
//import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;

/**
 * This class creates the GUI elements/widgets for live diagnosis using ssh. Any host can be selected and LTTNg tracing
 * can be started on that system and one of the eqarlier model can be used to test the live tracing
 * 
 * 
 * @author <p> Syed Shariyar Murtaza justsshary@hotmail.com </p>
 *
 */
public class LiveMonitor {
	//Initializes variables
	private TracingTypeSelector traceTypeSelector;
	private Text txtPassword;
	private Text txtUserAtHost;
	private TraceBrowser traceBrowser;
	private StringBuilder tmfTracePath;
	private StringBuilder currentlySelectedTracesPath;
	private ModelLoader modelLoader;
	private ResultsAndFeedback resultsAndFeedback;
	private Button btnSelTestTraces;
	private Button btnSelTMFTrace;
	private Button btnLiveTracing;
	private ProgressConsole console;

	/**
	 * Constructor of the LiveMonitor class
	 * @param tabFolderParent TabFolder object
	 *
	 */
	public LiveMonitor(CTabFolder tabFolderParent){
		tmfTracePath=new StringBuilder();
		currentlySelectedTracesPath=new StringBuilder();
		//LiveMonitor Tab Item
		CTabItem tbItmDiagnosis = new CTabItem(tabFolderParent, SWT.NONE);
		tbItmDiagnosis.setText("Live Monitor");
		//Making scrollable tab item 
		ScrolledComposite scrolCompAnom=new ScrolledComposite(tabFolderParent, SWT.H_SCROLL | SWT.V_SCROLL);
		Composite comptbItmDiagnosis = new Composite(scrolCompAnom,SWT.NONE);
		tbItmDiagnosis.setControl(scrolCompAnom);
		
		//Desiging the Layout of the GUI Items  for the LiveMonitor Tab Item
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.horizontalSpan=1;
		comptbItmDiagnosis.setLayoutData(gridData);
		comptbItmDiagnosis.setLayout(new GridLayout(2, false));
	
		///////////////////////////////////////////////////////////////////////////
		//Creating GUI widgets for selection of a trace type and a selection of the model
		///////////////////////////////////////////////////////////////////
		Composite compTraceTypeAndModel=new Composite(comptbItmDiagnosis, SWT.NONE);
		compTraceTypeAndModel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
		compTraceTypeAndModel.setLayout(new GridLayout(1, false));
		
	
		selectHostUsingSSH(compTraceTypeAndModel);
		// Create GUI elements for a selection of a trace
		
		//Initialize a class which loads model names from db and create appropriate GUI elements
		
		//modelLoader=new ModelLoader(compTraceTypeAndModel);
		//modelLoader.setTrace(currentlySelectedTracesPath);///////////////////////////////
		//modelLoader.setTraceTypeSelector(traceTypeSelector);
		
		
		//////////////////////////////////////////////////////////////////////
		// Creating GUI widgets for status, results and feedback
		//////////////////////////////////////////////////////////////////
		Composite compStatusResults=new Composite(comptbItmDiagnosis, SWT.NONE);
		compStatusResults.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		compStatusResults.setLayout(new GridLayout(1, false));
		console =new ProgressConsole(compStatusResults);
		
		resultsAndFeedback=new ResultsAndFeedback(compStatusResults);
		//modelLoader.setResultsAndFeedback(resultsAndFeedback);
	
		
		//Adjust settings for scrollable LiveMonitor Tab Item
		scrolCompAnom.setContent(comptbItmDiagnosis);
		 // Set the minimum size
		scrolCompAnom.setMinSize(500, 500);
	    // Expand both horizontally and vertically
		scrolCompAnom.setExpandHorizontal(true);
		scrolCompAnom.setExpandVertical(true);
	}

	
	
	/**
	 * Creates GUI widgets for a selection of traces and trace types
	 * @param compDiagnosis Composite of LiveMonitor
	 */
	private void selectHostUsingSSH(Composite compDiagnosis){
		/**
		 *  Group trace selection
		 */
		Group grpSSHConfig = new Group(compDiagnosis, SWT.NONE);
		grpSSHConfig.setText("Select Configuration");
		
		grpSSHConfig.setLayoutData(new GridData(SWT.FILL,SWT.FILL,false,false));
		grpSSHConfig.setLayout(new GridLayout(3,false));
		
		Label userAtHost= new Label(grpSSHConfig, SWT.NONE);
		userAtHost.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, false, false,1,1));
		userAtHost.setText("Enter username@hostname   ");
		
		Label lblSudoPassword= new Label(grpSSHConfig, SWT.NONE);
		lblSudoPassword.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, false, false,1,1));
		 lblSudoPassword.setText("Sudo Password ");
		
		Label lblPort= new Label(grpSSHConfig, SWT.NONE);
		lblPort.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false,1,1));
		lblPort.setText("Port");
			
		txtUserAtHost=new Text(grpSSHConfig, SWT.BORDER);
		txtUserAtHost.setEnabled(true);
		txtUserAtHost.setLayoutData(new GridData(SWT.FILL,SWT.TOP,false,false));
		txtUserAtHost.setText(System.getProperty("user.name")+"@localhost");
		txtUserAtHost.setText("shary@172.30.39.85");
		
		Text txtSudoPassword=new Text(grpSSHConfig,SWT.BORDER|SWT.PASSWORD);
		txtSudoPassword.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false,1,1));
		txtSudoPassword.setText("grt_654321");
		
		Text txtPort=new Text(grpSSHConfig,SWT.BORDER);
		txtPort.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false,1,1));
		txtPort.setText("22");
		/////////
		///SSH Password and Private Key
		/////////
		Group grpPrivacy = new Group(grpSSHConfig, SWT.NONE);
		grpPrivacy.setText("SSH Password/Pvt. Key");
		
		grpPrivacy.setLayoutData(new GridData(SWT.FILL,SWT.FILL,false,false,3,2));
		grpPrivacy.setLayout(new GridLayout(3,false));
				
		Button btnPassword = new Button(grpPrivacy, SWT.RADIO);
		btnPassword.setText("Enter Password");
		btnPassword.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true, false,1,1));
		
		
		Button btnPvtKey = new Button(grpPrivacy, SWT.RADIO);
		btnPvtKey.setText("Select Private Key");
		btnPvtKey.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true, false,2,1));
		
		
		txtPassword=new Text(grpPrivacy,SWT.BORDER|SWT.PASSWORD);
		txtPassword.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false,1,1));
		txtPassword.setText("grt_654321");
		
		Text txtPvtKey=new Text(grpPrivacy,SWT.BORDER);
		txtPvtKey.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false,1,1));
		txtPvtKey.setText("");
		
		TraceBrowser trcbrowser=new TraceBrowser(grpPrivacy, txtPvtKey, new GridData(SWT.RIGHT, SWT.TOP, false, false));
		//////////
		///End SSH password and private key
		//////
		///////////////
		// Duration and Port
		//////////
		Composite compDurationPort=new Composite(grpSSHConfig, SWT.NONE);
		compDurationPort.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false,3,2));
		compDurationPort.setLayout(new GridLayout(2,false));
		
		Label lblSnapshotDuration= new Label(compDurationPort, SWT.NONE);
		lblSnapshotDuration.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false,1,1));
		lblSnapshotDuration.setText("Snapshot Duration (secs)");
		
		Label lblIntervalDuration= new Label(compDurationPort, SWT.NONE);
		lblIntervalDuration.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false,1,1));
		lblIntervalDuration.setText("Snapshots Interval (mins)");
		
		
		Combo cmbSnapshot=new Combo(compDurationPort, SWT.NONE| SWT.READ_ONLY);
		cmbSnapshot.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false,1,1));
		cmbSnapshot.add("10"); 
		cmbSnapshot.add("20"); 
		cmbSnapshot.add("30");
		cmbSnapshot.add("60");
		cmbSnapshot.add("80");
		cmbSnapshot.add("100");
		cmbSnapshot.select(0);
		
		Combo cmbInterval=new Combo(compDurationPort, SWT.NONE|SWT.READ_ONLY);
		cmbInterval.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false,1,1));
		cmbInterval.add("3"); 
		cmbInterval.add("5"); 
		cmbInterval.add("7");
		cmbInterval.add("10");
		cmbInterval.add("15");
		cmbInterval.add("20");
		cmbInterval.add("30");
		cmbInterval.add("60");
		cmbInterval.select(0);
		
		trainingAndEvaluation(compDiagnosis);
		
		btnLiveTracing=new Button(compDiagnosis, SWT.BORDER);
		btnLiveTracing.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, false,1,1));
		btnLiveTracing.setText("Start");
		
		
		btnLiveTracing.addMouseListener(new MouseListener() {
		
			
			@Override
			public void mouseUp(MouseEvent e) {
				if (txtUserAtHost.getText().isEmpty() || txtPassword.getText().isEmpty()){
					MessageBox msgBox=new MessageBox(org.eclipse.ui.PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell() ,SWT.ICON_ERROR|SWT.OK);
					msgBox.setMessage("Empty fields are not allowed");
					msgBox.open();
				}else {
					int port=7225;
					BackgroundLiveMonitor liveEval= new BackgroundLiveMonitor
							  (txtUserAtHost.getText(), txtPassword.getText(), txtPassword.getText(), 
									  "", port,10,1, console);
					liveEval.start();
				}
			}
			
			@Override
			public void mouseDown(MouseEvent e) {
				
				
			}
			
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				
				
			}
		});
		/**
		 * End group trace selection
		 */
	}
	/**
	 * 
	 */
	public void trainingAndEvaluation(Composite compParent){
		/////////
		///Training and Evaluation
		/////////
		Group grpTrainingAndEval = new Group(compParent, SWT.NONE);
		grpTrainingAndEval.setText("Model Selection");
		grpTrainingAndEval.setLayoutData(new GridData(SWT.FILL,SWT.FILL,false,true));
		grpTrainingAndEval.setLayout(new GridLayout(2,false));
		
		Button btnTrainingAndEval=new Button(grpTrainingAndEval, SWT.NONE|SWT.RADIO);
		btnTrainingAndEval.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false,1,1));
		btnTrainingAndEval.setText("Training and Evaluation");
		
		Button btnTesting=new Button(grpTrainingAndEval, SWT.NONE|SWT.RADIO);
		btnTesting.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false,1,1));
		btnTesting.setText("Evaluation");
		
		//////////////////
		/////// Existing Model
		//////////////
		Group grpModelSelection=new Group(grpTrainingAndEval,SWT.NONE);	
		grpModelSelection.setLayoutData(new GridData(SWT.FILL,SWT.FILL,false,true));
		grpModelSelection.setLayout(new GridLayout(1,false));
		grpModelSelection.setText("Existing Model");
		
		Composite compModelSelection=new Composite(grpModelSelection, SWT.NONE);
	    compModelSelection.setLayoutData(new GridData(SWT.LEFT,SWT.TOP,false,false));
		compModelSelection.setLayout(new GridLayout(2,false));
	    	
		   
		Button btnSettings=new Button(compModelSelection, SWT.NONE);
		btnSettings.setLayoutData(new GridData(SWT.FILL,SWT.TOP,false,false,1,1));
		btnSettings.setText(" Settings ");
		
		Button btnDelete=new Button(compModelSelection, SWT.NONE);
		btnDelete.setLayoutData(new GridData(SWT.FILL,SWT.TOP,false,false,1,1));
		btnDelete.setText("   Delete   ");
		
		Tree treeModels = new Tree(grpModelSelection, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION| SWT.V_SCROLL | SWT.H_SCROLL);
		treeModels.setLinesVisible(true);
		treeModels.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
		
		ModelSelection modelSelectionHandler=new ModelSelection(btnSettings, btnDelete, treeModels);
		
	}
	
	
	
}