package com.openspine.io;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;

/** Keeps atlas page and region names independent of the operating system's default charset. */
final class AtlasFiles {
	static TextureAtlasData readData(FileHandle source, FileHandle imagesDirectory, boolean flip) {
		FileHandle utf8 = new FileHandle(source.path()) {
			@Override public BufferedReader reader(int bufferSize) {
				return source.reader(bufferSize, StandardCharsets.UTF_8.name());
			}
		};
		return new TextureAtlasData(utf8, imagesDirectory, flip);
	}

	private AtlasFiles() {}
}
