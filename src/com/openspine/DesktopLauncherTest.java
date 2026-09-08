package com.openspine;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

/** Headless regression tests for desktop dialog routing and recent directories. */
public final class DesktopLauncherTest {
	public static void main(String[] args) throws Exception {
		File assets = new File("assets").getAbsoluteFile();
		assertTrue(assets.isDirectory(), "test assets directory exists");
		assertEquals(assets, DesktopLauncher.existingDirectory(null, assets),
			"missing recent path uses the fallback directory");
		assertEquals(assets, DesktopLauncher.existingDirectory(assets.getPath(), null),
			"an existing recent directory is preserved");
		assertEquals(assets, DesktopLauncher.existingDirectory(new File(assets, "hero.json").getPath(), null),
			"a file suggestion resolves to its existing parent directory");
		assertEquals(assets, DesktopLauncher.existingDirectory("missing-openspine-folder/hero.json", assets),
			"an invalid recent path falls back safely");
		assertTrue(DesktopLauncher.acceptsOpenFileName("hero.SPINE", true),
			"the native import picker accepts editor projects case-insensitively");
		assertTrue(!DesktopLauncher.acceptsOpenFileName("hero.json", true),
			"the import-only picker rejects runtime exports");
		assertTrue(DesktopLauncher.acceptsOpenFileName("hero.spine", false)
			&& DesktopLauncher.acceptsOpenFileName("hero.JSON", false)
			&& DesktopLauncher.acceptsOpenFileName("hero.skel", false),
			"the native open picker accepts every supported project type");
		assertTrue(!DesktopLauncher.acceptsOpenFileName("hero.atlas", false),
			"the native open picker rejects unrelated assets");
		assertTrue(DesktopLauncher.acceptsAnimationFileName("walk.JSON")
			&& DesktopLauncher.acceptsAnimationFileName("walk.skel"),
			"the animation picker accepts runtime JSON and binary data");
		assertTrue(!DesktopLauncher.acceptsAnimationFileName("walk.spine")
			&& !DesktopLauncher.acceptsAnimationFileName("walk.atlas"),
			"the animation picker excludes editor projects and unrelated assets");
		assertTrue(DesktopLauncher.acceptsImageFileName("hero.PNG")
			&& DesktopLauncher.acceptsImageFileName("hero.jpg")
			&& DesktopLauncher.acceptsImageFileName("hero.JPEG"),
			"the atlas picker accepts PNG and JPEG images");
		assertTrue(!DesktopLauncher.acceptsImageFileName("hero.webp"),
			"the atlas picker rejects unsupported image formats");
		assertTrue(DesktopLauncher.acceptsPsdFileName("layers.PSD")
			&& !DesktopLauncher.acceptsPsdFileName("layers.psb")
			&& !DesktopLauncher.acceptsPsdFileName("layers.png"),
			"the layered artwork picker accepts only PSD files");
		assertEquals("hero.json", DesktopLauncher.ensureSaveExtension("hero", ".json"),
			"JSON filter appends the JSON extension");
		assertEquals("hero.skel", DesktopLauncher.ensureSaveExtension("hero", ".skel"),
			"binary filter appends the skeleton extension");
		assertEquals("hero.SKEL", DesktopLauncher.ensureSaveExtension("hero.SKEL", ".json"),
			"an explicitly typed binary extension is preserved");
		assertEquals("hero-project.zip", DesktopLauncher.ensurePackageExtension("hero-project"),
			"project packages receive the ZIP extension");
		assertEquals("hero.ZIP", DesktopLauncher.ensurePackageExtension("hero.ZIP"),
			"an explicitly typed package extension is preserved");
		assertEquals("hero.spine", DesktopLauncher.ensureSpineProjectExtension("hero"),
			"Spine project chooser appends its required extension");
		assertEquals("hero.SPINE", DesktopLauncher.ensureSpineProjectExtension("hero.SPINE"),
			"an explicitly typed Spine project extension is preserved");
		assertEquals("hero.png", DesktopLauncher.ensurePngExtension("hero"),
			"PNG chooser appends its required extension");
		assertEquals("hero.PNG", DesktopLauncher.ensurePngExtension("hero.PNG"),
			"an explicitly typed PNG extension is preserved");
		assertEquals("hero.jpg", DesktopLauncher.ensureJpegExtension("hero"),
			"JPEG chooser appends its preferred extension");
		assertEquals("hero.JPEG", DesktopLauncher.ensureJpegExtension("hero.JPEG"),
			"an explicitly typed JPEG extension is preserved");
		assertEquals("hero.gif", DesktopLauncher.ensureGifExtension("hero"),
			"GIF chooser appends its required extension");
		assertEquals("hero.GIF", DesktopLauncher.ensureGifExtension("hero.GIF"),
			"an explicitly typed GIF extension is preserved");
		assertEquals("hero.apng", DesktopLauncher.ensureApngExtension("hero"),
			"APNG chooser appends its preferred extension");
		assertEquals("hero.APNG", DesktopLauncher.ensureApngExtension("hero.APNG"),
			"an explicitly typed APNG extension is preserved");
		assertEquals("hero.PNG", DesktopLauncher.ensureApngExtension("hero.PNG"),
			"APNG chooser also preserves the standard PNG extension");
		assertEquals("hero.avi", DesktopLauncher.ensureAviExtension("hero"),
			"AVI chooser appends its required extension");
		assertEquals("hero.AVI", DesktopLauncher.ensureAviExtension("hero.AVI"),
			"an explicitly typed AVI extension is preserved");
		assertEquals("*.jpg;*.jpeg", DesktopLauncher.extensionPattern("jpg", "JPEG"),
			"native dialog filters combine accepted extensions");
		assertEquals("JPEG 图片", DesktopLauncher.filterDescriptionWithoutPattern("JPEG 图片 (*.jpg / *.jpeg)"),
			"native filter labels omit duplicate wildcard text");
		String nativeFilters = WindowsFileDialog.filterString(java.util.List.of(
			new WindowsFileDialog.Filter("Spine 工程", "*.spine"),
			new WindowsFileDialog.Filter("骨架数据", "*.json;*.skel")));
		assertEquals("Spine 工程\0*.spine\0骨架数据\0*.json;*.skel\0\0", nativeFilters,
			"Win32 filter text uses the required double-null-terminated layout");
		assertTrue(WindowsFileDialog.structureSize() == 152 || WindowsFileDialog.structureSize() == 88,
			"OPENFILENAMEW layout matches the JVM pointer size");
		if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"))
			assertTrue(WindowsFileDialog.apiAvailable(), "Windows common-dialog APIs are available");
		assertTrue(!DesktopLauncher.installMainWindow(0L), "a missing GLFW owner is rejected");
		DesktopLauncher.SequenceFileSelection sequence =
			new DesktopLauncher.SequenceFileSelection("hero.png", true);
		assertEquals("hero.png", sequence.path(), "sequence chooser results preserve the requested prefix");
		assertTrue(sequence.replaceExisting(), "sequence chooser results carry explicit overwrite consent");
		assertThrows(() -> new DesktopLauncher.SequenceFileSelection(" ", false),
			"blank sequence chooser results are rejected");
		assertTrue(DesktopLauncher.defaultDirectory() != null && DesktopLauncher.defaultDirectory().isDirectory(),
			"the desktop chooser has an existing default directory");

		assertTrue(DesktopLauncher.onEventDispatchThread(SwingUtilities::isEventDispatchThread),
			"dialog actions are dispatched to the Swing event thread");
		assertEquals("complete", DesktopLauncher.onEventDispatchThread(() -> "complete"),
			"dialog action results return to the caller");
		assertThrows(() -> DesktopLauncher.onEventDispatchThread(() -> {
			throw new IllegalArgumentException("expected");
		}), "dialog action failures propagate to the caller");

		CountDownLatch releaseAction = new CountDownLatch(1);
		CountDownLatch asyncCompletion = new CountDownLatch(1);
		AtomicBoolean callerReleasedAction = new AtomicBoolean();
		AtomicBoolean asyncRanOnEdt = new AtomicBoolean();
		AtomicReference<String> asyncResult = new AtomicReference<>();
		AtomicReference<Throwable> asyncError = new AtomicReference<>();
		DesktopLauncher.onEventDispatchThreadAsync(() -> {
			asyncRanOnEdt.set(SwingUtilities.isEventDispatchThread());
			callerReleasedAction.set(releaseAction.await(2, TimeUnit.SECONDS));
			return "async-complete";
		}, (result, error) -> {
			asyncResult.set(result);
			asyncError.set(error);
			asyncCompletion.countDown();
		});
		releaseAction.countDown();
		assertTrue(asyncCompletion.await(3, TimeUnit.SECONDS), "async Swing action completes");
		assertTrue(callerReleasedAction.get(), "async Swing dispatch does not block its caller");
		assertTrue(asyncRanOnEdt.get(), "async Swing action runs on the event thread");
		assertEquals("async-complete", asyncResult.get(), "async Swing result reaches the completion");
		assertTrue(asyncError.get() == null, "successful async Swing action has no error");

		CountDownLatch failedCompletion = new CountDownLatch(1);
		AtomicReference<String> failedResult = new AtomicReference<>();
		AtomicReference<Throwable> failedError = new AtomicReference<>();
		DesktopLauncher.<String>onEventDispatchThreadAsync(() -> {
			throw new IllegalStateException("async-expected");
		}, (result, error) -> {
			failedResult.set(result);
			failedError.set(error);
			failedCompletion.countDown();
		});
		assertTrue(failedCompletion.await(3, TimeUnit.SECONDS), "failed async Swing action completes");
		assertTrue(failedResult.get() == null, "failed async Swing action has no result");
		assertTrue(failedError.get() instanceof IllegalStateException,
			"failed async Swing action reaches the completion once");
		System.out.println("ALL DESKTOP LAUNCHER TESTS PASSED");
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
	}

	private static void assertThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private DesktopLauncherTest() {}
}
