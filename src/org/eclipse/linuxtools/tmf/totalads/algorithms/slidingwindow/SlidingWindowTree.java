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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.linuxtools.tmf.totalads.algorithms.IAlgorithmOutStream;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDBCursor;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDBRecord;
import org.eclipse.linuxtools.tmf.totalads.dbms.IDataAccessObject;
import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSDBMSException;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


/**
 * This class implements the tree transformation methods for the sliding window
 * algorithm. it converts a trace of events into a tree of patterns and
 * serializes to database when needed.
 *
 * @author  Syed Shariyar Murtaza
 *
 *
 */
class SlidingWindowTree {
    /**
     * Constructor
     */
    public SlidingWindowTree() {

    }

    /**
     * Searches and adds a sequence in the tree of events forming sequences
     *
     * @param newSequence
     * @param sysCallSequences
     * @param out
     */
    public void searchAndAddSequence(Integer[] newSequence, HashMap<Integer, Event[]> sysCallSequences, IAlgorithmOutStream out) {
        // public void searchAndAddSequence(String []newSequence, String
        // database, IDataAccessObject dataAccessObject) throws TotalADSDBMSException{
        Integer seqSize = newSequence.length;
        Event[] eventSequence = sysCallSequences.get(newSequence[0]);// load
                                                                     // from
                                                                     // database
        // Event[] eventSequence= loadTreeFromDatabase(newSequence[0], database,
        // dataAccessObject) ;//load from database

        if (eventSequence == null) { // if there is no such starting event then
                                     // add sequence to such an event

            eventSequence = new Event[seqSize + 1];
            for (int j = 0; j < seqSize; j++) {
                eventSequence[j] = new Event();
                eventSequence[j].setEvent(newSequence[j]);
                // out.addOutputEvent(newSequence[j].toString()+ " ");
            }
            eventSequence[seqSize] = new Event();
            eventSequence[seqSize].setEvent(1);
            // out.addNewLine();

        } else {

            Boolean isFound = searchAndAppendSequence(eventSequence, newSequence);// search
                                                                                  // in
                                                                                  // tree
                                                                                  // from
                                                                                  // this
                                                                                  // node/sequence

            if (isFound == false) {// if not match is found then add on 0 branch
                                   // because only top event matches
                eventSequence[0].setBranches(new ArrayList<Event[]>());
                Event[] newBranchSeq = new Event[seqSize + 1];
                for (int j = 0; j < seqSize - 1; j++) {
                    newBranchSeq[j] = new Event();
                    newBranchSeq[j].setEvent(newSequence[j]);
                    // out.addOutputEvent(newSequence[j].toString()+ " ");
                }
                newBranchSeq[seqSize] = new Event();
                newBranchSeq[seqSize].setEvent(1);
                eventSequence[0].getBranches().add(newBranchSeq);
                // out.addNewLine();

            }
        }
        // putting the sequence (graph actually) to the starting event (node) as
        // a name
        sysCallSequences.put(newSequence[0], eventSequence);// add to database
        // saveTreeInDatabase( database, dataAccessObject, eventSequence,
        // HmmModelCollection.COLLECTION_NAME.toString());
    }

    /**
     * Searches and appends a sequence(set of events) to an already existing
     * tree of events. If a sequence already exists, it updates the counter Use
     * the following tree example to understand the code
     *
     * 108-106-5-55-45 90 | 3---9-6 | |-3-10 | |-3-6 | 106 | 5
     *
     * @param eventSeq
     *            an already existing tree of events
     * @param newSeq
     *            a sequence of events that requires to be appended
     * @return
     */
    private Boolean searchAndAppendSequence(Event[] eventSeq, Integer[] newSeq) {

        Integer seqSize = newSeq.length;
        Integer j;
        for (j = 0; j < seqSize; j++) {

            if (!eventSeq[j].getEvent().equals(newSeq[j])) {
                break;
            }
        }

        Integer matchIdx = j - 1;

        if (matchIdx >= seqSize - 1) {
            Integer counter = eventSeq[seqSize].getEvent() + 1;
            eventSeq[seqSize].setEvent(counter);
            return true;
        }
        else if (matchIdx < 0) {
            return false; // return false if mismatch on the first idx
        }

        else {
            Event[] newEventSeq = new Event[seqSize - matchIdx];// +1 for the
                                                                // count
            Integer[] newTmpSeq = new Integer[seqSize - matchIdx - 1];
            Boolean isFound = false;
            Integer i;
            for (i = 0; i < newEventSeq.length - 1; i++) {
                newEventSeq[i] = new Event();
                newEventSeq[i].setEvent(newSeq[matchIdx + i + 1]);// that is
                                                                  // copy from
                                                                  // the next
                                                                  // index of
                                                                  // matched
                                                                  // index
                newTmpSeq[i] = newSeq[matchIdx + i + 1];
            }
            newEventSeq[i] = new Event();
            newEventSeq[i].setEvent(1);// add 1 as a counter at the leaf, for
                                       // the first sequence on a branch

            ArrayList<Event[]> branches = eventSeq[matchIdx].getBranches();
            // When there are no more branches then we shall automatically
            // add a new branch by skipping the if block
            if (branches != null) {
                // if the branches exist then we need to recursively go through
                // the remaining branches to find
                // a possible location to append the new sequence

                for (int bCount = 0; bCount < branches.size(); bCount++) {
                    Event[] branchEventSeq = branches.get(bCount);
                    // / ****** recursive call
                    isFound = searchAndAppendSequence(branchEventSeq, newTmpSeq);
                    // / ****** recursive call
                    if (isFound == true) {// {// there is no need to iterate
                                          // more branches, we have found a
                                          // match
                        // branches.set(bCount, branchEventSeq);
                        // branches.add(returnSeq);
                        break;
                    }
                }
            } else {
                branches = new ArrayList<>();
            }
            // We have just found out where to append a branch in the tree
            // add a new branch to the event and return the eventSeq.

            if (isFound == false) {
                branches.add(newEventSeq);
            }

            eventSeq[matchIdx].setBranches(branches);
            return true;

        }
        // End of function searchAndAddSequence
    }

    /**
     * This function saves the model in the database by converting HashMap to
     * JSON and serializing in MongoDB
     *
     * @param outStream
     *            An object to display output
     * @param database
     *            Database name
     * @param dataAccessObject
     *            IDataAccessObject object
     * @param sysCallSequences
     *            A map containing one tree of events for every name
     * @param collectionName
     *            Collection name
     * @throws TotalADSDBMSException
     */
    public void saveinDatabase(IAlgorithmOutStream outStream, String database, IDataAccessObject dataAccessObject, HashMap<Integer,
            Event[]> sysCallSequences) throws TotalADSDBMSException {

        outStream.addOutputEvent("Saving in database.....");
        outStream.addNewLine();

        for (Map.Entry<Integer, Event[]> nodes : sysCallSequences.entrySet()) {

            Event[] events = nodes.getValue();

            com.google.gson.Gson gson = new com.google.gson.Gson();

            JsonElement jsonArray = gson.toJsonTree(events);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty(TraceCollection.KEY.toString(), nodes.getKey());
            jsonObject.add(TraceCollection.TREE.toString(), jsonArray);

            JsonObject jsonKey = new JsonObject();
            jsonKey.addProperty(TraceCollection.KEY.toString(), nodes.getKey());

            // console.printTextLn(jsonObject.toString());

            dataAccessObject.insertOrUpdateUsingJSON(database, jsonKey, jsonObject, TraceCollection.COLLECTION_NAME.toString());

        }
        // printSequence(console, database, sysCallSequences);
    }

    /**
     * Saves a tree in the database
     *
     * @param database
     * @param dataAccessObject
     * @param sysCallSequences
     * @param collectionName
     * @throws TotalADSDBMSException
     */
    public void saveTreeInDatabase(String database, IDataAccessObject dataAccessObject,
            Event[] tree, String collectionName) throws TotalADSDBMSException {

        Integer key = tree[0].getEvent(); // top node is the name

        com.google.gson.Gson gson = new com.google.gson.Gson();

        JsonElement jsonArray = gson.toJsonTree(tree);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(TraceCollection.KEY.toString(), key);
        jsonObject.add(TraceCollection.TREE.toString(), jsonArray);

        JsonObject jsonKey = new JsonObject();
        jsonKey.addProperty(TraceCollection.TREE.toString(), key);

        // console.printTextLn(jsonObject.toString());
        dataAccessObject.insertOrUpdateUsingJSON(database, jsonKey, jsonObject, collectionName);

    }

    /**
     * Loads a tree based on the root node from the database
     *
     * @param rootNode
     * @param database
     * @param dataAccessObject
     * @return
     * @throws TotalADSDBMSException
     */
    public Event[] loadTreeFromDatabase(String rootNode, String database, IDataAccessObject dataAccessObject) throws TotalADSDBMSException {

        Event[] event = null;
        try (IDBCursor cursor = dataAccessObject.select(TraceCollection.KEY.toString(), "",
                    rootNode, database, TraceCollection.COLLECTION_NAME.toString())){
           if (cursor.hasNext()) {
               IDBRecord record = cursor.next();
               Gson gson = new Gson();
               event = gson.fromJson(record.get(TraceCollection.TREE.toString()).toString(), Event[].class);
           }
        }
        return event;
    }

    /**
     * Prints all the sequences in a tree; use for testing
     *
     * @param outStream
     *            An object to display information
     * @param sysCallSequences
     *            A map containing one tree of events for every name
     * @param nameToId
     *            NameToIDMapper
     */
    public void printSequence(IAlgorithmOutStream outStream, HashMap<Integer, Event[]> sysCallSequences, NameToIDMapper nameToId) {

        for (Map.Entry<Integer, Event[]> nodes : sysCallSequences.entrySet()) {

            printRecursive(nodes.getValue(), "", outStream, nameToId); //$NON-NLS-1$
        }

    }

    /**
     * /** This function goes through the tree of Events and print the sequences
     * in a human readable form.
     *
     * @param nodes
     *            Root event
     * @param prefix
     *            Prefix of the event sequence
     * @param OutStream
     *            An object to display output
     * @param nameToId
     *            NameToId mapper
     */
    private void printRecursive(Event[] nodes, String prefix, IAlgorithmOutStream outStream, NameToIDMapper nameToId) {

        // Boolean isPrefixPrinted=false;
        String thePrefix = prefix;
        for (int nodeCount = 0; nodeCount < nodes.length; nodeCount++) {

            ArrayList<Event[]> branches = nodes[nodeCount].getBranches();
            if (nodeCount == nodes.length - 1) {
                thePrefix = thePrefix + " -: Count=" + nodes[nodeCount].getEvent();// the
                                                                                   // last
                                                                                   // element
                                                                                   // is
                                                                                   // the
                                                                                   // count
                                                                                   // of
                                                                                   // the
                                                                                   // sequence
            }
            else {
                thePrefix = thePrefix + "\""+nameToId.getKey(nodes[nodeCount].getEvent().intValue()) + "\" ";// just
                                                                                                      // append
                                                                                                      // the
                                                                                                      // events

            }

            if (branches != null) { // if there are branches on an event then
                                    // keep

                for (int i = 0; i < branches.size(); i++) {
                    printRecursive(branches.get(i), thePrefix, outStream, nameToId);
                }
            } else {

                // Print only when we reach a leaf of a branch
                if (nodeCount == nodes.length - 1) {
                    outStream.addOutputEvent(thePrefix);
                }

                // console.printText(nodes[nodeCount].event+"-");
            }
        }
        outStream.addNewLine();
    }

    /**
     * Searches a matching sequence in the tree
     *
     * @param eventSeq
     *            Tree
     * @param newSeq
     *            New sequence
     * @return True if a sequence matches, else false
     */
    public Boolean searchMatchingSequenceInTree(Event[] eventSeq, String[] newSeq) {

        Integer seqSize = newSeq.length;
        Integer j;
        for (j = 0; j < seqSize; j++) {
            if (!eventSeq[j].getEvent().equals(newSeq[j])) {
                break;
            }
        }

        Integer matchIdx = j - 1;

        if (matchIdx >= seqSize - 1) {
            return true;
        } else if (matchIdx < 0) {
            return false; // return null if mismatch on the first idx
        } else {
            // Event []newEventSeq= new Event[seqSize-matchIdx];//+1 for the
            // count
            String[] newTmpSeq = new String[seqSize - matchIdx - 1];
            Boolean isFound = false;
            Integer i;
            for (i = 0; i < newTmpSeq.length - 1; i++) {
                // that is copy from the next index of matched index
                newTmpSeq[i] = newSeq[matchIdx + i + 1];
            }

            ArrayList<Event[]> branches = eventSeq[matchIdx].getBranches();
            // When there are no more branches then we shall automatically
            // add a new branch by skipping the if block
            if (branches != null) {
                // if the branches exist then we need to recursively go through
                // the remaining branches to find
                // a possible location to append the new sequence

                for (int bCount = 0; bCount < branches.size(); bCount++) {
                    Event[] branchEventSeq = branches.get(bCount);
                    // / ****** recursive call
                    isFound = searchMatchingSequenceInTree(branchEventSeq, newTmpSeq);
                    // / ****** recursive call
                    if (isFound == true) {
                        break;
                    }

                }
            }

            if (isFound) {
                return true;
            }
            return false;

        }
        // End of function searchMatchingSequence
    }

    /**
     * Searches a new sequence in the tree and returns the Hamming distance. if
     * Hamming distance is zero then a sequence matched otherwise Hamming
     * distance is equal to the number of mismatches
     *
     * @param nodes
     *            Tree of events
     * @param newSeq
     *            Sequence to search
     */
    public Integer getHammingAndSearch(Event[] nodes, Integer[] newSeq) {

        Integer hammDis = 0, minHammDis = 100000000;// initializing minimum
                                                    // hamming distance with a
                                                    // very large number

        for (int nodeCount = 0; nodeCount < newSeq.length; nodeCount++) {

            ArrayList<Event[]> branches = nodes[nodeCount].getBranches();
            if (!nodes[nodeCount].getEvent().equals(newSeq[nodeCount])) {
                // System.out.print (", x "+nodes[nodeCount].getEvent());
                hammDis++;
            }
            // else
            // System.out.print (", =="+nodes[nodeCount].getEvent());

            if (branches != null) { // if there are branches on an event then
                                    // keep
                Integer[] newTmpSeq = new Integer[newSeq.length - nodeCount - 1];
                for (int i = 0; i < newTmpSeq.length; i++)
                {
                    newTmpSeq[i] = newSeq[nodeCount + i + 1];// that is copy
                                                             // from the next
                                                             // index of matched
                                                             // index
                }

                for (int i = 0; i < branches.size(); i++) {
                    // System.out.println();
                    Integer branchHamming = getHammingAndSearch(branches.get(i), newTmpSeq);
                    branchHamming = branchHamming + hammDis;// add the
                                                            // mismatches that
                                                            // have been found
                                                            // before this
                                                            // branch
                    if (branchHamming == 0) { // there is no need to get further
                                              // branches
                        minHammDis = 0; // we have found a match, as hamming is
                                        // 0
                        break;
                    }
                    else if (branchHamming < minHammDis) {
                        minHammDis = branchHamming;
                    }

                }
            }
        }

        if (hammDis < minHammDis) {
            minHammDis = hammDis;
        }

        return minHammDis;
    }

    // End of class
}
