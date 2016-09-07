package com.ibm.replication.cdc.common;
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
import java.net.URL;
import java.util.Properties;
import java.util.Set;

public class UESettings {
	private final String DEFAULT_PROPERTIES_FILENAME = "CDCStreams.properties";

	// Properties to be retrieved from the file
	public String outputType = "tcpsource";
	public String tcpHostPort = "localhost:12345";
	public String namedPipe = "/tmp/fifofile";
	public String separator = "\u001d";
	public String fixColumnConversionCharacter = " ";
	public String metadataSeparator = "\u0000";
	public int handshakeAfterMaxTransactions = 100;
	public int handshakeAfterMaxSeconds = 60;
	public int handshakeTimeoutMs = 500;
	public int handshakeMaximumFailures = 0;
	public int tcpConnectionTimeoutSeconds = 120;
	public int initCDCSourceTimeoutSeconds = 10;
	public boolean debug = false;

	UETrace trace = new UETrace(true, null);

	// Constructor
	public UESettings(String propertiesFileName) {
		if (propertiesFileName != null && !propertiesFileName.isEmpty())
			load(propertiesFileName);
		else
			load(DEFAULT_PROPERTIES_FILENAME);
	}

	// Load variables from the properties file
	private void load(String propertiesFileName) {

		trace.writeAlways("Reading configuration from properties file " + propertiesFileName);

		Properties properties = new Properties();

		try {
			// First try to find properties file in classpath
			InputStream propertiesStream;
			URL fileURL = UESettings.class.getClassLoader().getResource(propertiesFileName);
			if (fileURL != null) {
				trace.writeAlways("Resolved properties file from classpath: " + fileURL);
				propertiesStream = UESettings.class.getClassLoader().getResourceAsStream(propertiesFileName);
				properties.load(propertiesStream);
			} else {
				trace.writeAlways("Properties file could not be resolved from classpath, checking current directory");
				File propertiesFile = new File(propertiesFileName);
				trace.writeAlways("Resolved properties file: " + propertiesFile.getAbsolutePath());
				propertiesStream = new FileInputStream(propertiesFile);
				properties.load(propertiesStream);
			}

			// Log all properties into the trace
			Set<Object> propertiesKeys = properties.keySet();
			for (Object key : propertiesKeys) {
				String propertyValue = properties.getProperty((String) key);
				trace.writeAlways(key + "=" + propertyValue + " (length=" + propertyValue.length() + ")");
			}
		} catch (Exception e) {
			trace.writeAlways(
					"Error processing properties from file " + propertiesFileName + ", message: " + e.getMessage());
		}

		outputType = properties.getProperty("outputType", outputType);
		tcpHostPort = properties.getProperty("tcpHostPort", tcpHostPort);
		namedPipe = properties.getProperty("namedPipe", namedPipe);
		separator = properties.getProperty("separator", separator);
		fixColumnConversionCharacter = properties.getProperty("fixColumnConversionCharacter",
				fixColumnConversionCharacter);
		metadataSeparator = properties.getProperty("metadataSeparator", metadataSeparator);
		handshakeAfterMaxTransactions = Integer.parseInt(properties.getProperty("handshakeAfterMaxTransactions",
				Integer.toString(handshakeAfterMaxTransactions)));
		handshakeAfterMaxSeconds = Integer.parseInt(
				properties.getProperty("handshakeAfterMaxSeconds", Integer.toString(handshakeAfterMaxSeconds)));
		handshakeTimeoutMs = Integer
				.parseInt(properties.getProperty("handshakeTimeoutMs", Integer.toString(handshakeTimeoutMs)));
		handshakeMaximumFailures = Integer.parseInt(
				properties.getProperty("handshakeMaximumFailures", Integer.toString(handshakeMaximumFailures)));
		tcpConnectionTimeoutSeconds = Integer.parseInt(
				properties.getProperty("tcpConnectionTimeoutSeconds", Integer.toString(tcpConnectionTimeoutSeconds)));
		initCDCSourceTimeoutSeconds = Integer.parseInt(
				properties.getProperty("initCDCSourceTimeoutSeconds", Integer.toString(initCDCSourceTimeoutSeconds)));
		debug = Boolean.parseBoolean(properties.getProperty("debug", Boolean.toString(debug)));

	}

	// Main method, just for validating this class
	public static void main(String[] args) {
		if (args.length == 0)
			new UESettings(null);
		else
			new UESettings(args[0]);
	}
}