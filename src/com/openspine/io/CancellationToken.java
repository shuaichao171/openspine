package com.openspine.io;

import java.io.IOException;
import java.util.concurrent.CancellationException;

/** Coordinates cooperative cancellation with an atomic file commit. */
public class CancellationToken {
	@FunctionalInterface
	public interface CommitAction {
		void run() throws IOException;
	}

	private boolean cancellationRequested;

	public synchronized void cancel() {
		cancellationRequested = true;
	}

	public synchronized boolean isCancellationRequested() {
		return cancellationRequested;
	}

	public void checkCancelled() {
		boolean cancelled;
		synchronized (this) {
			cancelled = cancellationRequested;
		}
		if (cancelled || Thread.currentThread().isInterrupted())
			throw new CancellationException("文件操作已取消");
	}

	/** A cancellation requested before this method obtains the lock prevents the commit. */
	public synchronized void commit(CommitAction action) throws IOException {
		if (action == null) throw new IllegalArgumentException("Commit action is required");
		if (cancellationRequested || Thread.currentThread().isInterrupted())
			throw new CancellationException("文件操作已取消");
		action.run();
	}
}
