package com.openspine;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.Pointer;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.windows.User32;

/** Win32 Explorer-style file dialogs owned by the GLFW editor window. */
final class WindowsFileDialog {
	private static final int FILE_BUFFER_CHARS = 32768;
	private static final int OFN_EXPLORER = 0x00080000;
	private static final int OFN_HIDEREADONLY = 0x00000004;
	private static final int OFN_NOCHANGEDIR = 0x00000008;
	private static final int OFN_PATHMUSTEXIST = 0x00000800;
	private static final int OFN_FILEMUSTEXIST = 0x00001000;
	private static final int MB_OK = 0x00000000;
	private static final int MB_YESNO = 0x00000004;
	private static final int MB_ICONWARNING = 0x00000030;
	private static final int MB_DEFBUTTON2 = 0x00000100;
	private static final int IDYES = 6;

	private static final int OFN_SIZE = Pointer.BITS64 ? 152 : 88;
	private static final int HWND_OWNER = Pointer.BITS64 ? 8 : 4;
	private static final int HINSTANCE = Pointer.BITS64 ? 16 : 8;
	private static final int FILTER = Pointer.BITS64 ? 24 : 12;
	private static final int CUSTOM_FILTER = Pointer.BITS64 ? 32 : 16;
	private static final int MAX_CUSTOM_FILTER = Pointer.BITS64 ? 40 : 20;
	private static final int FILTER_INDEX = Pointer.BITS64 ? 44 : 24;
	private static final int FILE = Pointer.BITS64 ? 48 : 28;
	private static final int MAX_FILE = Pointer.BITS64 ? 56 : 32;
	private static final int FILE_TITLE = Pointer.BITS64 ? 64 : 36;
	private static final int MAX_FILE_TITLE = Pointer.BITS64 ? 72 : 40;
	private static final int INITIAL_DIRECTORY = Pointer.BITS64 ? 80 : 44;
	private static final int TITLE = Pointer.BITS64 ? 88 : 48;
	private static final int FLAGS = Pointer.BITS64 ? 96 : 52;
	private static final int DEFAULT_EXTENSION = Pointer.BITS64 ? 104 : 60;
	private static final int CUSTOM_DATA = Pointer.BITS64 ? 112 : 64;
	private static final int HOOK = Pointer.BITS64 ? 120 : 68;
	private static final int TEMPLATE_NAME = Pointer.BITS64 ? 128 : 72;
	private static final int RESERVED_POINTER = Pointer.BITS64 ? 136 : 76;
	private static final int RESERVED = Pointer.BITS64 ? 144 : 80;
	private static final int FLAGS_EX = Pointer.BITS64 ? 148 : 84;

	private static volatile long ownerWindow;

	record Filter(String label, String pattern) {
		Filter {
			if (label == null || label.isBlank()) throw new IllegalArgumentException("Filter label is required");
			if (pattern == null || pattern.isBlank()) throw new IllegalArgumentException("Filter pattern is required");
		}
	}

	record Selection(String path, int filterIndex) {}

	static boolean installOwner(long glfwWindowHandle) {
		if (!supported() || glfwWindowHandle == MemoryUtil.NULL) {
			ownerWindow = MemoryUtil.NULL;
			return false;
		}
		try {
			ownerWindow = GLFWNativeWin32.glfwGetWin32Window(glfwWindowHandle);
			return ownerWindow != MemoryUtil.NULL;
		} catch (LinkageError | RuntimeException error) {
			ownerWindow = MemoryUtil.NULL;
			return false;
		}
	}

	static boolean available() {
		return supported() && ownerWindow != MemoryUtil.NULL;
	}

	/** Makes a temporary AWT owner window itself owned by the GLFW editor window. */
	static boolean attachSwingOwner(String windowTitle) {
		if (!available() || windowTitle == null || windowTitle.isBlank()) return false;
		try (SharedLibrary user32 = Library.loadNative(WindowsFileDialog.class, "org.lwjgl", "user32");
			MemoryStack stack = MemoryStack.stackPush()) {
			long findWindow = user32.getFunctionAddress("FindWindowW");
			if (findWindow == MemoryUtil.NULL) return false;
			long title = MemoryUtil.memAddress(stack.UTF16(windowTitle, true));
			long swingWindow = JNI.invokePPP(MemoryUtil.NULL, title, findWindow);
			if (swingWindow == MemoryUtil.NULL) return false;
			User32.SetWindowLongPtr(swingWindow, User32.GWL_HWNDPARENT, ownerWindow);
			return User32.GetWindowLongPtr(swingWindow, User32.GWL_HWNDPARENT) == ownerWindow;
		} catch (LinkageError | RuntimeException error) {
			return false;
		}
	}

	static boolean apiAvailable() {
		if (!supported()) return false;
		try (SharedLibrary comdlg32 = Library.loadNative(WindowsFileDialog.class, "org.lwjgl", "comdlg32")) {
			return comdlg32.getFunctionAddress("GetOpenFileNameW") != MemoryUtil.NULL
				&& comdlg32.getFunctionAddress("GetSaveFileNameW") != MemoryUtil.NULL
				&& comdlg32.getFunctionAddress("CommDlgExtendedError") != MemoryUtil.NULL;
		} catch (LinkageError | RuntimeException error) {
			return false;
		}
	}

	static Selection open(String title, Path initialDirectory, List<Filter> filters, int filterIndex) {
		return show(true, title, initialDirectory, null, filters, filterIndex);
	}

	static Selection save(String title, Path initialDirectory, String suggestedFileName,
		List<Filter> filters, int filterIndex) {
		return show(false, title, initialDirectory, suggestedFileName, filters, filterIndex);
	}

	static boolean confirm(String title, String message) {
		return messageBox(title, message, MB_YESNO | MB_ICONWARNING | MB_DEFBUTTON2) == IDYES;
	}

	static void warning(String title, String message) {
		messageBox(title, message, MB_OK | MB_ICONWARNING);
	}

	static String filterString(List<Filter> filters) {
		if (filters == null || filters.isEmpty()) throw new IllegalArgumentException("At least one filter is required");
		StringBuilder value = new StringBuilder();
		for (Filter filter : filters) value.append(filter.label()).append('\0').append(filter.pattern()).append('\0');
		return value.append('\0').toString();
	}

	static int structureSize() { return OFN_SIZE; }

	private static Selection show(boolean open, String title, Path initialDirectory, String suggestedFileName,
		List<Filter> filters, int requestedFilterIndex) {
		if (!available()) throw new IllegalStateException("Windows file dialog owner is not installed");
		int selectedFilter = Math.max(1, Math.min(requestedFilterIndex, filters.size()));
		ByteBuffer structure = MemoryUtil.memCalloc(OFN_SIZE);
		ByteBuffer fileBuffer = MemoryUtil.memCalloc(FILE_BUFFER_CHARS * Character.BYTES);
		try (SharedLibrary comdlg32 = Library.loadNative(WindowsFileDialog.class, "org.lwjgl", "comdlg32");
			MemoryStack stack = MemoryStack.stackPush()) {
			long function = comdlg32.getFunctionAddress(open ? "GetOpenFileNameW" : "GetSaveFileNameW");
			long extendedError = comdlg32.getFunctionAddress("CommDlgExtendedError");
			if (function == MemoryUtil.NULL || extendedError == MemoryUtil.NULL)
				throw new IllegalStateException("Windows common file dialog functions are unavailable");
			if (suggestedFileName != null && !suggestedFileName.isBlank()) {
				if (suggestedFileName.length() >= FILE_BUFFER_CHARS)
					throw new IllegalArgumentException("Suggested file name is too long");
				MemoryUtil.memUTF16(suggestedFileName, true, fileBuffer);
			}

			long address = MemoryUtil.memAddress(structure);
			MemoryUtil.memPutInt(address, OFN_SIZE);
			putPointer(address, HWND_OWNER, ownerWindow);
			putPointer(address, HINSTANCE, MemoryUtil.NULL);
			putPointer(address, FILTER, MemoryUtil.memAddress(stack.UTF16(filterString(filters), false)));
			putPointer(address, CUSTOM_FILTER, MemoryUtil.NULL);
			MemoryUtil.memPutInt(address + MAX_CUSTOM_FILTER, 0);
			MemoryUtil.memPutInt(address + FILTER_INDEX, selectedFilter);
			putPointer(address, FILE, MemoryUtil.memAddress(fileBuffer));
			MemoryUtil.memPutInt(address + MAX_FILE, FILE_BUFFER_CHARS);
			putPointer(address, FILE_TITLE, MemoryUtil.NULL);
			MemoryUtil.memPutInt(address + MAX_FILE_TITLE, 0);
			putPointer(address, INITIAL_DIRECTORY, initialDirectory == null ? MemoryUtil.NULL
				: MemoryUtil.memAddress(stack.UTF16(initialDirectory.toString(), true)));
			putPointer(address, TITLE, title == null || title.isBlank() ? MemoryUtil.NULL
				: MemoryUtil.memAddress(stack.UTF16(title, true)));
			int flags = OFN_EXPLORER | OFN_HIDEREADONLY | OFN_NOCHANGEDIR | OFN_PATHMUSTEXIST;
			if (open) flags |= OFN_FILEMUSTEXIST;
			MemoryUtil.memPutInt(address + FLAGS, flags);
			putPointer(address, DEFAULT_EXTENSION, MemoryUtil.NULL);
			putPointer(address, CUSTOM_DATA, MemoryUtil.NULL);
			putPointer(address, HOOK, MemoryUtil.NULL);
			putPointer(address, TEMPLATE_NAME, MemoryUtil.NULL);
			putPointer(address, RESERVED_POINTER, MemoryUtil.NULL);
			MemoryUtil.memPutInt(address + RESERVED, 0);
			MemoryUtil.memPutInt(address + FLAGS_EX, 0);

			if (JNI.invokePI(address, function) == 0) {
				int error = JNI.invokeI(extendedError);
				if (error == 0) return null;
				throw new IllegalStateException("Windows file dialog failed (0x"
					+ Integer.toHexString(error).toUpperCase() + ")");
			}
			String path = MemoryUtil.memUTF16(MemoryUtil.memAddress(fileBuffer));
			return new Selection(path, MemoryUtil.memGetInt(address + FILTER_INDEX));
		} finally {
			MemoryUtil.memFree(fileBuffer);
			MemoryUtil.memFree(structure);
		}
	}

	private static int messageBox(String title, String message, int type) {
		if (!available()) throw new IllegalStateException("Windows dialog owner is not installed");
		try (SharedLibrary user32 = Library.loadNative(WindowsFileDialog.class, "org.lwjgl", "user32");
			MemoryStack stack = MemoryStack.stackPush()) {
			long function = user32.getFunctionAddress("MessageBoxW");
			if (function == MemoryUtil.NULL) throw new IllegalStateException("Windows MessageBoxW is unavailable");
			long text = MemoryUtil.memAddress(stack.UTF16(message == null ? "" : message, true));
			long caption = MemoryUtil.memAddress(stack.UTF16(title == null ? "OpenSpine" : title, true));
			return JNI.invokePPPI(ownerWindow, text, caption, type, function);
		}
	}

	private static void putPointer(long structure, int offset, long value) {
		MemoryUtil.memPutAddress(structure + offset, value);
	}

	private static boolean supported() {
		return Platform.get() == Platform.WINDOWS;
	}

	private WindowsFileDialog() {}
}
