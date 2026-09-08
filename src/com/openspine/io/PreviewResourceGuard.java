package com.openspine.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Resource boundary and decode budgets for untrusted MCP preview projects. */
public final class PreviewResourceGuard {
	private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
	private static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;
	private static final long MAX_IMAGE_PIXELS = 16L * 1024 * 1024;
	private static final long MAX_TOTAL_PIXELS = 64L * 1024 * 1024;
	private final Path root;
	private final Set<Path> files = new HashSet<>();
	private final Set<Path> images = new HashSet<>();
	private long bytes;
	private long pixels;

	public PreviewResourceGuard(Path root) throws IOException {
		this.root = root.toRealPath();
	}

	public Path directory(Path path) {
		Path real = withinRoot(path);
		if (!Files.isDirectory(real)) throw new IllegalArgumentException("Preview resource directory not found: " + path);
		return real;
	}

	public Path file(Path path) {
		Path real = withinRoot(path);
		try {
			if (!Files.isRegularFile(real)) throw new IOException("Not a regular file: " + path);
			if (!files.contains(real)) {
				long size = Files.size(real);
				if (size > MAX_FILE_BYTES || bytes + size > MAX_TOTAL_BYTES || files.size() >= 2048)
					throw new IOException("Preview resource size or file-count limit exceeded.");
				bytes += size;
				files.add(real);
			}
			return real;
		} catch (IOException error) {
			throw new IllegalArgumentException(error.getMessage(), error);
		}
	}

	public Path image(Path path) {
		Path real = file(path);
		if (images.contains(real)) return real;
		try (ImageInputStream input = ImageIO.createImageInputStream(real.toFile())) {
			if (input == null) throw new IOException("Unable to inspect preview image: " + path);
			var readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) throw new IOException("Unsupported preview image: " + path);
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0), height = reader.getHeight(0);
				long count = (long)width * height;
				if (width <= 0 || height <= 0 || width > 8192 || height > 8192
					|| count > MAX_IMAGE_PIXELS || pixels + count > MAX_TOTAL_PIXELS)
					throw new IOException("Preview image pixel limit exceeded: " + path);
				pixels += count;
				images.add(real);
			} finally { reader.dispose(); }
		} catch (IOException error) {
			throw new IllegalArgumentException(error.getMessage(), error);
		}
		return real;
	}

	private Path withinRoot(Path path) {
		try {
			Path absolute = path.toAbsolutePath().normalize();
			if (!absolute.startsWith(root)) throw new IOException("Preview resource is outside the configured MCP root: " + path);
			Path real = absolute.toRealPath();
			if (!real.startsWith(root)) throw new IOException("Preview resource is outside the configured MCP root: " + path);
			return real;
		} catch (IOException error) {
			throw new IllegalArgumentException(error.getMessage(), error);
		}
	}
}
