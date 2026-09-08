package com.openspine.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.openspine.KeyBindings;
import com.openspine.KeyBindings.BindResult;
import com.openspine.KeyBindings.Combo;
import com.openspine.KeyBindings.EditorAction;
import com.openspine.io.ExportPresetStore;
import com.openspine.ui.I18n.Language;
import com.openspine.ui.UiAppearance.Mode;
import com.openspine.ui.UiAppearance.Theme;

/** Modal settings window with category navigation on the left and the active page on the right.
 * Pages cover interface language and layout, viewport overlays, event audio, and rebindable
 * shortcuts with conflict detection. */
public final class SettingsDialog {
	/** Editable SkeletonData project metadata exposed by the project settings page. */
	public record ProjectSettings(String imagesPath, String audioPath, float x, float y,
		float width, float height, float referenceScale, float fps) {
		public ProjectSettings {
			imagesPath = imagesPath == null ? "" : imagesPath.trim();
			audioPath = audioPath == null ? "" : audioPath.trim();
		}
	}

	public record AnimationMix(String from, String to, float duration) {
		public AnimationMix {
			from = from == null ? "" : from.trim();
			to = to == null ? "" : to.trim();
			if (from.isEmpty() || to.isEmpty() || from.equals(to)
				|| !Float.isFinite(duration) || duration < 0f)
				throw new IllegalArgumentException("动画混合设置无效");
		}
	}

	/** Availability information for optional export integrations. */
	public record ExportDependencies(boolean webpAvailable, String webpDetail,
		boolean ffmpegAvailable, String ffmpegExecutable) {
		public ExportDependencies {
			webpDetail = webpDetail == null ? "" : webpDetail;
			ffmpegExecutable = ffmpegExecutable == null ? "" : ffmpegExecutable;
		}
	}

	/** Editor state read and mutated by the settings pages. */
	public interface Host {
		Mode appearanceMode();

		void setAppearanceMode(Mode mode);

		boolean resolvedDarkAppearance();

		Theme appearanceTheme(boolean dark);

		void setAppearanceTheme(boolean dark, Theme theme);

		boolean isGridVisible();

		void setGridVisible(boolean visible);

		boolean isPixelGridVisible();

		void setPixelGridVisible(boolean visible);

		boolean isBonesVisible();

		void setBonesVisible(boolean visible);

		boolean isSelectionHidden();

		void setSelectionHidden(boolean hidden);

		boolean isShowInactiveSkinBones();

		void setShowInactiveSkinBones(boolean visible);

		boolean isGhostingVisible();

		void setGhostingVisible(boolean visible);

		boolean isGhostBonesVisible();

		void setGhostBonesVisible(boolean visible);

		boolean isTextureSmoothing();

		void setTextureSmoothing(boolean smooth);

		boolean isViewportFlipped();

		void setViewportFlipped(boolean flipped);

		boolean isZoomLocked();

		void setZoomLocked(boolean locked);

		boolean isPixelSnappingEnabled();

		void setPixelSnappingEnabled(boolean enabled);

		boolean areConstraintsEnabled();

		void setConstraintsEnabled(boolean enabled);

		boolean isEventAudioEnabled();

		void setEventAudioEnabled(boolean enabled);

		/** Restores the default workspace layout. */
		void resetLayout();

		/** Refreshes toolbar/menu state after a settings change. */
		void syncCommandState();

		/** Persists preferences; called once when the dialog closes. */
		void settingsClosed();

		/** Project metadata hooks. Defaults keep third-party hosts source-compatible. */
		default ProjectSettings projectSettings() {
			return new ProjectSettings("", "", 0f, 0f, 0f, 0f, 100f, 30f);
		}

		default boolean updateProjectSettings(ProjectSettings settings) { return false; }

		default String[] animationNames() { return new String[0]; }

		default float defaultAnimationMix() { return 0f; }

		default java.util.List<AnimationMix> animationMixes() { return java.util.List.of(); }

		default boolean updateAnimationMixSettings(float defaultMix, java.util.List<AnimationMix> mixes) {
			return false;
		}

		default String spineVersionSummary() { return "- -> 4.3.0"; }

		default ExportDependencies exportDependencies() {
			return new ExportDependencies(false, "", false, "ffmpeg");
		}

		/** Stores a user-selected ffmpeg executable. Returns false when the path is invalid. */
		default boolean setFfmpegExecutable(String path) { return false; }

		default java.util.List<String> exportPresetNames() { return java.util.List.of(); }

		default boolean saveExportPreset(String name) { return false; }

		default boolean loadExportPreset(String name) { return false; }

		default boolean deleteExportPreset(String name) { return false; }
	}

	/** Left-hand navigation categories. */
	public enum Page {
		APPEARANCE("外观", "Appearance"), INTERFACE("界面", "Interface"), PROJECT("项目", "Project"),
		EXPORT("导出", "Export"),
		VIEWPORT("视口", "Viewport"),
		AUDIO("音频", "Audio"), SHORTCUTS("快捷键", "Shortcuts");

		private final String chinese;
		private final String english;

		Page(String chinese, String english) {
			this.chinese = chinese;
			this.english = english;
		}

		public String label() {
			return I18n.t(chinese, english);
		}
	}

	private static final float NAV_WIDTH = 104f;
	private static final float CONTENT_WIDTH = 470f;
	private static final float CONTENT_HEIGHT = 330f;
	private static final float ROW_HEIGHT = 26f;
	private static final float CONTROL_WIDTH = 112f;
	private static final float RESET_LAYOUT_WIDTH = 154f;

	private final UiKit kit;
	private final Stage stage;
	private final KeyBindings bindings;
	private final Host host;

	private Window window;
	private Actor previousKeyboardFocus;
	private final Table navColumn = new Table();
	private final Container<Actor> content = new Container<>();
	private Label statusLabel;
	private TextButton doneButton;
	private Page page = Page.APPEARANCE;
	private EditorAction capturing;
	private float shortcutsScrollY;

	public SettingsDialog(UiKit kit, Stage stage, KeyBindings bindings, Host host) {
		this.kit = kit;
		this.stage = stage;
		this.bindings = bindings;
		this.host = host;
	}

	/** Builds the window, centers it on the stage, and takes keyboard focus. */
	public void show() {
		previousKeyboardFocus = stage.getKeyboardFocus();
		window = kit.window(title());
		window.setModal(true);
		kit.padWindow(window, 10f);
		window.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return handleKeyDown(keycode);
			}
		});

		content.top().left();
		content.fill();
		Table body = new Table();
		body.add(navColumn).width(NAV_WIDTH).top().padRight(10);
		body.add(content).width(CONTENT_WIDTH).height(CONTENT_HEIGHT).top();
		window.add(body).row();

		statusLabel = new Label("", kit.dialogBodyStyle);
		statusLabel.setEllipsis(true);
		doneButton = new TextButton("", kit.accentButton);
		doneButton.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				close();
			}
		});
		Table actions = new Table();
		actions.add(statusLabel).growX().minWidth(0f).padRight(10);
		actions.add(doneButton).height(ROW_HEIGHT).minWidth(76f);
		window.add(actions).growX().padTop(10);

		relabel();
		selectPage(page);
		window.pack();
		window.setPosition((stage.getWidth() - window.getWidth()) / 2f,
			(stage.getHeight() - window.getHeight()) / 2f);
		stage.addActor(window);
		stage.setKeyboardFocus(window);
	}

	/** True while the dialog is on stage. */
	public boolean isShowing() {
		return window != null && window.getStage() != null;
	}

	private void close() {
		if (window == null) return;
		capturing = null;
		host.settingsClosed();
		UiDialogs.closeModal(stage, window, previousKeyboardFocus);
		window = null;
	}

	/** Handles dialog keys: capture mode consumes everything, otherwise Enter/Escape close. */
	private boolean handleKeyDown(int keycode) {
		if (capturing != null) {
			if (keycode == Input.Keys.ESCAPE) {
				endCapture(I18n.t("已取消改键", "Rebinding cancelled"));
				return true;
			}
			if (isModifierKey(keycode)) return true;
			applyCapture(keycode);
			return true;
		}
		return switch (UiDialogs.keyAction(keycode)) {
		case CONFIRM, CANCEL -> {
			close();
			yield true;
		}
		case NONE -> false;
		};
	}

	private static boolean isModifierKey(int keycode) {
		return keycode == Input.Keys.CONTROL_LEFT || keycode == Input.Keys.CONTROL_RIGHT
			|| keycode == Input.Keys.SHIFT_LEFT || keycode == Input.Keys.SHIFT_RIGHT
			|| keycode == Input.Keys.ALT_LEFT || keycode == Input.Keys.ALT_RIGHT;
	}

	private void applyCapture(int keycode) {
		boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
			|| Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
		if (alt) {
			setStatus(I18n.t("暂不支持 Alt 组合，请使用 Ctrl 或 Shift",
				"Alt combinations are not supported; use Ctrl or Shift"));
			return;
		}
		boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
			|| Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
		boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
			|| Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
		Combo combo = new Combo(keycode, ctrl, shift);
		EditorAction action = capturing;
		BindResult result = bindings.bind(action, combo);
		if (result.bound()) {
			endCapture(boundMessage(action, combo));
			return;
		}
		// Stay in capture mode so the user can immediately try another combination.
		setStatus(result.reserved() ? reservedMessage(combo) : conflictMessage(result.conflict()));
	}

	private void beginCapture(EditorAction action) {
		capturing = action;
		setStatus(I18n.t("正在录制新键位,Esc 取消", "Recording new keys, Esc cancels"));
		refreshShortcutsPage();
	}

	private void endCapture(String status) {
		capturing = null;
		setStatus(status);
		refreshShortcutsPage();
	}

	private void setStatus(String text) {
		statusLabel.setText(text == null ? "" : text);
	}

	/** Applies the active language to the window chrome and rebuilds navigation. */
	private void relabel() {
		window.getTitleLabel().setText(title());
		doneButton.setText(I18n.t("完成", "Done"));
		rebuildNav();
	}

	private static String title() {
		return I18n.t("设置", "Settings");
	}

	private void rebuildNav() {
		navColumn.clear();
		navColumn.top();
		for (Page candidate : Page.values()) {
			TextButton navButton = new TextButton(candidate.label(),
				candidate == page ? kit.activeToolButton : kit.toolButton);
			navButton.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					if (capturing != null) endCapture("");
					selectPage(candidate);
				}
			});
			navColumn.add(navButton).growX().height(ROW_HEIGHT).padBottom(4).row();
		}
	}

	private void selectPage(Page target) {
		float previousScrollY = page == Page.VIEWPORT && target == page
			&& content.getActor() instanceof ScrollPane scroll ? scroll.getScrollY() : 0f;
		page = target;
		rebuildNav();
		content.setActor(buildPage(target));
		if (target == Page.VIEWPORT && content.getActor() instanceof ScrollPane scroll) {
			content.validate();
			scroll.layout();
			scroll.setScrollY(previousScrollY);
			scroll.updateVisualScroll();
		}
	}

	private Actor buildPage(Page target) {
		return switch (target) {
		case APPEARANCE -> buildAppearancePage();
		case INTERFACE -> buildInterfacePage();
		case PROJECT -> buildProjectPage();
		case EXPORT -> buildExportPage();
		case VIEWPORT -> buildViewportPage();
		case AUDIO -> buildAudioPage();
		case SHORTCUTS -> buildShortcutsPage();
		};
	}

	private Table pageTable() {
		Table table = new Table();
		table.top().left().defaults().left();
		return table;
	}

	private Label rowLabel(String chinese, String english) {
		return new Label(I18n.t(chinese, english), kit.labelStyle);
	}

	private Table buildAppearancePage() {
		Table table = pageTable();
		table.add(rowLabel("模式", "Mode")).growX().minWidth(0f).height(ROW_HEIGHT);
		Table modes = new Table();
		for (Mode mode : Mode.values()) {
			boolean active = host.appearanceMode() == mode;
			TextButton button = new TextButton(mode.label(), active ? kit.activeToolButton : kit.toolButton);
			button.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					host.setAppearanceMode(mode);
					host.syncCommandState();
					selectPage(page);
					setStatus(I18n.t("外观模式已更新", "Appearance mode updated"));
				}
			});
			modes.add(button).width(94f).height(ROW_HEIGHT).padLeft(4f);
		}
		table.add(modes).right();
		table.row().padTop(14);

		boolean dark = host.resolvedDarkAppearance();
		Theme selectedTheme = host.appearanceTheme(dark);
		table.add(rowLabel("主题", "Theme")).growX().minWidth(0f).height(ROW_HEIGHT);
		Table themes = new Table();
		for (Theme theme : Theme.values()) {
			boolean active = selectedTheme == theme;
			Button button = new Button(active ? kit.activeToolButton : kit.toolButton);
			Image swatch = new Image(kit.white);
			swatch.setColor(UiAppearance.palette(dark, theme).accent());
			Label label = new Label(theme.label(), active
				? new Label.LabelStyle(kit.font, Color.WHITE) : kit.labelStyle);
			button.add(swatch).size(12f).padRight(6f);
			button.add(label);
			button.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					host.setAppearanceTheme(dark, theme);
					host.syncCommandState();
					selectPage(page);
					setStatus(I18n.t("界面主题已更新", "Interface theme updated"));
				}
			});
			themes.add(button).width(94f).height(ROW_HEIGHT).padLeft(4f);
		}
		table.add(themes).right();
		table.row().padTop(14);

		String resolvedMode = dark ? I18n.t("深色", "Dark") : I18n.t("浅色", "Light");
		Label summary = new Label(I18n.t("当前：", "Current: ") + resolvedMode + " · " + selectedTheme.label(),
			kit.dialogBodyStyle);
		table.add(summary).colspan(2).growX().padTop(8);
		return table;
	}

	private Table buildInterfacePage() {
		Table table = pageTable();
		table.add(rowLabel("语言", "Language")).growX().minWidth(0f).height(ROW_HEIGHT);
		Table languages = new Table();
		for (Language language : Language.values()) {
			boolean active = I18n.language() == language;
			TextButton languageButton = new TextButton(language.displayName,
				active ? kit.activeToolButton : kit.toolButton);
			languageButton.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					switchLanguage(language);
				}
			});
			languages.add(languageButton).height(ROW_HEIGHT).minWidth(84f).padRight(4);
		}
		table.add(languages).right();
		table.row().padTop(10);

		table.add(rowLabel("工作区布局", "Workspace Layout")).growX().minWidth(0f).height(ROW_HEIGHT);
		TextButton resetLayout = new TextButton(I18n.t("恢复默认布局", "Reset Default Layout"), kit.toolButton);
		resetLayout.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				host.resetLayout();
				host.syncCommandState();
				setStatus(I18n.t("已恢复默认布局", "Default layout restored"));
			}
		});
		table.add(resetLayout).width(RESET_LAYOUT_WIDTH).height(ROW_HEIGHT).right();
		table.row().padTop(10);
		Label hint = new Label(I18n.t("语言立即生效，并自动保存到本机设置。",
			"Language changes apply immediately and are saved automatically."),
			kit.dialogBodyStyle);
		hint.setWrap(true);
		table.add(hint).colspan(2).growX().padTop(6);
		return table;
	}

	private void switchLanguage(Language language) {
		if (I18n.language() == language) return;
		I18n.setLanguage(language);
		relabel();
		selectPage(page);
		host.syncCommandState();
		setStatus(I18n.t("界面语言已切换", "Interface language switched"));
	}

	private ScrollPane buildViewportPage() {
		Table table = pageTable();
		addToggleRow(table, "视口网格", "Viewport Grid",
			host.isGridVisible(), () -> host.setGridVisible(!host.isGridVisible()), false, true);
		addToggleRow(table, "项目像素网格", "Project Pixel Grid",
			host.isPixelGridVisible(), () -> host.setPixelGridVisible(!host.isPixelGridVisible()), false, true);
		addToggleRow(table, "骨骼显示", "Bone Overlay",
			host.isBonesVisible(), () -> host.setBonesVisible(!host.isBonesVisible()), false, true);
		addToggleRow(table, "隐藏视区选择", "Hide Viewport Selection",
			host.isSelectionHidden(), () -> host.setSelectionHidden(!host.isSelectionHidden()), false, true);
		addToggleRow(table, "非活动皮肤骨骼", "Inactive Skin Bones",
			host.isShowInactiveSkinBones(), () -> host.setShowInactiveSkinBones(!host.isShowInactiveSkinBones()),
			false, host.isBonesVisible());
		addToggleRow(table, "动画残影", "Animation Ghosting",
			host.isGhostingVisible(), () -> host.setGhostingVisible(!host.isGhostingVisible()), false, true);
		addToggleRow(table, "残影骨骼", "Ghost Bones",
			host.isGhostBonesVisible(), () -> host.setGhostBonesVisible(!host.isGhostBonesVisible()),
			false, host.isGhostingVisible());
		addToggleRow(table, "图片过滤", "Texture Filtering",
			host.isTextureSmoothing(), () -> host.setTextureSmoothing(!host.isTextureSmoothing()), true, true);
		addToggleRow(table, "翻转视区", "Flip Viewport",
			host.isViewportFlipped(), () -> host.setViewportFlipped(!host.isViewportFlipped()), false, true);
		addToggleRow(table, "锁定视区缩放", "Lock Viewport Zoom",
			host.isZoomLocked(), () -> host.setZoomLocked(!host.isZoomLocked()), false, true);
		addToggleRow(table, "像素吸附", "Pixel Snapping",
			host.isPixelSnappingEnabled(),
			() -> host.setPixelSnappingEnabled(!host.isPixelSnappingEnabled()), false, true);
		addToggleRow(table, "禁用全部约束", "Disable All Constraints",
			!host.areConstraintsEnabled(), () -> host.setConstraintsEnabled(!host.areConstraintsEnabled()),
			false, true);
		ScrollPane scroll = new ScrollPane(table, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		scroll.setScrollingDisabled(true, false);
		scroll.setOverscroll(false, false);
		return scroll;
	}

	private Table buildProjectPage() {
		Table table = pageTable();
		ProjectSettings current = host.projectSettings();
		table.add(rowLabel("Spine 版本", "Spine versions")).growX().minWidth(0f).height(ROW_HEIGHT);
		Label versions = new Label(host.spineVersionSummary(), kit.dialogBodyStyle);
		versions.setEllipsis(true);
		table.add(versions).width(210f).right().height(ROW_HEIGHT);
		table.row();
		TextField images = new TextField(current.imagesPath(), kit.textFieldStyle);
		TextField audio = new TextField(current.audioPath(), kit.textFieldStyle);
		TextField x = new TextField(UiNumbers.format(current.x()), kit.textFieldStyle);
		TextField y = new TextField(UiNumbers.format(current.y()), kit.textFieldStyle);
		TextField width = new TextField(UiNumbers.format(current.width()), kit.textFieldStyle);
		TextField height = new TextField(UiNumbers.format(current.height()), kit.textFieldStyle);
		TextField scale = new TextField(UiNumbers.format(current.referenceScale()), kit.textFieldStyle);
		TextField fps = new TextField(UiNumbers.format(current.fps()), kit.textFieldStyle);
		TextField defaultMix = new TextField(UiNumbers.format(host.defaultAnimationMix()), kit.textFieldStyle);
		addProjectField(table, "图片路径", "Images path", images);
		addProjectField(table, "音频路径", "Audio path", audio);
		addProjectField(table, "边界 X", "Bounds X", x);
		addProjectField(table, "边界 Y", "Bounds Y", y);
		addProjectField(table, "边界宽", "Bounds width", width);
		addProjectField(table, "边界高", "Bounds height", height);
		addProjectField(table, "参考缩放", "Reference scale", scale);
		addProjectField(table, "项目 FPS", "Project FPS", fps);
		addProjectField(table, "默认混合", "Default mix", defaultMix);

		java.util.ArrayList<AnimationMix> mixDraft = new java.util.ArrayList<>(host.animationMixes());
		SelectBox<String> configuredMix = kit.selectBox();
		SelectBox<String> fromMix = kit.selectBox();
		SelectBox<String> toMix = kit.selectBox();
		Array<String> animationItems = new Array<>();
		for (String name : host.animationNames()) if (name != null && !name.isBlank()) animationItems.add(name);
		fromMix.setItems(animationItems);
		toMix.setItems(new Array<>(animationItems));
		if (animationItems.size > 1) toMix.setSelectedIndex(1);
		TextField pairDuration = new TextField("0.2", kit.textFieldStyle);
		TextButton savePair = new TextButton(I18n.t("添加 / 更新", "Add / Update"), kit.toolButton);
		TextButton removePair = new TextButton(I18n.t("删除", "Remove"), kit.toolButton);
		Runnable refreshMixList = () -> {
			Array<String> labels = new Array<>();
			for (AnimationMix mix : mixDraft)
				labels.add(mix.from() + " -> " + mix.to() + "  " + UiNumbers.format(mix.duration()) + "s");
			if (labels.isEmpty()) labels.add(I18n.t("（无单独混合）", "(No pair mixes)"));
			configuredMix.setItems(labels);
			configuredMix.setDisabled(mixDraft.isEmpty());
			removePair.setDisabled(mixDraft.isEmpty());
		};
		configuredMix.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				int index = configuredMix.getSelectedIndex();
				if (index < 0 || index >= mixDraft.size()) return;
				AnimationMix mix = mixDraft.get(index);
				fromMix.setSelected(mix.from());
				toMix.setSelected(mix.to());
				pairDuration.setText(UiNumbers.format(mix.duration()));
			}
		});
		savePair.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				try {
					AnimationMix requested = new AnimationMix(fromMix.getSelected(), toMix.getSelected(),
						UiNumbers.parseFinite(pairDuration.getText()));
					int existing = -1;
					for (int index = 0; index < mixDraft.size(); index++)
						if (mixDraft.get(index).from().equals(requested.from())
							&& mixDraft.get(index).to().equals(requested.to())) { existing = index; break; }
					if (existing < 0) mixDraft.add(requested); else mixDraft.set(existing, requested);
					refreshMixList.run();
					configuredMix.setSelectedIndex(existing < 0 ? mixDraft.size() - 1 : existing);
					setStatus(I18n.t("动画混合已加入待应用设置", "Animation mix added to pending settings"));
				} catch (RuntimeException error) {
					setStatus(error.getMessage() == null ? I18n.t("动画混合无效", "Invalid animation mix")
						: error.getMessage());
				}
			}
		});
		removePair.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				int index = configuredMix.getSelectedIndex();
				if (index < 0 || index >= mixDraft.size()) return;
				mixDraft.remove(index);
				refreshMixList.run();
			}
		});
		refreshMixList.run();
		addProjectSelectRow(table, "动画对", "Animation pair", configuredMix);
		Table pairSelectors = new Table();
		pairSelectors.add(fromMix).growX().minWidth(0f).height(ROW_HEIGHT).padRight(4f);
		pairSelectors.add(toMix).growX().minWidth(0f).height(ROW_HEIGHT);
		table.add(rowLabel("来源 / 目标", "From / To")).growX().minWidth(0f).height(ROW_HEIGHT).padTop(4f);
		table.add(pairSelectors).width(210f).right().padTop(4f).row();
		Table pairActions = new Table();
		pairActions.add(pairDuration).width(64f).height(ROW_HEIGHT).padRight(4f);
		pairActions.add(savePair).growX().minWidth(0f).height(ROW_HEIGHT).padRight(4f);
		pairActions.add(removePair).width(56f).height(ROW_HEIGHT);
		table.add(rowLabel("混合时间", "Mix duration")).growX().minWidth(0f).height(ROW_HEIGHT).padTop(4f);
		table.add(pairActions).width(210f).right().padTop(4f).row();
		TextButton apply = new TextButton(I18n.t("应用项目设置", "Apply Project Settings"), kit.accentButton);
		apply.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				try {
					ProjectSettings next = new ProjectSettings(images.getText(), audio.getText(),
						UiNumbers.parseFinite(x.getText()), UiNumbers.parseFinite(y.getText()),
						UiNumbers.parseFinite(width.getText()), UiNumbers.parseFinite(height.getText()),
						UiNumbers.parseFinite(scale.getText()), UiNumbers.parseFinite(fps.getText()));
					if (next.width() < 0f || next.height() < 0f || next.referenceScale() <= 0f
						|| next.fps() <= 0f) throw new NumberFormatException("项目数值范围无效");
					float nextDefaultMix = UiNumbers.parseFinite(defaultMix.getText());
					if (nextDefaultMix < 0f) throw new NumberFormatException("默认动画混合时间不能小于 0");
					boolean projectChanged = host.updateProjectSettings(next);
					boolean mixChanged = host.updateAnimationMixSettings(nextDefaultMix, java.util.List.copyOf(mixDraft));
					if (!projectChanged && !mixChanged) {
						setStatus(I18n.t("项目设置没有变化或无法应用", "Project settings were unchanged or rejected"));
						return;
					}
					setStatus(I18n.t("项目设置已应用", "Project settings applied"));
					selectPage(Page.PROJECT);
				} catch (RuntimeException error) {
					setStatus(error.getMessage() == null ? I18n.t("项目数值无效", "Invalid project values")
						: error.getMessage());
				}
			}
		});
		table.add(apply).colspan(2).right().padTop(12f).height(ROW_HEIGHT);
		return table;
	}

	private void addProjectSelectRow(Table table, String chinese, String english, SelectBox<String> field) {
		table.add(rowLabel(chinese, english)).growX().minWidth(0f).height(ROW_HEIGHT).padTop(4f);
		table.add(field).width(210f).height(ROW_HEIGHT).right().padTop(4f);
		table.row();
	}

	private void addProjectField(Table table, String chinese, String english, TextField field) {
		table.add(rowLabel(chinese, english)).growX().minWidth(0f).height(ROW_HEIGHT).padTop(4f);
		table.add(field).width(210f).height(ROW_HEIGHT).right().padTop(4f);
		table.row();
	}

	private Table buildExportPage() {
		Table table = pageTable();
		table.columnDefaults(0).width(150f).minWidth(0f);
		table.columnDefaults(1).width(310f).minWidth(0f);
		ExportDependencies dependencies = host.exportDependencies();
		table.add(rowLabel("WebP 编码器", "WebP encoder")).height(ROW_HEIGHT);
		Label webp = new Label(dependencies.webpAvailable()
			? I18n.t("可用", "Available") : I18n.t("不可用", "Unavailable"),
			dependencies.webpAvailable() ? kit.dialogBodyStyle : kit.errorLabelStyle);
		table.add(webp).right().height(ROW_HEIGHT);
		table.row().padTop(8f);
		table.add(rowLabel("WebP 状态", "WebP detail")).height(ROW_HEIGHT);
		Label webpDetail = new Label(dependencies.webpDetail().isBlank() ? "ImageIO" : dependencies.webpDetail(),
			kit.dialogBodyStyle);
		webpDetail.setEllipsis(true);
		table.add(webpDetail).width(310f).right().height(ROW_HEIGHT);
		table.row().padTop(8f);
		table.add(rowLabel("ffmpeg", "ffmpeg")).height(ROW_HEIGHT);
		Label ffmpeg = new Label(dependencies.ffmpegAvailable()
			? I18n.t("可用", "Available") : I18n.t("不可用", "Unavailable"),
			dependencies.ffmpegAvailable() ? kit.dialogBodyStyle : kit.errorLabelStyle);
		table.add(ffmpeg).right().height(ROW_HEIGHT);
		table.row().padTop(8f);
		table.add(rowLabel("ffmpeg 路径", "ffmpeg path")).height(ROW_HEIGHT);
		TextField ffmpegPath = new TextField(dependencies.ffmpegExecutable(), kit.textFieldStyle);
		ffmpegPath.setMessageText("ffmpeg 或完整路径");
		TextButton applyFfmpeg = new TextButton(I18n.t("应用", "Apply"), kit.toolButton);
		applyFfmpeg.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (host.setFfmpegExecutable(ffmpegPath.getText())) {
					statusLabel.setText(I18n.t("ffmpeg 路径已保存", "ffmpeg path saved"));
				} else {
					statusLabel.setText(I18n.t("ffmpeg 路径无效或不可执行", "ffmpeg path is invalid or not executable"));
				}
			}
		});
		Table ffmpegPathRow = new Table();
		ffmpegPathRow.add(ffmpegPath).width(188f).height(ROW_HEIGHT);
		ffmpegPathRow.add(applyFfmpeg).width(58f).height(ROW_HEIGHT).padLeft(4f);
		table.add(ffmpegPathRow).width(250f).right().height(ROW_HEIGHT);
		table.row().padTop(16f);
		Label dependencyNote = new Label(I18n.t("WebP 与 WebM/MOV 依赖外部编码组件；不可用时对应导出入口会禁用。",
			"WebP and WebM/MOV require external encoders; unavailable formats are disabled."), kit.dialogBodyStyle);
		dependencyNote.setWrap(true);
		dependencyNote.setColor(0.62f, 0.68f, 0.76f, 1f);
		table.add(dependencyNote).colspan(2).width(460f).minWidth(0f).left().padBottom(8f);
		table.row();

		table.add(rowLabel("导出预设", "Export presets")).height(ROW_HEIGHT);
		SelectBox<String> presets = kit.selectBox();
		refreshExportPresets(presets);
		table.add(presets).width(210f).height(ROW_HEIGHT).right();
		table.row().padTop(6f);
		TextField presetName = new TextField("", kit.textFieldStyle);
		presetName.setMessageText(I18n.t("预设名称", "Preset name"));
		TextButton save = new TextButton(I18n.t("保存预设", "Save preset"), kit.accentButton);
		TextButton load = new TextButton(I18n.t("恢复预设", "Load preset"), kit.toolButton);
		TextButton remove = new TextButton(I18n.t("删除预设", "Delete preset"), kit.toolButton);
		Table actions = new Table();
		actions.add(presetName).width(150f).height(ROW_HEIGHT).padRight(5f);
		actions.add(save).width(92f).height(ROW_HEIGHT).padRight(5f);
		actions.add(load).width(92f).height(ROW_HEIGHT).padRight(5f);
		actions.add(remove).width(92f).height(ROW_HEIGHT);
		table.add(actions).colspan(2).width(460f).right();
		table.row().padTop(10f);
		Label hint = new Label(I18n.t("预设保存当前已填写并成功提交的导出参数。",
			"Presets store the export parameters last submitted successfully."), kit.dialogBodyStyle);
		hint.setWrap(true);
		table.add(hint).colspan(2).width(460f).minWidth(0f).padTop(4f);

		save.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				String name = ExportPresetStore.normalizeName(presetName.getText());
				if (name == null) { setStatus(I18n.t("请输入 1-64 个字符的预设名称", "Enter a preset name from 1 to 64 characters")); return; }
				if (host.saveExportPreset(name)) {
					setStatus(I18n.t("导出预设已保存", "Export preset saved"));
					refreshExportPresets(presets);
					presets.setSelected(name);
				} else setStatus(I18n.t("没有可保存的导出参数", "No export parameters are available to save"));
			}
		});
		load.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				String name = presets.getSelected();
				if (name == null || name.isBlank() || !host.loadExportPreset(name)) {
					setStatus(I18n.t("请选择有效的导出预设", "Select a valid export preset"));
					return;
				}
				presetName.setText(name);
				setStatus(I18n.t("导出预设已恢复", "Export preset loaded"));
			}
		});
		remove.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				String name = presets.getSelected();
				if (name == null || name.isBlank() || !host.deleteExportPreset(name)) {
					setStatus(I18n.t("请选择要删除的导出预设", "Select an export preset to delete"));
					return;
				}
				refreshExportPresets(presets);
				setStatus(I18n.t("导出预设已删除", "Export preset deleted"));
			}
		});
		return table;
	}

	private void refreshExportPresets(SelectBox<String> presets) {
		java.util.List<String> names = host.exportPresetNames();
		com.badlogic.gdx.utils.Array<String> items = new com.badlogic.gdx.utils.Array<>();
		for (String name : names) if (name != null && !name.isBlank()) items.add(name);
		if (items.isEmpty()) items.add(I18n.t("（暂无预设）", "(No presets)"));
		presets.setItems(items);
		presets.setDisabled(names.isEmpty());
	}

	private Table buildAudioPage() {
		Table table = pageTable();
		addToggleRow(table, "事件音频", "Event Audio",
			host.isEventAudioEnabled(), () -> host.setEventAudioEnabled(!host.isEventAudioEnabled()),
			false, true);
		return table;
	}

	/** Adds one label + state-button row; clicking toggles, syncs the editor, and rebuilds the page. */
	private void addToggleRow(Table table, String chinese, String english, boolean current,
		Runnable toggle, boolean smoothingWords, boolean enabled) {
		table.add(rowLabel(chinese, english)).growX().minWidth(0f).height(ROW_HEIGHT);
		TextButton state = new TextButton(smoothingWords ? smoothingText(current) : toggleText(current),
			current ? kit.activeToolButton : kit.toolButton);
		state.setDisabled(!enabled);
		state.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				if (state.isDisabled()) return;
				toggle.run();
				host.syncCommandState();
				selectPage(page);
			}
		});
		table.add(state).width(CONTROL_WIDTH).height(ROW_HEIGHT).right();
		table.row().padTop(8);
	}

	private Actor buildShortcutsPage() {
		Table table = pageTable();
		Label hint = new Label(I18n.t("点击键位开始录制;支持 Ctrl/Shift 组合,Esc 取消。",
			"Click a binding to record; Ctrl/Shift combinations supported, Esc cancels."), kit.dialogBodyStyle);
		hint.setWrap(true);
		Table header = new Table();
		header.add(hint).growX().minWidth(0f).padRight(8);
		TextButton resetAll = new TextButton(I18n.t("全部重置", "Reset All"), kit.toolButton);
		resetAll.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				capturing = null;
				bindings.resetAll();
				setStatus(I18n.t("已重置全部快捷键", "All shortcuts reset"));
				refreshShortcutsPage();
			}
		});
		header.add(resetAll).height(ROW_HEIGHT).minWidth(84f).top();
		table.add(header).growX().padBottom(6).row();

		Table list = new Table();
		list.top().left().defaults().left();
		KeyBindings.Category category = null;
		for (EditorAction action : EditorAction.values()) {
			if (action.category != category) {
				category = action.category;
				Label categoryLabel = new Label(category.label(), kit.dialogBodyStyle);
				list.add(categoryLabel).padTop(category == EditorAction.values()[0].category ? 0 : 10)
					.padBottom(2).colspan(3).row();
			}
			list.add(kit.controlLabel(action.label())).width(190f).height(ROW_HEIGHT).padLeft(10);
			boolean recording = capturing == action;
			TextButton comboButton = new TextButton(recording
				? I18n.t("按下新键位...", "Press new keys...")
				: bindings.combo(action).display(),
				recording ? kit.activeToolButton : kit.toolButton);
			comboButton.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					beginCapture(action);
				}
			});
			list.add(comboButton).width(150f).height(ROW_HEIGHT).padRight(6);
			TextButton reset = new TextButton(I18n.t("重置", "Reset"), kit.toolButton);
			reset.setDisabled(bindings.isDefault(action));
			reset.addListener(new ChangeListener() {
				@Override public void changed(ChangeEvent event, Actor actor) {
					if (reset.isDisabled()) return;
					if (capturing == action) capturing = null;
					bindings.reset(action);
					setStatus(boundMessage(action, bindings.combo(action)));
					refreshShortcutsPage();
				}
			});
			list.add(reset).width(60f).height(ROW_HEIGHT).row();
		}
		ScrollPane scroll = new ScrollPane(list, kit.scrollStyle);
		scroll.setFadeScrollBars(false);
		scroll.setScrollingDisabled(true, false);
		scroll.setScrollY(shortcutsScrollY);
		scroll.updateVisualScroll();
		table.add(scroll).grow();
		return table;
	}

	/** Rebuilds the shortcuts page in place, preserving the scroll position. */
	private void refreshShortcutsPage() {
		if (page != Page.SHORTCUTS) return;
		shortcutsScrollY = currentShortcutsScrollY();
		selectPage(Page.SHORTCUTS);
		shortcutsScrollY = 0f;
	}

	private float currentShortcutsScrollY() {
		Actor current = content.getActor();
		if (!(current instanceof Table table)) return 0f;
		for (Actor child : table.getChildren())
			if (child instanceof ScrollPane scroll) return scroll.getScrollY();
		return 0f;
	}

	static String toggleText(boolean on) {
		return on ? I18n.t("开启", "On") : I18n.t("关闭", "Off");
	}

	static String smoothingText(boolean smooth) {
		return smooth ? I18n.t("平滑", "Smooth") : I18n.t("像素", "Pixel");
	}

	static String boundMessage(EditorAction action, Combo combo) {
		return action.label() + ": " + combo.display();
	}

	static String reservedMessage(Combo combo) {
		return I18n.t("键位 " + combo.display() + " 由编辑器保留,无法绑定",
			"Key " + combo.display() + " is reserved by the editor");
	}

	static String conflictMessage(EditorAction owner) {
		if (owner == null) return I18n.t("无法绑定该键位", "Cannot bind this key");
		return I18n.t("与已有快捷键冲突: ", "Conflicts with: ") + owner.label()
			+ " (" + owner.category.label() + ")";
	}
}
