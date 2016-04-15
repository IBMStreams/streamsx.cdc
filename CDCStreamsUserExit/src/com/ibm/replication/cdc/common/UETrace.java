package com.ibm.replication.cdc.common;

/****************************************************************************
 ** Licensed Materials - Property of IBM
 ** IBM InfoSphere Change Data Capture
 ** 5724-U70
 **
 ** (c) Copyright IBM Corp. 2001, 2008, 2009 All rights reserved.
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
import com.datamirror.ts.target.publication.userexit.SubscriptionEventPublisherIF;
import com.datamirror.ts.util.trace.Trace;

/**
 * Tracing facility for user exit, piggy backs on the IIDR (CDC) tracing
 * facility
 */
public class UETrace {
	boolean enabled = false;
	SubscriptionEventPublisherIF eventPublisher = null;

	public UETrace(boolean enabled) {
		this.enabled = enabled;
		eventPublisher = null;
	}

	/**
	 * Initializes the tracing facility. If the eventPublisher parameter is not
	 * null, the logEvent method will also write to the event log.
	 */
	public UETrace(boolean enabled, SubscriptionEventPublisherIF eventPublisher) {
		this.enabled = enabled;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * Write message to subscription trace and subscription-level target event
	 * log (if initialized)
	 * 
	 * @param message
	 *            Message to be sent to the subscription event log and log file
	 */
	public void logEvent(String message) {
		writeAlways(message);
		if (eventPublisher != null)
			eventPublisher.logEvent(message);
	}

	/**
	 * Writes a trace message (always)
	 * 
	 * @param message
	 *            - Messag to write to the trace
	 */
	public void writeAlways(String message) {
		System.out.println(message);
		Trace.traceAlways(message);
		return;
	}

	/**
	 * Writes a trace message
	 * 
	 * @param message
	 *            - Messag to write to the trace
	 */
	public void write(String message) {
		if (enabled) {
			Trace.traceAlways(message);
		}
		return;
	}

	/**
	 * Cleanup for trace facility -> not used in this implementation
	 */
	public void close() {
	}
}