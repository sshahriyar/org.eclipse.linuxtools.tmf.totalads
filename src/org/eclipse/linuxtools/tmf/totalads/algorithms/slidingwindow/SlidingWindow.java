/*********************************************************************************************
 * Copyright (c) 2014  Software Behaviour Analysis Lab, Concordia University, Montreal, Canada
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of XYZ License which
 * accompanies this distribution, and is available at xyz.com/license
 *
 * Contributors:
 *    Syed Shariyar Murtaza
 **********************************************************************************************/
package org.eclipse.linuxtools.tmf.totalads.algorithms.slidingwindow;

//import java.util.ArrayList;
//import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.linuxtools.tmf.totalads.algorithms.IAlgorithmOutStream;
import org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm;
import org.eclipse.linuxtools.tmf.totalads.algorithms.AlgorithmFactory;
import org.eclipse.linuxtools.tmf.totalads.algorithms.Results;
import org.eclipse.linuxtools.tmf.totalads.algorithms.AlgorithmTypes;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDBCursor;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDBRecord;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSDBMSException;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSReaderException;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSGeneralException;
import org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator;
import org.swtchart.Chart;
import com.google.gson.Gson;
import com.google.gson.JsonObject;


/**
 * This class implements the Sliding Window algorithm for the host-based anomaly
 * detection.
 *
 * @author <p>
 *         Syed Shariyar Murtaza justsshary@hotmail.com
 *         </p>
 *
 */
public class SlidingWindow implements IDetectionAlgorithm {

    // private String
    // TRACE_COLLECTION="trace_data";//Configuration.traceCollection;
    // private String
    // SETTINGS_COLLECTION="settings";//Configuration.settingsCollection;
    private Integer fMaxWin = 5;
    private Integer fMaxHamDis = 0;
    private HashMap<Integer, Event[]> fSysCallSequences;
    private Boolean fDetailedAnalysis = false;
    private String[] fTrainingOptions = { "Max Win", "5", "Max Hamming Distance", "0", "Detailed Analysis", "false" };
    private String[] fTestingOptions = { "Max Hamming Distance", "0", "Detailed Analysis", "false" };
    private Integer fValidationTraceCount = 0;
    private Integer fValidationAnomalies = 0;
    private Integer fTestTraceCount = 0;
    private Integer fTestAnomalies = 0;
    private Boolean fIsintialize = false;
    private Boolean fIsTestStarted = false;
    private SlidingWindowTree fTreeTransformer;
    private int fMaxWinLimit = 25;
    private NameToIDMapper fNameToID;
    private int fTestNameToIDSize;
    private  Boolean isValidationStarted = false;


    /**
     * Constructor
     **/
    public SlidingWindow() {
        // treeExists=false;
        fSysCallSequences = new HashMap<>();
        fTreeTransformer = new SlidingWindowTree();
        fNameToID = new NameToIDMapper();

    }

    /**
     * Initializes the model, if already exists in the database
     *
     * @param dataAccessObject
     *            IDataAccessObject object
     * @param database
     *            Database name
     * @throws TotalADSDBMSException DBMS exception
     */
    private void initialize(IDataAccessObject dataAccessObject, String database) throws TotalADSDBMSException {

        try (IDBCursor cursor = dataAccessObject.selectAll(database,
                TraceCollection.COLLECTION_NAME.toString())){
                while (cursor.hasNext()) {
                    IDBRecord record = cursor.next();
                    Gson gson = new Gson();
                    Integer key = (Integer) record.get(TraceCollection.KEY.toString());
                    Object obj = record.get(TraceCollection.TREE.toString());
                    if (obj != null) {
                        Event[] event = gson.fromJson(obj.toString(), Event[].class);
                        fSysCallSequences.put(key, event);
                    }

                }
        }

        // Get the fMaxWin and maxHam
        try (IDBCursor cursor = dataAccessObject.selectAll(database,
                    SettingsCollection.COLLECTION_NAME.toString())){

            while (cursor.hasNext()) {
                IDBRecord record = cursor.next();
                fMaxWin = (Integer) record.get(SettingsCollection.MAX_WIN.toString());
                fMaxHamDis = (Integer) record.get(SettingsCollection.MAX_HAM_DIS.toString());
                fDetailedAnalysis = (Boolean) record.get(SettingsCollection.DETAILED_ANALYSIS.toString());
            }
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#
     * getTrainingOptions()
     */
    @Override
    public String[] getTrainingSettings() {

        return fTrainingOptions;

    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#
     * saveTestingOptions(java.lang.String[], java.lang.String,
     * org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject)
     */
    @Override
    public void saveTestSettings(String[] options, String database, IDataAccessObject dataAccessObject) throws TotalADSGeneralException, TotalADSDBMSException {

        Integer theMaxHamDis = 0;
        Boolean detailAnalysis = false;
        if (options != null) {
            if (options[0].equals(this.fTestingOptions[0])) {
                try {
                    theMaxHamDis = Integer.parseInt(options[1]);
                } catch (NumberFormatException ex) {
                    throw new TotalADSGeneralException("Please, enter an integer value.");
                }
            }

            if (options[2].equals(this.fTestingOptions[2])) {
                if (options[3].equals("true")) {
                    detailAnalysis = true;
                } else if (options[3].equals("false")) {
                    detailAnalysis = false;
                } else {
                    throw new TotalADSGeneralException("Please, enter only true or false for detailed analysis");
                }

            }

        }
        // / Get previous max window first
        loadSetings(database, dataAccessObject);
        fMaxHamDis = theMaxHamDis;// change the maxHam
        fDetailedAnalysis = detailAnalysis;
        saveSettings(database, dataAccessObject); // save maxHamm

    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#
     * getTestingOptions(java.lang.String,
     * org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject)
     */
    @Override
    public String[] getTestSettings(String database, IDataAccessObject dataAccessObject) throws TotalADSDBMSException {
        loadSetings(database, dataAccessObject);
        fTestingOptions[1] = fMaxHamDis.toString();
        fTestingOptions[3] = fDetailedAnalysis.toString();
        return fTestingOptions;

    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#
     * initializeModelAndSettings(java.lang.String,
     * org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject,
     * java.lang.String[])
     */
    @Override
    public void initializeModelAndSettings(String modelName, IDataAccessObject dataAccessObject, String[] trainingSettings) throws TotalADSDBMSException, TotalADSGeneralException {

        if (trainingSettings != null) {
            try {
                for (int i = 0; i < trainingSettings.length; i++) {
                    if (trainingSettings[i].equals(this.fTrainingOptions[0])) {
                        fMaxWin = Integer.parseInt(trainingSettings[i + 1]);// on
                                                                            // error
                                                                            // exception
                                                                            // will
                                                                            // be
                                                                            // thrown
                                                                            // automatically
                    } else if (trainingSettings[i].equals(this.fTrainingOptions[2]))
                    {
                        fMaxHamDis = Integer.parseInt(trainingSettings[i + 1]);// on
                                                                               // error
                                                                               // exception
                                                                               // will
                                                                               // be
                                                                               // thrown
                                                                               // automatically
                    }
                }
            } catch (Exception ex) {// Capturing exception to send a UI error
                throw new TotalADSGeneralException("Please, enter integer numbers only in settings' fileds.");
            }

            if (fMaxWin > fMaxWinLimit) {
                throw new TotalADSGeneralException("Sequence size too large; select " + fMaxWinLimit + " or lesser.");
            }

            if (trainingSettings[4].equals(this.fTrainingOptions[4])) {
                if (trainingSettings[5].equals("true")) {
                    fDetailedAnalysis = true;
                } else if (trainingSettings[5].equals("false")) {
                    fDetailedAnalysis = false;
                } else {
                    throw new TotalADSGeneralException("Please, enter only true or false for detailed analysis");
                }
            }

        }

        String[] collectionNames = { TraceCollection.COLLECTION_NAME.toString(), SettingsCollection.COLLECTION_NAME.toString(),
                NameToIDCollection.COLLECTION_NAME.toString() };
        dataAccessObject.createDatabase(modelName, collectionNames);
        saveSettings(modelName, dataAccessObject);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#
     * getSettingsToDisplay(java.lang.String,
     * org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject)
     */
    @Override
    public String[] getSettingsToDisplay(String database, IDataAccessObject dataAccessObject) throws TotalADSDBMSException {
        loadSetings(database, dataAccessObject);
        String[] settings = fTrainingOptions.clone();
        settings[1] = fMaxWin.toString();
        settings[3] = fMaxHamDis.toString();
        settings[5] = fDetailedAnalysis.toString();
        return settings;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#train
     * (org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator,
     * java.lang.Boolean, java.lang.String,
     * org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject,
     * org.eclipse.linuxtools.tmf.totalads.algorithms.IAlgorithmOutStream)
     */
    @Override
    public void train(ITraceIterator trace, Boolean isLastTrace, String database, IDataAccessObject dataAccessObject, IAlgorithmOutStream outStream) throws TotalADSGeneralException, TotalADSDBMSException, TotalADSReaderException {

        if (!fIsintialize) {
            fValidationTraceCount = 0;
            fValidationAnomalies = 0;
            initialize(dataAccessObject, database);
            fIsintialize = true;
            fNameToID.loadMap(dataAccessObject, database);

        }

        outStream.addOutputEvent("Starting to slide window on the trace, please wait while I process...");
        outStream.addNewLine();

        int winWidth = 0;
        LinkedList<Integer> newSequence = new LinkedList<>();
        String event = null;

        while (trace.advance()) {
            event = trace.getCurrentEvent();

            newSequence.add(fNameToID.getId(event));

            winWidth++;

            if (winWidth >= fMaxWin) {

                winWidth--;
                Integer[] seq = new Integer[fMaxWin];
                seq = newSequence.toArray(seq);

                fTreeTransformer.searchAndAddSequence(seq, fSysCallSequences, outStream);
                // fTreeTransformer.searchAndAddSequence(seq,database,dataAccessObject);
                newSequence.remove(0);
                // seqCount++;
            }

        }
        if (isLastTrace) {
            // Saving events tree in database
            outStream.addOutputEvent("All unique sequences ");
            outStream.addNewLine();
            if (fSysCallSequences.size() > 0) {
                fTreeTransformer.printSequence(outStream, fSysCallSequences, fNameToID);
            } else{
                String err="No sequences of length "+fMaxWin + " found in traces";
                outStream.addOutputEvent(err);
                outStream.addNewLine();
                throw new TotalADSGeneralException(err);

            }

            fTreeTransformer.saveinDatabase(outStream, database, dataAccessObject, fSysCallSequences);
            fIsintialize = false;
            fNameToID.saveMap(dataAccessObject, database);
        }

    }


    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#validate
     * (org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator,
     * java.lang.String,
     * org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject,
     * java.lang.Boolean,
     * org.eclipse.linuxtools.tmf.totalads.algorithms.IAlgorithmOutStream)
     */
    @Override
    public void validate(ITraceIterator trace, String database, IDataAccessObject dataAccessObject, Boolean isLastTrace, IAlgorithmOutStream outStream)
            throws TotalADSGeneralException, TotalADSDBMSException, TotalADSReaderException {

        if (!isValidationStarted) {
            loadSetings(database, dataAccessObject);
            isValidationStarted = true;
        }
        fValidationTraceCount++;// count the number of traces

        Results result = evaluateTrace(trace, database, dataAccessObject, outStream);

        if (result.getAnomaly()) {
            String details = result.getDetails().toString();
            outStream.addOutputEvent(details);
            outStream.addNewLine();
            fValidationAnomalies++;

        }

        if (isLastTrace) {

            outStream.addOutputEvent("Total traces in validation folder: " + fValidationTraceCount);
            outStream.addNewLine();
            Double anomalyPrcentage = (fValidationAnomalies.doubleValue() / fValidationTraceCount.doubleValue()) * 100;

            outStream.addOutputEvent("Total anomalies at max hamming distance " + fMaxHamDis + " are " + anomalyPrcentage);
            outStream.addNewLine();

            Double normalPercentage = (100 - anomalyPrcentage);
            outStream.addOutputEvent("Total normal at max hamming distance " + fMaxHamDis + " are " + normalPercentage);
            outStream.addNewLine();
            // Update the settings collection for maxwin and maxhamm
            saveSettings(database, dataAccessObject);
            outStream.addOutputEvent("Database updated..");
            outStream.addNewLine();

            // if (!warningMessage.isEmpty()){
            // outStream.addOutputEvent(warningMessage);
            // outStream.addNewLine();
            // }
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#test
     * (org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator,
     * java.lang.String,
     * org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject,
     * org.eclipse.linuxtools.tmf.totalads.algorithms.IAlgorithmOutStream)
     */
    @Override
    public Results test(ITraceIterator trace, String database, IDataAccessObject dataAccessObject, IAlgorithmOutStream outputStream)
            throws TotalADSGeneralException, TotalADSDBMSException, TotalADSReaderException {

        if (!fIsTestStarted) {
            fTestTraceCount = 0;
            fTestAnomalies = 0;
            initialize(dataAccessObject, database); // get the trees from db

            fIsTestStarted = true;
            fNameToID.loadMap(dataAccessObject, database);
            fTestNameToIDSize = fNameToID.getSize();
        }

        Results res = evaluateTrace(trace, database, dataAccessObject, outputStream);
        outputStream.addOutputEvent("Finished evaluating the trace");
        outputStream.addNewLine();
        return res;

    }

    /**
     * Evaluates a trace
     *
     * @param trace
     * @param database
     * @param dataAccessObject
     * @return
     * @throws TotalADSReaderException
     */
    private Results evaluateTrace(ITraceIterator trace, String database, IDataAccessObject dataAccessObject, IAlgorithmOutStream outStream) throws TotalADSReaderException {

        int winWidth = 0, anomalousSequences = 0, maxAnomalousSequencesToReturn;
        int displaySeqCount = 0, totalAnomalousSequences = 0, largestHam = 0;
        Integer[] largestHamSeq = null;
        Results results = new Results();
        if (fDetailedAnalysis == true) {
            maxAnomalousSequencesToReturn = 10;
        } else {
            maxAnomalousSequencesToReturn = 5;
        }

        String headerMsg = "First " + maxAnomalousSequencesToReturn + " or less distinct anomalous sequences with non-overlapping  events at Ham > " + fMaxHamDis + "\n\n";
        results.setAnomalyType("");
        results.setAnomaly(false);
        fTestTraceCount++;

        LinkedList<Integer> newSequence = new LinkedList<>();
        outStream.addOutputEvent("Starting to slide window on the trace, please wait while I process...");
        outStream.addNewLine();
        outStream.addOutputEvent("Evaluating sequences: ");
        outStream.addNewLine();
        String event = null;
        int seqCount = 0;
        while (trace.advance()) {

            event = trace.getCurrentEvent();
            newSequence.add(fNameToID.getId(event));

            winWidth++;

            if (winWidth >= fMaxWin) {
                seqCount++;

                winWidth--;

                Integer[] seq = new Integer[fMaxWin];
                seq = newSequence.toArray(seq);

                // Calculate the minimum Hamming distance
                Integer hammDisForSequence = seq.length; // we assign max
                                                         // hamming distance
                for (Map.Entry<Integer, Event[]> tree : fSysCallSequences.entrySet()) {
                    Event[] nodes = tree.getValue();
                    // just get the hamming and search with a full sequence
                    Integer hammDisForTree = fTreeTransformer.getHammingAndSearch(nodes, seq);
                    if (hammDisForTree < hammDisForSequence) {
                        hammDisForSequence = hammDisForTree;
                    }
                    if (hammDisForSequence == 0)
                    {
                        break;// if Hamming is zero, we found a match break;
                              // don't continue further, save time
                    }
                }

                // Print every 20,000th sequence because trace parsing could
                // take longer
                if ((seqCount % 100000) == 0) {
                    outStream.addOutputEvent("Evaluation Upto Seq #" + seqCount + ": largest Ham so far=" + largestHam);
                    outStream.addNewLine();
                }
                // If Hamming distance is greater than the set threshold then it
                // is an anomaly
                if (hammDisForSequence > fMaxHamDis) {
                    totalAnomalousSequences++;

                    if (headerMsg.length() >= 1) {
                        results.setAnomaly(true);
                        results.setDetails(headerMsg);
                        headerMsg = "";
                    }
                    // Add a new sequence for display, when all of the previous
                    // events are gone
                    if (displaySeqCount <= maxAnomalousSequencesToReturn) {
                        if (anomalousSequences % fMaxWin == 0) {
                            // Convert sequence in integer ids to name
                            StringBuilder seqName = new StringBuilder();
                            for (int i = 0; i < seq.length; i++) {
                                if (i == seq.length - 1) {
                                    seqName.append(fNameToID.getKey(seq[i]));
                                } else {
                                    seqName.append(fNameToID.getKey(seq[i])).append(" ");
                                }
                            }
                            seqName.append(":: Ham=").append(hammDisForSequence).append("\n");
                            // Add sequence to results
                            results.setDetails(seqName.toString());
                            displaySeqCount++;
                        }
                    }

                    // Get the sequence with the largest Hamming distance
                    if (hammDisForSequence > largestHam) {
                        largestHam = hammDisForSequence;
                        largestHamSeq = seq;

                    }
                    anomalousSequences++;
                    // When fDetailedAnalysis is false, then just break after
                    // ten anomalous sequences
                    if (fDetailedAnalysis == false && displaySeqCount > maxAnomalousSequencesToReturn) {
                        outStream.addOutputEvent("Found " + maxAnomalousSequencesToReturn + " distinct anomalous sequences.. stopping"
                                + " further processing since detailed analysis is false");
                        outStream.addNewLine();
                        break;
                    }
                }// End of Ham comparison

                newSequence.remove(0);// remove the top event and slide a window
            }

        }
        if (seqCount == 0) {
            results.setAnomaly(true);
        }
        additionalInforForResults(largestHam, largestHamSeq, results, totalAnomalousSequences);

        return results;

    }

    /**
     * Adds additional information to the results
     *
     * @param largestHam
     * @param largestHamSeq
     * @param results
     * @param totalAnomalousSequences
     */
    private void additionalInforForResults(int largestHam, Integer[] largestHamSeq, Results results, int totalAnomalousSequences) {

        if (results.getAnomaly()) {
            fTestAnomalies++;
        }

        if (results.getAnomaly() && fDetailedAnalysis) {

            results.setDetails("\n\nLargest Hamming distance: " + largestHam + "\n");
            results.setDetails("Last sequence with the largest Hamming distance:\n ");
            for (int i = 0; i < largestHamSeq.length; i++) {
                results.setDetails(fNameToID.getKey(largestHamSeq[i]) + " ");
            }
            results.setDetails("\n\nTotal anomalous sequences " + totalAnomalousSequences);
        }

        // // get unknown events
        if (fNameToID.getSize() > fTestNameToIDSize) {
            Integer diff = fNameToID.getSize() - fTestNameToIDSize;
            int eventCount = 0;
            if (diff > 10) {
                eventCount = fTestNameToIDSize + 10;
            } else {
                eventCount = fTestNameToIDSize + diff;
            }

            results.setDetails("\n\nTen or less unknown events: \n");
            int count = 0;
            for (int i = fTestNameToIDSize; i < eventCount; i++) {// All these
                                                                  // events are
                                                                  // unknown
                results.setDetails(fNameToID.getKey(i) + " ");
                count++;
                if ((count) % 10 == 0) {
                    results.setDetails("\n");
                }
            }
            fTestNameToIDSize += diff;// don't display this for the second trace
                                      // unless or untill there are additional
                                      // events

        }
    }

    /**
     * Updates settings collection
     *
     * @param datatbase
     * @param dataAccessObject
     * @throws TotalADSDBMSException
     */
    private void saveSettings(String database, IDataAccessObject dataAccessObject) throws TotalADSDBMSException {

        String settingsKey = "SWN_SETTINGS";

        JsonObject jsonKey = new JsonObject();
        jsonKey.addProperty("_id", settingsKey);

        JsonObject jsonObjToUpdate = new JsonObject();
        jsonObjToUpdate.addProperty(SettingsCollection.KEY.toString(), settingsKey);
        jsonObjToUpdate.addProperty(SettingsCollection.MAX_WIN.toString(), fMaxWin);
        jsonObjToUpdate.addProperty(SettingsCollection.MAX_HAM_DIS.toString(), fMaxHamDis);
        jsonObjToUpdate.addProperty(SettingsCollection.DETAILED_ANALYSIS.toString(), fDetailedAnalysis);
        dataAccessObject.insertOrUpdateUsingJSON(database, jsonKey, jsonObjToUpdate, SettingsCollection.COLLECTION_NAME.toString());

    }

    /**
     * Loads settings into the class variables fMaxWin and fMaxHamDis
     *
     * @param database
     * @param dataAccessObject
     * @throws TotalADSDBMSException
     */

    private void loadSetings(String database, IDataAccessObject dataAccessObject) throws  TotalADSDBMSException {

        try (IDBCursor cursor = dataAccessObject.selectAll(database,
                    SettingsCollection.COLLECTION_NAME.toString())){

            if (cursor.hasNext()) {
                IDBRecord record = cursor.next();
                fMaxWin = (Integer)record.get(SettingsCollection.MAX_WIN.toString());
                fMaxHamDis = (Integer) record.get(SettingsCollection.MAX_HAM_DIS.toString());
                fDetailedAnalysis = (Boolean) record.get(SettingsCollection.DETAILED_ANALYSIS.toString());

            }
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#
     * getTotalAnomalyPercentage()
     */
    @Override
    public Double getTotalAnomalyPercentage() {
        Double anomalousPercentage = (fTestAnomalies.doubleValue() / fTestTraceCount.doubleValue()) * 100;
        return anomalousPercentage;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#
     * graphicalResults
     * (org.eclipse.linuxtools.tmf.totalads.readers.ITraceIterator)
     */
    @Override
    public Chart graphicalResults(ITraceIterator traceIterator) {

        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#
     * createInstance()
     */
    @Override
    public IDetectionAlgorithm createInstance() {

        return new SlidingWindow();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#getName
     * ()
     */
    @Override
    public String getName() {

        return "Sliding Window (SWN)";
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#getAcronym
     * ()
     */
    @Override
    public String getAcronym() {

        return "SWN";
    }

    /**
     * Self registration of the model with the modelFactory
     */
    public static void registerModel() throws TotalADSGeneralException {
        AlgorithmFactory modelFactory = AlgorithmFactory.getInstance();
        SlidingWindow sldWin = new SlidingWindow();
        modelFactory.registerModelWithFactory(AlgorithmTypes.ANOMALY, sldWin);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#
     * getDescription()
     */
    @Override
    public String getDescription() {
        return "SWN works by extracting sequences of length ‘n’ from a trace by sliding a window one event "
                + "(e.g., system call) at a time. For example, for a trace having system calls “3, 6, 195, 195”, "
                + "two sequences “3, 6, 195” and “6, 195, 195” of length 3 can be extracted. SWN extracts sequences "
                + "from normal traces and then compares them against the sequences in an unknown trace. If a new "
                + "sequence is found in an unknown trace then it is considers it as anomalous. The Hamming distance "
                + "between sequences can be used to adjust the decision threshold to reduce false alarms; e.g., a "
                + "sequence “3, 5, 195” is anomalous for above sequences but the mismatch occurs only at one "
                + "position—i.e., a hamming distance difference of only one. If the minimum Hamming distance matching "
                + "criterion is set to more than one, then it is a normal sequence. ";
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.linuxtools.tmf.totalads.algorithms.IDetectionAlgorithm#
     * isOnlineLearningSupported()
     */
    @Override
    public boolean isOnlineLearningSupported() {

        return true;
    }

}
