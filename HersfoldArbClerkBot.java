package wiki;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Scanner;
import java.util.TimeZone;

import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

public class HersfoldArbClerkBot{

	/**
	 * Set to true to disable editing outside of the bot's userspace
	 */
	private static boolean NO_EDIT_MODE;

	public static final String BOT_PASSWORD_FILE = ".HACBpassword.txt";
	public static final String BOT_NAME = "HersfoldArbClerkBot";
	public static final int RETRY_MAX = 2;

	private static Wiki wikipedia = null;
	private static String password = null;

	/**
	 * Logs the bot into Wikipedia
	 * @return true if successful
	 */
	private static boolean login() throws IOException{
		try{
			File pwdFile = new File(BOT_PASSWORD_FILE);
			if(!pwdFile.exists()){
				System.err.println("ERROR - Password file not found!");
				System.exit(-3);
			}
			else if(!pwdFile.canRead()){
				System.err.println("ERROR - Password file cannot be read!");
				System.exit(-3);
			}
			else{
				Scanner input = new Scanner(new FileInputStream(pwdFile));
				password = input.nextLine();
			}
			wikipedia.login(BOT_NAME, password.toCharArray());
		}
		catch(FailedLoginException e){
			System.err.println("FAILED TO LOG IN, TERMINATING.");
			System.err.println(e.getMessage());
			e.printStackTrace(System.err);
			System.exit(-2);
		}
		return true;
	}	

	public static void main(String[] args) {
		if(args.length > 1){
			System.err.println("Too many arguments, extras will be ignored");
		}
		if(args.length == 1){
			NO_EDIT_MODE = Boolean.parseBoolean(args[0]);
			if(NO_EDIT_MODE){
				System.out.println("Running in no edit mode");
			}
		}
		
		wikipedia = new Wiki("en.wikipedia.org");

		// Transfer output to log file
		File logFile = new File("HersfoldArbClerkBotLog " + currentTimestamp() + ".txt");
		PrintStream output = null;
		PrintStream oldOut = System.out;
		PrintStream oldErr = System.err;

		try{
			if(!logFile.exists()){
				logFile.createNewFile();
			}
			output = new PrintStream(new FileOutputStream(logFile));
		}
		catch(IOException e){
			System.err.println(e.getMessage());
			e.printStackTrace(System.err);
			System.exit(-1);
		}

		if(output != null){
			System.setOut(output);
			System.setErr(output);
		}
		else{
			System.err.println("ERROR! Unable to create output stream.");
			System.exit(-4);
		}

		// Set up system and log in
		try{
			login();

			if(wikipedia.hasNewMessages()){
				throw new NewMessagesException();
			}

			int retryCount = 0;
			boolean notdone = true;
			while(notdone){
				try{
					wikipedia.edit("User:HersfoldArbClerkBot/LastRun", "~~~~~", "Recording time of last run", true);
					notdone = false;
				}
				catch(LoginException e){
					if(retryCount == RETRY_MAX){
						retryFailed(e, "main - editing LastRun page");
					}
					System.err.println("BOT IS NOT LOGGED IN!");
					System.err.println(e.getMessage());
					e.printStackTrace(System.err);
					System.err.println("Attempting to log back in...");
					login();
					notdone = true;
					retryCount++;
				}
			}
			
			ArrayList<HACBModule> modules = new ArrayList<HACBModule>();
			modules.add(new HACBEvidenceModule(wikipedia, NO_EDIT_MODE));
			
			for(HACBModule module : modules){
				module.run();
			}
		}
		catch(NewMessagesException e){
			System.err.println(e.getMessage());
		}
		catch(IOException e){
			System.err.println(e.getMessage());
			e.printStackTrace(System.err);
		}
		catch(Exception e){
			// To ensure graceful shutdown, and send unexpected
			// errors to the console so they get emailed to me
			oldErr.println(e.getMessage());
			e.printStackTrace(oldErr);
		}
		finally{
			wikipedia.logout();
			System.out.println("\n\n");
			output.close();
			System.setErr(oldErr);
			System.setOut(oldOut);
		}
		System.exit(0);
	}

	public static void attemptLogin(Exception e, String methodName) throws IOException{
		int retryCount = 0;
		do{
			System.err.println("BOT IS NOT LOGGED IN!");
			System.err.println(e.getMessage());
			e.printStackTrace(System.err);
			System.err.println("Attempting to log back in...");
			boolean result = login();
			if(result){
				return;
			}
			retryCount++;
		}while(retryCount < RETRY_MAX);
		retryFailed(e, methodName);
	}

	/**
	 * If the bot is unexpectedly logged out, it will attempt to log back
	 * in and redo the edit up to three times. After that, this method is
	 * called to terminate the bot.
	 * @param e the exception thrown by the Wiki framework
	 * @param methodName the method the bot was in when the problem was encountered
	 */
	private static void retryFailed(Exception e, String methodName){
		System.err.println("MAXIMUM RETRIES EXCEEDED - TERMINATING.");
		System.err.println("Source method: " + methodName);
		System.err.println(e.getMessage());
		e.printStackTrace(System.err);
		System.exit(-2);
	}

	public static String currentTimestamp(){
		Calendar time = GregorianCalendar.getInstance();
		time.setTimeZone(TimeZone.getTimeZone("Universal"));
		time.setTimeInMillis(System.currentTimeMillis());

		String stamp = time.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US);
		stamp += " " + time.get(Calendar.DAY_OF_MONTH);
		stamp += ", " + time.get(Calendar.YEAR);
		stamp += " " + time.get(Calendar.HOUR_OF_DAY);
		stamp += "-" + (time.get(Calendar.MINUTE) < 10 ? "0" : "") + time.get(Calendar.MINUTE);

		return stamp;		
	}


}
