package server;

public class Paragraph {
	private final String paragraphID;
	private String paragraphText;
	
	/**
	 * Constructor for the class paragraph
	 * @param paragraphID String representing the paragraph ID of the paragraph
	 */
	public Paragraph(String paragraphID) {
		this.paragraphID = paragraphID;
		this.paragraphText = "";
	}
	
	/**
	 * Returns the paragraph ID of the particular paragraph
	 * @return String representing paragraphID of the paragraph
	 */
	public String getParagraphID() {
		return paragraphID;
	}
	
	/**
	 * Returns the text in a paragraph
	 * @return String representing the text contained in the paragraph
	 */
	public String getParagraphText() {
		return paragraphText;
	}
	
	public void setParagraphText(String newParagraphText) {
		paragraphText = newParagraphText;
	}
	
	@Override
	public String toString() {
		return paragraphText;
	}
}