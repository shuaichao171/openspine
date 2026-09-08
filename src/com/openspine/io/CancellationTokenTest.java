package com.openspine.io;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Headless regression tests for cancellation and final atomic commit coordination. */
public final class CancellationTokenTest {
	public static void main(String[] args) throws Exception {
		CancellationToken cancelled = new CancellationToken();
		AtomicBoolean committed = new AtomicBoolean();
		cancelled.cancel();
		assertCancelled(cancelled::checkCancelled, "a cancelled operation stops at its next checkpoint");
		assertCancelled(() -> cancelled.commit(() -> committed.set(true)),
			"cancellation requested before commit prevents replacement");
		assertTrue(!committed.get(), "a prevented commit does not run its file replacement");

		CancellationToken committing = new CancellationToken();
		CountDownLatch commitEntered = new CountDownLatch(1);
		CountDownLatch releaseCommit = new CountDownLatch(1);
		CountDownLatch cancelStarted = new CountDownLatch(1);
		CountDownLatch cancelReturned = new CountDownLatch(1);
		Thread writer = new Thread(() -> {
			try {
				committing.commit(() -> {
					commitEntered.countDown();
					try {
						if (!releaseCommit.await(3, TimeUnit.SECONDS)) throw new IOException("commit release timed out");
					} catch (InterruptedException error) {
						Thread.currentThread().interrupt();
						throw new IOException("commit interrupted", error);
					}
					committed.set(true);
				});
			} catch (IOException error) {
				throw new AssertionError(error);
			}
		}, "cancellation-token-test-writer");
		Thread canceller = new Thread(() -> {
			cancelStarted.countDown();
			committing.cancel();
			cancelReturned.countDown();
		}, "cancellation-token-test-canceller");
		committed.set(false);
		writer.start();
		assertTrue(commitEntered.await(3, TimeUnit.SECONDS), "the final commit begins");
		canceller.start();
		assertTrue(cancelStarted.await(3, TimeUnit.SECONDS), "the cancellation request begins");
		assertTrue(!cancelReturned.await(100, TimeUnit.MILLISECONDS),
			"cancellation waits while final replacement owns the commit lock");
		releaseCommit.countDown();
		writer.join(3000);
		canceller.join(3000);
		assertTrue(!writer.isAlive() && !canceller.isAlive(), "commit and cancellation both finish");
		assertTrue(committed.get(), "a replacement already in progress completes successfully");
		assertTrue(committing.isCancellationRequested(), "the later cancellation request is retained");
		System.out.println("ALL CANCELLATION TOKEN TESTS PASSED");
	}

	private static void assertCancelled(ThrowingRunnable action, String message) throws Exception {
		try {
			action.run();
		} catch (CancellationException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private CancellationTokenTest() {}
}
