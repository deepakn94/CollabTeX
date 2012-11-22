package server;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Document {
	
	private String documentID;
	private String documentName;
	private Map<String, Paragraph> paragraphs;
	private Calendar lastEditDateTime;
	private List<String> onlineCollaborators;

	/**
	 * Constructor of the class Document
	 * @param documentID String representing the document ID of the document
	 * @param documentName String representing the name of the document
	 */
	public Document(String documentID, String documentName) {
		this.documentName = documentName;
		this.documentID = documentID;
		this.paragraphs = new HashMap<String, Paragraph> ();
		this.lastEditDateTime = Calendar.getInstance();
		this.onlineCollaborators = new ArrayList<String> ();
	}
	
	/**
	 * Returns the document ID of the particular document
	 * @return String representing the document ID of the document
	 */
	public String getDocumentID() {
		return documentID;
	}
	
	/**
	 * Returns the name of the document
	 * @return String representing the name of the document
	 */
	public String getDocumentName() {
		return documentName;
	}
	
	/**
	 * Returns a paragraph with the given paragraph ID if it already exists.
	 * Otherwise, creates a new paragraph with the given paragraph ID and returns it. Updates document to contain the new paragraph as well.
	 * @param paragraphID String representing the paragraph ID of the Paragraph object in the document
	 * @return A Paragraph object that either already exists in the document, or is newly created with input paragraph ID
	 */
	public Paragraph getParagraph(String paragraphID) {
		if (paragraphs.containsKey(paragraphID)) {
			return paragraphs.get(paragraphID);
		} else {
			Paragraph newParagraph = new Paragraph(paragraphID);
			paragraphs.put(paragraphID, newParagraph);
			return newParagraph;
		}
	}
	
	/**
	 * Sets the lastEditDateTime state of the class to a date object that represents the current date and time
	 */
	public void setLastEditDateTime() {
		lastEditDateTime = Calendar.getInstance();
	}
	
	/**
	 * @return String representation of the time of the last edit of the document
	 */
	public String getLastEditDateTime() {
		String AM_PM = lastEditDateTime.get(Calendar.AM_PM) == 0 ? "AM" : "PM";
		String currentHour = String.valueOf(lastEditDateTime.get(Calendar.HOUR));
		String currentMinute = String.valueOf(lastEditDateTime.get(Calendar.MINUTE));
		String currentMonth = String.valueOf(lastEditDateTime.get(Calendar.MONTH) + 1);
		String currentDay = String.valueOf(lastEditDateTime.get(Calendar.DAY_OF_MONTH));
		
		String date = currentHour + ":" + currentMinute + " " + AM_PM + " , " + currentMonth + "/" + currentDay;
		return date;
	}
	
	/**
	 * Method that adds the name of a new collaborator to the list of currently online
	 * collaborators
	 * @param newCollaborator String representing the name of new collaborator
	 */
	public void addCollaborator(String newCollaborator) {
		onlineCollaborators.add(newCollaborator);
	}
	
	/**
	 * Method that removes the name of a collaborator if the collaborator exits the document
	 * @param collaborator String representing name of the collaborator who just exitted the
	 * document
	 */
	public void removeCollaborator(String collaborator) {
		onlineCollaborators.remove(collaborator);
	}
	
	@Override
	public String toString() {
		StringBuilder documentText = new StringBuilder();
		Set<String> paragraphKeys = paragraphs.keySet();
		for (String paragraphKey : paragraphKeys) {
			Paragraph paragraph = paragraphs.get(paragraphKey);
			documentText.append(paragraph.toString()).append("\n");
		}
		return documentText.toString();
	}
}