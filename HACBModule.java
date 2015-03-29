package wiki;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class HACBModule {
	
	public static final String ARB_CASE_PREFIX = "Wikipedia:Arbitration/Requests/Case/";
	public static final String REVIEW_SUFFIX = "/Review";
	public static final String EVIDENCE_SUFFIX = "/Evidence";
	public static final String ARBCOMOPENTASKS = "Template:ArbComOpenTasks/Cases";
	public static final String OPEN_TASKS_LINE = "{{ArbComOpenTasks/line";
	public static final String OPEN_TASKS_NAME = "name";
	public static final String OPEN_TASKS_MODE = "mode";
	public static final String CONFIGURATION_PAGE = "User:HersfoldArbClerkBot/Configuration";
	
	protected final Wiki wiki;
	protected final boolean noEditMode;
	
	public HACBModule(Wiki wiki, boolean noEditMode){
		if(wiki == null){
			throw new IllegalArgumentException("You must provide a wiki object to this module!");
		}
		this.wiki = wiki;
		this.noEditMode = noEditMode;
	}

	/**
	 * Activates the module.
	 * @throws NewMessagesException
	 */
	public abstract void run() throws NewMessagesException;
	
	/**
	 * Handled IOException errors by ending the program, unless the error is caused by page protection
	 * @param e the IOException
	 */
	protected static void IOError(IOException e){
		if(e.getMessage().contains("protectedpage")){
			System.err.println("ERROR: Edit failed due to page protection. Ignoring exception and continuing.");
			return;
		}
		System.err.println(e.getMessage());
		e.printStackTrace(System.err);
		System.exit(-1);
	}
	
	/**
	 * Checks for new messages and throws an exception if any are noted.
	 * Modules may catch these exceptions, but only as needed to ensure graceful
	 * shutdown. The exception should still be thrown to the main driver.
	 * @throws NewMessagesException
	 */
	protected void checkForNewMessages() throws NewMessagesException{
		try{
			if(wiki.hasNewMessages()){
				throw new NewMessagesException();
			}
		}
		catch(IOException e){
			IOError(e);
		}
	}
	
	/**
	 * Checks a page to determine if the bot is denied access with the {{bots}}
	 * template. Returns true if ok to edit.
	 * @param pageContent the content of the page
	 * @return true if the bot is not excluded
	 */
	protected boolean noExclusions(String pageContent){
		boolean ok = true;

		if(pageContent.contains("{{nobots") || pageContent.contains("{{bots")){
			boolean nobots = true;
			int index1 = pageContent.indexOf("{{nobots");
			if(index1 == -1){
				index1 = pageContent.indexOf("{{bots");
				nobots = false;
			}
			int index2 = pageContent.indexOf("}}", index1) + 2;
			String template = pageContent.substring(index1, index2);

			if(template.contains("allow")){
				index1 = template.indexOf("allow");
				index2 = template.indexOf("|", index1);
				int index3 = template.indexOf("}}", index1);
				String allow = template.substring(index1, (index2 == -1 ? index3 : index2));
				ok = allow.contains("all") || allow.contains(HersfoldArbClerkBot.BOT_NAME);
			}
			else if(template.contains("deny")){
				index1 = template.indexOf("deny");
				index2 = template.indexOf("|", index1);
				int index3 = template.indexOf("}}", index1);
				String deny = template.substring(index1, (index2 == -1 ? index3 : index2));
				ok = !(deny.contains("all") || deny.contains(HersfoldArbClerkBot.BOT_NAME));
			}
			else{
				ok = !nobots;
			}
		}

		return ok;
	}
	
	public static String decodeHTMLEntities(String string) {
		Pattern regex = Pattern.compile(".*(&#(\\d+);).*");
		Matcher matcher = regex.matcher(string);
		while (matcher.matches()) {
			String entity = matcher.group(1);
			int charValue = Integer.parseInt(matcher.group(2));
			string = string.replaceAll(entity, "" + (char) charValue);
			matcher = regex.matcher(string);
		}
		return string;
	}

}
