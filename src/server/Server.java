package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import model.Document;

/**
 * This class runs the server that hosts the documents and maintains connections with
 * clients. This class needs to be run before any clients connect to the server. This 
 * server can handle multiple clients connecting to it concurrently. 
 * 
 * The server interacts with the client by running two threads that continually pull 
 * from streams. One of the threads looks for new connections and attaches the socket 
 * of any new connection to the serverSocket of the server. The other thread handles
 * requests from the client. Clients can view, create, and modify documents and log in 
 * and log out, among other actions. This thread reads the request from each client 
 * and modifies the corresponding document. We keep track of the clients by assigning
 * each client a unique ID. 
 * 
 * Rep Invariants:
 * 		onlineUsers is a list of unique names
 * 		Each document has a unique name
 * 		
 */
public class Server {
	
	private List<Document> currentDocuments;
	private static int port = 4444;
	private final ServerSocket serverSocket;
	private Set<String> onlineUsers;
	private Map<Integer, String> socketUserMappings;
	private List<PrintWriter> outputStreamWriters;
	
	private final Object lock = new Object();
	
	private LinkedBlockingQueue<ServerRequest> queue;
	
	/**
	 * Initializes the EtherpadServer with the default port number
	 * @throws IOException If there is an error creating the server socket
	 */
	public Server() throws IOException {
		this(port);
	}
	
	/**
	 * Initializes the EtherpadServer with the given port number
	 * @param port The port number to which the server publishes messages
	 * @throws IOException If there is an error creating the server socket
	 */
	public Server(int givenPort) throws IOException {
		currentDocuments = new ArrayList<Document>();
		serverSocket = new ServerSocket(port);
		
		onlineUsers = new HashSet<String> ();
		socketUserMappings = new HashMap<Integer, String> ();
		
		outputStreamWriters = new ArrayList<PrintWriter> ();
		
		queue = new LinkedBlockingQueue<ServerRequest> ();
	}
	
	/**
     * Run the server, listening for client connections and handling them.  
     * Never returns unless an exception is thrown.
     * @throws IOException if the main server socket is broken
     * (IOExceptions from individual clients do *not* terminate serve()).
     */
    public void serve() throws IOException {
    	int ID = 0;
        while (true) {
            // block until a client connects
            Socket socket = serverSocket.accept();
            ID++; //ID keeps track of the socket ID, to take care of users exiting
            Thread socketThread = new Thread(new RunnableServer(socket, ID));
            socketThread.start();
        }
    }
    
    /**
     * Class defined to help start a new thread every time a new client connects to the server
     */
    private class RunnableServer implements Runnable {
    	Socket socket;
    	int ID;
    	
    	public RunnableServer(Socket socket, int ID) {
    		this.socket = socket;
    		this.ID = ID;
    	}
    	
		@Override
		public void run() {
			try {
				handleConnection(socket, ID);
			} catch (IOException e) {
				e.printStackTrace();
			}			
		}
    	
    }
    
    /**
     * Handle a single client connection.  Returns when client disconnects.
     * @param socket socket where the client is connected
     * @throws IOException if connection has an error or terminates unexpectedly
     */
	public void handleConnection(Socket socket, int ID) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        
        try {
        	synchronized (lock) {
        		outputStreamWriters.add(out);
        	}
        	out.println("id: "+ID);
	        for (String line = in.readLine(); line!=null; line=in.readLine()) {
	        	ServerRequest serverRequest = new ServerRequest(ID, line);
	        	queue.add(serverRequest);
	            
	        }
        } catch (IOException e) {
        	if (socketUserMappings.containsKey(ID)) {
        		String userName = socketUserMappings.get(ID);
        		onlineUsers.remove(userName);
        		socketUserMappings.remove(ID);
        		
        	}
        	outputStreamWriters.remove(out);
        	out.close();
        	in.close();
        }

	}
	
	/**
	 * Attends to the different requests made by the different clients.
	 * @throws InterruptedException throws an interrupted exception when popping out of the block queue is interrupted
	 */
	public synchronized void attendRequest() throws InterruptedException {
		while (true) {
			ServerRequest serverRequest = queue.take();
			int ID = serverRequest.getID();
			String request = serverRequest.getLine();
			RequestType requestType = serverRequest.getType();
			
			String response = handleRequest(request, ID, requestType);

            for (PrintWriter outputStream : outputStreamWriters) {
            	outputStream.println(response);
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
    private String handleRequest(String input, int ID, RequestType requestType) {
    	String userName = "";
    	
    	switch (requestType) {
    	case LOGIN:
    		//attempts to log the user in, checks if name is unique
			userName = input;
			return logIn(userName, ID);
			
    	case NEWDOC:
			//creates a new document if the input is formatted validly
			String[] newdocTokens = input.split(" ");
			if (newdocTokens.length == 2) {
				userName = newdocTokens[0];
				String docName = newdocTokens[1];
				return newDoc(userName, docName);
			}
			break;
			
		case OPENDOC: 
			//opens a document if the input is validly formatted
			String[] tokens = input.split(" ");
			if (tokens.length == 2) {
				userName = tokens[0];
				String docName = tokens[1];
				return openDoc(userName, docName);
			}
			break;
		
	
		case CHANGEDOC:
			//passes off the input to a helper method
			//this is called when a user inserts or deletes a character in a document
			return changeDoc(input);
			
		case EXITDOC:
			//exits the document and returns the user to the document table screen
			String[] exitdocSplit = input.split(" ");
			userName = exitdocSplit[0];
			String docName = exitdocSplit[1];
			return exitDoc(userName, docName);
			
		case LOGOUT:
			//logs the user out and returns them to the login page
			String[] inputSplit = input.split(" ");
			userName = inputSplit[0];
			return logOut(userName);
			
		default:
			return "Invalid request";
    	}
    	
    	//catches the case where the token length is not correct
    	return "Invalid request";
    	
	}
    
    /**
     * Makes a change to the document, as per the instructions of the client
     * @param input
     * @return
     */
    private synchronized String changeDoc(String input) {
    	String[] inputSplit = input.split("\\|");

    	int version;
    	//insertion is in the form docName | position | change | length | version
    	//deletion is in the form docName | position | length | version
		String docName = inputSplit[1];
		String userName = inputSplit[0];
		Document currentDocument = getDoc(docName);
				
		//initializes our variables
		int position = -1;
		int length = -1;
		boolean isInsertion = false;

		//if the user wants to insert a letter
		if (inputSplit.length == 6) {
			position = Integer.valueOf(inputSplit[2]);
			String change = inputSplit[3];
			length = Integer.valueOf(inputSplit[4]);
			version = Integer.valueOf(inputSplit[5]);
			isInsertion = true;
			
			//update the model of the data
			//a tab character represents a newline so that socket input is not broken over multiple lines
			//the user is not able to enter tabs so we don't have to worry about how to represent tabs
			if (change.equals("\t")) {
				currentDocument.insertContent("\n", position, version);
			} else {
				currentDocument.insertContent(change, position, version);
			}
			currentDocument.setLastEditDateTime();
			
			//version updating is handled by the insertion/deletion of content
			int versionNumber = currentDocument.getVersion();
			
			if (position != -1 && length != -1) {
				return "changed|" + userName + "|" + docName + "|" + change + "|" + position + "|" + length + "|" + versionNumber + "|" + isInsertion;
			}
		} 
		
		//if the user wants to delete a letter
		else if (inputSplit.length == 5) {
			position = Integer.valueOf(inputSplit[2]);
			length = Integer.valueOf(inputSplit[3]);
			version = Integer.valueOf(inputSplit[4]);
			
			//update the model of the data
			currentDocument.deleteContent(position, length, version);
			
			currentDocument.setLastEditDateTime();
			
			//version updating is handled by the insertion/deletion of content
			int versionNumber = currentDocument.getVersion();
			
			if (position != -1 && length != -1) {
				return "changed|" + userName + "|" + docName + "|" + position + "|" + length + "|" + versionNumber + "|" + isInsertion;
			}

		}
		
		throw new RuntimeException("Should not reach here");
    }
    
    /**
     * Logs the user in if they have a unique username
     * @param userName Username of the user who logs into the system
     * @return the response which encodes whether or not the login was successful
     */
    private String logIn(String userName, int ID) {
		//if the username already is logged in
    	if (onlineUsers.contains(userName)) {
			return "notloggedin";
		} 
		
    	//otherwise, the user has a unique name
		else {
			onlineUsers.add(userName);
			socketUserMappings.put(ID, userName);
			
			//this returns information about the user logged in 
			//it then returns a list of documents and their corresponding names, dates, and collaborators
			StringBuilder stringBuilder = new StringBuilder("loggedin " + userName + " " + ID);
			stringBuilder.append("\n");
			
			for (Document document : currentDocuments){
				stringBuilder.append(document.getName());
				stringBuilder.append("\t");
				stringBuilder.append(document.getDate());
				stringBuilder.append("\t");
				String collaborators = document.getCollab();
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
    private synchronized String newDoc(String userName, String docName) {
    	for (Document doc : currentDocuments){
    		if(docName.equals(doc.getName())){
    			return "notcreatedduplicate";
    		}
    	}
    	Document newDoc = new Document(docName, userName);
		currentDocuments.add(newDoc);
		String date = newDoc.getDate();
		return "created|" + userName + "|" + docName + "|" + userName + "|" + date; 
    }
    
    /**
     * Opens an existing document
     * @param userName The name of the user that opens the document
     * @param docName The name of the document that is being opened
     * @return Response from the server to the clients; all GUIs are updated
     */
    private String openDoc(String userName, String docName) {
		Document currentDocument = getDoc(docName);
		currentDocument.addCollaborator(userName);
		String docContent = currentDocument.toString();
		docContent = docContent.replace("\n", "\t");
		String collaborators = currentDocument.getCollab();
		int version = currentDocument.getVersion();
		
		//updates collaborators than opens the document
		return "update|" + docName + "|" + collaborators + "\nopened|" + userName + "|" + docName + "|" + docContent + "|" + collaborators + "|" + version; 		
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
		
		//add information about all the documents in the database
		for (Document document : currentDocuments){
			stringBuilder.append(document.getName());
			stringBuilder.append("\t");
			stringBuilder.append(document.getDate());
			stringBuilder.append("\t");
			String collaborators = document.getCollab();
			stringBuilder.append(collaborators);
			stringBuilder.append("\n");
		}
		stringBuilder.append("enddocinfo");
		
		//returns exitdoc response then the information about the document list for the document table
		return stringBuilder.toString();
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
	 * This server stores documents and can be accessed by clients running the controller. 
	 * @param args Unused
	 */
	public static void main(String[] args) {
		final Server etherpadServer;
		try {
			etherpadServer = new Server();
			
			//Serving thread handles new connections made to the server
			Thread servingThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						System.out.println("Listening for requests");
						etherpadServer.serve();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
			
			//Attending thread attends to the different requests made by the clients
			Thread attendingThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						System.out.println("Attending requests");
						etherpadServer.attendRequest();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			});
			
			servingThread.start();
			attendingThread.start();
			
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
}
