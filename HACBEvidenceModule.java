package wiki;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.login.LoginException;

public class HACBEvidenceModule extends HACBModule {

	public static final String DIFF_REGEX = "/w/index\\.php[^]\\s]*?diff";
	public static final String LENGTH_REPORT_PAGE = "User:HersfoldArbClerkBot/Length reports";
	public static final String LENGTH_REPORT_SUMMARY = "Updating evidence length data for cases: ";
	public static final String USER_NOTICE_HEADER = "Your Arbitration evidence is too long";
	public static final String USER_NOTICE_TEMPLATE = "{{subst:User:HersfoldArbClerkBot/User Notice";
	public static final String WARNING_LOG = "warningLog.txt";
	public static final String UPDATE_SECTION_SUMMARY = "[[User:HersfoldArbClerkBot|Bot]] updating evidence length information";
	public static final String INVALID_SECTION_TEMPLATE = "{{User:HersfoldArbClerkBot/InvalidSectionName}}";
	public static final String INVALID_SECTION_SUMMARY = "Marking section with malformed header - please correct for analysis.";
	public static final String USER_TEMPLATE_REGEX = "\\{\\{([Aa]dmin|[Uu]serlinks)\\|([^}]+)\\}\\}";

	private ArrayList<String> caseList = new ArrayList<String>();
	private HashMap<String, ArrayList<String>> warningLog = new HashMap<String, ArrayList<String>>();
	private HashMap<String, HashMap<String, LimitData>> overrides = new HashMap<String, HashMap<String, LimitData>>();
	private boolean changesMade = false;

	public HACBEvidenceModule(Wiki wiki, boolean noEditMode) {
		super(wiki, noEditMode);
	}

	@Override
	public void run() throws NewMessagesException {
		// Get configuration information
		getCases();
		// ArrayList<String> cases = caseList;
		getConfiguration();
		LimitData info = new LimitData();
		LimitData partyInfo = new LimitData(true);
		getCasePartyOverrides();
		// HashMap<String, HashMap<String, LimitData>> overrideList = overrides;
		getWarningLog();

		String lengthReport = "Currently using the following default settings for parties:\n"
				+ "* Word limit: "
				+ (partyInfo.getWordLimit() == Integer.MAX_VALUE ? "Disabled"
						: partyInfo.getWordLimit() + " Tolerance: "
								+ partyInfo.getWordTolerance())
				+ "\n"
				+ "* Diff limit: "
				+ (partyInfo.getDiffLimit() == Integer.MAX_VALUE ? "Disabled"
						: partyInfo.getDiffLimit() + " Tolerance: "
								+ partyInfo.getDiffTolerance())
				+ "\n"
				+ "* Link Limit: "
				+ (partyInfo.getLinkLimit() == Integer.MAX_VALUE ? "Disabled"
						: partyInfo.getLinkLimit() + " Tolerance: "
								+ partyInfo.getLinkTolerance()) + "\n\n";

		lengthReport += "Currently using the following default settings for all other users:\n"
				+ "* Word limit: "
				+ (info.getWordLimit() == Integer.MAX_VALUE ? "Disabled" : info
						.getWordLimit()
						+ " Tolerance: "
						+ info.getWordTolerance())
				+ "\n"
				+ "* Diff limit: "
				+ (info.getDiffLimit() == Integer.MAX_VALUE ? "Disabled" : info
						.getDiffLimit()
						+ " Tolerance: "
						+ info.getDiffTolerance())
				+ "\n"
				+ "* Link Limit: "
				+ (info.getLinkLimit() == Integer.MAX_VALUE ? "Disabled" : info
						.getLinkLimit()
						+ " Tolerance: "
						+ info.getLinkTolerance()) + "\n\n";

		// Begin run
		for (String caseName : caseList) {
			checkForNewMessages();
			lengthReport += processCase(caseName);
		}

		// Update report, even if stopped mid-run
		if (changesMade) {
			editLengthReport(lengthReport);
		}
	}

	/**
	 * Provides a word count given the text within a section. Does not count
	 * content within headers or templates, and discounts external links (the
	 * link part only) and targets of internal links
	 * 
	 * @param text
	 *            the text of a section
	 * @return a word count of the section
	 */
	private int countWords(String text) {
		text = stripLists(text);
		text = stripNewlines(text);
		text = stripHeaders(text);
		text = stripHTML(text);
		text = stripHAT(text);
		text = stripTemplates(text);
		text = stripExternalLinks(text);
		text = cleanInternalLinks(text);
		text = stripTimestamps(text);
		text = trimWhitespace(text);
		text = text.trim();

		String[] words = text.split("\\s"); // Split at whitespace
		int count = 0;

		for (int i = 0; i < words.length; i++) {
			// only count if it contains at least one alphanumeric character
			if (words[i].matches(".*[A-Za-z0-9].*")) {
				count++;
			}
		}

		return count;
	}

	/**
	 * Uses a regex to strip all list/indentation formatting
	 * 
	 * @param text
	 *            the original string
	 * @return the original text with list formatting removed
	 */
	private String stripLists(String text) {
		return text.replaceAll("\n[\\*#:]*", "\n ");
	}

	/**
	 * Uses a regex to strip all newlines
	 * 
	 * @param text
	 *            the original string
	 * @return the original text on a single line
	 */
	private String stripNewlines(String text) {
		return text.replaceAll("\n", " ");
	}

	/**
	 * Uses a regex to strip all headers down to level 6
	 * 
	 * @param text
	 *            the original string
	 * @return the original text with subheaders removed
	 */
	private String stripHeaders(String text) {
		String returnText = text.replaceAll("======[^=]*======", " ");
		returnText = returnText.replaceAll("=====[^=]*=====", " ");
		returnText = returnText.replaceAll("====[^=]*====", " ");
		returnText = returnText.replaceAll("===[^=]*===", " ");
		returnText = returnText.replaceAll("==[^=]*==", " ");

		return returnText;
	}

	/**
	 * Uses a regex to strip all HTML tags
	 * 
	 * @param text
	 *            the original string
	 * @return the original text with HTML removed
	 */
	private String stripHTML(String text) {
		return text.replaceAll("<.*?>", "");
	}

	/**
	 * Uses a regex to strip out all links posted in external link format:
	 * [http://link.com link title] --> link title] http://link.com --> empty
	 * string
	 * 
	 * @param text
	 *            the original string
	 * @return the original text with external links removed
	 */
	private String stripExternalLinks(String text) {
		String returnText = text.replaceAll("[^\\[]\\[[^\\[][^\\s]*", " ");
		returnText = returnText.replaceAll("http://[^\\s]*", " ");
		return returnText;
	}

	/**
	 * Uses a regex to strip out the targets of internal links [[target|text]]
	 * --> text]] [[text]] --> [[text]]
	 * 
	 * @param text
	 *            the original string
	 * @return the original text with internal link targets removed
	 */
	private String cleanInternalLinks(String text) {
		return text.replaceAll("\\[\\[[^\\]]*\\|", " ");
	}

	/**
	 * Uses a regex to remove all templates
	 * 
	 * @param text
	 *            the original string
	 * @return the original string absent all templates
	 */
	private String stripTemplates(String text) {
		// attempt to preserve visible bits of "diff" templates:
		while (text.contains("{{diff")) {
			int index1 = text.indexOf("{{diff");
			int index2 = text.indexOf("}}", index1);
			String diffTemplate = text.substring(index1, index2 + 2);
			index2 = diffTemplate.indexOf("}}");
			// dummy external link that gets stripped out when counting words
			// but counts as a diff when counting diffs and links
			String replacementText = "http://en.wikipedia.org/w/index.php?title=foo&diff=123 ";
			// annoyingly there are two forms of this template, one of which has
			// optional args
			if (diffTemplate.contains("diff2")) {
				int pipe1 = diffTemplate.indexOf("|");
				int pipe2 = diffTemplate.indexOf("|", pipe1 + 1);
				int pipe3 = (pipe2 == -1 ? -1 : diffTemplate.indexOf("|",
						pipe2 + 1));
				if (pipe2 != -1) {
					if (pipe3 != -1) {
						replacementText += diffTemplate
								.substring(pipe3, index2);
					} else {
						replacementText += diffTemplate
								.substring(pipe2, index2);
					}
				}
			} else {
				int pipe1 = diffTemplate.indexOf("|");
				int pipe2 = diffTemplate.indexOf("|", pipe1 + 1);
				int pipe3 = diffTemplate.indexOf("|", pipe2 + 1);
				int pipe4 = diffTemplate.indexOf("|", pipe3 + 1);
				String display = diffTemplate.substring(pipe4, index2);
				display = display.replace("label=", "");
				display = display.replace("|", "");
				replacementText += display;
			}
			text = text.replace(diffTemplate, replacementText);
		}
		return text.replaceAll("\\{\\{[^\\}]*\\}\\}[^\\s]*", " ");
	}

	private String stripHAT(String text) {
		return text
				.replaceAll(
						"\\{\\{(hat|hidden archive top)\\}\\}.*?\\{\\{(hab|hidden archive bottom)\\}\\}",
						" ");
	}

	/**
	 * Uses a regex to remove timestamps placed by ~~~~ or ~~~~~
	 * 
	 * @param text
	 *            the original string
	 * @return the original string absent all timestamps
	 */
	private String stripTimestamps(String text) {
		return text
				.replaceAll(
						"[0-2]\\d:[0-5]\\d, [1-3]?\\d [A-S][a-y]{2,8} 20[0-9]{2} \\(UTC\\)",
						" ");
	}

	/**
	 * Uses a regex to replace repeated whitespace characters with a single
	 * space
	 * 
	 * @param text
	 *            the original string
	 * @return the original string with duplicate whitespace replaced by a
	 *         single space
	 */
	private String trimWhitespace(String text) {
		return text.replaceAll("\\s\\s+", " ");
	}

	/**
	 * Returns a diff count for a section
	 * 
	 * @param text
	 *            the content of a section
	 * @return the number of diffs in the section
	 */
	private int countDiffs(String text) {
		text = stripNewlines(text);
		text = stripHAT(text);
		text = stripTemplates(text);
		text = stripHTML(text);
		if (text.contains("diff")) {
			String[] diffs = text.split(DIFF_REGEX);
			return diffs.length - 1; // split in this case results in one extra
		} else
			return 0;
	}

	/**
	 * Returns a count of non-diff links for a section
	 * 
	 * @param text
	 *            the content of a section
	 * @param diffCount
	 *            the number of diffs in the section
	 * @return the number of non-diff links
	 */
	private int countOtherLinks(String text, int diffCount) {
		text = stripNewlines(text);
		text = stripHAT(text);
		text = stripTemplates(text);
		text = stripHTML(text);
		if (text.contains("http")) {
			String[] links = text.split("http");
			return links.length - 1 - diffCount;
		} else
			return 0;
	}

	/**
	 * Gets a list of open cases from Template:ArbComOpenTasks
	 */
	private void getCases() {
		String templateContent = null;
		try {
			templateContent = this.wiki.getPageText(ARBCOMOPENTASKS);
		} catch (IOException e) {
			IOError(e);
		}
		if (templateContent != null) {
			while (templateContent.contains(OPEN_TASKS_LINE)) {
				int primaryIndex = templateContent.indexOf(OPEN_TASKS_LINE);
				int secondaryIndex = templateContent
						.indexOf("}}", primaryIndex);

				String lineEntry = templateContent.substring(primaryIndex,
						secondaryIndex);
				lineEntry.replace("\n", "|");
				lineEntry += "|";
				templateContent = templateContent.substring(secondaryIndex);

				int modeStart = lineEntry.indexOf("=",
						lineEntry.indexOf(OPEN_TASKS_MODE)) + 1;
				int modeEnd = lineEntry.indexOf("|", modeStart);
				String mode = lineEntry.substring(modeStart,
						(modeEnd < 0 ? lineEntry.length() - 1 : modeEnd));
				mode = mode.trim();

				int nameStart = lineEntry.indexOf("=",
						lineEntry.indexOf(OPEN_TASKS_NAME) + 1) + 1;
				String name = lineEntry.substring(nameStart,
						lineEntry.indexOf("|", nameStart));
				name = name.trim();
				if (mode.equalsIgnoreCase("review")) {
					name += REVIEW_SUFFIX;
				}
				if (!caseList.contains(name)) {
					caseList.add(name);
				}
			}
		}
	}

	private String extractUsername(String sectionName) throws IOException {
		String userName = sectionName.replace("Evidence presented by ", "");
		if (userName.contains("(uninvolved)"))
			userName = userName.replaceAll("\\s*\\(uninvolved\\)\\s*", "");
		if (userName.contains("(uninvolved editor)"))
			userName = userName.replaceAll("\\s*\\(uninvolved editor\\)\\s*",
					"");
		if (userName.contains("uninvolved editor"))
			userName = userName.replaceAll("\\s*uninvolved editor\\s*", "");
		if (userName.contains("uninvolved"))
			userName = userName.replaceAll("\\s*uninvolved\\s*", "");
		if (userName.contains("User:")) {
			userName = userName.replaceAll("\\s*(\\[\\[)?User:", "");
			userName = userName.replaceAll("\\]\\]\\s*", "");
		}
		if (!userName.contains("{") && !this.wiki.userExists(userName)
				&& userName.contains("(")) {
			// try removing parentheticals
			userName = userName.replaceAll("\\(.*\\)", "");
		}

		return userName.trim();
	}

	/**
	 * Checks to ensure that a section name meets the format expected by the bot
	 * 
	 * @param sectionName
	 *            the name of the section to check
	 * @return true if the section is of the form "Evidence presented by *"
	 */
	private boolean validSectionName(String sectionName) {
		if (sectionName.matches("Evidence (presented|submitted) by .*")) {
			return true;
		}
		return false;
	}

	/**
	 * Reviews the length of evidence sections in a given case and acts
	 * accordingly
	 * 
	 * @param caseName
	 *            the name of the case to be reviewed
	 * @return a length summary report for the case
	 */
	private String processCase(String caseName) {
		HashMap<Integer, String> sections = null;
		String evidencePage = ARB_CASE_PREFIX + caseName + EVIDENCE_SUFFIX;
		String lengthReport = "== Length reports for " + caseName
				+ ", ~~~~~ ==\n\n";

		try {
			sections = this.wiki.getLevelTwoHeaders(evidencePage);

			if (sections != null && !sections.isEmpty()) {
				for (int sectionNum : sections.keySet()) {
					String sectionName = sections.get(sectionNum);
					String userName = extractUsername(sectionName);
					userName = decodeHTMLEntities(userName);

					if (!sectionName.matches(".*\\{.*\\}.*")
							&& validSectionName(sectionName)) {
						String sectionText = this.wiki.getSectionText(
								evidencePage, sectionNum);

						int wordCount = countWords(sectionText);
						int diffCount = countDiffs(sectionText);
						int linkCount = countOtherLinks(sectionText, diffCount);

						lengthReport += compileLengthReport(caseName,
								sectionName, userName, wordCount, diffCount,
								linkCount);

						updateSectionCount(sectionNum, sectionText,
								sectionName, caseName, wordCount, diffCount,
								linkCount);

						checkLimitsAndWarnUser(caseName, userName, wordCount,
								diffCount, linkCount);

					} else if (!validSectionName(sectionName)) {
						String sectionText = this.wiki.getSectionText(
								evidencePage, sectionNum);

						markInvalidSection(evidencePage, sectionName,
								sectionNum, sectionText);
					}
				}
			}

		} catch (IOException e) {
			IOError(e);
		}

		lengthReport += "\n\n";

		return lengthReport;
	}

	private void markInvalidSection(String evidencePage, String sectionName,
			int sectionNum, String sectionText) {

		String template = INVALID_SECTION_TEMPLATE;

		if (!noEditMode) {
			if (!warningLog.containsKey(sectionName)) {
				recordToWarningLog(sectionName, evidencePage);

				if (sectionText
						.contains("User:HersfoldArbClerkBot/Length header")) {
					// remove template
					int index1 = sectionText
							.indexOf("{{User:HersfoldArbClerkBot/Length");
					int index2 = sectionText.indexOf("}}", index1) + 2;
					String oldTemp = sectionText.substring(index1, index2);
					if (!oldTemp.equalsIgnoreCase(template)) {
						sectionText = sectionText
								.replaceAll(
										"\\{\\{User:HersfoldArbClerkBot/Length header\\|.*?\\}\\}",
										"");
					}
					changesMade = true;
				}

				sectionName = sectionName.replace("(", "\\(");
				sectionName = sectionName.replace(")", "\\)");
				sectionText = sectionText.replaceFirst("==\\s*" + sectionName
						+ "\\s*==", "== " + sectionName + " ==\n" + template
						+ "\n");

				boolean notdone = true;
				while (notdone) {
					try {
						this.wiki.edit(evidencePage, sectionText,
								INVALID_SECTION_SUMMARY, false,
								sectionNum);
						notdone = false;
					} catch (LoginException e) {
						try {
							HersfoldArbClerkBot.attemptLogin(e,
									"updateSectionCount");
						} catch (IOException ioe) {
							IOError(ioe);
							notdone = false;
						}
						notdone = true;
					} catch (IOException e) {
						IOError(e);
						notdone = false;
					}
				}
			}
		} else {
			System.out.println("In no edit mode, following edit aborted:");
			System.out.println(template);
			System.out.println("Evidence page: " + evidencePage + " Section: "
					+ sectionName);
		}
	}

	/**
	 * Edits a section of an Arbitration Evidence page to add a hatnote-like
	 * summary of the length of the evidence in the section
	 * 
	 * @param number
	 *            the section index number
	 * @param text
	 *            the content of the section
	 * @param sectionName
	 *            the name of the section
	 * @param caseName
	 *            the name of the case
	 * @param words
	 *            the number of words in the section
	 * @param diffs
	 *            the number of diffs in the section
	 * @param links
	 *            the number of links in the section
	 */
	private void updateSectionCount(int number, String text,
			String sectionName, String caseName, int words, int diffs, int links)
			throws IOException {
		sectionName = decodeHTMLEntities(sectionName);
		String userName = extractUsername(sectionName);

		LimitData info = getLimitData(userName, caseName);

		String template = "{{User:HersfoldArbClerkBot/Length header|word="
				+ words + "|diff=" + diffs + "|link=" + links;

		template += "|wLimit=" + info.getWordLimit() + "|dLimit="
				+ info.getDiffLimit() + "|lLimit=" + info.getLinkLimit();

		template += "}}";

		if (text.contains("User:HersfoldArbClerkBot/Length header")) {
			int index1 = text.indexOf("{{User:HersfoldArb");
			int index2 = text.indexOf("}}", index1) + 2;
			String oldTemp = text.substring(index1, index2);
			if (!oldTemp.equalsIgnoreCase(template)) {
				text = text
						.replaceAll(
								"\\{\\{User:HersfoldArbClerkBot/Length header\\|.*?\\}\\}",
								template);
				changesMade = true;
			}
		} else {
			// sanitize for regex
			sectionName = sectionName.replace("(", "\\(");
			sectionName = sectionName.replace(")", "\\)");
			text = text.replaceFirst("==\\s*" + sectionName + "\\s*==", "== "
					+ sectionName + " ==\n" + template + "\n");
			changesMade = true;
		}

		boolean notdone = true;
		while (notdone) {
			if (noEditMode) {
				notdone = false;
				System.out.println("In no edit mode, following edit aborted:");
				System.out.println(template);
				System.out.println("Case: " + caseName + " Section: "
						+ sectionName);
			} else {
				try {
					this.wiki
							.edit(ARB_CASE_PREFIX + caseName + EVIDENCE_SUFFIX,
									text,
									UPDATE_SECTION_SUMMARY,
									true, number);
					notdone = false;
				} catch (LoginException e) {
					try {
						HersfoldArbClerkBot.attemptLogin(e,
								"updateSectionCount");
					} catch (IOException ioe) {
						IOError(ioe);
						notdone = false;
					}
					notdone = true;
				} catch (IOException e) {
					IOError(e);
					notdone = false;
				}
			}
		}

	}

	/**
	 * Retrieves a LimitData object for the given user and case. If no
	 * applicable overrides can be found, returns the defaults.
	 * 
	 * @param userName
	 *            the user being checked
	 * @param caseName
	 *            the case being checked
	 * @return a LimitData object
	 */
	private LimitData getLimitData(String userName, String caseName) {
		LimitData info = null;
		if (overrides.containsKey(userName)) {
			info = overrides.get(userName).get(caseName);
			if (info == null) {
				info = overrides.get(userName).get("all");
			}
		} else if (overrides.containsKey("all")) {
			info = overrides.get("all").get(caseName);
		}
		if (info == null) {
			info = new LimitData();
		}
		return info;
	}

	/**
	 * Compiled the length summary report for this user in this case.
	 * 
	 * @param caseName
	 *            the case being reviewed
	 * @param sectionName
	 *            the title of the user's evidence section
	 * @param userName
	 *            the name of the user
	 * @param wordCount
	 *            the user's word count
	 * @param diffCount
	 *            the user's diff count
	 * @param linkCount
	 *            the user's link count
	 * @return a portion of the length summary report
	 */
	private String compileLengthReport(String caseName, String sectionName,
			String userName, int wordCount, int diffCount, int linkCount) {
		String lengthReport = "";

		LimitData info = getLimitData(userName, caseName);
		boolean override = info.isOverride();

		lengthReport += "* '''[[" + ARB_CASE_PREFIX + caseName
				+ EVIDENCE_SUFFIX + "#" + sectionName;
		lengthReport += "|" + userName + "]]''' ([[User talk:" + userName
				+ "|user talk page]])\n";
		if (wordCount > info.getWordLimit() * info.getWordTolerance()) {
			lengthReport += "**'''{{red|Word count: " + wordCount + "}}'''";
		} else if (wordCount > info.getWordLimit()) {
			lengthReport += "**'''Word count: " + wordCount + "'''";
		} else {
			lengthReport += "**Word count: " + wordCount;
		}
		if (override) {
			lengthReport += " ''(Custom limits: " + info.getWordLimit() + "/"
					+ info.getWordTolerance() + ")''";
		}
		if (diffCount > info.getDiffLimit() * info.getDiffTolerance()) {
			lengthReport += "\n**'''{{red|Diff count: " + diffCount + "}}'''";
		} else if (diffCount > info.getDiffLimit()) {
			lengthReport += "\n**'''Diff count: " + diffCount + "'''";
		} else {
			lengthReport += "\n**Diff count: " + diffCount;
		}
		if (override) {
			lengthReport += " ''(Custom limits: " + info.getDiffLimit() + "/"
					+ info.getDiffTolerance() + ")''";
		}
		if (linkCount > info.getLinkLimit() * info.getLinkTolerance()) {
			lengthReport += "\n**'''{{red|Link count: " + linkCount + "}}'''";
		} else if (linkCount > info.getLinkLimit()) {
			lengthReport += "\n**'''Link count: " + linkCount + "'''";
		} else {
			lengthReport += "\n**Link count: " + linkCount + "";
		}
		if (override) {
			lengthReport += " ''(Custom limits: " + info.getLinkLimit() + "/"
					+ info.getLinkTolerance() + ")''";
		}
		lengthReport += "\n";

		return lengthReport;
	}

	/**
	 * Checks to see if a user has exceeded their limits, and if so, issues a
	 * one-time-per-case warning on their talk page.
	 * 
	 * @param caseName
	 *            the case being reviewed
	 * @param userName
	 *            the user being checked
	 * @param words
	 *            the number of words in the user's evidence
	 * @param diffs
	 *            the number of diffs in the user's evidence
	 * @param links
	 *            the number of links in the user's evidence
	 */
	private void checkLimitsAndWarnUser(String caseName, String userName,
			int words, int diffs, int links) {
		LimitData info = getLimitData(userName, caseName);

		if (words > info.getWordLimit() * info.getWordTolerance()
				|| diffs > info.getDiffLimit() * info.getDiffTolerance()
				|| links > info.getLinkLimit() * info.getLinkTolerance()) {
			if (warningLog.containsKey(userName)) {
				ArrayList<String> cases = warningLog.get(userName);
				if (cases.contains(caseName)) {
					System.out.println("Notice already given to " + userName
							+ " for " + caseName + ", aborting.");
				} else {
					cases.add(caseName);
					recordToWarningLog(userName, caseName);
				}
			} else {
				if (noEditMode) {
					System.out.println("Notice to " + userName + " for case "
							+ caseName + " aborted - no edit mode.");
					return;
				} else {
					String pageContent = null;
					try {
						pageContent = this.wiki.getPageText("User talk:"
								+ userName);
					} catch (IOException e) {
						IOError(e);
					}
					boolean notdone = true;
					while (notdone) {
						try {
							if (noExclusions(pageContent)) {
								this.wiki.newSection("User talk:" + userName,
										USER_NOTICE_HEADER,
										USER_NOTICE_TEMPLATE + "|case="
												+ caseName + "|words=" + words
												+ "|diffs=" + diffs + "|links="
												+ links + "}}", false);
							}
							notdone = false;

							ArrayList<String> cases = new ArrayList<String>();
							cases.add(caseName);
							warningLog.put(userName, cases);

							recordToWarningLog(userName, caseName);
						} catch (LoginException e) {
							try {
								HersfoldArbClerkBot.attemptLogin(e,
										"checkLimitsAndWarnUser");
							} catch (IOException ioe) {
								IOError(ioe);
								notdone = false;
							}
							notdone = true;
						} catch (IOException e) {
							IOError(e);
							notdone = false;
						}
					}
				}
			}
		}
	}

	/**
	 * Adds a user/case pair to the warning log file for later retrieval
	 * 
	 * @param username
	 *            the user being warned
	 * @param caseName
	 *            the case the user is being warned for
	 */
	private void recordToWarningLog(String username, String caseName) {
		File logFile = new File(WARNING_LOG);

		try {
			if (!logFile.exists()) {
				logFile.createNewFile();
			}
			if (!logFile.canWrite()) {
				throw new IOException("Cannot write to warning log file "
						+ WARNING_LOG);
			}
			PrintStream output = new PrintStream(new FileOutputStream(logFile,
					true));

			output.append(username + ">>>" + caseName + "\n");
			output.flush();
			output.close();
		} catch (IOException e) {
			IOError(e);
		}
	}

	private void getCasePartyOverrides() {
		for (String caseName : caseList) {
			LimitData partyData = new LimitData(true);
			String mainCasePage = ARB_CASE_PREFIX + caseName;
			String partySection = "";
			try {
				partySection = this.wiki.getSectionText(mainCasePage, 1);
			} catch (IOException e) {
				IOError(e);
			}

			Pattern regex = Pattern.compile(USER_TEMPLATE_REGEX);
			Matcher matcher = regex.matcher(partySection);

			while (matcher.find()) {
				String user = matcher.group(2);
				HashMap<String, LimitData> caseOverrides = overrides.get(user);
				if (caseOverrides == null) {
					caseOverrides = new HashMap<String, LimitData>();
					caseOverrides.put(caseName, partyData);
					overrides.put(user, caseOverrides);
				} else {
					if (caseOverrides.get(caseName) == null) {
						caseOverrides.put(caseName, partyData);
					}
					// else allow the provided overrides to stand
				}
			}
		}
	}

	/**
	 * Gets configuration information from the bot's config page onwiki
	 */
	private void getConfiguration() {
		String config = null;
		int wordLimit, diffLimit, linkLimit;
		double wordTolerance, diffTolerance, linkTolerance;

		try {
			config = this.wiki.getPageText(CONFIGURATION_PAGE);
		} catch (IOException e) {
			IOError(e);
		}

		if (config != null) {
			// Get word limit
			int index1 = config.indexOf("*WORD_LENGTH=")
					+ "*WORD_LENGTH=".length();
			int index2 = config.indexOf("\n", index1);
			// ensure page is not corrupted
			if (index1 != "*WORD_LENGTH=".length() - 1) {
				wordLimit = Integer.parseInt(config.substring(index1, index2));
				if (wordLimit == -1) {
					LimitData.setDefaultWordLimit(Integer.MAX_VALUE);
					LimitData.setDefaultWordTolerance(1.00);
				} else {
					index1 = config.indexOf("*WORD_TOLERANCE=")
							+ "*WORD_TOLERANCE=".length();
					index2 = config.indexOf("\n", index1);
					if (index1 != "*WORD_TOLERANCE=".length() - 1) {
						wordTolerance = Double.parseDouble(config.substring(
								index1, index2));
						LimitData.setDefaultWordTolerance(wordTolerance);
					} else {
						// Default already set
					}
					LimitData.setDefaultWordLimit(wordLimit);
				}
			} else {
				// Do nothing, defaults already set
			}

			// Get diff limit
			index1 = config.indexOf("*DIFF_COUNT=") + "*DIFF_COUNT=".length();
			index2 = config.indexOf("\n", index1);
			// ensure page is not corrupted
			if (index1 != "*DIFF_COUNT=".length() - 1) {
				diffLimit = Integer.parseInt(config.substring(index1, index2));
				if (diffLimit == -1) {
					LimitData.setDefaultDiffLimit(Integer.MAX_VALUE);
					LimitData.setDefaultDiffTolerance(1.00);
				} else {
					index1 = config.indexOf("*DIFF_TOLERANCE=")
							+ "*DIFF_TOLERANCE=".length();
					index2 = config.indexOf("\n", index1);
					if (index1 != "*DIFF_TOLERANCE=".length() - 1) {
						diffTolerance = Double.parseDouble(config.substring(
								index1, index2));
						LimitData.setDefaultDiffTolerance(diffTolerance);
					} else {
						// Default already set
					}
					LimitData.setDefaultDiffLimit(diffLimit);
				}
			} else {
				// Do nothing, defaults already set
			}

			// Get link limit
			index1 = config.indexOf("*LINK_COUNT=") + "*LINK_COUNT=".length();
			index2 = config.indexOf("\n", index1);
			// ensure page is not corrupted
			if (index1 != "*LINK_COUNT=".length() - 1) {
				linkLimit = Integer.parseInt(config.substring(index1, index2));
				if (linkLimit == -1) {
					LimitData.setDefaultLinkLimit(Integer.MAX_VALUE);
					LimitData.setDefaultLinkTolerance(1.00);
				} else {
					index1 = config.indexOf("*LINK_TOLERANCE=")
							+ "*LINK_TOLERANCE=".length();
					index2 = config.indexOf("\n", index1);
					if (index1 != "*LINK_TOLERANCE=".length() - 1) {
						linkTolerance = Double.parseDouble(config.substring(
								index1, index2));
						LimitData.setDefaultLinkTolerance(linkTolerance);
					} else {
						// Default already set
					}
					LimitData.setDefaultLinkLimit(linkLimit);
				}
			} else {
				// Do nothing, defaults already set
			}

			// Get party word limit
			index1 = config.indexOf("*PARTY_LENGTH=")
					+ "*PARTY_LENGTH=".length();
			index2 = config.indexOf("\n", index1);
			// ensure page is not corrupted
			if (index1 != "*PARTY_LIMIT=".length() - 1) {
				wordLimit = Integer.parseInt(config.substring(index1, index2));
				if (wordLimit == -1) {
					LimitData.setDefaultPartyWordLimit(Integer.MAX_VALUE);
				} else {
					LimitData.setDefaultPartyWordLimit(wordLimit);
				}
			} else {
				// Do nothing, defaults already set
			}

			// Get party diff limit
			index1 = config.indexOf("*PARTY_DIFF_COUNT=")
					+ "*PARTY_DIFF_COUNT=".length();
			index2 = config.indexOf("\n", index1);
			// ensure page is not corrupted
			if (index1 != "*PARTY_DIFF_COUNT=".length() - 1) {
				diffLimit = Integer.parseInt(config.substring(index1, index2));
				if (diffLimit == -1) {
					LimitData.setDefaultPartyDiffLimit(Integer.MAX_VALUE);
				} else {
					LimitData.setDefaultPartyDiffLimit(diffLimit);
				}
			} else {
				// Do nothing, defaults already set
			}

			// Get party link limit
			index1 = config.indexOf("*PARTY_LINK_COUNT=")
					+ "*PARTY_LINK_COUNT=".length();
			index2 = config.indexOf("\n", index1);
			// ensure page is not corrupted
			if (index1 != "*PARTY_LINK_COUNT=".length() - 1) {
				linkLimit = Integer.parseInt(config.substring(index1, index2));
				if (linkLimit == -1) {
					LimitData.setDefaultPartyLinkLimit(Integer.MAX_VALUE);
				} else {
					LimitData.setDefaultPartyLinkLimit(linkLimit);
				}
			} else {
				// Do nothing, defaults already set
			}

			while (config.contains("OVERRIDE")) {
				index1 = config.indexOf("*OVERRIDE=") + "*OVERRIDE=".length();
				index2 = config.indexOf("\n", index1);
				String overrideArgs = config.substring(index1, index2);
				config = config.substring(index2);

				// We will assume that everything here is in order
				String[] args = overrideArgs.split("\\|");
				String username = "", casename = "";
				try {
					if (args.length == 8) {
						username = args[0].trim();
						casename = args[1];
						wordLimit = Integer.parseInt(args[2]);
						diffLimit = Integer.parseInt(args[3]);
						linkLimit = Integer.parseInt(args[4]);
						wordTolerance = Double.parseDouble(args[5]);
						diffTolerance = Double.parseDouble(args[6]);
						linkTolerance = Double.parseDouble(args[7].trim());

						if (wordLimit == -1) {
							wordLimit = Integer.MAX_VALUE;
							wordTolerance = 1.0;
						}
						if (diffLimit == -1) {
							diffLimit = Integer.MAX_VALUE;
							diffTolerance = 1.0;
						}
						if (linkLimit == -1) {
							linkLimit = Integer.MAX_VALUE;
							linkTolerance = 1.0;
						}

						LimitData info = new LimitData(wordLimit, diffLimit,
								linkLimit, wordTolerance, diffTolerance,
								linkTolerance);
						HashMap<String, LimitData> caseEntry = null;
						if (overrides.containsKey(username)) {
							caseEntry = overrides.get(username);
						} else {
							caseEntry = new HashMap<String, LimitData>();
						}
						caseEntry.put(casename, info);
						overrides.put(username, caseEntry);
					} else {
						throw new Exception("Wrong number of arguments");
					}
				} catch (Exception e) {
					// Handles number format exceptions
					// Ignores override and records notice in log
					System.err.println("WARNING: Override for " + username
							+ " on case " + casename + " malformed. Ignoring.");
					System.err.println(e.getMessage());
					e.printStackTrace(System.err);
				}
			}
		}
	}

	/**
	 * Loads information from the warning log file regarding which users have
	 * received warnings for which cases
	 */
	private void getWarningLog() {
		File logFile = new File(WARNING_LOG);

		try {
			if (logFile.exists()) {
				if (logFile.canRead()) {
					Scanner input = new Scanner(new FileInputStream(logFile));

					while (input.hasNextLine()) {
						String line = input.nextLine();
						String[] data = line.split(">>>");
						String user = data[0];
						String caseName = data[1];

						if (!warningLog.containsKey(user)) {
							warningLog.put(user, new ArrayList<String>());
						}
						warningLog.get(user).add(caseName);
					}

					input.close();
				} else {
					throw new IOException("Cannot read from warning log file "
							+ WARNING_LOG);
				}
			}
		} catch (IOException e) {
			IOError(e);
		}
	}

	/**
	 * Edits the bot's length report summary page in its userspace
	 * 
	 * @param lengthReport
	 *            the summary of length counts across all cases
	 */
	private void editLengthReport(String lengthReport) {
		String summary = LENGTH_REPORT_SUMMARY;
		for (String caseName : caseList) {
			summary += caseName + "  ";
		}

		boolean notdone = true;
		while (notdone) {
			try {
				this.wiki.edit(LENGTH_REPORT_PAGE, lengthReport, summary, false);
				notdone = false;
			} catch (LoginException e) {
				try {
					HersfoldArbClerkBot.attemptLogin(e, "editLengthReport");
				} catch (IOException ioe) {
					IOError(ioe);
				}
			} catch (IOException e) {
				IOError(e);
				notdone = false;
			}
		}
	}

	private static class LimitData {
		private static int DEFAULT_WORD_LIMIT = 500;
		private static int DEFAULT_DIFF_LIMIT = 50;
		private static int DEFAULT_LINK_LIMIT = Integer.MAX_VALUE;

		private static int DEFAULT_PARTY_WORD_LIMIT = 1000;
		private static int DEFAULT_PARTY_DIFF_LIMIT = 100;
		private static int DEFAULT_PARTY_LINK_LIMIT = Integer.MAX_VALUE;

		private static double DEFAULT_WORD_TOLERANCE = 1.10;
		private static double DEFAULT_DIFF_TOLERANCE = 1.10;
		private static double DEFAULT_LINK_TOLERANCE = 1.00;

		private int wordLimit = DEFAULT_WORD_LIMIT;
		private int diffLimit = DEFAULT_DIFF_LIMIT;
		private int linkLimit = DEFAULT_LINK_LIMIT;
		private double wordTolerance = DEFAULT_WORD_TOLERANCE;
		private double diffTolerance = DEFAULT_DIFF_TOLERANCE;
		private double linkTolerance = DEFAULT_LINK_TOLERANCE;
		private boolean override = false;

		public LimitData() {
			// go with defaults
		}

		public LimitData(boolean party) {
			if (party) {
				this.wordLimit = DEFAULT_PARTY_WORD_LIMIT;
				this.diffLimit = DEFAULT_PARTY_DIFF_LIMIT;
				this.linkLimit = DEFAULT_PARTY_LINK_LIMIT;
				// remaining are defaults
			}
			// else go with defaults
		}

		public LimitData(int wordL, int diffL, int linkL, double wordT,
				double diffT, double linkT) {
			wordLimit = wordL;
			diffLimit = diffL;
			linkLimit = linkL;
			wordTolerance = wordT;
			diffTolerance = diffT;
			linkTolerance = linkT;
			override = true;
		}

		public boolean equals(Object otherObj) {
			if (otherObj instanceof LimitData) {
				LimitData otherLD = (LimitData) otherObj;
				return this.getDiffLimit() == otherLD.getDiffLimit()
						&& this.getWordLimit() == otherLD.getWordLimit()
						&& this.getLinkLimit() == otherLD.getLinkLimit()
						&& this.getDiffTolerance() == otherLD
								.getDiffTolerance()
						&& this.getWordTolerance() == otherLD
								.getWordTolerance()
						&& this.getLinkTolerance() == otherLD
								.getLinkTolerance();
			}
			return false;
		}

		public boolean isOverride() {
			return override;
		}

		public static void setDefaultWordLimit(int defaultWordLimit) {
			DEFAULT_WORD_LIMIT = defaultWordLimit;
		}

		public static void setDefaultDiffLimit(int defaultDiffLimit) {
			DEFAULT_DIFF_LIMIT = defaultDiffLimit;
		}

		public static void setDefaultLinkLimit(int defaultLinkLimit) {
			DEFAULT_LINK_LIMIT = defaultLinkLimit;
		}

		public static void setDefaultPartyWordLimit(int defaultPartyWordLimit) {
			DEFAULT_PARTY_WORD_LIMIT = defaultPartyWordLimit;
		}

		public static void setDefaultPartyDiffLimit(int defaultPartyDiffLimit) {
			DEFAULT_PARTY_DIFF_LIMIT = defaultPartyDiffLimit;
		}

		public static void setDefaultPartyLinkLimit(int defaultPartyLinkLimit) {
			DEFAULT_PARTY_LINK_LIMIT = defaultPartyLinkLimit;
		}

		public static void setDefaultWordTolerance(double defaultWordTolerance) {
			DEFAULT_WORD_TOLERANCE = defaultWordTolerance;
		}

		public static void setDefaultDiffTolerance(double defaultDiffTolerance) {
			DEFAULT_DIFF_TOLERANCE = defaultDiffTolerance;
		}

		public static void setDefaultLinkTolerance(double defaultLinkTolerance) {
			DEFAULT_LINK_TOLERANCE = defaultLinkTolerance;
		}

		public int getWordLimit() {
			return wordLimit;
		}

		public int getDiffLimit() {
			return diffLimit;
		}

		public int getLinkLimit() {
			return linkLimit;
		}

		public double getWordTolerance() {
			return wordTolerance;
		}

		public double getDiffTolerance() {
			return diffTolerance;
		}

		public double getLinkTolerance() {
			return linkTolerance;
		}

		public String toString() {
			StringBuilder string = new StringBuilder("Words: ");
			string.append(wordLimit);
			string.append("/");
			string.append(wordTolerance);
			string.append(" Diffs: ");
			string.append(diffLimit);
			string.append("/");
			string.append(diffTolerance);
			string.append(" Links: ");
			string.append(linkLimit);
			string.append("/");
			string.append(linkTolerance);

			return string.toString();
		}
	}
}
