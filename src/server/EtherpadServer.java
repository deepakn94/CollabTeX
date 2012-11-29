package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.Document;
import model.User;

public class EtherpadServer {
	
	private List<Document> currentDocuments;
	private int port = 4444;
	private final ServerSocket serverSocket;
	private Map<String, User> name_userMappings;
	private Set<String> onlineUsers;
	
	/**
	 * Initializes the EtherpadServer with the default port number
	 * @throws IOException If there is an error creating the server socket
	 */
	public EtherpadServer() throws IOException {
		currentDocuments = new ArrayList<Document>();
		name_userMappings = new HashMap<String,User>();
		serverSocket = new ServerSocket(port);
		
		onlineUsers = new HashSet<String> ();
	}
	
	/**
	 * Initializes the EtherpadServer with the given port number
	 * @param port The port number to which the server publishes messages
	 * @throws IOException If there is an error creating the server socket
	 */
	public EtherpadServer(int port) throws IOException {
		this.port = port;
		this.serverSocket = new ServerSocket(port);
		currentDocuments = new ArrayList<Document> ();
		name_userMappings = new HashMap<String, User> ();
		
		onlineUsers = new HashSet<String> ();
	}
	
	/**
     * Run the server, listening for client connections and handling them.  
     * Never returns unless an exception is thrown.
     * @throws IOException if the main server socket is broken
     * (IOExceptions from individual clients do *not* terminate serve()).
     */
    public void serve() throws IOException {
        while (true) {
            // block until a client connects
            Socket socket = serverSocket.accept();
            Thread socketThread = new Thread(new RunnableServer(socket));
            socketThread.start();
        }
    }
    
    /**
     * Class defined to help start a new thread every time a new client connects to the server
     */
    private class RunnableServer implements Runnable {
    	Socket socket;
    	
    	public RunnableServer(Socket socket) {
    		this.socket = socket;
    	}
    	
		@Override
		public void run() {
			try {
				handleConnection(socket);
			} catch (IOException e) {
				e.printStackTrace();
			}			
		}
    	
    }
  
    /**
     * handler for client input
     * 
     * make requested mutations on game state if applicable, then return 
     * appropriate message to the user.
     * 
     * @param input The request from the client to the server
     * @return Response from the server to the client
     */
    private String handleRequest(String input) {
		System.out.println(input);
		if (input.startsWith("LOGIN")) {
			
			String[] tokens = input.split(" ");
			String userName = tokens[1].trim();
			return logIn(userName);
			
		} else if (input.startsWith("NEWDOC")){
			
			String[] tokens = input.split(" ");
			if (tokens.length == 3) {
				String userName = tokens[1];
				String docName = tokens[2];
				return newDoc(userName, docName);
			}
			
		} 
		else if (input.startsWith("OPENDOC")){

			String[] tokens = input.split(" ");
			if (tokens.length == 3) {
				String userName = tokens[1];
				String docName = tokens[2];
				return openDoc(userName, docName);
			}
			
		} 
		else if (input.startsWith("CHANGE")){
			return changeDoc(input);
		} 
		else if (input.startsWith("EXITDOC")){
			
			String[] inputSplit = input.split(" ");
			String userName = inputSplit[1];
			String docName = inputSplit[2];
			return exitDoc(userName, docName);
			
		} 
		else if (input.startsWith("LOGOUT")){
			
			String[] inputSplit = input.split(" ");
			String userName = inputSplit[1];
			return logOut(userName);
			
		}
		
		throw new RuntimeException("Should not reach here");
	}
    
    /**
     * Makes a change to the document, as per the instructions of the client
     * @param input
     * @return
     */
    private String changeDoc(String input) {
    	String[] inputSplit = input.split("\\|");
		String docName = inputSplit[1];
		Document currentDocument = getDoc(docName);
		String docContent = null;
		if (inputSplit.length == 4) {
			int position = Integer.valueOf(inputSplit[2]);
			String change = inputSplit[3];
			String content;
			if (change.equals("\t")) {
				content = currentDocument.insertContent("\n", position);
			} else {
				content = currentDocument.insertContent(change, position);
			}
			currentDocument.updateContent(content);
			docContent = content.replace("\n", "\t");
			
		} else if (inputSplit.length == 5) {
			
			int position = Integer.valueOf(inputSplit[3]);
			int length = Integer.valueOf(inputSplit[4]);
			String content = currentDocument.deleteContent(position, length);
			currentDocument.updateContent(content);
			docContent = currentDocument.toString();	
			
		}
		currentDocument.setLastEditDateTime();
		if (docContent != null) {
			return "changed|" + docName + "|" + docContent;
		}
		
		throw new RuntimeException("Should not reach here");
    }
    
    /**
     * Logs the user in
     * @param userName Username of the user who logs into the system
     * @return 
     */
    private String logIn(String userName) {
		if (onlineUsers.contains(userName)) {
			return "notloggedin";
		} else {
			name_userMappings.put("username", new User(userName, ""));
			onlineUsers.add(userName);
			StringBuilder stringBuilder = new StringBuilder("loggedin " + userName);
			stringBuilder.append("\n");
			for (Document document : currentDocuments){
				stringBuilder.append(document.getName());
				stringBuilder.append("\t");
				stringBuilder.append(document.getDate());
				stringBuilder.append("\t");
				String collaborators = document.getCollab().toString();
				int collaboratorLength = collaborators.length();
				collaborators = collaborators.substring(1, collaboratorLength - 1);
				stringBuilder.append(collaborators);
				stringBuilder.append("\n");
			}
			stringBuilder.append("enddocinfo");
			return stringBuilder.toString();
		}
    }
    
    /**
     * Logs the user out
     * @param userName The name of the user to be logged out
     * @return
     */
    private String logOut(String userName) {
		onlineUsers.remove(userName);
		return "loggedout " + userName;
    }
    
    /**
     * Creates a new document
     * @param userName The name of the user that creates the new document
     * @param docName The name of the newly created document
     * @return Response from the server to the client
     */
    private String newDoc(String userName, String docName) {
		currentDocuments.add(new Document("asdf", docName, userName));
		return "created " + userName + " " + docName; 
    }
    
    /**
     * Opens a new document
     * @param userName The name of the user that opens the document
     * @param docName The name of the document that is being opened
     * @return Response from the server to the client
     */
    private String openDoc(String userName, String docName) {
    	
		Document currentDocument = getDoc(docName);
		currentDocument.addCollaborator(userName);
		String docContent = currentDocument.toString();
		docContent = docContent.replace("\n", "\t");
		return "opened|" + userName + "|" + docName + "|" + docContent; 
		
    }
    
    /**
     * Exits the document
     * @param userName The name of the user that is exiting the document
     * @param docName The name of the document that is being exited
     * @return Response from the server to the client
     */
    private String exitDoc(String userName, String docName) {
		StringBuilder stringBuilder = new StringBuilder("exiteddoc " + userName + " " + docName);
		stringBuilder.append("\n");
		for (Document document : currentDocuments){
			stringBuilder.append(document.getName());
			stringBuilder.append("\t");
			stringBuilder.append(document.getDate());
			stringBuilder.append("\t");
			String collaborators = document.getCollab().toString();
			int collaboratorLength = collaborators.length();
			collaborators = collaborators.substring(1, collaboratorLength - 1);
			stringBuilder.append(collaborators);
			stringBuilder.append("\n");
		}
		stringBuilder.append("enddocinfo");
		return stringBuilder.toString();
    }
	
    /**
     * Handle a single client connection.  Returns when client disconnects.
     * @param socket socket where the client is connected
     * @throws IOException if connection has an error or terminates unexpectedly
     */
	public void handleConnection(Socket socket) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        
        try {
	        for (String line = in.readLine(); line!=null; line=in.readLine()) {
	            String output = handleRequest(line);
	            System.out.println("Just sent:" + output);
	            out.println(output);
	        }
        } finally {
        	out.close();
        	in.close();
        }
	}
	
	/**
	 * Adds a new Document to the list of currently active documents
	 * @param newDocument The document object of the new document being added to the collection
	 */
	public void addDocument(Document newDocument) {
		currentDocuments.add(newDocument);
	}
	
	/**
	 * Returns the list of documents that are stored in the server
	 * @return A List of document objects that are stored in the server
	 */
	public List<Document> getDocuments() {
		return currentDocuments;
	}
	
	/**
	 * Returns the document object corresponding to the given document name
	 * @param docName Name of the document object to be retrieved
	 * @return An object of type Document
	 */
	private Document getDoc(String docName) {
		for (Document document: currentDocuments) {
			String name = document.getName();
			if (docName.equals(name)) {
				return document;
			}
		}
		throw new RuntimeException("Document not found");
	}
	
	/**
	 * Starts the etherpad server
	 * @param args Unused
	 */
	public static void main(String[] args) {
		try {
			EtherpadServer etherpadServer = new EtherpadServer();
			etherpadServer.serve();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
