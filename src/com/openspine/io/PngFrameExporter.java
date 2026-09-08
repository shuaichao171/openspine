package com.openspine.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.FloatArray;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.utils.SkeletonClipping;
import com.openspine.view.PageAwareSkeletonRenderer;

/** Renders the current applied skeleton pose to a cropped PNG image. */
public final class PngFrameExporter {
	public static final float MIN_SCALE = 0.01f;
	public static final float MAX_SCALE = 64f;
	public static final int MAX_PADDING = 4096;
	public static final int MIN_COMPRESSION = 0;
	public static final int MAX_COMPRESSION = 9;
	public static final int DEFAULT_COMPRESSION = 6;
	public static final long MAX_PIXELS = 64L * 1024L * 1024L;

	public static final int DEFAULT_BACKGROUND_RGBA = 0x131418ff;

	public record Settings(float scale, int padding, int compression, boolean transparent,
		boolean premultipliedAlpha, int backgroundRgba) {
		public Settings {
			if (!Float.isFinite(scale) || scale < MIN_SCALE || scale > MAX_SCALE)
				throw new IllegalArgumentException("PNG 缩放必须在 " + MIN_SCALE + " 到 " + MAX_SCALE + " 之间");
			if (padding < 0 || padding > MAX_PADDING)
				throw new IllegalArgumentException("PNG 留白必须在 0 到 " + MAX_PADDING + " 像素之间");
			if (compression < MIN_COMPRESSION || compression > MAX_COMPRESSION)
				throw new IllegalArgumentException("PNG 压缩级别必须在 0 到 9 之间");
		}

		public Settings(float scale, int padding, boolean transparent, boolean premultipliedAlpha) {
			this(scale, padding, DEFAULT_COMPRESSION, transparent, premultipliedAlpha,
				DEFAULT_BACKGROUND_RGBA);
		}

		public Settings(float scale, int padding, int compression, boolean transparent,
			boolean premultipliedAlpha) {
			this(scale, padding, compression, transparent, premultipliedAlpha, DEFAULT_BACKGROUND_RGBA);
		}
	}

	public record Bounds(float x, float y, float width, float height) {
		public Bounds {
			if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
				|| width < 0f || height < 0f)
				throw new IllegalArgumentException("PNG 导出边界无效");
		}
	}

	public record OutputSize(int width, int height) {}

	public record ExportResult(Path path, int width, int height, Bounds bounds) {}

	/** Measures Region and Mesh attachments eligible for current-frame export in the applied pose. */
	public static Bounds measure(Skeleton skeleton) {
		if (skeleton == null) throw new IllegalArgumentException("没有可导出的骨架");
		Array<Slot> suppressedSlots = new Array<>();
		Array<Attachment> suppressedAttachments = new Array<>();
		suppressNonExportableAttachments(skeleton, suppressedSlots, suppressedAttachments);
		Vector2 offset = new Vector2();
		Vector2 size = new Vector2();
		SkeletonClipping clipper = new SkeletonClipping();
		try {
			skeleton.getBounds(offset, size, new FloatArray(), clipper);
		} finally {
			restoreSuppressedAttachments(suppressedSlots, suppressedAttachments);
		}
		if (!Float.isFinite(offset.x) || !Float.isFinite(offset.y)
			|| !Float.isFinite(size.x) || !Float.isFinite(size.y) || size.x < 0f || size.y < 0f)
			throw new IllegalStateException("当前姿态没有可导出的 Region 或 Mesh 附件");
		return new Bounds(offset.x, offset.y, size.x, size.y);
	}

	public static OutputSize outputSize(Bounds bounds, Settings settings, int maxDimension) {
		if (bounds == null || settings == null) throw new IllegalArgumentException("PNG 导出设置不完整");
		if (maxDimension <= 0) throw new IllegalArgumentException("GPU 最大纹理尺寸无效");
		long contentWidth = scaledPixels(bounds.width(), settings.scale());
		long contentHeight = scaledPixels(bounds.height(), settings.scale());
		long width = contentWidth + settings.padding() * 2L;
		long height = contentHeight + settings.padding() * 2L;
		if (width > maxDimension || height > maxDimension)
			throw new IllegalArgumentException("PNG 尺寸 " + width + "x" + height
				+ " 超过 GPU 限制 " + maxDimension + "x" + maxDimension);
		if (width * height > MAX_PIXELS)
			throw new IllegalArgumentException("PNG 像素数量过大：" + width + "x" + height);
		return new OutputSize((int)width, (int)height);
	}

	public static ExportResult write(Skeleton skeleton, Path requestedTarget, Settings settings) throws IOException {
		return write(skeleton, requestedTarget, settings, false);
	}

	public static ExportResult write(Skeleton skeleton, Path requestedTarget, Settings settings,
		boolean sourcePremultipliedAlpha) throws IOException {
		if (Gdx.gl == null) throw new IllegalStateException("PNG 导出需要可用的 OpenGL 上下文");
		Path target = normalizeTarget(requestedTarget);
		Bounds bounds = measure(skeleton);
		OutputSize size = outputSize(bounds, settings, maxTextureSize());
		Pixmap pixels = render(skeleton, bounds, size, settings, sourcePremultipliedAlpha);
		try {
			if (settings.transparent() && !settings.premultipliedAlpha()) unpremultiply(pixels);
			writePixmapAtomic(target, pixels, settings.compression(), true);
		} finally {
			pixels.dispose();
		}
		return new ExportResult(target, size.width(), size.height(), bounds);
	}

	static Pixmap render(Skeleton skeleton, Bounds bounds, OutputSize size, Settings settings) {
		return render(skeleton, bounds, size, settings, false);
	}

	public static Pixmap render(Skeleton skeleton, Bounds bounds, OutputSize size, Settings settings,
		boolean sourcePremultipliedAlpha) {
		FrameBuffer frameBuffer = null;
		PolygonSpriteBatch batch = null;
		try {
			FrameBufferBuilder builder = new FrameBufferBuilder(size.width(), size.height());
			builder.addBasicColorTextureAttachment(Pixmap.Format.RGBA8888);
			frameBuffer = builder.build();
			batch = new PolygonSpriteBatch();
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, size.width() / settings.scale(), size.height() / settings.scale());
			camera.position.set(bounds.x() + bounds.width() * 0.5f,
				bounds.y() + bounds.height() * 0.5f, 0f);
			camera.update();

			frameBuffer.begin();
			try {
				if (settings.transparent()) Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
				else {
					int rgba = settings.backgroundRgba();
					Gdx.gl.glClearColor(((rgba >>> 24) & 0xff) / 255f, ((rgba >>> 16) & 0xff) / 255f,
						((rgba >>> 8) & 0xff) / 255f, 1f);
				}
				Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
				batch.setProjectionMatrix(camera.combined);
				batch.begin();
				Array<Slot> suppressedSlots = new Array<>();
				Array<Attachment> suppressedAttachments = new Array<>();
				suppressNonExportableAttachments(skeleton, suppressedSlots, suppressedAttachments);
				try {
					newRenderer(sourcePremultipliedAlpha).draw(batch, skeleton);
				} finally {
					restoreSuppressedAttachments(suppressedSlots, suppressedAttachments);
					if (batch.isDrawing()) batch.end();
				}
				return Pixmap.createFromFrameBuffer(0, 0, size.width(), size.height());
			} finally {
				frameBuffer.end();
			}
		} finally {
			if (batch != null) batch.dispose();
			if (frameBuffer != null) frameBuffer.dispose();
		}
	}

	static SkeletonRenderer newRenderer(boolean premultipliedAlpha) {
		SkeletonRenderer renderer = new PageAwareSkeletonRenderer();
		renderer.setPremultipliedAlpha(premultipliedAlpha);
		return renderer;
	}

	public static void unpremultiply(Pixmap pixmap) {
		if (pixmap == null || pixmap.getFormat() != Pixmap.Format.RGBA8888)
			throw new IllegalArgumentException("反预乘需要 RGBA8888 Pixmap");
		ByteBuffer pixels = pixmap.getPixels();
		int count = Math.multiplyExact(pixmap.getWidth(), pixmap.getHeight());
		for (int pixel = 0; pixel < count; pixel++) {
			int offset = pixel * 4;
			int alpha = pixels.get(offset + 3) & 0xff;
			if (alpha == 255) continue;
			if (alpha == 0) {
				pixels.put(offset, (byte)0);
				pixels.put(offset + 1, (byte)0);
				pixels.put(offset + 2, (byte)0);
				continue;
			}
			pixels.put(offset, (byte)unpremultiplyComponent(pixels.get(offset) & 0xff, alpha));
			pixels.put(offset + 1, (byte)unpremultiplyComponent(pixels.get(offset + 1) & 0xff, alpha));
			pixels.put(offset + 2, (byte)unpremultiplyComponent(pixels.get(offset + 2) & 0xff, alpha));
		}
	}

	static int unpremultiplyComponent(int value, int alpha) {
		if (alpha <= 0) return 0;
		if (alpha >= 255) return Math.max(0, Math.min(255, value));
		return Math.min(255, (Math.max(0, value) * 255 + alpha / 2) / alpha);
	}

	static void writePixmapAtomic(Path requestedTarget, Pixmap pixmap, boolean flipY) throws IOException {
		writePixmapAtomic(requestedTarget, pixmap, DEFAULT_COMPRESSION, flipY);
	}

	static void writePixmapAtomic(Path requestedTarget, Pixmap pixmap, int compression, boolean flipY)
		throws IOException {
		Path target = normalizeTarget(requestedTarget);
		if (pixmap == null || pixmap.isDisposed()) throw new IllegalArgumentException("PNG 像素数据无效");
		if (compression < MIN_COMPRESSION || compression > MAX_COMPRESSION)
			throw new IllegalArgumentException("PNG 压缩级别必须在 0 到 9 之间");
		Path parent = target.getParent();
		if (parent == null || !Files.isDirectory(parent))
			throw new IOException("PNG 目标目录不存在：" + parent);
		if (Files.exists(target) && !Files.isRegularFile(target))
			throw new IOException("PNG 目标不是普通文件：" + target);

		Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		try {
			try {
				PixmapIO.writePNG(new FileHandle(temporary.toFile()), pixmap, compression, flipY);
			} catch (RuntimeException error) {
				throw new IOException("无法编码 PNG：" + target.getFileName(), error);
			}
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static Path normalizeTarget(Path requestedTarget) {
		if (requestedTarget == null) throw new IllegalArgumentException("PNG 目标路径不能为空");
		Path target = requestedTarget.toAbsolutePath().normalize();
		Path fileName = target.getFileName();
		if (fileName == null || !fileName.toString().toLowerCase(Locale.ROOT).endsWith(".png"))
			throw new IllegalArgumentException("PNG 目标路径必须以 .png 结尾");
		return target;
	}

	private static long scaledPixels(float worldSize, float scale) {
		double scaled = Math.ceil((double)worldSize * scale);
		if (!Double.isFinite(scaled) || scaled > Integer.MAX_VALUE)
			throw new IllegalArgumentException("PNG 导出尺寸过大");
		return Math.max(1L, (long)scaled);
	}

	public static int maxTextureSize() {
		IntBuffer value = BufferUtils.newIntBuffer(1);
		Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, value);
		int size = value.get(0);
		if (size <= 0) throw new IllegalStateException("无法读取 GPU 最大纹理尺寸");
		return size;
	}

	static void suppressNonExportableAttachments(Skeleton skeleton, Array<Slot> suppressedSlots,
		Array<Attachment> suppressedAttachments) {
		Array<Slot> drawOrder = skeleton.getDrawOrder().getAppliedPose();
		for (Slot slot : drawOrder) {
			Attachment attachment = slot.getAppliedPose().getAttachment();
			if (attachment == null || slot.getData().getVisible() && isExportableAttachment(attachment)) continue;
			suppressedSlots.add(slot);
			suppressedAttachments.add(attachment);
			slot.getAppliedPose().setAttachment(null);
		}
	}

	static void restoreSuppressedAttachments(Array<Slot> suppressedSlots,
		Array<Attachment> suppressedAttachments) {
		for (int index = 0; index < suppressedSlots.size; index++)
			suppressedSlots.get(index).getAppliedPose().setAttachment(suppressedAttachments.get(index));
	}

	private static boolean isExportableAttachment(Attachment attachment) {
		return attachment instanceof RegionAttachment || attachment instanceof MeshAttachment
			|| attachment instanceof ClippingAttachment;
	}

	private PngFrameExporter() {}
}
