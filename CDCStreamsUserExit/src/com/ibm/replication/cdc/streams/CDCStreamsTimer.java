package com.ibm.replication.cdc.streams;

/**
 * This subclass is used to run a background timer thread that checks if the
 * maximum number of seconds for the handshake has been passed. This to ensure
 * that transaction reception is frequently confirmed by the Streams application
 * so that the bookmark can be progressed.
 * 
 * The timer is started during the subscription initialization and also stopped
 * when the subscription ends.
 */

import com.ibm.replication.cdc.common.*;

public class CDCStreamsTimer implements Runnable {
	private boolean stop = false;
	private boolean stopped = false;

	private int handshakeAfterMaxSeconds;
	private long handshakeAfterMaxMs;
	private Long currentTimerMs;
	private static final int INTERVALMS = 100;

	UETrace trace;

	public CDCStreamsTimer(UESettings settings, UETrace trace) {
		this.trace = trace;
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
	 * This method is run when the thread is started. It executed an infinite
	 * loop until the stop variable is set to true
	 */
	public void run() {
		trace.write("Timer started, handshake with target will be done every " + handshakeAfterMaxSeconds + " seconds");
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
