package com.openspine.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 程序化 UI 皮肤：不依赖任何外部图片资源。
 * 用 1x1 白纹理 + 颜色着色生成所有 drawable，配合默认 BitmapFont。
 */
public class UiKit implements Disposable {
	static final float SCROLLBAR_THICKNESS = 10f;
	static final float SCROLLBAR_KNOB_LENGTH = 28f;
	static final float SPLIT_HANDLE_THICKNESS = 10f;
	static final float TREE_DISCLOSURE_SIZE = 12f;
	static final float TREE_VISIBILITY_ICON_SIZE = 16f;
	static final float WINDOW_CONTROL_ICON_SIZE = 18f;
	static final int CONTROL_TEXTURE_SIZE = 16;
	static final int CONTROL_CORNER_RADIUS = 5;
	static final int CONTROL_PATCH_BORDER = 6;
	static final float CONTROL_HORIZONTAL_PADDING = 6f;
	static final float CONTROL_VERTICAL_PADDING = 3f;
	static final float BUTTON_TEXT_Y_OFFSET = 3f;
	static final float FIELD_CURSOR_EXTRA_HEIGHT = 2f;
	static final float DIALOG_TITLE_SCALE = 1.1f;
	static final float DIALOG_TITLE_BAR_HEIGHT = 34f;
	static final float DIALOG_TITLE_TOP_PADDING = 6f;
	static final float DIALOG_TITLE_BOTTOM_PADDING = 5f;

	public final BitmapFont font;
	public final TextureRegionDrawable white;
	public final Drawable downDark;
	public final Drawable hoverDark;
	public final Drawable highlight;
	public final Drawable accent;
	public final Drawable danger;
	public final Drawable panelBg;
	public final Drawable scrollBg;
	public final Drawable knob;
	public final Drawable plusIcon;
	public final Drawable minusIcon;
	public final Drawable visibleEyeIcon;
	public final Drawable hiddenEyeIcon;
	public final Drawable windowMinimizeIcon;
	public final Drawable windowMaximizeIcon;
	public final Drawable windowCloseIcon;
	public final Drawable windowCloseActiveIcon;

	public final LabelStyle labelStyle;
	public final LabelStyle dialogBodyStyle;
	public final LabelStyle errorLabelStyle;
	public final TextButtonStyle toolButton;
	public final TextButtonStyle activeToolButton;
	public final TextButtonStyle accentButton;
	public final TextButtonStyle dangerButton;
	public final ButtonStyle windowControlButton;
	public final ButtonStyle windowCloseButton;
	public final SliderStyle sliderStyle;
	public final ScrollPaneStyle scrollStyle;
	public final SelectBoxStyle selectStyle;
	public final TextFieldStyle textFieldStyle;
	public final SplitPaneStyle splitStyle;
	public final TreeStyle treeStyle;
	public final WindowStyle windowStyle;

	private final Texture whiteTex;
	private final Texture roundedTex;
	private final Texture knobTex;
	private final Texture plusTex;
	private final Texture minusTex;
	private final Texture visibleEyeTex;
	private final Texture hiddenEyeTex;
	private final Texture windowMinimizeTex;
	private final Texture windowMaximizeTex;
	private final Texture windowCloseTex;
	private final Texture splitTex;
	private final ArrayList<ThemedDrawable> themedDrawables = new ArrayList<>();
	private final WeakHashMap<TextField, TextFieldStyle> hoveredTextFields = new WeakHashMap<>();
	private final Drawable textFieldHoverBackground;
	private Stage textFieldHoverStage;
	private InputListener textFieldHoverListener;
	private UiAppearance.Palette palette;

	public UiKit() {
		this(UiAppearance.palette(true, UiAppearance.Theme.JADE));
	}

	public UiKit(UiAppearance.Palette palette) {
		if (palette == null) throw new IllegalArgumentException("Appearance palette cannot be null.");
		this.palette = palette;
		font = DesktopBitmapFont.create();

		whiteTex = solidTexture(1, 1, Color.WHITE);
		white = new TextureRegionDrawable(new TextureRegion(whiteTex));
		roundedTex = roundedTexture(CONTROL_TEXTURE_SIZE, CONTROL_CORNER_RADIUS);
		downDark = rounded(UiAppearance.Role.CONTROL);
		hoverDark = rounded(UiAppearance.Role.CONTROL_HOVER);
		highlight = rounded(UiAppearance.Role.ACCENT_HOVER);
		accent = rounded(UiAppearance.Role.ACCENT);
		danger = rounded(UiAppearance.Role.DANGER);
		panelBg = flat(UiAppearance.Role.PANEL);
		scrollBg = flat(UiAppearance.Role.SURFACE);
		knobTex = roundedTexture(14, 5);
		knob = withMinimumSize(themed(new TextureRegionDrawable(new TextureRegion(knobTex)).tint(Color.WHITE),
			UiAppearance.Role.TEXT_MUTED), 14f, 14f);

		// 树展开/收起图标（三角形）
		plusTex = triTexture(6, 6, Color.WHITE, false);
		minusTex = triTexture(6, 6, Color.WHITE, true);
		plusIcon = themed(new TextureRegionDrawable(new TextureRegion(plusTex)).tint(Color.WHITE),
			UiAppearance.Role.ICON);
		minusIcon = themed(new TextureRegionDrawable(new TextureRegion(minusTex)).tint(Color.WHITE),
			UiAppearance.Role.ICON);
		withMinimumSize(plusIcon, TREE_DISCLOSURE_SIZE, TREE_DISCLOSURE_SIZE);
		withMinimumSize(minusIcon, TREE_DISCLOSURE_SIZE, TREE_DISCLOSURE_SIZE);
		visibleEyeTex = eyeTexture(false);
		hiddenEyeTex = eyeTexture(true);
		visibleEyeIcon = themed(new TextureRegionDrawable(new TextureRegion(visibleEyeTex)).tint(Color.WHITE),
			UiAppearance.Role.ICON);
		hiddenEyeIcon = themed(new TextureRegionDrawable(new TextureRegion(hiddenEyeTex)).tint(Color.WHITE),
			UiAppearance.Role.ICON);
		withMinimumSize(visibleEyeIcon, TREE_VISIBILITY_ICON_SIZE, TREE_VISIBILITY_ICON_SIZE);
		withMinimumSize(hiddenEyeIcon, TREE_VISIBILITY_ICON_SIZE, TREE_VISIBILITY_ICON_SIZE);
		windowMinimizeTex = windowControlIconTexture(WindowControlIcon.MINIMIZE);
		windowMaximizeTex = windowControlIconTexture(WindowControlIcon.MAXIMIZE);
		windowCloseTex = windowControlIconTexture(WindowControlIcon.CLOSE);
		windowMinimizeIcon = windowControlIcon(windowMinimizeTex);
		windowMaximizeIcon = windowControlIcon(windowMaximizeTex);
		windowCloseIcon = windowControlIcon(windowCloseTex);
		windowCloseActiveIcon = withMinimumSize(
			new TextureRegionDrawable(new TextureRegion(windowCloseTex)).tint(Color.WHITE),
			WINDOW_CONTROL_ICON_SIZE, WINDOW_CONTROL_ICON_SIZE);

		labelStyle = new LabelStyle(font, new Color(palette.text()));
		dialogBodyStyle = new LabelStyle(font, new Color(palette.mutedText()));
		errorLabelStyle = new LabelStyle(font, new Color(palette.errorText()));

		Drawable pressedDark = rounded(UiAppearance.Role.ACCENT_PRESSED);
		Drawable disabledControl = rounded(UiAppearance.Role.CONTROL_DISABLED);
		toolButton = new TextButtonStyle(downDark, pressedDark, hoverDark, font);
		toolButton.fontColor = new Color(palette.text());
		toolButton.over = hoverDark;
		Color disabledButtonText = new Color(palette.disabledText());
		withCenteredText(withDisabledState(toolButton, disabledControl, disabledButtonText));
		activeToolButton = new TextButtonStyle(accent, pressedDark, highlight, font);
		activeToolButton.fontColor = Color.WHITE;
		activeToolButton.over = highlight;
		withCenteredText(withDisabledState(activeToolButton, disabledControl, disabledButtonText));
		accentButton = new TextButtonStyle(accent, pressedDark, accent, font);
		accentButton.fontColor = Color.WHITE;
		accentButton.over = highlight;
		withCenteredText(withDisabledState(accentButton, disabledControl, disabledButtonText));
		Drawable dangerPressed = rounded(UiAppearance.Role.DANGER_PRESSED);
		Drawable dangerHover = rounded(UiAppearance.Role.DANGER_HOVER);
		dangerButton = new TextButtonStyle(danger, dangerPressed, dangerHover, font);
		dangerButton.fontColor = Color.WHITE;
		dangerButton.over = dangerHover;
		withCenteredText(withDisabledState(dangerButton, disabledControl, disabledButtonText));
		Drawable windowControlHover = flat(UiAppearance.Role.CONTROL_HOVER);
		windowControlButton = new ButtonStyle(scrollBg, flat(UiAppearance.Role.CONTROL), windowControlHover);
		windowControlButton.over = windowControlHover;
		Drawable windowCloseHover = flat(UiAppearance.Role.DANGER);
		windowCloseButton = new ButtonStyle(scrollBg, flat(UiAppearance.Role.DANGER_PRESSED), windowCloseHover);
		windowCloseButton.over = windowCloseHover;

		sliderStyle = new SliderStyle();
		sliderStyle.background = rounded(UiAppearance.Role.SLIDER_TRACK, 0f, 0f);
		sliderStyle.knob = knob;
		sliderStyle.knobBefore = rounded(UiAppearance.Role.ACCENT, 0f, 0f);
		sliderStyle.knobOver = withMinimumSize(
			rounded(UiAppearance.Role.INPUT_TEXT, 0f, 0f), 14f, 14f);
		sliderStyle.knobDown = withMinimumSize(
			rounded(UiAppearance.Role.ACCENT_PRESSED, 0f, 0f), 14f, 14f);
		sliderStyle.disabledBackground = rounded(UiAppearance.Role.CONTROL_DISABLED, 0f, 0f);
		sliderStyle.disabledKnob = withMinimumSize(
			rounded(UiAppearance.Role.TEXT_DISABLED, 0f, 0f), 14f, 14f);
		sliderStyle.disabledKnobBefore = rounded(UiAppearance.Role.BORDER, 0f, 0f);

		scrollStyle = new ScrollPaneStyle();
		scrollStyle.background = scrollBg;
		scrollStyle.vScroll = withMinimumSize(rounded(UiAppearance.Role.SCROLL_TRACK, 0f, 0f),
			SCROLLBAR_THICKNESS, 1f);
		scrollStyle.vScrollKnob = withMinimumSize(rounded(UiAppearance.Role.SCROLL_KNOB, 0f, 0f),
			SCROLLBAR_THICKNESS, SCROLLBAR_KNOB_LENGTH);
		scrollStyle.hScroll = withMinimumSize(rounded(UiAppearance.Role.SCROLL_TRACK, 0f, 0f),
			1f, SCROLLBAR_THICKNESS);
		scrollStyle.hScrollKnob = withMinimumSize(rounded(UiAppearance.Role.SCROLL_KNOB, 0f, 0f),
			SCROLLBAR_KNOB_LENGTH, SCROLLBAR_THICKNESS);

		splitTex = solidTexture(1, 1, Color.WHITE);
		Drawable splitHandle = themed(new TextureRegionDrawable(new TextureRegion(splitTex)).tint(Color.WHITE),
			UiAppearance.Role.BORDER);
		withMinimumSize(splitHandle, SPLIT_HANDLE_THICKNESS, SPLIT_HANDLE_THICKNESS);
		splitStyle = new SplitPaneStyle(splitHandle);

		List.ListStyle listStyle = new List.ListStyle(font, Color.WHITE,
			new Color(palette.text()), highlight);
		// SelectBox uses the list selection for its temporary hover item, while standalone
		// Lists use the over drawable. Keep both states visually consistent.
		listStyle.selection = highlight;
		listStyle.over = highlight;
		listStyle.down = downDark;
		listStyle.background = scrollBg;
		Drawable selectBackground = withVerticalContentOffset(downDark, BUTTON_TEXT_Y_OFFSET);
		Drawable selectHoverBackground = withVerticalContentOffset(hoverDark, BUTTON_TEXT_Y_OFFSET);
		Drawable selectOpenBackground = withVerticalContentOffset(highlight, BUTTON_TEXT_Y_OFFSET);
		Drawable selectDisabledBackground = withVerticalContentOffset(disabledControl, BUTTON_TEXT_Y_OFFSET);
		selectStyle = new SelectBoxStyle(font, new Color(palette.text()),
			selectBackground, scrollStyle, listStyle);
		selectStyle.background = selectBackground;
		selectStyle.backgroundOver = selectHoverBackground;
		selectStyle.backgroundOpen = selectOpenBackground;
		selectStyle.backgroundDisabled = selectDisabledBackground;
		selectStyle.disabledFontColor = new Color(palette.disabledText());
		selectStyle.listStyle = listStyle;
		selectStyle.scrollStyle = scrollStyle;

		// TextField passes the full text line height to the cursor drawable; keep the visible caret
		// just taller than the glyph and center it within that line.
		float cursorHeight = Math.min(font.getLineHeight(), font.getCapHeight() + FIELD_CURSOR_EXTRA_HEIGHT);
		Drawable fieldCursor = centeredCursor(flat(UiAppearance.Role.FIELD_CURSOR), cursorHeight);
		withMinimumSize(fieldCursor, 2f, cursorHeight);
		Drawable fieldBackground = withVerticalContentOffset(downDark, BUTTON_TEXT_Y_OFFSET);
		textFieldHoverBackground = withVerticalContentOffset(hoverDark, BUTTON_TEXT_Y_OFFSET);
		Drawable fieldDisabledBackground = withVerticalContentOffset(disabledControl, BUTTON_TEXT_Y_OFFSET);
		textFieldStyle = new TextFieldStyle(font, new Color(palette.color(UiAppearance.Role.INPUT_TEXT)),
			fieldCursor, highlight, fieldBackground);
		textFieldStyle.focusedBackground = textFieldHoverBackground;
		textFieldStyle.disabledBackground = fieldDisabledBackground;
		textFieldStyle.disabledFontColor = new Color(palette.disabledText());
		textFieldStyle.messageFontColor = new Color(palette.mutedText());

		treeStyle = new TreeStyle();
		treeStyle.plus = plusIcon;
		treeStyle.minus = minusIcon;
		treeStyle.selection = rounded(UiAppearance.Role.TREE_SELECTION);

		windowStyle = new WindowStyle(font, new Color(palette.text()),
			rounded(UiAppearance.Role.WINDOW, 8f, 8f));
		windowStyle.titleFontColor = new Color(palette.text());
		windowStyle.stageBackground = flat(UiAppearance.Role.OVERLAY);
		applyAppearance(palette);
	}

	public UiAppearance.Palette palette() { return palette; }

	/** Recolors all shared drawables and styles without replacing live UI actors. */
	public void applyAppearance(UiAppearance.Palette palette) {
		if (palette == null) throw new IllegalArgumentException("Appearance palette cannot be null.");
		this.palette = palette;
		for (ThemedDrawable themed : themedDrawables)
			setDrawableColor(themed.drawable, palette.color(themed.role));
		if (labelStyle == null) return;
		labelStyle.fontColor = new Color(palette.text());
		dialogBodyStyle.fontColor = new Color(palette.mutedText());
		errorLabelStyle.fontColor = new Color(palette.errorText());
		toolButton.fontColor = new Color(palette.text());
		toolButton.disabledFontColor = new Color(palette.disabledText());
		activeToolButton.fontColor = Color.WHITE;
		activeToolButton.disabledFontColor = new Color(palette.disabledText());
		accentButton.fontColor = Color.WHITE;
		accentButton.disabledFontColor = new Color(palette.disabledText());
		dangerButton.fontColor = Color.WHITE;
		dangerButton.disabledFontColor = new Color(palette.disabledText());
		selectStyle.fontColor = new Color(palette.text());
		selectStyle.disabledFontColor = new Color(palette.disabledText());
		selectStyle.listStyle.fontColorSelected = Color.WHITE;
		selectStyle.listStyle.fontColorUnselected = new Color(palette.text());
		textFieldStyle.fontColor = new Color(palette.color(UiAppearance.Role.INPUT_TEXT));
		textFieldStyle.disabledFontColor = new Color(palette.disabledText());
		textFieldStyle.messageFontColor = new Color(palette.mutedText());
		for (Map.Entry<TextField, TextFieldStyle> entry : hoveredTextFields.entrySet()) {
			TextField field = entry.getKey();
			if (field != null)
				field.setStyle(hoveredTextFieldStyle(entry.getValue(), textFieldHoverBackground));
		}
		windowStyle.titleFontColor = new Color(palette.text());
	}

	/** Adds button-like mouse hover feedback to every current and future text input on the stage. */
	public void installTextFieldHover(Stage stage) {
		if (stage == null) throw new IllegalArgumentException("Text field hover stage cannot be null.");
		if (textFieldHoverStage == stage) return;
		uninstallTextFieldHover();
		textFieldHoverStage = stage;
		textFieldHoverListener = new InputListener() {
			@Override public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
				if (pointer != -1 || !(event.getTarget() instanceof TextField field)
					|| hoveredTextFields.containsKey(field)) return;
				TextFieldStyle original = field.getStyle();
				hoveredTextFields.put(field, original);
				field.setStyle(hoveredTextFieldStyle(original, textFieldHoverBackground));
			}

			@Override public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
				if (pointer != -1 || !(event.getTarget() instanceof TextField field)) return;
				TextFieldStyle original = hoveredTextFields.remove(field);
				if (original != null) field.setStyle(original);
			}
		};
		stage.addCaptureListener(textFieldHoverListener);
	}

	private void uninstallTextFieldHover() {
		if (textFieldHoverStage != null && textFieldHoverListener != null)
			textFieldHoverStage.removeCaptureListener(textFieldHoverListener);
		for (Map.Entry<TextField, TextFieldStyle> entry : hoveredTextFields.entrySet()) {
			TextField field = entry.getKey();
			if (field != null) field.setStyle(entry.getValue());
		}
		hoveredTextFields.clear();
		textFieldHoverStage = null;
		textFieldHoverListener = null;
	}

	static TextFieldStyle hoveredTextFieldStyle(TextFieldStyle original, Drawable hoverBackground) {
		if (original == null || hoverBackground == null)
			throw new IllegalArgumentException("Text field hover style values cannot be null.");
		TextFieldStyle hovered = new TextFieldStyle(original);
		hovered.background = hoverBackground;
		return hovered;
	}

	private Drawable rounded(UiAppearance.Role role) {
		return rounded(role, CONTROL_HORIZONTAL_PADDING, CONTROL_VERTICAL_PADDING);
	}

	private Drawable rounded(UiAppearance.Role role, float horizontalPadding, float verticalPadding) {
		return themed(roundedTint(roundedTex, Color.WHITE, horizontalPadding, verticalPadding), role);
	}

	private Drawable flat(UiAppearance.Role role) {
		return themed(white.tint(Color.WHITE), role);
	}

	private Drawable windowControlIcon(Texture texture) {
		return withMinimumSize(themed(new TextureRegionDrawable(new TextureRegion(texture)).tint(Color.WHITE),
			UiAppearance.Role.ICON), WINDOW_CONTROL_ICON_SIZE, WINDOW_CONTROL_ICON_SIZE);
	}

	public static ImageButtonStyle windowControlImageStyle(ButtonStyle background, Drawable icon,
		Drawable activeIcon) {
		if (background == null || icon == null || activeIcon == null)
			throw new IllegalArgumentException("Window control style and icons cannot be null");
		ImageButtonStyle style = new ImageButtonStyle(background);
		style.imageUp = icon;
		style.imageOver = activeIcon;
		style.imageDown = activeIcon;
		return style;
	}

	private Drawable themed(Drawable drawable, UiAppearance.Role role) {
		ThemedDrawable themed = new ThemedDrawable(drawable, role);
		themedDrawables.add(themed);
		setDrawableColor(drawable, palette.color(role));
		return drawable;
	}

	private static void setDrawableColor(Drawable drawable, Color color) {
		if (drawable instanceof NinePatchDrawable ninePatch) {
			ninePatch.getPatch().setColor(color);
			return;
		}
		if (drawable instanceof SpriteDrawable sprite) {
			sprite.getSprite().setColor(color);
			return;
		}
		throw new IllegalArgumentException("Theme drawable does not support tinting: "
			+ drawable.getClass().getSimpleName());
	}

	private static final class ThemedDrawable {
		final Drawable drawable;
		final UiAppearance.Role role;

		ThemedDrawable(Drawable drawable, UiAppearance.Role role) {
			this.drawable = drawable;
			this.role = role;
		}
	}

	public Window window(String title) {
		Window window = new Window(title, windowStyle);
		window.getTitleLabel().setAlignment(Align.left);
		window.getTitleLabel().setFontScale(DIALOG_TITLE_SCALE);
		window.getTitleTable().padLeft(6f).padRight(6f)
			.padTop(DIALOG_TITLE_TOP_PADDING).padBottom(DIALOG_TITLE_BOTTOM_PADDING);
		return window;
	}

	/** Reapplies the shared window style so title labels created before a theme change are recolored. */
	public Window refreshWindowAppearance(Window window) {
		if (window == null) throw new IllegalArgumentException("Window cannot be null.");
		window.setStyle(windowStyle);
		return window;
	}

	public Window padWindow(Window window, float contentPadding) {
		if (window == null || !Float.isFinite(contentPadding) || contentPadding < 0f)
			throw new IllegalArgumentException("Window padding must be finite and non-negative.");
		window.padLeft(contentPadding);
		window.padRight(contentPadding);
		window.padBottom(contentPadding);
		window.padTop(Math.max(DIALOG_TITLE_BAR_HEIGHT, contentPadding));
		return window;
	}

	public <T> SelectBox<T> selectBox() {
		SelectBox<T> box = new SelectBox<>(selectStyle);
		box.setAlignment(Align.center);
		box.getList().setAlignment(Align.center);
		return box;
	}

	/** Creates a label whose text shares the visual baseline used by adjacent buttons. */
	public Label controlLabel(CharSequence text) {
		return new ControlLabel(text, labelStyle);
	}

	private static final class ControlLabel extends Label {
		ControlLabel(CharSequence text, LabelStyle style) {
			super(text, style);
		}

		@Override public void draw(Batch batch, float parentAlpha) {
			float originalY = getY();
			setY(controlLabelDrawY(originalY));
			try {
				super.draw(batch, parentAlpha);
			} finally {
				setY(originalY);
			}
		}
	}

	static float controlLabelDrawY(float actorY) {
		return actorY + BUTTON_TEXT_Y_OFFSET;
	}

	static Drawable withMinimumSize(Drawable drawable, float minWidth, float minHeight) {
		if (!(drawable instanceof BaseDrawable base))
			throw new IllegalArgumentException("Drawable does not support configurable minimum dimensions.");
		if (!Float.isFinite(minWidth) || !Float.isFinite(minHeight) || minWidth < 0f || minHeight < 0f)
			throw new IllegalArgumentException("Drawable minimum dimensions must be finite and non-negative.");
		base.setMinWidth(minWidth);
		base.setMinHeight(minHeight);
		return drawable;
	}

	static Drawable withVerticalContentOffset(Drawable drawable, float offsetY) {
		if (drawable == null) throw new IllegalArgumentException("Content background cannot be null.");
		if (!Float.isFinite(offsetY) || offsetY < 0f || offsetY > drawable.getTopHeight())
			throw new IllegalArgumentException("Content offset must fit within the top padding.");
		return new ContentOffsetDrawable(drawable, offsetY);
	}

	static Drawable centeredCursor(Drawable drawable, float cursorHeight) {
		if (drawable == null) throw new IllegalArgumentException("Cursor drawable cannot be null.");
		if (!Float.isFinite(cursorHeight) || cursorHeight <= 0f)
			throw new IllegalArgumentException("Cursor height must be finite and positive.");
		return new CenteredCursorDrawable(drawable, cursorHeight);
	}

	private static final class CenteredCursorDrawable extends BaseDrawable {
		private final Drawable delegate;
		private final float cursorHeight;

		CenteredCursorDrawable(Drawable delegate, float cursorHeight) {
			super(delegate);
			this.delegate = delegate;
			this.cursorHeight = cursorHeight;
		}

		@Override public void draw(Batch batch, float x, float y, float width, float height) {
			if (width <= 0f || height <= 0f) return;
			float drawnHeight = Math.min(cursorHeight, height);
			delegate.draw(batch, x, y + (height - drawnHeight) / 2f, width, drawnHeight);
		}
	}

	private static final class ContentOffsetDrawable extends BaseDrawable {
		private final Drawable delegate;

		ContentOffsetDrawable(Drawable delegate, float offsetY) {
			super(delegate);
			this.delegate = delegate;
			setTopHeight(delegate.getTopHeight() - offsetY);
			setBottomHeight(delegate.getBottomHeight() + offsetY);
		}

		@Override public void draw(Batch batch, float x, float y, float width, float height) {
			delegate.draw(batch, x, y, width, height);
		}
	}

	static TextButtonStyle withDisabledState(TextButtonStyle style, Drawable background, Color fontColor) {
		if (style == null || background == null || fontColor == null)
			throw new IllegalArgumentException("Disabled button style values cannot be null.");
		style.disabled = background;
		style.disabledFontColor = new Color(fontColor);
		return style;
	}

	static TextButtonStyle withCenteredText(TextButtonStyle style) {
		if (style == null) throw new IllegalArgumentException("Button style cannot be null.");
		style.unpressedOffsetX = 0f;
		style.unpressedOffsetY = BUTTON_TEXT_Y_OFFSET;
		style.pressedOffsetX = 0f;
		style.pressedOffsetY = BUTTON_TEXT_Y_OFFSET;
		style.checkedOffsetX = 0f;
		style.checkedOffsetY = BUTTON_TEXT_Y_OFFSET;
		return style;
	}

	/** Configures a command button so a click cannot leave it in the checked visual state. */
	public static <T extends Button> T asActionButton(T button) {
		if (button == null) throw new IllegalArgumentException("Action button cannot be null.");
		button.setProgrammaticChangeEvents(false);
		button.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				button.setChecked(false);
			}
		});
		return button;
	}

	private static Drawable roundedTint(Texture texture, Color color,
		float horizontalPadding, float verticalPadding) {
		NinePatch patch = new NinePatch(new TextureRegion(texture), CONTROL_PATCH_BORDER, CONTROL_PATCH_BORDER,
			CONTROL_PATCH_BORDER, CONTROL_PATCH_BORDER);
		patch.setColor(color);
		patch.setPadding(horizontalPadding, horizontalPadding, verticalPadding, verticalPadding);
		return new NinePatchDrawable(patch);
	}

	private static Texture solidTexture(int w, int h, Color c) {
		Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
		pm.setColor(c);
		pm.fill();
		Texture t = new Texture(pm);
		pm.dispose();
		return t;
	}

	private static Texture roundedTexture(int size, int radius) {
		if (size < 3 || radius < 1 || radius * 2 >= size)
			throw new IllegalArgumentException("Rounded texture dimensions are invalid.");
		Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
		pm.setBlending(Pixmap.Blending.None);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float px = x + 0.5f;
				float py = y + 0.5f;
				float dx = Math.max(radius - px, Math.max(0f, px - (size - radius)));
				float dy = Math.max(radius - py, Math.max(0f, py - (size - radius)));
				float alpha = Math.min(1f, Math.max(0f, radius + 0.5f - (float)Math.sqrt(dx * dx + dy * dy)));
				pm.setColor(1f, 1f, 1f, alpha);
				pm.drawPixel(x, y);
			}
		}
		Texture texture = new Texture(pm);
		texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		pm.dispose();
		return texture;
	}

	private static Texture triTexture(int w, int h, Color c, boolean down) {
		Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
		pm.setColor(c);
		int[] xs, ys;
		if (!down) { // 向右的箭头
			xs = new int[] { 1, 1, w - 1 };
			ys = new int[] { 1, h - 1, h / 2 };
		} else { // 向下的箭头
			xs = new int[] { 1, w - 1, w / 2 };
			ys = new int[] { 1, 1, h - 1 };
		}
		pm.fillTriangle(xs[0], ys[0], xs[1], ys[1], xs[2], ys[2]);
		Texture t = new Texture(pm);
		pm.dispose();
		return t;
	}

	private static Texture eyeTexture(boolean hidden) {
		int size = (int)TREE_VISIBILITY_ICON_SIZE;
		Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
		pm.setColor(Color.WHITE);
		pm.drawLine(1, 8, 4, 5);
		pm.drawLine(4, 5, 7, 4);
		pm.drawLine(7, 4, 9, 4);
		pm.drawLine(9, 4, 12, 5);
		pm.drawLine(12, 5, 14, 8);
		pm.drawLine(1, 8, 4, 11);
		pm.drawLine(4, 11, 7, 12);
		pm.drawLine(7, 12, 9, 12);
		pm.drawLine(9, 12, 12, 11);
		pm.drawLine(12, 11, 14, 8);
		pm.fillCircle(8, 8, 2);
		if (hidden) {
			pm.drawLine(3, 3, 13, 13);
			pm.drawLine(3, 4, 12, 13);
		}
		Texture texture = new Texture(pm);
		texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		pm.dispose();
		return texture;
	}

	private enum WindowControlIcon { MINIMIZE, MAXIMIZE, CLOSE }

	private static Texture windowControlIconTexture(WindowControlIcon icon) {
		int size = (int)WINDOW_CONTROL_ICON_SIZE;
		Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
		pm.setColor(Color.WHITE);
		switch (icon) {
		case MINIMIZE -> pm.fillRectangle(1, 9, 16, 2);
		case MAXIMIZE -> {
			pm.fillRectangle(2, 2, 14, 2);
			pm.fillRectangle(2, 14, 14, 2);
			pm.fillRectangle(2, 4, 2, 10);
			pm.fillRectangle(14, 4, 2, 10);
		}
		case CLOSE -> {
			pm.drawLine(2, 2, 15, 15);
			pm.drawLine(2, 3, 14, 15);
			pm.drawLine(2, 15, 15, 2);
			pm.drawLine(2, 14, 14, 2);
		}
		}
		Texture texture = new Texture(pm);
		texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
		pm.dispose();
		return texture;
	}

	@Override
	public void dispose() {
		uninstallTextFieldHover();
		font.dispose();
		whiteTex.dispose();
		roundedTex.dispose();
		knobTex.dispose();
		plusTex.dispose();
		minusTex.dispose();
		visibleEyeTex.dispose();
		hiddenEyeTex.dispose();
		windowMinimizeTex.dispose();
		windowMaximizeTex.dispose();
		windowCloseTex.dispose();
		splitTex.dispose();
	}
}
