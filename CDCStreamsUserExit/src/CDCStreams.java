/****************************************************************************
 ** Licensed Materials - Property of IBM
 ** IBM InfoSphere Change Data Capture
 ** 5724-U70
 **
 ** (c) Copyright IBM Corp. 2001-2014 All rights reserved.
 **
 ** The following sample of source code ("Sample") is owned by International
 ** Business Machines Corporation or one of its subsidiaries ("IBM") and is
 ** copyrighted and licensed, not sold. You may use, copy, modify, and
 ** distribute the Sample in any form without payment to IBM.
 **
 ** The Sample code is provided to you on an "AS IS" basis, without warranty of
 ** any kind. IBM HEREBY EXPRESSLY DISCLAIMS ALL WARRANTIES, EITHER EXPRESS OR
 ** IMPLIED, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 ** MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. Some jurisdictions do
 ** not allow for the exclusion or limitation of implied warranties, so the above
 ** limitations or exclusions may not apply to you. IBM shall not be liable for
 ** any damages you suffer as a result of using, copying, modifying or
 ** distributing the Sample, even if IBM has been advised of the possibility of
 ** such damages.
 *****************************************************************************/
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.datamirror.ts.target.publication.userexit.*;

/*
 * This user exit facilitates replicating changes captured using InfoSphere CDC to an InfoSphere Streams 
 * application. Preferably, the user exit would be configured for a subscription going from a database to 
 * to one of the CDC database Java engines, including FlexRep, but it can also be configured for CDC Event Server,
 * albeit that it may be less efficient because every operation is followed by a commit with this engine.
 * 
 * Implementing the user exit must be done through the following steps:
 * - Copy all .class files to the <cdc_home>/lib directory of the target engine
 * - Copy the CDCStreams.properties to the <cdc_home> directory of the target engine; alternatively, you can
 *   copy it under a different name to facilitate different properties per subscription
 * - Modify the properties to match your Streams application's requirements
 * 
 * Once implemented, specify the user exit at the subscription level and at the before-insert, before-update and
 * before-delete operation user exit points. If you choose to use a different properties file as documented earlier,
 * specify this file at the subscription-level user exit point.
 * 
 * The user exit facilitates targeting a Streams application via the following mechanisms:
 * - Named pipe  - the user exit writes the changes, including the commit indicators and the handshakes to the
 *                 specified target (fifo) file.
 * - TCPSource   - the user exit connects to the Streams application via TCP/IP and the Streams application typically
 *                 receives the changes through the TCPSource operator. Commit indicators and handshakes are sent
 *                 but not enforced.
 * - CDCSource   - the user exit connects to the Streams application via TCP/IP and the Streams application receives
 *                 the changes through the CDCSource operator. Commit indicators and handshakes are sent to the
 *                 Streams application handshakes are enforced.
 * - Null        - Debugging only, changes are not sent to any target application. If debug=true, detail information
 *                 is written to the CDC instance logs.
 * 
 */
public class CDCStreams implements UserExitIF, SubscriptionUserExitIF {

	private boolean calledAtSubscriptionLevel = false;
	private boolean firstTime = true;
	private String emptyBeforeImage = "";
	private String emptyAfterImage = "";

	private final SimpleDateFormat ISO_DATEFORMAT = new SimpleDateFormat(
			"yyyy-MM-dd' 'HH:mm:ss.SSS'000'");

	// Context to be shared between all the instances of this class
	protected SubscriptionContext subscriptionContext;

	// Localized references to subscription-wide objects
	private UETrace trace;
	private UESettings settings;
	private SubscriptionEventPublisherIF eventPublisher;
	private StreamsWriter streamsWriter;
	private String publisherID;
	private String tablePath = null;
	private String tableName = null;
	private long currentTransactions;

	/**
	 * Subscription-level initialization.
	 * 
	 * This method is called once when the subscription is started and
	 * initializes the subscription context. Also, it ensures that the
	 * processSubscriptionEvent method is invoked before every commit.
	 * 
	 * @param eventPublisher
	 *            - Handle to the event publisher; this parameter can only be
	 *            used during this method to subscribe to certain events.
	 */
	public void init(SubscriptionEventPublisherIF eventPublisher)
			throws UserExitException {
		subscriptionContext = createContext(eventPublisher);
		calledAtSubscriptionLevel = true;

		// Initialize context variables for this instance of the class
		initContext();

		// Ensure that the user exit is called prior to commit
		trace.write("Subscription-level init() start for publisher "
				+ publisherID);
		eventPublisher.logEvent("Subscription-level user exit "
				+ this.getClass().getName() + " started.");
		eventPublisher.unsubscribeEvent(SubscriptionEventTypes.ALL_EVENTS);
		eventPublisher
				.subscribeEvent(SubscriptionEventTypes.BEFORE_PHYSICAL_COMMIT_EVENT);

		// Open the output stream to write the records to
		subscriptionContext.streamsWriter = new StreamsWriter();
		streamsWriter = subscriptionContext.streamsWriter;

		// Start the timer thread to flush the output on a regular basis
		subscriptionContext.timer = new Timer();
		new Thread(subscriptionContext.timer).start();

		trace.write("Subscription-level init() end");
	}

	/**
	 * Initializes the context for the subscription and this is shared within
	 * all object instances used in the subscription (table level and
	 * subscription level). This method is only called once at the subscription
	 * level. At the table level, the SubscriptionContext object is retrieved
	 * only.
	 * 
	 * @param eventPublisher
	 *            - The Subscription-level event
	 */
	protected SubscriptionContext createContext(
			SubscriptionEventPublisherIF eventPublisher) {
		SubscriptionContext context = new SubscriptionContext();
		context.eventPublisher = eventPublisher;
		context.publisherID = new String(eventPublisher.getSourceSystemID());
		context.settings = new UESettings(eventPublisher.getParameter());
		context.trace = new UETrace(context.settings.debug);
		context.trace.write("Context created for publisher ID "
				+ context.publisherID);

		eventPublisher.setUserExitSubscriptionContext(context);

		return context;
	}

	/**
	 * Initialize instance variables from the subscription context that was
	 * created during subscription initialization.
	 */
	private void initContext() {
		trace = subscriptionContext.trace;
		settings = subscriptionContext.settings;
		streamsWriter = subscriptionContext.streamsWriter;
		eventPublisher = subscriptionContext.eventPublisher;
		publisherID = subscriptionContext.publisherID;
	}

	/**
	 * Write message to subscription trace and subscription-level target event
	 * log.
	 * 
	 * @param message
	 *            Message to be sent to the subscription event log and log file
	 */
	private void logEvent(String message) {
		trace.writeAlways(message);
		eventPublisher.logEvent(message);
	}

	/**
	 * Executed when a subscription-level event is detected (commit). This
	 * method ensures that the output channel is flushed and that handshaking
	 * with the Streams application take place.
	 * 
	 * @param subscriptionEvent
	 *            - handle to subscription event
	 */
	public boolean processSubscriptionEvent(
			SubscriptionEventIF subscriptionEvent) throws UserExitException {
		trace.write("Subscription event: "
				+ getSubscriptionEventTypeAsString(subscriptionEvent
						.getEventType())
				+ ", commit reason: "
				+ getSubscriptionCommitReasonAsString(subscriptionEvent
						.getCommitReason()));

		// If there are pending operations or transactions, default to not
		// commit, otherwise allow committing of the bookmark
		boolean commit = false;
		if (subscriptionContext.currentTransactionOperations == 0
				&& currentTransactions == 0)
			commit = true;

		// If there are pending operations, send commit to Streams and increase
		// number of pending transactions.
		if (subscriptionContext.currentTransactionOperations > 0) {
			trace.write("Number of operations in current transaction (ID="
					+ subscriptionContext.currentTransactionID + "): "
					+ subscriptionContext.currentTransactionOperations);
			// Tell Streams application that a commit has taken place
			streamsWriter.doCommit(
					subscriptionContext.currentTransactionTimestamp,
					subscriptionContext.currentTransactionID);
			currentTransactions++;
			trace.write("Number of pending transactions: "
					+ currentTransactions);
			subscriptionContext.currentTransactionOperations = 0;
		}

		// Check if the handshake must be done
		if (currentTransactions >= settings.handshakeAfterMaxTransactions
				|| subscriptionContext.timer.isHandshakeDue()) {
			trace.write("Handshake will be done. Number of transactions: "
					+ currentTransactions + ", timed handshake: "
					+ subscriptionContext.timer.isHandshakeDue());
			commit = streamsWriter.doHandshake();
			currentTransactions = 0;
			subscriptionContext.timer.resetTimer();
		}

		streamsWriter.flushOutput();
		trace.write("Commit transaction(s): " + commit);
		return commit;
	}

	/**
	 * Table-level initialization.
	 * 
	 * This method is called once for every mapped table at subscription
	 * startup. It first retrieves the subscription context and then registers
	 * the events it wants to listen to.
	 * 
	 * @param eventPublisher
	 *            - Handle to engine environment information
	 */
	public void init(ReplicationEventPublisherIF eventPublisher)
			throws UserExitException {
		// Retrieve the subscription-level context
		subscriptionContext = (SubscriptionContext) eventPublisher
				.getUserExitSubscriptionContext();

		// If the subscription-level user exit was not configured, abend to
		// avoid NullPointerException
		if (subscriptionContext == null) {
			String errorMessage = "ERROR: Initialization of the user exit was not performed. "
					+ "The subscription-level user exit is probably not configured.";
			eventPublisher.logEvent(errorMessage);
			throw new UserExitException(errorMessage);
		}

		// Initialize local context variables
		initContext();

		trace.writeAlways("Table-level init() start");

		// Subscribe to Before-Insert/Update/Delete events
		eventPublisher.unsubscribeEvent(ReplicationEventTypes.ALL_EVENTS);
		eventPublisher
				.subscribeEvent(ReplicationEventTypes.BEFORE_INSERT_EVENT);
		eventPublisher
				.subscribeEvent(ReplicationEventTypes.BEFORE_UPDATE_EVENT);
		eventPublisher
				.subscribeEvent(ReplicationEventTypes.BEFORE_DELETE_EVENT);

		trace.writeAlways("Table-level init() end");
	}

	/**
	 * Executed when table-level event is detected (insert/update/delete). This
	 * method writes an entry for the table-level operation to the currently
	 * open output stream.
	 * 
	 * @param replicationEvent
	 *            - Handle to replication event
	 * @return false - This flag indicates whether the default operation should
	 *         be applied (true) or not (false). As we only want to send the
	 *         changes to a Streams application, the return value is always
	 *         false.
	 */
	public boolean processReplicationEvent(ReplicationEventIF replicationEvent)
			throws UserExitException {
		trace.write("processReplicationEvent() start");
		subscriptionContext.currentTransactionTimestamp = replicationEvent
				.getJournalHeader().getTimestamp();
		subscriptionContext.currentTransactionID = replicationEvent
				.getJournalHeader().getCommitID();
		String entryType = convertEntryType(replicationEvent.getJournalHeader()
				.getEntryType());
		String transactionUser = replicationEvent.getJournalHeader()
				.getUserName();

		DataRecordIF beforeImage = replicationEvent.getSourceBeforeData();
		DataRecordIF afterImage = replicationEvent.getSourceData();
		if (firstTime) {
			tablePath = replicationEvent.getJournalHeader().getLibrary();
			tableName = replicationEvent.getJournalHeader().getObjectName();
			DataRecordIF image = (afterImage != null) ? afterImage
					: beforeImage;
			// Generate the empty before image and empty after image based on
			// the number of table columns replicated
			for (int i = 1; i <= image.getColumnCount(); i++) {
				if (image.getColumnName(i).startsWith("&"))
					break;
				if (i != 1)
					emptyBeforeImage += settings.separator;
				emptyAfterImage += settings.separator;

			}
			trace.write("Empty image: " + emptyBeforeImage);
			firstTime = false;
		}
		trace.write("Table: " + tablePath + "." + tableName);
		trace.write("Timestamp: "
				+ subscriptionContext.currentTransactionTimestamp);
		trace.write("Transaction ID: "
				+ subscriptionContext.currentTransactionID);
		trace.write("Operation type: " + entryType);
		trace.write("User: " + transactionUser);

		// Prepare the journal information to be included in the output record,
		// respectively the fully qualified table name, the timestamp of the
		// commit, the transaction ID at the source, the type of operation and
		// finally the user who did the operation at the source
		String printLine = ("d" + settings.metadataSeparator + tablePath + "."
				+ tableName + settings.metadataSeparator
				+ subscriptionContext.currentTransactionTimestamp
				+ settings.metadataSeparator
				+ subscriptionContext.currentTransactionID
				+ settings.metadataSeparator + entryType
				+ settings.metadataSeparator + transactionUser + settings.metadataSeparator);

		// Write column-level information for the before-image (update + delete)
		if (beforeImage != null) {
			for (int i = 1; i <= beforeImage.getColumnCount(); i++) {
				// Ensure that only the table's columns are replicated, not the
				// journal control columns
				if (beforeImage.getColumnName(i).startsWith("&"))
					break;
				try {
					if (i != 1)
						printLine += settings.separator;
					if (beforeImage.getObject(i) != null)
						printLine += beforeImage.getObject(i).toString();
				} catch (DataTypeConversionException e) {
					trace.write(e.getMessage());
				}
			}
		} else {
			trace.write("Before image is empty");
			printLine += emptyBeforeImage;
		}

		// Write column-level information for the after-image (insert+update)
		if (afterImage != null) {
			for (int i = 1; i <= afterImage.getColumnCount(); i++) {
				// Ensure that only the table's columns are replicated, not the
				// journal control columns
				if (afterImage.getColumnName(i).startsWith("&"))
					break;
				try {
					printLine += settings.separator;
					if (afterImage.getObject(i) != null)
						printLine += afterImage.getObject(i).toString();
				} catch (DataTypeConversionException e) {
					trace.write(e.getMessage());
				}
			}
		} else {
			trace.write("After image is empty");
			printLine += emptyAfterImage;
		}

		// Write the line
		streamsWriter.writeStreams(printLine);

		subscriptionContext.currentTransactionOperations++;

		// Ensure that the CDC engine does not write to the target table
		return false;
	}

	/**
	 * Converts the CDC internal entry type to a common representation.
	 * 
	 * @param entryType
	 *            Entry type generated by CDC, can be PT, PX for Insert, UP for
	 *            Update and DL for Delete.
	 * @return Converted entry type: "I", "U" or "D"
	 */
	private String convertEntryType(String entryType) {
		String convertedType = "";
		if (entryType.equals("PT") || entryType.equals("PX")
				|| entryType.equals("RR"))
			convertedType = "I";
		else if (entryType.equals("UP"))
			convertedType = "U";
		else if (entryType.equals("DL"))
			convertedType = "D";
		return convertedType;
	}

	/**
	 * This method is called for both subscription-level and table-level
	 * clean-up. It will close the output stream.
	 * 
	 * @throws UserExitException
	 */
	public void finish() {
		if (calledAtSubscriptionLevel) {
			trace.write("finish() start");
			try {
				streamsWriter.doFinalize();
			} catch (UserExitException ignore) {
			}
			streamsWriter.close();
			subscriptionContext.timer.stop();
			trace.write("finish() end");
		}
		return;
	}

	/**
	 * Translates the subscription event to a readable text string (mainly for
	 * debugging).
	 * 
	 * @param eventType
	 *            - Type of the subscription event
	 * @return Event type description
	 */
	private String getSubscriptionEventTypeAsString(int eventType) {
		if (eventType == SubscriptionEventTypes.BEFORE_COMMIT_EVENT)
			return "BEFORE_COMMIT_EVENT";
		else if (eventType == SubscriptionEventTypes.BEFORE_PHYSICAL_COMMIT_EVENT)
			return "BEFORE_PHYSICAL_COMMIT_EVENT";
		else if (eventType == SubscriptionEventTypes.BEFORE_DDL_EVENT)
			return "BEFORE_DDL_EVENT";
		else if (eventType == SubscriptionEventTypes.AFTER_COMMIT_EVENT)
			return "AFTER_COMMIT_EVENT";
		else if (eventType == SubscriptionEventTypes.AFTER_PHYSICAL_COMMIT_EVENT)
			return "AFTER_PHYSICAL_COMMIT_EVENT";
		else if (eventType == SubscriptionEventTypes.AFTER_DDL_EVENT)
			return "AFTER_DDL_EVENT";
		else if (eventType == SubscriptionEventTypes.AFTER_EVENT_SHIFT)
			return "AFTER_EVENT_SHIFT";
		else
			return "UNKNOWN_SUBSCRIPTION_EVENT_TYPE: " + eventType;
	}

	/**
	 * Translates the commit reason to a readable text string (mainly for
	 * debugging).
	 * 
	 * @param commitReason
	 *            - Reason for committing the transaction
	 * @return Commit reason description
	 */
	private String getSubscriptionCommitReasonAsString(int commitReason) {
		if (commitReason == CommitReasonTypes.SOURCE_COMMIT)
			return "SOURCE_COMMIT";
		else if (commitReason == CommitReasonTypes.OPERATION_WITHOUT_COMMITMENT_CONTROL)
			return "OPERATION_WITHOUT_COMMITMENT_CONTROL";
		else if (commitReason == CommitReasonTypes.REFRESH)
			return "REFRESH";
		else if (commitReason == CommitReasonTypes.REPORT_POSITION)
			return "REPORT_POSITION";
		else if (commitReason == CommitReasonTypes.INTERIM_COMMIT)
			return "INTERIM_COMMIT";
		else if (commitReason == CommitReasonTypes.SHUTDOWN)
			return "SHUTDOWN";
		else
			return "UNKNOWN_COMMIT_REASON: " + commitReason;
	}

	/**
	 * This subclass is used to maintain the overall subscription context as
	 * this user exit is instantiated at the subscription (target) level and for
	 * all tables. As the same class is used at the subscription and table level
	 * we have chosen to create a subclass to create a subclass for the
	 * subscription context.
	 */
	protected class SubscriptionContext {
		protected UETrace trace; // trace object
		protected UESettings settings; // Holds the settings for the user exit
		protected SubscriptionEventPublisherIF eventPublisher;
		protected String publisherID; // Publisher ID for subscription
		protected StreamsWriter streamsWriter; // Writes to Streams application
		protected Timer timer; // Timer to control handshake
		protected String currentTransactionID; // Current transaction ID
		protected String currentTransactionTimestamp; // Last timestamp of tx
		protected long currentTransactionOperations; // Number of operations
	}

	/**
	 * This subclass takes care of the output to the Streams application
	 */
	protected class StreamsWriter {

		Socket socket = null;
		private PrintWriter printWriter;
		private BufferedReader feedbackStream;
		private int handshakeFailures = 0;

		/**
		 * Open the output stream to which the records will be written. This
		 * method generates a PrintWriter which targets either a TCP/IP socket
		 * (TCPSource or CDCSource) or a named pipe (fifo file).
		 * 
		 * @return PrintWriter object
		 * @throws IOException
		 * @throws UserExitException
		 */
		public StreamsWriter() throws UserExitException {
			try {
				// Prepare for writing to TCP/IP socket or Named pipe
				if (settings.outputType.equalsIgnoreCase("tcpsource")
						|| settings.outputType.equalsIgnoreCase("cdcsource")) {
					openWriterTCP();
				} else if (settings.outputType.equalsIgnoreCase("namedpipe")
						|| settings.outputType.equalsIgnoreCase("fifo")) {
					openWriterNamedPipe();
				} else {
					openWriterNull();
				}
			} catch (IOException e) {
				throw new UserExitException(e.getMessage());
			}
		}

		/**
		 * Opens writer to TCP/IP socket.
		 * 
		 * @throws UserExitException
		 * @throws IOException
		 */
		private void openWriterTCP() throws UserExitException, IOException {
			eventPublisher
					.logEvent("User exit will write to InfoSphere Streams application on address "
							+ settings.tcpHostPort);
			String hostName = "";
			int port = 0;
			String[] tcpElements = settings.tcpHostPort.split(":");
			if (tcpElements.length == 2) {
				hostName = tcpElements[0];
				port = Integer.parseInt(tcpElements[1]);
			} else
				throw new UserExitException(
						"Property tcpHostPort is invalid, should be of format <host_name_or_ip>:<port>");

			// Try to connect to the port on the specified server
			trace.writeAlways("Connecting to server " + hostName + ", port "
					+ port);
			logEvent("Connecting to server " + hostName + ", port " + port);
			InetSocketAddress socketAddress = new InetSocketAddress(hostName,
					port);
			long beginTimestamp = System.currentTimeMillis();
			long endTimestamp = beginTimestamp
					+ (1000 * settings.tcpConnectionTimeoutSeconds);
			long remainingTimeMillis = 0;
			long waitMessage = endTimestamp - beginTimestamp;
			// Try to connect iteratively until successful or timed out, send a
			// status message every 10 seconds
			for (; System.currentTimeMillis() < endTimestamp;) {
				remainingTimeMillis = endTimestamp - System.currentTimeMillis();
				try {
					socket = new Socket();
					socket.connect(socketAddress, 1000);
					break;
				} catch (Exception ce) {
					// Only send a message every 10 seconds
					if (remainingTimeMillis < (waitMessage - 10000)) {
						logEvent("Waiting for server connection, timing out in "
								+ ((endTimestamp - System.currentTimeMillis()) / 1000)
								+ " seconds");
						waitMessage = remainingTimeMillis;
					}
					try {
						Thread.sleep(100);
					} catch (InterruptedException doNothing) {
					}
				}
			}
			if (socket.isConnected()) {
				logEvent("Connected to TCP address " + settings.tcpHostPort);
				printWriter = new PrintWriter(new OutputStreamWriter(
						socket.getOutputStream()));
				feedbackStream = new BufferedReader(new InputStreamReader(
						socket.getInputStream()));
				// If the target is a CDCSource operator, wait until all its
				// ports are ready
				if (settings.outputType.equalsIgnoreCase("cdcsource")) {
					logEvent("Waiting for CDCSource operator to report it is ready, maximum wait time is "
							+ settings.initCDCSourceTimeoutSeconds
							+ " seconds.");
					String feedbackString = getFeedback(settings.initCDCSourceTimeoutSeconds * 1000);
					if (feedbackString != null
							&& feedbackString.startsWith("i")) {
						logEvent("CDCSource operator is ready to receive changes.");
					} else {
						throw new UserExitException(
								"CDCSource operator did not report readiness within "
										+ settings.initCDCSourceTimeoutSeconds
										+ " seconds. Terminating abnormally");
					}
				}
				// Now send string that CDC subscription has been initialized
				doInit();
			} else
				throw new UserExitException("Connection to TCP address "
						+ settings.tcpHostPort + " failed.");
		}

		/**
		 * Opens writer to Named Pipe.
		 * 
		 * @throws UserExitException
		 * @throws IOException
		 */
		private void openWriterNamedPipe() throws UserExitException,
				IOException {
			logEvent("User exit will write to InfoSphere Streams application using named pipe "
					+ settings.namedPipe);
			File fifoFile = new File(settings.namedPipe);
			if (fifoFile.exists() && !fifoFile.isDirectory()) {
				printWriter = new PrintWriter(new FileWriter(
						settings.namedPipe, true));
			} else
				throw new UserExitException("Named pipe " + settings.namedPipe
						+ " does not exist.");

		}

		/**
		 * Facilitates writing of no output.
		 */
		private void openWriterNull() {
			logEvent("User exit will not write to InfoSphere Streams application.");
			printWriter = null;
		}

		/**
		 * Writes the given string to the designated output.
		 * 
		 * @param printLine
		 * @throws UserExitException
		 */
		protected void writeStreams(String printLine) throws UserExitException {
			trace.write("Line being written to output: " + printLine);
			if (printWriter != null) {
				printWriter.write(printLine);
				printWriter.write("\n");
				if (printWriter.checkError()) {
					throw new UserExitException("Error while writing record: "
							+ printLine);
				}
			}
		}

		/**
		 * This method sends an initialization string to tell the Streams
		 * application that the subscription has been (re)started.
		 * 
		 * @return
		 * @throws UserExitException
		 */
		protected void doInit() throws UserExitException {
			String currentTimeString = ISO_DATEFORMAT.format(new Date());
			writeStreams("i" + settings.metadataSeparator + "***INITIALIZE***"
					+ settings.metadataSeparator + currentTimeString);
		}

		/**
		 * This method writes a commit to the opened writer
		 * 
		 * @return
		 * @throws UserExitException
		 */
		protected void doCommit(String transactionTimestamp,
				String transactionID) throws UserExitException {
			trace.write("Sending commit to server");
			writeStreams("c" + settings.metadataSeparator + "***COMMIT***"
					+ settings.metadataSeparator + transactionTimestamp
					+ settings.metadataSeparator + transactionID);

		}

		/**
		 * This method performs the handshake with the Streams application and
		 * returns whether or not the handshake was successful.
		 * 
		 * @return
		 * @throws UserExitException
		 */
		protected boolean doHandshake() throws UserExitException {
			boolean handshakeSuccessful = false;
			String currentTimeString = ISO_DATEFORMAT.format(new Date());
			writeStreams("h" + settings.metadataSeparator + "***HANDSHAKE***"
					+ settings.metadataSeparator + currentTimeString);
			// Only enforce handshake when sending to CDCSource Streams operator
			if (settings.outputType.equalsIgnoreCase("cdcsource")) {
				trace.write("Requesting handshake from Streams CDCSource operator");
				String feedback = getFeedback(settings.handshakeTimeoutMs);
				trace.write("Feedback received from CDCSource: " + feedback);
				if (feedback != null && feedback.startsWith("h")) {
					handshakeSuccessful = true;
					handshakeFailures = 0;
				} else if (feedback.startsWith("t")) {
					handshakeSuccessful = false;
				} else {
					handshakeFailures += 1;
					if (handshakeFailures > settings.handshakeMaximumFailures) {
						throw new UserExitException(
								"CDCSource operator did not handshake after "
										+ handshakeFailures
										+ " attempts, maximum number of handshake failures of "
										+ settings.handshakeMaximumFailures
										+ " has been exceeded. Terminating abnormally");
					}
				}
			} else
				handshakeSuccessful = true;
			return handshakeSuccessful;
		}

		/**
		 * This method writes a Finalize to the target
		 * 
		 * @return
		 * @throws UserExitException
		 */
		protected void doFinalize() throws UserExitException {
			trace.write("Sending finalize to server");
			String currentTimeString = ISO_DATEFORMAT.format(new Date());
			writeStreams("f" + settings.metadataSeparator + "***FINALIZE***"
					+ settings.metadataSeparator + currentTimeString);
		}

		/**
		 * This method gets feedback from the CDCSource operator over the same
		 * TCP/IP socket. If the feedback is not received within timeoutMs
		 * milliseconds, a null is returned.
		 * 
		 * @param timeoutMs
		 *            Maximum time
		 * @return
		 * @throws IOException
		 */
		protected String getFeedback(int timeoutMs) {
			String feedbackString = null;
			try {
				socket.setSoTimeout(timeoutMs);
				feedbackString = feedbackStream.readLine();
			} catch (IOException e) {
				trace.writeAlways("Feedback not received from server, message: "
						+ e.getMessage());
			}
			return feedbackString;
		}

		/**
		 * Flushes the output stream
		 * 
		 * @throws UserExitException
		 */
		protected void flushOutput() throws UserExitException {
			if (printWriter != null) {
				printWriter.flush();
				if (printWriter.checkError()) {
					throw new UserExitException("Error while flushing buffer: ");
				}
			}
		}

		/**
		 * Closes the output and feedback streams
		 */
		protected void close() {
			if (printWriter != null)
				printWriter.close();
			if (feedbackStream != null)
				try {
					feedbackStream.close();
				} catch (IOException ignore) {
				}
		}
	}

	/**
	 * This subclass is used to run a background timer thread that checks if the
	 * maximum number of seconds for the handshake has been passed. This to
	 * ensure that transaction reception is frequently confirmed by the Streams
	 * application so that the bookmark can be progressed.
	 * 
	 * The timer is started during the subscription initialization and also
	 * stopped when the subscription ends.
	 */
	protected class Timer implements Runnable {
		private boolean stop = false;
		private boolean stopped = false;

		private int handshakeAfterMaxSeconds;
		private long handshakeAfterMaxMs;
		private Long currentTimerMs;
		private static final int INTERVALMS = 100;

		public Timer() {
			this.currentTimerMs = new Long(0);
			this.handshakeAfterMaxSeconds = settings.handshakeAfterMaxSeconds;
			this.handshakeAfterMaxMs = handshakeAfterMaxSeconds * 1000;
		}

		/**
		 * Stops the thread
		 */
		protected void stop() {
			stop = true;
		}

		/**
		 * Returns if the thread has been stopped
		 */
		protected boolean isStopped() {
			return stopped;
		}

		/**
		 * Returns whether or not the handshake is due (timer interval has been
		 * reached)
		 */
		protected boolean isHandshakeDue() {
			return (currentTimerMs >= handshakeAfterMaxMs);
		}

		/**
		 * Returns whether or not the handshake is due (timer interval has been
		 * reached)
		 */
		protected void resetTimer() {
			synchronized (currentTimerMs) {
				currentTimerMs = 0L;
			}
		}

		/**
		 * This method is run when the thread is started. It executed an
		 * infinite loop until the stop variable is set to true
		 */
		public void run() {
			trace.write("Timer started, handshake with target will be done every "
					+ handshakeAfterMaxSeconds + " seconds");
			while (!stop) {
				try {
					Thread.sleep(INTERVALMS);
					synchronized (currentTimerMs) {
						currentTimerMs += INTERVALMS;
					}
				} catch (InterruptedException excp) {
				}
			}
			stopped = true;
		}
	}

}