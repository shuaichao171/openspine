package com.openspine.io;

import java.util.Map;
import java.util.WeakHashMap;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;

/** Retains each loaded atlas page's alpha convention by texture identity. */
public final class AtlasAlphaModes {
	private static final Map<Texture, Boolean> PREMULTIPLIED = new WeakHashMap<>();

	public static TextureAtlas load(FileHandle atlasFile) {
		if (atlasFile == null || !atlasFile.exists())
			throw new IllegalArgumentException("Atlas file does not exist: " + atlasFile);
		TextureAtlasData data = AtlasFiles.readData(atlasFile, atlasFile.parent(), false);
		TextureAtlas atlas = new TextureAtlas();
		try {
			atlas.load(data);
			register(data);
			return atlas;
		} catch (RuntimeException | Error failure) {
			atlas.dispose();
			throw failure;
		}
	}

	static void register(TextureAtlasData data) {
		if (data == null) return;
		synchronized (PREMULTIPLIED) {
			for (Page page : data.getPages()) if (page.texture != null)
				PREMULTIPLIED.put(page.texture, page.pma);
		}
	}

	public static boolean premultiplied(Texture texture, boolean fallback) {
		if (texture == null) return fallback;
		synchronized (PREMULTIPLIED) {
			Boolean value = PREMULTIPLIED.get(texture);
			return value == null ? fallback : value;
		}
	}

	private AtlasAlphaModes() { }
}
