package org.eclipse.linuxtools.tmf.totalads.algorithms.hiddenmarkovmodel;

import java.util.Arrays;
import java.util.Random;

import org.eclipse.linuxtools.tmf.totalads.dbms.IDBCursor;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDBRecord;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSDBMSException;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSGeneralException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.mahout.classifier.sequencelearning.hmm.*;
import org.apache.mahout.math.DenseMatrix;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.Vector;

/**
 *
 * @author <p>
 *         Syed Shariyar Murtaza justsshary@hotmail.com
 *         </p>
 *
 */
class HmmMahout {

    private HmmModel hmm;

    /**
     * Initializes Hidden Markov DataModel with random initial probabilities
     *
     * @param numSymbols
     *            number of symbols
     * @param numStates
     *            Number of states
     *
     */
    public void initializeHMM(int numSymbols, int numStates) {

        hmm = new HmmModel(numStates, numSymbols);

    }

    /**
     * Initializes HMM with the customized transition, emission and Initial probabilities rather than
     * using Mahout's initialization. Specially this function makes sure that initial probabilities are equal.
     * @param numSymbols Number of Symbols
     * @param numStates Number of States
     */
    public void initializeHMMWithCustomizeInitialValues(int numSymbols, int numStates) {
        // Generating transition probabilities with random numbers
        Random random = new Random();
        double start = 0.0001;
        double end = 1.0000;
        DenseMatrix tansitionProbabilities=new DenseMatrix(numStates, numStates);

      //  Measuring Transition Probabilities
        double[] rowSums = new double[numStates];
        Arrays.fill(rowSums, 0.0);

        for (int row = 0; row < numStates; row++) {
            for (int col = 0; col < numStates; col++) {
                tansitionProbabilities.set(row, col, getRandomRealNumber(start, end, random));
                rowSums[row] += tansitionProbabilities.get(row,col);
            }
        }

        for (int row = 0; row < numStates; row++) {
            for (int col = 0; col < numStates; col++) {
                tansitionProbabilities.set(row,col,  (tansitionProbabilities.get(row,col) / rowSums[row]));
            }
        }

        // Assigning initial state probabilities Pi; i.e. probabilities at time 1
        DenseVector initialProbabilities=new DenseVector(numStates);
        double initialProb= 1/((double)numStates);
           for (int idx = 0; idx < numStates; idx++) {
            initialProbabilities.set(idx,initialProb );
        }


        // Measuring Emission probabilities of each symbol
        DenseMatrix emissionProbabilities = new DenseMatrix(numStates,numSymbols);
        Arrays.fill(rowSums, 0.0);// Utilizing the same rowSums variable
        random = new Random();

        for (int row = 0; row < numStates; row++) {
            for (int col = 0; col < numSymbols; col++) {
                emissionProbabilities.set(row,col, getRandomRealNumber(start, end, random));
                rowSums[row] +=emissionProbabilities.get(row,col);
            }
        }

        for (int row = 0; row < numStates; row++) {
            for (int col = 0; col < numSymbols; col++) {
                emissionProbabilities.set(row,col, emissionProbabilities.get(row,col) / rowSums[row]);
            }
        }


        hmm = new HmmModel( tansitionProbabilities,emissionProbabilities,initialProbabilities);

    }

    /**
     * Returns a decimal random number within a decimal range
     *
     * @param start
     * @param end
     * @param random
     * @return
     */
    private static double getRandomRealNumber(double start, double end, Random random) {

        // get the range, casting to long to avoid overflow problems
        double range = end - start;
        // compute a fraction of the range, 0 <= frac < range
        double fraction = (range * random.nextDouble());
        double randomNumber = fraction + start;
        return randomNumber;
    }

    /**
     * Validates settings and saves them into the database after creating a new
     * database if required
     *
     * @param settings
     *            SettingsForm array
     * @param database
     *            Database name
     * @param connection
     *            IDataAccessObject object
     * @param isNewSettings
     *            True if settings are inserted first time, else false if
     *            existing fields are updated
     * @param isNewDB
     *            if new database has to be created
     * @throws TotalADSGeneralException
     * @throws TotalADSDBMSException
     */
    public void verifySaveSettingsCreateDb(String[] settings, String database, IDataAccessObject connection, Boolean isNewSettings, Boolean isNewDB) throws TotalADSGeneralException, TotalADSDBMSException {

        JsonObject settingObject = new JsonObject();
        for (int i = 0; i < settings.length; i += 2) {

            if (SettingsCollection.NUM_STATES.toString().equalsIgnoreCase(settings[i])) {
                try {
                    Integer num_states = Integer.parseInt(settings[i + 1]);
                    settingObject.add(SettingsCollection.NUM_STATES.toString(), new JsonPrimitive(num_states));
                } catch (Exception ex) {
                    throw new TotalADSGeneralException("Select an integer for number of states");
                }

            } else if (SettingsCollection.NUM_SYMBOLS.toString().equalsIgnoreCase(settings[i])) {
                try {
                    Integer num_symbols = Integer.parseInt(settings[i + 1]);
                    settingObject.add(SettingsCollection.NUM_SYMBOLS.toString(), new JsonPrimitive(num_symbols));
                } catch (Exception ex) {
                    throw new TotalADSGeneralException("Select an integer for number of symbols");
                }
            } else if (SettingsCollection.SEQ_LENGTH.toString().equalsIgnoreCase(settings[i])) {
                try {
                    Integer seqLength = Integer.parseInt(settings[i + 1]);
                    settingObject.add(SettingsCollection.SEQ_LENGTH.toString(), new JsonPrimitive(seqLength));
                } catch (Exception ex) {
                    throw new TotalADSGeneralException("Select an integer for sequence length");
                }
            } else if (SettingsCollection.LOG_LIKELIHOOD.toString().equalsIgnoreCase(settings[i])) {
                try {
                    Double prob = Double.parseDouble(settings[i + 1]);
                    if (prob > 0.0) {
                        throw new TotalADSGeneralException("Log likelihood should be a negative decimal number");
                    }
                    settingObject.add(SettingsCollection.LOG_LIKELIHOOD.toString().toString(), new JsonPrimitive(prob));
                } catch (Exception ex) {
                    throw new TotalADSGeneralException("Select a decimal number for the log likelihood threshold");
                }
            } else if (SettingsCollection.NUMBER_OF_ITERATIONS.toString().equalsIgnoreCase(settings[i])) {
                try {
                    Integer it = Integer.parseInt(settings[i + 1]);
                    if (it <= 0) {
                        throw new TotalADSGeneralException("Number of iterations can't be 0 or less");
                    }
                    settingObject.add(SettingsCollection.NUMBER_OF_ITERATIONS.toString().toString(), new JsonPrimitive(it));
                } catch (Exception ex) {
                    throw new TotalADSGeneralException("Select a integer for number of iterations");
                }
            } else if (SettingsCollection.KEY.toString().equalsIgnoreCase(settings[i])) {
                settingObject.add(SettingsCollection.KEY.toString(), new JsonPrimitive("hmm"));
            }
        }

        // creating id for query searching
        JsonObject jsonKey = new JsonObject();
        jsonKey.addProperty(SettingsCollection.KEY.toString(), "hmm");

        if (isNewDB) {
            String[] collectionNames = { HmmModelCollection.COLLECTION_NAME.toString(), SettingsCollection.COLLECTION_NAME.toString()
                    , NameToIDCollection.COLLECTION_NAME.toString() };
            connection.createDatabase(database, collectionNames);
        }

        if (isNewSettings) {
            connection.insertOrUpdateUsingJSON(database, jsonKey, settingObject, SettingsCollection.COLLECTION_NAME.toString());
        } else {
            connection.updateFieldsInExistingDocUsingJSON(database, jsonKey, settingObject, SettingsCollection.COLLECTION_NAME.toString());
        }

    }

    /**
     * Loads settings from the database
     *
     * @param database
     * @param connection
     * @return SettingsForm as an array of String
     * @throws TotalADSDBMSException
     */
    public String[] loadSettings(String database, IDataAccessObject connection) throws TotalADSDBMSException {
        String[] settings = null;
        try (IDBCursor cursor = connection.selectAll(database,
                SettingsCollection.COLLECTION_NAME.toString())) {
            if (cursor.hasNext()) {
                settings = new String[10];

                IDBRecord dbObject = cursor.next();
                settings[0] = SettingsCollection.NUM_STATES.toString();
                settings[1] = dbObject.get(SettingsCollection.NUM_STATES.toString()).toString();
                settings[2] = SettingsCollection.NUMBER_OF_ITERATIONS.toString();
                settings[3] = dbObject.get(SettingsCollection.NUMBER_OF_ITERATIONS.toString()).toString();
                settings[4] = SettingsCollection.NUM_SYMBOLS.toString();
                settings[5] = dbObject.get(SettingsCollection.NUM_SYMBOLS.toString()).toString();
                settings[6] = SettingsCollection.LOG_LIKELIHOOD.toString();
                settings[7] = dbObject.get(SettingsCollection.LOG_LIKELIHOOD.toString()).toString();
                settings[8] = SettingsCollection.SEQ_LENGTH.toString();
                settings[9] = dbObject.get(SettingsCollection.SEQ_LENGTH.toString()).toString();
            }
        }
        return settings;
    }

    /**
     * Trains an HMM on a sequence using the BaumWelch algorithm
     *
     * @param numIterations
     * @param observedSequence
     */
    public void learnUsingBaumWelch(Integer numIterations, Integer[] observedSequence) {

        int[] seq = new int[observedSequence.length];
        for (int i = 0; i < seq.length; i++) {
            seq[i] = observedSequence[i];
        }
        HmmTrainer.trainBaumWelch(hmm, seq, 0.01, numIterations, true);

    }

    /**
     * Trains an HMM on a sequence using the BaumWelch algorithm
     *
     * @param numIterations
     * @param observedSequence
     */
    public void learnUsingBaumWelch(Integer numIterations, int[] observedSequence) {

        HmmTrainer.trainBaumWelch(hmm, observedSequence, 0.01, numIterations, true);

    }

    /**
     * Returns the Observation log likelihood  of a sequences based on a model
     *
     * @param sequence
     *            Integer array of sequences
     * @return
     */
    public double observationLikelihood(int[] sequence) {

        Matrix m = HmmAlgorithms.forwardAlgorithm(hmm, sequence, true);
        int lastCol = m.numCols() - 1;
        int numRows = m.numRows();
        double sum = 0.0;
        for (int i = 0; i < numRows; i++) {
            sum += m.getQuick(i, lastCol);
        }

        return sum;
        // return HmmEvaluator.modelLikelihood(hmm, seq, true);
    }

    /**
     * Updating MM based on an incremental version descibed in
     * http://goanna.cs.rmit.edu.au/~jiankun/Sample_Publication/ICON04_Dau.pdf
     *
     * @param sequence
     * @param connection
     * @param database
     * @throws TotalADSDBMSException
     */
    public void updatePreviousModel(Integer[] sequence, IDataAccessObject connection, String database) throws TotalADSDBMSException {
        int[] seq = new int[sequence.length];
        for (int i = 0; i < sequence.length; i++) {
            seq[i] = sequence[i];
        }

        // double prob=observationProbability(seq);// this always zero
        // System.out.println(prob);
        double prob = 1.0;
        Matrix transition = hmm.getTransitionMatrix().divide(prob);
        Matrix emission = hmm.getEmissionMatrix().divide(prob);
        Vector initial = hmm.getInitialProbabilities().divide(prob);

        HmmMahout oldHMM = new HmmMahout();
        oldHMM.loadHmm(connection, database);
        if (oldHMM.hmm != null) {
            transition = oldHMM.hmm.getTransitionMatrix().plus(transition);
            emission = oldHMM.hmm.getEmissionMatrix().plus(emission);
            initial = oldHMM.hmm.getInitialProbabilities().plus(initial);
        }

        HmmMahout newHMM = new HmmMahout();
        newHMM.hmm = new HmmModel(transition, emission, initial);
        newHMM.saveHMM(database, connection);
    }

    /**
     * Loads the model directly from a database
     *
     * @param connection
     * @param database
     * @throws TotalADSDBMSException
     */
    public void loadHmm(IDataAccessObject connection, String database) throws TotalADSDBMSException {

        try (IDBCursor cursor = connection.selectAll(database,
                HmmModelCollection.COLLECTION_NAME.toString())) {
            if (cursor.hasNext()) {
                Gson gson = new Gson();
                if (cursor.hasNext()) {
                    IDBRecord dbObject = cursor.next();
                    Object emissionProb = dbObject.get(HmmModelCollection.EMISSIONPROB.toString());
                    Object transsitionProb = dbObject.get(HmmModelCollection.TRANSITIONPROB.toString());
                    Object initialProb = dbObject.get(HmmModelCollection.INTITIALPROB.toString());

                    DenseMatrix emissionMatrix = gson.fromJson(emissionProb.toString(), DenseMatrix.class);
                    DenseMatrix transitionMatrix = gson.fromJson(transsitionProb.toString(), DenseMatrix.class);
                    DenseVector initialProbVector = gson.fromJson(initialProb.toString(), DenseVector.class);

                    hmm = new HmmModel(transitionMatrix, emissionMatrix, initialProbVector);
                }

            }
        }
    }

    /**
     * This functions saves the HmmJahmm model into the database
     *
     * @param database
     * @param connection
     * @throws TotalADSDBMSException
     */
    public void saveHMM(String database, IDataAccessObject connection) throws TotalADSDBMSException {
        // / Inserting the states and probabilities
        // Creating states ids
        String key = "hmm";
        Gson gson = new Gson();

        DenseMatrix emissionMatrix = (DenseMatrix) hmm.getEmissionMatrix();
        DenseMatrix transitionMatrix = (DenseMatrix) hmm.getTransitionMatrix();
        Vector initialProb = hmm.getInitialProbabilities();

        JsonObject hmmDoc = new JsonObject();
        hmmDoc.add(HmmModelCollection.KEY.toString(), new JsonPrimitive(key));
        hmmDoc.add(HmmModelCollection.EMISSIONPROB.toString(), gson.toJsonTree(emissionMatrix));
        hmmDoc.add(HmmModelCollection.TRANSITIONPROB.toString(), gson.toJsonTree(transitionMatrix));
        hmmDoc.add(HmmModelCollection.INTITIALPROB.toString(), gson.toJsonTree(initialProb));

        // Creating id for query searching
        JsonObject jsonTheKey = new JsonObject();
        jsonTheKey.addProperty(HmmModelCollection.KEY.toString(), key);

        connection.insertOrUpdateUsingJSON(database, jsonTheKey, hmmDoc, HmmModelCollection.COLLECTION_NAME.toString());

    }

    /**
     * Prints the model
     *
     * @return
     */
    @Override
    public String toString() {

        return "HMM built for " + hmm.getNrOfHiddenStates() + " hidden states and " + hmm.getNrOfOutputStates() + " observable events"
                + "\n Emission Probabilities:\n " + hmm.getEmissionMatrix().toString() + " \n "
                + "Transition Probabilities: \n" + hmm.getTransitionMatrix().toString()
                + " \n Initial Probabilities \n " + hmm.getInitialProbabilities();

    }

}
