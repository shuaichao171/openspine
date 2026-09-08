package com.openspine;

import java.nio.IntBuffer;

import com.badlogic.gdx.graphics.Color;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.windows.MONITORINFOEX;
import org.lwjgl.system.windows.RECT;
import org.lwjgl.system.windows.User32;
import org.lwjgl.system.windows.WindowProc;

/** Applies the editor palette to the native Windows frame while preserving standard window controls. */
final class WindowsTitleBar {
	private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19;
	private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
	private static final int DWMWA_BORDER_COLOR = 34;
	private static final int DWMWA_CAPTION_COLOR = 35;
	private static final int DWMWA_TEXT_COLOR = 36;
	private static final int RDW_INVALIDATE = 0x0001;
	private static final int RDW_UPDATENOW = 0x0100;
	private static final int RDW_FRAME = 0x0400;
	private static final int TITLE_BACKGROUND = colorRef(25, 28, 36);
	private static final int TITLE_BORDER = colorRef(30, 34, 42);
	private static final int TITLE_TEXT = colorRef(225, 229, 235);
	private static final int RESIZE_BORDER_DIP = 7;
	private static final int CUSTOM_TITLE_BAR_HEIGHT_DIP = 30;
	private static final int WINDOW_CONTROLS_WIDTH_DIP = 46 * 3;
	private static final long CUSTOM_FRAME_STYLE = User32.WS_THICKFRAME | User32.WS_MINIMIZEBOX
		| User32.WS_MAXIMIZEBOX | User32.WS_SYSMENU;
	private static long subclassedWindow;
	private static long originalWindowProc;
	private static WindowProc customWindowProc;

	static boolean usesCustomFrame() {
		return Platform.get() == Platform.WINDOWS;
	}

	/** Restores native resize, snap, and system-menu behavior to GLFW's undecorated Windows window. */
	static synchronized boolean installCustomFrame(long glfwWindowHandle) {
		if (!usesCustomFrame() || glfwWindowHandle == MemoryUtil.NULL) return false;
		long windowHandle = MemoryUtil.NULL;
		long currentStyle = 0L;
		boolean installedHere = false;
		boolean styleChanged = false;
		try {
			windowHandle = GLFWNativeWin32.glfwGetWin32Window(glfwWindowHandle);
			if (windowHandle == MemoryUtil.NULL) return false;
			currentStyle = User32.GetWindowLongPtr(windowHandle, User32.GWL_STYLE);
			if (currentStyle == 0L) return false;
			installedHere = customWindowProc == null;
			if (!installWindowProcedure(windowHandle)) return false;
			styleChanged = User32.SetWindowLongPtr(windowHandle, User32.GWL_STYLE,
				customFrameStyle(currentStyle)) != MemoryUtil.NULL;
			if (!styleChanged) {
				rollbackInstallation(windowHandle, currentStyle, false, installedHere);
				return false;
			}
			boolean frameChanged = User32.SetWindowPos(windowHandle, MemoryUtil.NULL, 0, 0, 0, 0,
				User32.SWP_FRAMECHANGED | User32.SWP_NOMOVE | User32.SWP_NOSIZE
					| User32.SWP_NOZORDER | User32.SWP_NOACTIVATE);
			if (!frameChanged) rollbackInstallation(windowHandle, currentStyle, true, installedHere);
			return frameChanged;
		} catch (LinkageError | RuntimeException ignored) {
			rollbackInstallation(windowHandle, currentStyle, styleChanged, installedHere);
			return false;
		}
	}

	static synchronized void uninstallCustomFrame(long glfwWindowHandle) {
		if (customWindowProc == null || subclassedWindow == MemoryUtil.NULL) return;
		long windowHandle = nativeWindow(glfwWindowHandle);
		if (windowHandle != subclassedWindow) return;
		restoreWindowProcedure(windowHandle);
	}

	static boolean windowProcedureRestored(long replacedProcedure, long expectedProcedure) {
		return expectedProcedure != MemoryUtil.NULL && replacedProcedure == expectedProcedure;
	}

	static long customFrameStyle(long currentStyle) {
		return (currentStyle & ~User32.WS_CAPTION) | CUSTOM_FRAME_STYLE;
	}

	static int resizeHitTest(int x, int y, int left, int top, int right, int bottom,
		int border, boolean maximized) {
		if (maximized || border <= 0) return User32.HTCLIENT;
		boolean onLeft = x >= left && x < left + border;
		boolean onRight = x < right && x >= right - border;
		boolean onTop = y >= top && y < top + border;
		boolean onBottom = y < bottom && y >= bottom - border;
		if (onTop && onLeft) return User32.HTTOPLEFT;
		if (onTop && onRight) return User32.HTTOPRIGHT;
		if (onBottom && onLeft) return User32.HTBOTTOMLEFT;
		if (onBottom && onRight) return User32.HTBOTTOMRIGHT;
		if (onLeft) return User32.HTLEFT;
		if (onRight) return User32.HTRIGHT;
		if (onTop) return User32.HTTOP;
		if (onBottom) return User32.HTBOTTOM;
		return User32.HTCLIENT;
	}

	static boolean isWindowControlRegion(int x, int y, int left, int top, int right, int bottom, int dpi) {
		float scale = Math.max(96, dpi) / 96f;
		int controlsWidth = Math.round(WINDOW_CONTROLS_WIDTH_DIP * scale);
		int titleBarHeight = Math.round(CUSTOM_TITLE_BAR_HEIGHT_DIP * scale);
		return x >= Math.max(left, right - controlsWidth) && x < right
			&& y >= top && y < Math.min(bottom, top + titleBarHeight);
	}

	static int signedLowWord(long value) {
		return (short)(value & 0xffffL);
	}

	static int signedHighWord(long value) {
		return (short)(value >>> 16 & 0xffffL);
	}

	static boolean beginMove(long glfwWindowHandle) {
		long windowHandle = nativeWindow(glfwWindowHandle);
		if (windowHandle == MemoryUtil.NULL) return false;
		try {
			releaseCapture();
			User32.SendMessage(windowHandle, User32.WM_NCLBUTTONDOWN, User32.HTCAPTION, 0L);
			return true;
		} catch (LinkageError | RuntimeException ignored) {
			return false;
		}
	}

	static boolean minimize(long glfwWindowHandle) {
		if (!validGlfwWindow(glfwWindowHandle)) return false;
		try {
			GLFW.glfwIconifyWindow(glfwWindowHandle);
			return true;
		} catch (LinkageError | RuntimeException ignored) {
			return false;
		}
	}

	static boolean toggleMaximized(long glfwWindowHandle) {
		if (!validGlfwWindow(glfwWindowHandle)) return false;
		try {
			if (GLFW.glfwGetWindowAttrib(glfwWindowHandle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE)
				GLFW.glfwRestoreWindow(glfwWindowHandle);
			else
				GLFW.glfwMaximizeWindow(glfwWindowHandle);
			return true;
		} catch (LinkageError | RuntimeException ignored) {
			return false;
		}
	}

	static boolean applyDark(long glfwWindowHandle) {
		return apply(glfwWindowHandle, true,
			new Color(25 / 255f, 28 / 255f, 36 / 255f, 1f),
			new Color(30 / 255f, 34 / 255f, 42 / 255f, 1f),
			new Color(225 / 255f, 229 / 255f, 235 / 255f, 1f));
	}

	static boolean apply(long glfwWindowHandle, boolean dark, Color background, Color border, Color text) {
		if (glfwWindowHandle == MemoryUtil.NULL || Platform.get() != Platform.WINDOWS) return false;
		if (background == null || border == null || text == null)
			throw new IllegalArgumentException("Title-bar colors cannot be null.");
		try {
			long windowHandle = GLFWNativeWin32.glfwGetWin32Window(glfwWindowHandle);
			if (windowHandle == MemoryUtil.NULL) return false;
			try (SharedLibrary dwmapi = Library.loadNative(WindowsTitleBar.class, "org.lwjgl", "dwmapi");
				MemoryStack stack = MemoryStack.stackPush()) {
				long setWindowAttribute = dwmapi.getFunctionAddress("DwmSetWindowAttribute");
				if (setWindowAttribute == MemoryUtil.NULL) return false;
				int darkValue = dark ? 1 : 0;
				boolean immersive = setAttribute(setWindowAttribute, windowHandle,
					DWMWA_USE_IMMERSIVE_DARK_MODE, darkValue, stack);
				if (!immersive) immersive = setAttribute(setWindowAttribute, windowHandle,
					DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, darkValue, stack);
				boolean caption = setAttribute(setWindowAttribute, windowHandle,
					DWMWA_CAPTION_COLOR, colorRef(background), stack);
				boolean borderApplied = setAttribute(setWindowAttribute, windowHandle,
					DWMWA_BORDER_COLOR, colorRef(border), stack);
				boolean textApplied = setAttribute(setWindowAttribute, windowHandle,
					DWMWA_TEXT_COLOR, colorRef(text), stack);
				boolean applied = immersive || caption || borderApplied || textApplied;
				if (applied) {
					flush(dwmapi);
					redrawFrame(windowHandle);
				}
				return applied;
			}
		} catch (LinkageError | RuntimeException ignored) {
			return false;
		}
	}

	static int colorRef(int red, int green, int blue) {
		if ((red | green | blue) < 0 || red > 255 || green > 255 || blue > 255)
			throw new IllegalArgumentException("Title-bar color components must be between 0 and 255.");
		return red | green << 8 | blue << 16;
	}

	static int colorRef(Color color) {
		if (color == null) throw new IllegalArgumentException("Title-bar color cannot be null.");
		return colorRef(Math.round(255f * color.r), Math.round(255f * color.g), Math.round(255f * color.b));
	}

	private static boolean setAttribute(long function, long window, int attribute, int value, MemoryStack stack) {
		IntBuffer data = stack.ints(value);
		return JNI.invokePPI(window, attribute, MemoryUtil.memAddress(data), Integer.BYTES, function) == 0;
	}

	private static void flush(SharedLibrary dwmapi) {
		long function = dwmapi.getFunctionAddress("DwmFlush");
		if (function != MemoryUtil.NULL) JNI.invokeI(function);
	}

	private static void redrawFrame(long window) {
		try (SharedLibrary user32 = Library.loadNative(WindowsTitleBar.class, "org.lwjgl", "user32")) {
			long function = user32.getFunctionAddress("RedrawWindow");
			if (function != MemoryUtil.NULL)
				JNI.invokePPPI(window, MemoryUtil.NULL, MemoryUtil.NULL,
					RDW_INVALIDATE | RDW_UPDATENOW | RDW_FRAME, function);
		} catch (LinkageError | RuntimeException ignored) {
		}
	}

	private static long nativeWindow(long glfwWindowHandle) {
		if (!validGlfwWindow(glfwWindowHandle)) return MemoryUtil.NULL;
		try {
			return GLFWNativeWin32.glfwGetWin32Window(glfwWindowHandle);
		} catch (LinkageError | RuntimeException ignored) {
			return MemoryUtil.NULL;
		}
	}

	private static boolean installWindowProcedure(long windowHandle) {
		if (customWindowProc != null) return subclassedWindow == windowHandle;
		WindowProc procedure = WindowProc.create(WindowsTitleBar::handleWindowMessage);
		long original;
		try {
			original = User32.SetWindowLongPtr(windowHandle, User32.GWL_WNDPROC, procedure.address());
		} catch (LinkageError | RuntimeException error) {
			procedure.free();
			throw error;
		}
		if (original == MemoryUtil.NULL) {
			procedure.free();
			return false;
		}
		originalWindowProc = original;
		subclassedWindow = windowHandle;
		customWindowProc = procedure;
		return true;
	}

	private static void rollbackInstallation(long windowHandle, long previousStyle,
		boolean styleChanged, boolean installedHere) {
		if (windowHandle == MemoryUtil.NULL) return;
		if (styleChanged && previousStyle != 0L) {
			try {
				User32.SetWindowLongPtr(windowHandle, User32.GWL_STYLE, previousStyle);
			} catch (LinkageError | RuntimeException ignored) {
			}
		}
		if (installedHere) restoreWindowProcedure(windowHandle);
	}

	private static boolean restoreWindowProcedure(long windowHandle) {
		if (customWindowProc == null || subclassedWindow != windowHandle
			|| originalWindowProc == MemoryUtil.NULL) return false;
		long procedureAddress = customWindowProc.address();
		long replacedProcedure;
		try {
			if (User32.GetWindowLongPtr(windowHandle, User32.GWL_WNDPROC) != procedureAddress) return false;
			replacedProcedure = User32.SetWindowLongPtr(windowHandle, User32.GWL_WNDPROC,
				originalWindowProc);
		} catch (LinkageError | RuntimeException ignored) {
			return false;
		}
		if (!windowProcedureRestored(replacedProcedure, procedureAddress)) return false;
		WindowProc procedure = customWindowProc;
		customWindowProc = null;
		originalWindowProc = MemoryUtil.NULL;
		subclassedWindow = MemoryUtil.NULL;
		procedure.free();
		return true;
	}

	/** Removes the painted Win32 frame but keeps its resize behavior through edge hit testing. */
	private static long handleWindowMessage(long window, int message, long wParam, long lParam) {
		try {
			if (message == User32.WM_NCCALCSIZE) {
				if (User32.IsZoomed(window)) useMonitorWorkArea(window, lParam);
				return 0L;
			}
			if (message == User32.WM_NCHITTEST) {
				long originalResult = callOriginalWindowProc(window, message, wParam, lParam);
				try (MemoryStack stack = MemoryStack.stackPush()) {
					RECT bounds = RECT.calloc(stack);
					if (!User32.GetWindowRect(window, bounds)) return originalResult;
					int dpi = Math.max(96, User32.GetDpiForWindow(window));
					int x = signedLowWord(lParam);
					int y = signedHighWord(lParam);
					if (isWindowControlRegion(x, y, bounds.left(), bounds.top(), bounds.right(),
						bounds.bottom(), dpi)) return User32.HTCLIENT;
					if (originalResult != User32.HTCLIENT) return originalResult;
					int border = Math.max(1, Math.round(RESIZE_BORDER_DIP * dpi / 96f));
					return resizeHitTest(x, y,
						bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), border,
						User32.IsZoomed(window));
				}
			}
		} catch (LinkageError | RuntimeException ignored) {
		}
		return callOriginalWindowProc(window, message, wParam, lParam);
	}

	private static void useMonitorWorkArea(long window, long rectAddress) {
		if (rectAddress == MemoryUtil.NULL) return;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			long monitor = User32.MonitorFromWindow(window, User32.MONITOR_DEFAULTTONEAREST);
			if (monitor == MemoryUtil.NULL) return;
			MONITORINFOEX monitorInfo = MONITORINFOEX.calloc(stack).cbSize(MONITORINFOEX.SIZEOF);
			if (User32.GetMonitorInfo(monitor, monitorInfo)) RECT.create(rectAddress).set(monitorInfo.rcWork());
		}
	}

	private static long callOriginalWindowProc(long window, int message, long wParam, long lParam) {
		long original = originalWindowProc;
		try {
			return original == MemoryUtil.NULL ? User32.DefWindowProc(window, message, wParam, lParam)
				: User32.nCallWindowProc(original, window, message, wParam, lParam);
		} catch (LinkageError | RuntimeException ignored) {
			try {
				return User32.DefWindowProc(window, message, wParam, lParam);
			} catch (LinkageError | RuntimeException unavailable) {
				return 0L;
			}
		}
	}

	private static boolean validGlfwWindow(long glfwWindowHandle) {
		return usesCustomFrame() && glfwWindowHandle != MemoryUtil.NULL;
	}

	private static void releaseCapture() {
		try (SharedLibrary user32 = Library.loadNative(WindowsTitleBar.class, "org.lwjgl", "user32")) {
			long function = user32.getFunctionAddress("ReleaseCapture");
			if (function != MemoryUtil.NULL) JNI.invokeI(function);
		}
	}

	private WindowsTitleBar() {}
}
