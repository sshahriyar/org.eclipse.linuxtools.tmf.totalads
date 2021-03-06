/**
 *
 */
package org.eclipse.linuxtools.tmf.totalads.ui.models;

import java.util.LinkedHashSet;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.linuxtools.tmf.totalads.algorithms.AlgorithmFactory;
import org.eclipse.linuxtools.tmf.totalads.dbms.DBMSFactory;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDBMSObserver;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.jface.viewers.StructuredSelection;

/**
 * This class creates a view to display the models
 * @author <p> efraimlopez </p>
 * 			<p> Syed Shariyar Murtaza justsshary@hotmail.com</p>
 *
 */
public class DataModelsView extends  ViewPart implements ISelectionProvider{

	public static final String ID = "org.eclipse.linuxtools.tmf.totalads.ModelsView";
	private ModelsList modelListViewer = null;
	private ListenerList listeners ;
	private LinkedHashSet<String> selection;

    ///////////////////////////////////////////////////////////////////////////////////////////////
	// Inner class
	//////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 *
	 * Inner class representing a Table for models
	 *
	 */
	private  class ModelsList implements IDBMSObserver{
		private CheckboxTableViewer fViewer = null;
		private Table fTable=null;
		private IDataAccessObject fDataAccessObject;
		/**
		 * Constructor
		 * @param parent
		 */
		public ModelsList(Composite parent){

			fTable =new Table(parent, SWT.MULTI | SWT.H_SCROLL|SWT.CHECK
				      | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
			fTable.setHeaderVisible(true);
			fTable.setLinesVisible(true);
			fViewer = new CheckboxTableViewer(fTable);


			TableViewerColumn modelName = new TableViewerColumn(fViewer, SWT.NONE);
			modelName.getColumn().setWidth(100);
			modelName.getColumn().setText("Models");

			fViewer.setLabelProvider(new DataModelLabelProvider());
			fViewer.setContentProvider(new DataModelTableContentProvider());

			fDataAccessObject=DBMSFactory.INSTANCE.getDataAccessObject();
			DBMSFactory.INSTANCE.verifyConnection();

			fViewer.setInput(fDataAccessObject);
			fDataAccessObject.addObserver(this);

			// Event handler for check marks (selection) in the Table
			fViewer.addCheckStateListener(new ICheckStateListener() {
				@Override
				public void checkStateChanged(CheckStateChangedEvent event) {

					checkState(event);
				}
			});// end of event handler


		}
		/**
		 * Handles the check state of the fTable
		 * @param event
		 */
		private void checkState(CheckStateChangedEvent event){
			MessageBox msgBox=new MessageBox(Display.getCurrent().getActiveShell(), SWT.ERROR);

			String modelName=(String)event.getElement();
			String []modelAcronym=modelName.split("_");

			if (event.getChecked()){

				//Don't let user select no connection message
				if (modelName.equals(DataModelTableContentProvider.EMPTY_VIEW_FIELD)){
					uncheckedSelectedModel(modelName);
					return;
				}//See if more than 5 models are slected
				else if (selection.size()>=5){
					msgBox.setMessage("Please select less than six models only");
				    msgBox.open();
				    uncheckedSelectedModel(modelName);
				    return;
				} //Making sure that it is not a database that already exist in the db
				 else if(modelAcronym==null ||  modelAcronym.length<2){
					msgBox.setMessage(modelName+ " is not a valid model created by TotalADS!");
					msgBox.open();
					uncheckedSelectedModel(modelName);
					return;

				}//Make sure the algorithm is there in the list
				 else if (AlgorithmFactory.getInstance().getAlgorithmByAcronym(modelAcronym[1])==null){
						msgBox.setMessage(modelName +" is not a valid model created by TotalADS!");
						msgBox.open();
						uncheckedSelectedModel(modelName);
						return;
				}

				selection.add(modelName);

			}else if (!event.getChecked()){

				selection.remove(modelName);
			} // end of event checking
			// now calling listeners

			setSelection(new StructuredSelection(selection.clone()));
		}

		/**
		 * Unchecked selected model in the fTable view
		 * @param modelName
		 * @param fViewer
		 */
		private void uncheckedSelectedModel(String modelName ){
			for (int i=0;i<fViewer.getTable().getItemCount(); i++) {
                if (fViewer.getTable().getItem(i).getText().equals(modelName)){
		    		     fViewer.getTable().getItem(i).setChecked(false);
		    		     break;
		    	}
            }
		}

		/*
		 * (non-Javadoc)
		 * @see org.eclipse.linuxtools.tmf.totalads.dbms.IDBMSObserver#update()
		 */

		@Override
		public void update() {
			if(fViewer!=null){
				Table table= this.fViewer.getTable();
				if (table!=null && !table.isDisposed()){
					table.removeAll();
				    this.fViewer.refresh();
				    selection.clear();
					setSelection(new StructuredSelection(selection.clone()));
				}

			}
		}

		/**
		 * Disposes the object
		 */
		public void dispose(){
			fDataAccessObject.removeObserver(this);
		}
	}
    ///////////////////////////////////////////////////////////////////////////////////////////////
	// Inner class ends
	//////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Constructor
	 */
	public DataModelsView() {
		listeners = new ListenerList();
		selection=new LinkedHashSet<>();
	}


	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createPartControl(Composite parent) {
		modelListViewer = new ModelsList(parent);
		// Registering fViewer as a provider with Eclipse to monitor changes
		getSite().setSelectionProvider(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#setFocus()
	 */
	@Override
	public void setFocus() {


	}
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ISelectionProvider#addSelectionChangedListener(org.eclipse.jface.viewers.ISelectionChangedListener)
	 */
	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		listeners.add(listener);
		/// Uncheck all the models because  a view could be closed and opened in the middle
		//Iterator<String> it= selection.iterator();

		//while (it.hasNext())
		  //   modelListViewer.uncheckedSelectedModel(it.next());
		//selection.clear();


	}
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ISelectionProvider#getSelection()
	 */
	@Override
	public ISelection getSelection() {
		  return new StructuredSelection(selection);
	}
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ISelectionProvider#removeSelectionChangedListener(org.eclipse.jface.viewers.ISelectionChangedListener)
	 */
	@Override
	public void removeSelectionChangedListener(
			ISelectionChangedListener listener) {
		listeners.remove(listener);

	}
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ISelectionProvider#setSelection(org.eclipse.jface.viewers.ISelection)
	 */
	@Override
	public void setSelection(ISelection selection) {
		 Object[] list = listeners.getListeners();
		  for (int i = 0; i < list.length; i++) {
		      ((ISelectionChangedListener) list[i])
		            .selectionChanged(new SelectionChangedEvent(this, selection));
		  }

	}
	/**
	 * Refreshes the fViewer and its contents
	 */
	public void refresh(){
		this.modelListViewer.update();
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#dispose()
	 */
	@Override
	public void dispose(){

		modelListViewer.dispose();
		super.dispose();
	}


}
