package wiki;

public class NewMessagesException extends Exception {
	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE = "The bot has received new messages and will not run until they have been reviewed.";
	
	public NewMessagesException() {
		super(MESSAGE);
	}
	
	public NewMessagesException(String message){
		super(MESSAGE + " - " + message);
	}
	
	public NewMessagesException(String message, Throwable cause){
		super(MESSAGE + " - " + message, cause);
	}
	
	public NewMessagesException(Throwable cause){
		super(MESSAGE, cause);
	}
}
