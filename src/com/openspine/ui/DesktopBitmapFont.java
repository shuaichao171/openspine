package com.openspine.ui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Builds a small Unicode bitmap font atlas from the desktop's installed fonts. */
final class DesktopBitmapFont {
	private static final String UI_GLYPHS =
		"一三上下不与且世丢两个中串为主举义之也予事二于互些交亮人仅仍从他代以们件价任会但位体何作使例供依侧便保修值做停像允充先免兜入全关其具内再冒写决出击分切列则创初删判别到制刷前剪功加动助势包化区单占即历原参及双发取变口只可右号各合同名后向否含听启命和器回因图在地均坐块型域基填境增处复外大失头如始姿子字存孙安完定实容宽寸对导封将小少尚尝就尺尾局层屏展属嵌工左已布帧帮幕平并序应底度建开式引弹归当录形往径循必快态性总恢意成我或户所手扑打找把抛护拓拖拟择括拽拾持挂指按换据排接控描提插摆撤播操支收改放故效数整文断新方旋无既旧时是显晚暂曲更替最有朝未末本机权束条来板构析枚架某染查标树样根格框桌档检槽模次正此步段每比没法泛测消涉添清渲源滑满演点烟焦照父片状独环现理生用由画界留畸白的皮监目直相看真着码研确示种秒称移程空竖端符等筛简算管箭箱类粘系素索红约级纹线组绑结绕绘给统继绪续缀缓编缩网置联聚肤能自至致色节若获菜藏行补表被装要视角解触计订认记许设证识试话该误读调象负责败贴费资赋赖起足跟路跳身轨转软轴轻载辅辑输边过运返还这进连追退适选递通速造遍道避部都配释里重量钩链销错键长闭间附限除随隐隔集零需静面顶项顺须预颜额驱验骨骼高默鼠" +
		"塞尔损活激版览贝非请，。、！？：；（）【】《》“”‘’…·—✓×";

	private DesktopBitmapFont() {}

	static BitmapFont create() {
		try {
			return build();
		} catch (Throwable error) {
			if (Gdx.app != null) Gdx.app.error("OpenSpine", "无法创建桌面中文字体，已使用默认字体", error);
			return new BitmapFont();
		}
	}

	private static BitmapFont build() {
		Set<Character> chars = uiCharacters();

		Font awtFont = new Font(findFontFamily(), Font.PLAIN, 15);
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D probeGraphics = probe.createGraphics();
		configure(probeGraphics);
		probeGraphics.setFont(awtFont);
		FontMetrics metrics = probeGraphics.getFontMetrics();
		int cellWidth = Math.max(16, metrics.getMaxAdvance() + 2);
		int cellHeight = Math.max(18, metrics.getHeight() + 2);
		probeGraphics.dispose();

		int columns = Math.max(1, (int)Math.ceil(Math.sqrt(chars.size())));
		int rows = (chars.size() + columns - 1) / columns;
		int atlasWidth = nextPowerOfTwo(columns * cellWidth);
		int atlasHeight = nextPowerOfTwo(rows * cellHeight);
		BufferedImage image = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		configure(graphics);
		graphics.setFont(awtFont);
		graphics.setColor(java.awt.Color.WHITE);

		BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
		data.name = awtFont.getFamily();
		data.lineHeight = cellHeight;
		data.capHeight = metrics.getAscent();
		data.ascent = 0;
		data.descent = -metrics.getDescent();
		data.down = -cellHeight;
		data.spaceXadvance = metrics.charWidth(' ');
		data.xHeight = metrics.getAscent();

		int index = 0;
		for (char c : chars) {
			int column = index % columns;
			int row = index / columns;
			int x = column * cellWidth;
			int y = row * cellHeight;
			graphics.drawString(String.valueOf(c), x + 1, y + 1 + metrics.getAscent());

			BitmapFont.Glyph glyph = new BitmapFont.Glyph();
			glyph.id = c;
			glyph.srcX = x;
			glyph.srcY = y;
			glyph.width = cellWidth;
			glyph.height = cellHeight;
			glyph.xoffset = 0;
			glyph.yoffset = -cellHeight;
			configureGlyphAdvance(glyph, glyphAdvance(metrics, c));
			glyph.page = 0;
			data.setGlyph(c, glyph);
			index++;
		}
		graphics.dispose();

		Pixmap pixmap = null;
		Texture texture = null;
		try {
			pixmap = new Pixmap(atlasWidth, atlasHeight, Pixmap.Format.RGBA8888);
			copyArgbToRgba(image.getRGB(0, 0, atlasWidth, atlasHeight, null, 0, atlasWidth), pixmap.getPixels());
			texture = new Texture(pixmap);
			texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
			BitmapFont font = new BitmapFont(data, new TextureRegion(texture), false);
			font.setOwnsTexture(true);
			font.setUseIntegerPositions(true);
			data.missingGlyph = data.getGlyph('?');
			texture = null;
			return font;
		} finally {
			if (pixmap != null) pixmap.dispose();
			if (texture != null) texture.dispose();
		}
	}

	static int glyphAdvance(FontMetrics metrics, char character) {
		if (metrics == null) throw new IllegalArgumentException("Font metrics are required");
		return Math.max(1, metrics.charWidth(character));
	}

	static void configureGlyphAdvance(BitmapFont.Glyph glyph, int advance) {
		if (glyph == null) throw new IllegalArgumentException("Glyph is required");
		glyph.xadvance = Math.max(1, advance);
		// The atlas cell is wider than the visible glyph. Use the logical advance at line ends so
		// TextField does not place its cursor after the cell's transparent right-hand padding.
		glyph.fixedWidth = true;
	}

	static Set<Character> uiCharacters() {
		Set<Character> chars = new LinkedHashSet<>();
		for (char c = 32; c <= 126; c++) chars.add(c);
		for (int i = 0; i < UI_GLYPHS.length(); i++) chars.add(UI_GLYPHS.charAt(i));
		Charset gb2312 = Charset.forName("GB2312");
		byte[] encoded = new byte[2];
		for (int high = 0xa1; high <= 0xf7; high++) {
			encoded[0] = (byte)high;
			for (int low = 0xa1; low <= 0xfe; low++) {
				encoded[1] = (byte)low;
				String decoded = new String(encoded, gb2312);
				if (decoded.length() == 1 && decoded.charAt(0) != '\ufffd') chars.add(decoded.charAt(0));
			}
		}
		return chars;
	}

	static void copyArgbToRgba(int[] argb, ByteBuffer rgba) {
		if (argb == null || rgba == null || rgba.capacity() < argb.length * 4L)
			throw new IllegalArgumentException("RGBA buffer is too small for the source pixels.");
		rgba.clear();
		for (int color : argb) {
			rgba.put((byte)(color >>> 16));
			rgba.put((byte)(color >>> 8));
			rgba.put((byte)color);
			rgba.put((byte)(color >>> 24));
		}
		rgba.flip();
	}

	private static void configure(Graphics2D graphics) {
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
	}

	private static String findFontFamily() {
		String[] preferred = {"Microsoft YaHei UI", "Microsoft YaHei", "SimHei", "Noto Sans CJK SC", "Dialog"};
		String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		for (String candidate : preferred) {
			for (String family : available) if (family.equalsIgnoreCase(candidate)) return family;
		}
		return Font.DIALOG;
	}

	static int nextPowerOfTwo(int value) {
		if (value <= 0) throw new IllegalArgumentException("Texture dimension must be positive.");
		int result = 1;
		while (result < value) {
			if (result > 1 << 29) throw new IllegalArgumentException("Texture dimension is too large.");
			result <<= 1;
		}
		return result;
	}
}
