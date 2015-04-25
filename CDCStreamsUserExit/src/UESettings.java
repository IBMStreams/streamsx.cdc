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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Properties;

public class UESettings {
	private final String DEFAULT_PROPERTIES_FILENAME = "CDCStreams.properties";

	// Properties to be retrieved from the file
	public String outputType = "tcpsource";
	public String tcpHostPort = "localhost:12345";
	public String namedPipe = "/tmp/fifofile";
	public String separator = "\u001d";
	public String metadataSeparator = "\u0000";
	public int handshakeAfterMaxTransactions = 100;
	public int handshakeAfterMaxSeconds = 60;
	public int handshakeTimeoutMs = 500;
	public int handshakeMaximumFailures = 0;
	public int tcpConnectionTimeoutSeconds = 120;
	public int initCDCSourceTimeoutSeconds = 10;
	public boolean debug = false;

	// Constructor
	public UESettings(String propertiesFileName) {
		if (propertiesFileName != null && !propertiesFileName.isEmpty())
			load(propertiesFileName);
		else
			load(DEFAULT_PROPERTIES_FILENAME);
	}

	// Load variables from the properties file
	private void load(String propertiesFileName) {

		Properties properties = new Properties();

		try {
			FileInputStream fileIn = new FileInputStream(propertiesFileName);
			properties.load(fileIn);
			fileIn.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		outputType = properties.getProperty("outputType", outputType);
		tcpHostPort = properties.getProperty("tcpHostPort", tcpHostPort);
		namedPipe = properties.getProperty("namedPipe", namedPipe);
		separator = properties.getProperty("separator", separator);
		metadataSeparator = properties.getProperty("metadataSeparator",
				metadataSeparator);
		handshakeAfterMaxTransactions = Integer.parseInt(properties
				.getProperty("handshakeAfterMaxTransactions",
						Integer.toString(handshakeAfterMaxTransactions)));
		handshakeAfterMaxSeconds = Integer.parseInt(properties.getProperty(
				"handshakeAfterMaxSeconds",
				Integer.toString(handshakeAfterMaxSeconds)));
		handshakeTimeoutMs = Integer.parseInt(properties.getProperty(
				"handshakeTimeoutMs", Integer.toString(handshakeTimeoutMs)));
		handshakeMaximumFailures = Integer.parseInt(properties.getProperty(
				"handshakeMaximumFailures",
				Integer.toString(handshakeMaximumFailures)));
		tcpConnectionTimeoutSeconds = Integer.parseInt(properties.getProperty(
				"tcpConnectionTimeoutSeconds",
				Integer.toString(tcpConnectionTimeoutSeconds)));
		initCDCSourceTimeoutSeconds = Integer.parseInt(properties.getProperty(
				"initCDCSourceTimeoutSeconds",
				Integer.toString(initCDCSourceTimeoutSeconds)));
		debug = Boolean.parseBoolean(properties.getProperty("debug",
				Boolean.toString(debug)));

		// Now that we have loaded the variables, output them to the IIDR event
		// log
		output(propertiesFileName);
	}

	// Output all variables retrieved from the properties file
	private void output(String propertiesFileName) {
		UETrace trace = new UETrace(true);
		trace.writeAlways("Properties in file " + propertiesFileName + ":");
		Field[] fields = this.getClass().getDeclaredFields();
		for (Field field : fields) {
			int fieldModifier = field.getModifiers();
			if (Modifier.isPublic(fieldModifier)) {
				try {
					trace.writeAlways(field.getName() + ": " + field.get(this));
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
	}

	// Main method, just for validating this class
	public static void main(String[] args) {
		if (args.length == 0)
			new UESettings(null);
		else
			new UESettings(args[0]);
	}
}