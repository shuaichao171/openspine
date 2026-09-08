package com.openspine.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;

final class McpImages {
	static final int MAX_DIMENSION = 2048;
	static final long MAX_PIXELS = 4L * 1024 * 1024;
	static final int MAX_PNG_BYTES = 16 * 1024 * 1024;
	static final int MAX_METADATA_BYTES = 64 * 1024;

	record Result(JsonValue metadata, List<byte[]> images) {}

	static byte[] encode(Pixmap source, boolean flipY, int maxWidth, int maxHeight) throws IOException {
		Pixmap scaled = null;
		PixmapIO.PNG encoder = new PixmapIO.PNG();
		try {
			int[] size = fit(source.getWidth(), source.getHeight(), maxWidth, maxHeight);
			Pixmap image = source;
			if (size[0] != source.getWidth() || size[1] != source.getHeight()) {
				scaled = new Pixmap(size[0], size[1], Pixmap.Format.RGBA8888);
				scaled.setBlending(Pixmap.Blending.None);
				scaled.setFilter(Pixmap.Filter.BiLinear);
				scaled.drawPixmap(source, 0, 0, source.getWidth(), source.getHeight(), 0, 0, size[0], size[1]);
				image = scaled;
			}
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			encoder.setFlipY(flipY);
			encoder.setCompression(6);
			encoder.write(output, image);
			if (output.size() > MAX_PNG_BYTES) throw new IOException("MCP PNG response exceeds the size limit.");
			return output.toByteArray();
		} finally {
			encoder.dispose();
			if (scaled != null) scaled.dispose();
		}
	}

	static int[] fit(int width, int height, int maxWidth, int maxHeight) {
		if (width <= 0 || height <= 0 || maxWidth < 64 || maxHeight < 64
			|| maxWidth > MAX_DIMENSION || maxHeight > MAX_DIMENSION)
			throw new IllegalArgumentException("Image dimensions must be between 64 and 2048.");
		double scale = Math.min(1d, Math.min(maxWidth / (double)width, maxHeight / (double)height));
		return new int[] {Math.max(1, (int)Math.floor(width * scale)), Math.max(1, (int)Math.floor(height * scale))};
	}

	static JsonValue object() { return new JsonValue(JsonValue.ValueType.object); }
	static void put(JsonValue object, String name, String value) { object.addChild(name, new JsonValue(value)); }
	static void put(JsonValue object, String name, long value) { object.addChild(name, new JsonValue(value)); }
	static void put(JsonValue object, String name, double value) { object.addChild(name, new JsonValue(value)); }

	static JsonValue readJson(Path path) throws IOException {
		String text = new String(readLimited(path, MAX_METADATA_BYTES), java.nio.charset.StandardCharsets.UTF_8);
		StrictJson.validate(text);
		return new JsonReader().parse(text);
	}

	static byte[] readLimited(Path path, int limit) throws IOException {
		if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > limit)
			throw new IOException("Invalid or oversized MCP image response file.");
		try (var input = Files.newInputStream(path)) {
			byte[] bytes = input.readNBytes(limit + 1);
			if (bytes.length > limit) throw new IOException("MCP image response exceeds the size limit.");
			return bytes;
		}
	}

	static void writeJson(Path path, JsonValue value) throws IOException {
		Path temporary = Files.createTempFile(path.getParent(), ".mcp-", ".tmp");
		try {
			Files.writeString(temporary, value.toJson(OutputType.json));
			try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally { Files.deleteIfExists(temporary); }
	}

	private McpImages() {}
}
