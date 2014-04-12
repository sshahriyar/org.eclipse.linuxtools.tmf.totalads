package org.eclipse.linuxtools.tmf.totalads.ui.live;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import org.eclipse.linuxtools.tmf.totalads.exceptions.TotalADSNetException;
import org.eclipse.linuxtools.tmf.totalads.core.Configuration;
import org.eclipse.linuxtools.tmf.totalads.ui.ProgressConsole;












/*********************************************************************************************
 * Copyright (c) 2014  Software Behaviour Analysis Lab, Concordia University, Montreal, Canada
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of XYZ License which
 * accompanies this distribution, and is available at xyz.com/license
 *
 * Contributors:
 *    Syed Shariyar Murtaza
 **********************************************************************************************/
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.UserInfo;
/**
 * http://www.jcraft.com/jsch/examples/Sudo.java.html
 * @author <p> Syed Shariyar Murtaza justsshary@hotmail.com </p>
 *
 */
public class SSHConnector {
	private JSch jsch; 
	private Integer port;
	private UserInfo ui;
	private String user;
	private  String host;
	private Session session;
	private ProgressConsole console;
	private  String totalADSLocalDir;
	private Integer snapshotDuration;
	/**
	 * Constructor
	 */
	public SSHConnector() {
		jsch=new JSch(); 
		totalADSLocalDir=Configuration.getCurrentPath()+"totalads-traces";
		File file =new File(totalADSLocalDir);
		if (!file.isDirectory())// create this directory if it doesn't exist
			file.mkdir();
		else // delete all its contents if it the system was close abruptly last time without stopping the thread
			deleteFolderContents(file);
	}
	
	/**
	 * Returns the path of the remote LTTng trace on the local hard disk
	 * @return The path of a trace
	 */
	public String getTrace(){
		return totalADSLocalDir;
	}
	/**
	 * Opens an SSH connection using a password, executes LTTng commands on a remote system and 
	 * @param userAtHost
	 * @param password
	 * @param portToConnect
	 * @param console
	 * @throws TotalADSNetException
	 */
	public void openSSHConnectionUsingPassword(String userAtHost,String password, Integer portToConnect, 
				ProgressConsole console, Integer snapshotDurationInSeconds) throws TotalADSNetException{
	try{
	    
		 port=portToConnect;
		 user=userAtHost.substring(0, userAtHost.indexOf('@'));
	     host=userAtHost.substring(userAtHost.indexOf('@')+1);
      	 this.console=console;    
      	 this.snapshotDuration=snapshotDurationInSeconds;
	      // password will be given via UserInfo interface.
	     ui=new UserInfoSSH(password, console) ;
	     session=jsch.getSession(user, host, port);
			 
		 session.setUserInfo(ui);
		 session.connect();
		 console.printTextLn("SSH connection established");
			     
	    } catch (JSchException e) {
	    	 throw new TotalADSNetException("SSH Communication Error\n"+e.getMessage());
		}
	}
	
	/**
	 * Connects with an SSH server using a private Key file present on the hard disk (Public key
	 * should be present on the server)
	 * @param userAtHost
	 * @param pathToPrivateKey
	 * @param portToConnect
	 * @param console
	 * @throws TotalADSNetException
	 */
	public void openSSHConnectionUsingPrivateKey(String userAtHost,String pathToPrivateKey, Integer portToConnect,
			ProgressConsole console, Integer snapshotDurationInSeconds) throws TotalADSNetException{
		try{
		    
			 port=portToConnect;
			 user=userAtHost.substring(0, userAtHost.indexOf('@'));
		     host=userAtHost.substring(userAtHost.indexOf('@')+1);
	      	 this.console=console;
	      	 this.snapshotDuration=snapshotDurationInSeconds;
	      	 
	      	 jsch.addIdentity(pathToPrivateKey);
	         console.printTextLn("Identity added ");
		     session=jsch.getSession(user, host, port);
		     java.util.Properties config = new java.util.Properties();
	         config.put("StrictHostKeyChecking", "no");
	         session.setConfig(config);	 
			 session.setUserInfo(ui);
			 session.connect();
			 console.printTextLn("SSH connection established");
				     
		    } catch (JSchException e) {
		    	 throw new TotalADSNetException("SSH Communication Error\n"+e.getMessage());
			}
		}
	/**
	 * Executes LTTng commands on a remote system and downloads the trace in a local folder
	 * @param session
	 * @param console
	 * @param sudoPassword
	 */
	public void collectATrace( String sudoPassword) throws TotalADSNetException{
		
	      String totalADSRemoteDir="/tmp/totalads";
	      String totalADSRemoteTrace=totalADSRemoteDir+"/kernel/";
	   	  String  sessionName="totalads-trace-0";  
	   	  // If an exception occurs, don't execute further commands and let the exception be thrown
	   	  executeSudoCommand("sudo -S -p  '' rm -rf "+totalADSRemoteTrace, sudoPassword);
	   	  executeSudoCommand("sudo -S -p '' mkdir -p "+totalADSRemoteDir, sudoPassword);
	      executeSudoCommand("sudo -S -p '' lttng create "+sessionName+" -o "+totalADSRemoteDir,  sudoPassword);
	      executeSudoCommand("sudo -S -p '' lttng enable-event -a -k",  sudoPassword);
	      executeSudoCommand("sudo -S -p '' lttng start",  sudoPassword);
	      
	      // Wait for these many seconds and then stop the trace
	      try{
	    	  console.printTextLn("Tracing for "+snapshotDuration+" secs.....");
	    	  TimeUnit.SECONDS.sleep(snapshotDuration);
	      }catch (InterruptedException ee){}
	      
	      executeSudoCommand("sudo -S -p '' lttng stop",  sudoPassword);
	      executeSudoCommand("sudo -S -p  '' lttng destroy "+sessionName, sudoPassword);
	      executeSudoCommand("sudo -S -p  '' chmod -R 777 "+totalADSRemoteDir, sudoPassword);
	      
	      downloadTrace(session,totalADSRemoteTrace , totalADSLocalDir);
	      executeSudoCommand("sudo -S -p  '' rm -rf "+totalADSRemoteTrace, sudoPassword);
	 	
	}
	
	 /**
	  * Executes a sudo (root) command
	  * @param command
	  * @param sudoPass
	 * 
	  */
	private void executeSudoCommand(String command, String sudoPass)throws TotalADSNetException {
		try{
			 
			Channel channel=session.openChannel("exec");
			((ChannelExec)channel).setCommand(command);
			  ((ChannelExec)channel).setErrStream(System.err);
		      InputStream in=channel.getInputStream();
		      OutputStream out=channel.getOutputStream();
		      
		      channel.connect();
		      out.write((sudoPass+"\n").getBytes());
		      out.flush();
		      displayStream(in, channel);
		      channel.disconnect();
		      
		     
	    }
		 catch(IOException e){
	    		console.printTextLn(e.getMessage());
	    		throw new TotalADSNetException("SSH Communication error");// Don't continue further
	    }
	    catch(JSchException e){
	    		console.printTextLn(e.getMessage());
	    		throw new TotalADSNetException("SSH Communication error");// Don't continue further
	    }
	}
	
	/**
	 * Display the output of a command on a remote system
	 * @param in
	 * @param channel
	 * @throws IOException
	 */
	private void displayStream(InputStream in, Channel channel) throws IOException{
		
		byte[] tmp=new byte[1024];
	      while(true){
	        while(in.available()>0){
	          int i=in.read(tmp, 0, 1024);
	          	if(i<0)break;
	          		//System.out.print(new String(tmp, 0, i));
	          		console.printText(new String(tmp, 0, i));
	        }
	        
	        if(channel.isClosed()){
	          if(in.available()>0) continue; 
	          		//System.out.println("exit-status: "+channel.getExitStatus());
	          		console.printTextLn("exit-status: "+channel.getExitStatus());
	          break;
	        }
	        try{
	        	Thread.sleep(1000); // Wait for some time to get more data over network stream
	        	}catch(Exception ex){}
	      }
	}
	
	/**
	 * This functions downloads the trace collected at the remote system
	 * @param session
	 * @param remoteFolder
	 * @throws IOException 
	 */
	private void downloadTrace(Session session, String remoteFolder, String localDownloadFolder) throws TotalADSNetException {
		  
		  try{
				ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
		        sftpChannel.connect();
		        sftpChannel.cd(remoteFolder);
		        java.util.Vector<ChannelSftp.LsEntry> list= sftpChannel.ls("*");
		        
		        for (ChannelSftp.LsEntry entry : list){
		        	console.printTextLn("Processing remote "+ entry.getFilename()); // actually downloading
		        	
			        sftpChannel.get(entry.getFilename(),localDownloadFolder+File.separator+entry.getFilename());
		        }
		  
			}
		 
		  catch(SftpException e){
		 		 console.printTextLn(e.getCause().getMessage()); // Exception printed
		 		throw new TotalADSNetException("SSH Communication error");// Don't continue further
		   }
		   catch(JSchException e){
		   		console.printTextLn(e.getCause().getMessage()); // Exception printed
		   		throw new TotalADSNetException("SSH Communication error");// Don't continue further
		   	}
	
	}
	 /**
	  * Closes the SSH connection and clears the trace from the local drive
	  */
	 public void close(){
		 deleteFolderContents(new File(this.totalADSLocalDir));
		 session.disconnect();
	 }
	
	 
	 /**
	  * Deletes all the contents of a folder. This function is used to delete an LTTng trace,
	  * which is a collection of files in a folder 
	  * @param folder Folder name
	  */
	 private  void deleteFolderContents(File folder) {
		    File[] files = folder.listFiles();
		    if(files!=null) { 
		        for(File f: files) {
		            if(f.isDirectory()) {
		                deleteFolderContents(folder);
		            } else {
		                f.delete();
		            }
		        }
		    }
		   // folder.delete();
		}
	 
	 
	 
	public static void main (String args[]){
		SSHConnector ssh=new SSHConnector();
		
		try {
			//ssh.openSSHConnection("shary@localhost", "grt_654321", 7225, null);
			System.out.println(Configuration.getCurrentPath());
			
		} catch (Exception e) {
			
			e.printStackTrace();
		}
	}
}
