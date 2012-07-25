/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.aeten.google.apps.mail;

import com.google.code.com.sun.mail.imap.IMAPFolder;
import com.google.code.com.sun.mail.imap.IMAPMessage;
import com.google.code.com.sun.mail.imap.IMAPSSLStore;
import com.google.code.javax.mail.*;
import com.google.code.javax.mail.search.GmailThreadIDTerm;
import com.google.code.javax.mail.search.OrTerm;
import com.google.code.javax.mail.search.SearchTerm;
import com.google.code.samples.xoauth.XoauthAuthenticator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.DTD;
import javax.swing.text.html.parser.DocumentParser;
import javax.swing.text.html.parser.ParserDelegator;
import javax.swing.text.html.parser.TagElement;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import net.oauth.OAuthConsumer;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class TestThreadFetch {

	private static final String INBOX = "INBOX";
	private static final FetchProfile fp;
	private static final FetchProfile minimalFetchProfile;

	static {
		fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		fp.add(FetchProfile.Item.FLAGS);
		fp.add(IMAPFolder.FetchProfileItem.X_GM_THRID);
		fp.add(IMAPFolder.FetchProfileItem.X_GM_LABELS);
		minimalFetchProfile = new FetchProfile();
		minimalFetchProfile.add(IMAPFolder.FetchProfileItem.X_GM_THRID);
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 5) {
			throw  new IllegalArgumentException(TestThreadFetch.class.getSimpleName() + " takes 5 arguments (email consumerKey consumerSecret oauthToken oauthTokenSecret)");
		}
		String email = args[0];
		String consumerKey = args[1];
		String consumerSecret = args[2];
		String oauthToken = args[3];
		String oauthTokenSecret = args[4];

		OAuthConsumer consumer = new OAuthConsumer(null, consumerKey, consumerSecret, null);
		IMAPSSLStore imapSslStore = XoauthAuthenticator.connectToImap("imap.gmail.com",
				993,
				email,
				oauthToken,
				oauthTokenSecret,
				consumer,
				false);
		if (imapSslStore != null) {
			execute(imapSslStore, 10, email);
		}
	}

	public static void execute(IMAPSSLStore store, int threadsToShow, final String user) throws IOException, MessagingException {
		Map<Long, List<Message>> threads = null;
		IMAPFolder folder = null;
		try {
			folder = (IMAPFolder) store.getFolder(INBOX);
			if (folder != null) {
				threads = searchThreads(folder, threadsToShow);
				TreeMap<Long, Collection<Message>> threads_sorted = new TreeMap(threads);
				int mLen;
				ArrayList<Message> msgs;
				IMAPMessage youngest, oldest;
				for (Collection<Message> ms : threads_sorted.descendingMap().values()) {
					msgs = new ArrayList<>(ms);
					mLen = msgs.size();
					youngest = (IMAPMessage) msgs.get(mLen - 1);
					oldest = (IMAPMessage) msgs.get(0);
					System.out.println("Thread size: " + mLen);
					System.out.println("Subject: " + oldest.getSubject());
					System.out.println("Date: " + youngest.getReceivedDate());
					String summary = getText(youngest);
					Scanner scanner = new Scanner(summary);
					summary = "";
					while (scanner.hasNextLine()) {
						String line = scanner.nextLine().trim();
						String li = "";
						for (String l : line.split("\\s+|[-_]{4,}+")) {
							if (li.length() > 0) {
								li += " ";
							}
							li += l;
						}
						line = li;
						line.replace('\t', ' ');

						if (summary.length() > 0) {
							summary += " ";
						}
						summary += line;
						if (summary.length() > 160) {
							summary = summary.substring(0, 160) + "…";
							break;
						}
					}
					System.out.println("Summary: " + summary);
					String[] labels = youngest.getGoogleMessageLabels();
					if (labels != null) {
						for (String label : labels) {
							System.out.println("*" + label);
						}
					}
					System.out.println("Google Thread Id:" + Long.toHexString(youngest.getGoogleMessageThreadId()));
					System.out.println();
				}
			}
		} finally {
			if (folder.isOpen()) {
				folder.close(true);
			}
			if (store.isConnected()) {
				store.close();
			}
		}
	}

	private static String getText(Part part) throws MessagingException, IOException {
		Object content = part.getContent();
		if (content instanceof Multipart) {
			return getText((Multipart) content);
		}
		String type = part.getContentType().toLowerCase();
		if (type.startsWith("text")) {
			System.out.println("Mime: " + type.toLowerCase());
			System.out.println("Part type: " + part.getClass().getName());
			String text = "";
			if (type.startsWith("text/plain")) {
				text = content.toString();
			} else if (type.startsWith("text/html")) {
				text = content.toString();
				final List<String> textList = new ArrayList<>();
				new DocumentParser(DTD.getDTD("")) {

					boolean isInTag = false;

					@Override
					protected void handleStartTag(TagElement tag) {
						isInTag = true;
					}

					@Override
					protected void handleEndTag(TagElement tag) {
						isInTag = false;
					}

					@Override
					protected void handleText(char[] data) {
						if (isInTag) {
							String text = new String(data).trim();
							if (text.length() > 0) {
								textList.add(text);
							}
						}
					}
				}.parse(new StringReader(text), new HTMLEditorKit.ParserCallback(), true);

				text = "";
				for (String line : textList) {
					if (text.length() > 0) {
						text += "\n";
					}
					text += line;
				}
			}

			return text;
		}
		return null;
	}

	private static String getText(Multipart multipart) throws MessagingException, IOException {
		for (int i = 0; i < multipart.getCount(); i++) {
			BodyPart bodyPart = multipart.getBodyPart(i);
			Object content = bodyPart.getContent();
			if (content instanceof Multipart) {
				return getText(((Multipart) content));
			}
			String type = bodyPart.getContentType().toLowerCase();
			if (type.startsWith("text")) {
				return getText(bodyPart);
			}
		}
		return null;
	}

	public static Map<Long, List<Message>> searchThreads(final IMAPFolder folder, final int threadsToShow) throws MessagingException {
		LinkedHashMap<Long, List<Message>> threads = new LinkedHashMap<>();
		int messagesToFetch = threadsToShow * 4;
		boolean stop = Boolean.FALSE;
		int msgCount, first, last;
		if (folder != null) {
			folder.open(folder.READ_ONLY);
			msgCount = folder.getMessageCount();
			if (msgCount < messagesToFetch) {
				messagesToFetch = msgCount;
			}
			first = msgCount - messagesToFetch;
			if (first < 1) {
				first = 1;
			}
			last = msgCount;
			// Fetch messages in batches until desired thread count is reached (or INBOX is exhausted)
			while (threads.size() < threadsToShow && !stop) {
				threads = fetchThreads(folder, threadsToShow, first + 1, last, threads);
				last = first - 1;
				first = last - messagesToFetch;
				if (first < 1) {
					threads = fetchThreads(folder, threadsToShow, 1, last, threads);
					stop = Boolean.TRUE;
				}
			}
		}
		return threads;
	}

	private static LinkedHashMap<Long, List<Message>> fetchThreads(final IMAPFolder folder, final int threadsToShow, final int first, final int last, final LinkedHashMap<Long, List<Message>> threads) throws MessagingException {
		// Fetch batch of messages
		Message[] msgs = folder.getMessages(first, last);
		HashSet<Long> thrd_ids = new HashSet<>();
		folder.fetch(msgs, minimalFetchProfile);
		int alreadyFetchedThreads = threads.size();
		IMAPMessage im;
		long googleThrdId;
		// Loop through fetched messages looking for message threads that have not already been read
		for (int i = msgs.length - 1; i > 0; i--) {
			im = (IMAPMessage) msgs[i];
			googleThrdId = im.getGoogleMessageThreadId();
			if (thrd_ids.size() < (threadsToShow - alreadyFetchedThreads)) {
				if (!threads.containsKey(googleThrdId)) {
					thrd_ids.add(googleThrdId);
				}
			} else {
				break;
			}
		}
		// Create an array SearchTerm containing this batch of previously unread Google threads
		SearchTerm[] searchTerms = new SearchTerm[thrd_ids.size()];
		int c = 0;
		for (long thrId : thrd_ids) {
			searchTerms[c] = new GmailThreadIDTerm(thrId + "");
			c++;
		}
		if (searchTerms.length > 0) {
			// Fetch unread threads
			Message[] thrd_msgs = folder.search(new OrTerm(searchTerms));
			folder.fetch(thrd_msgs, fp);
			// Group individual fetched messages into message threads map
			for (Message msg : thrd_msgs) {
				long id = ((IMAPMessage) msg).getGoogleMessageThreadId();
				List<Message> messages = threads.get(id);
				if (messages == null) {
					messages = new ArrayList<>();
					threads.put(id, messages);
				}
				messages.add(msg);

			}
		}
		return threads;
	}
}