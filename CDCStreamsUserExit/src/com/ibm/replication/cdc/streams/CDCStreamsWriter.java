package com.ibm.replication.cdc.streams;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.datamirror.ts.target.publication.userexit.UserExitException;
import com.ibm.replication.cdc.common.*;

public class CDCStreamsWriter {

	private final SimpleDateFormat ISO_DATEFORMAT = new SimpleDateFormat(
			"yyyy-MM-dd' 'HH:mm:ss.SSS'000'");

	Socket socket = null;
	private PrintWriter printWriter;
	private BufferedReader feedbackStream;
	private int handshakeFailures = 0;

	UESettings settings;
	UETrace trace;

	/**
	 * Open the output stream to which the records will be written. This method
	 * generates a PrintWriter which targets either a TCP/IP socket (TCPSource
	 * or CDCSource) or a named pipe (fifo file).
	 * 
	 * @return PrintWriter object
	 * @throws IOException
	 * @throws UserExitException
	 */
	public CDCStreamsWriter(UESettings settings, UETrace trace) throws UserExitException {

		this.settings = settings;
		this.trace=trace;

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
		trace.logEvent("User exit will write to InfoSphere Streams application on address "
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
		trace.logEvent("Connecting to server " + hostName + ", port " + port);
		InetSocketAddress socketAddress = new InetSocketAddress(hostName, port);
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
					trace.logEvent("Waiting for server connection, timing out in "
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
			trace.logEvent("Connected to TCP address " + settings.tcpHostPort);
			printWriter = new PrintWriter(new OutputStreamWriter(
					socket.getOutputStream()));
			feedbackStream = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			// If the target is a CDCSource operator, wait until all its
			// ports are ready
			if (settings.outputType.equalsIgnoreCase("cdcsource")) {
				trace.logEvent("Waiting for CDCSource operator to report it is ready, maximum wait time is "
						+ settings.initCDCSourceTimeoutSeconds + " seconds.");
				String feedbackString = getFeedback(settings.initCDCSourceTimeoutSeconds * 1000);
				if (feedbackString != null && feedbackString.startsWith("i")) {
					trace.logEvent("CDCSource operator is ready to receive changes.");
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
	private void openWriterNamedPipe() throws UserExitException, IOException {
		trace.logEvent("User exit will write to InfoSphere Streams application using named pipe "
				+ settings.namedPipe);
		File fifoFile = new File(settings.namedPipe);
		if (fifoFile.exists() && !fifoFile.isDirectory()) {
			printWriter = new PrintWriter(new FileWriter(settings.namedPipe,
					true));
		} else
			throw new UserExitException("Named pipe " + settings.namedPipe
					+ " does not exist.");

	}

	/**
	 * Facilitates writing of no output.
	 */
	private void openWriterNull() {
		trace.logEvent("User exit will not write to InfoSphere Streams application.");
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
	protected void doCommit(String transactionTimestamp, String transactionID)
			throws UserExitException {
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
