package com.example.blockminer;

public final class Clock {
	private long deadlineNanos;
	private boolean scheduled;

	public void schedule(long milliseconds) {
		if (milliseconds < 0L) {
			throw new IllegalArgumentException("Timer duration cannot be negative");
		}
		deadlineNanos = System.nanoTime() + milliseconds * 1_000_000L;
		scheduled = true;
	}

	public boolean isScheduled() {
		return scheduled;
	}

	public boolean passed() {
		return scheduled && System.nanoTime() >= deadlineNanos;
	}

	public void reset() {
		deadlineNanos = 0L;
		scheduled = false;
	}
}
