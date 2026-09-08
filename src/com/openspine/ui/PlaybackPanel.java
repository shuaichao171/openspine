package com.openspine.ui;

import java.util.Locale;
import java.util.function.BiConsumer;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.openspine.model.SkeletonDocument;
import com.openspine.model.EditHistory;

/**
 * 底部播放控制：动画列表、皮肤列表、播放/暂停、时间线、速度。
 */
public class PlaybackPanel extends Table {
	private static final float MIN_PLAYBACK_SPEED = 0f;
	private static final float MAX_PLAYBACK_SPEED = 3f;
	static final int DEFAULT_FRAME = 0;
	static final float FRAME_FIELD_WIDTH = 46f;
	static final float DEFAULT_PLAYBACK_SPEED = 1f;
	static final float RATE_CONTROL_SLIDER_WIDTH = 100f;

	private final SkeletonDocument doc;
	private final UiKit kit;

	final SelectBox<String> animBox;
	final SelectBox<String> skinBox;
	final TextButton skinCombinationBtn;
	final TextButton playBtn;
	final TextButton resetBtn;
	final Slider timeSlider;
	final Slider speedSlider;
	final TextField frameField;
	final Label timeLabel;
	final TextButton speedResetBtn;
	final TextButton frameResetBtn;
	private final Label animationLabel;
	private final Label skinLabel;
	final TextButton loopBtn;
	final TextButton previousFrameBtn;
	final TextButton nextFrameBtn;

	private final UpdateGuard updates = new UpdateGuard();
	private Runnable onAnimationChanged;
	private Runnable onModeChanged;
	private Runnable onSkinChanged;
	private EditHistory history;
	private BiConsumer<String, Boolean> onStatus;

	public PlaybackPanel(SkeletonDocument doc, UiKit kit) {
		this.doc = doc;
		this.kit = kit;
		setBackground(kit.panelBg);
		pad(4);

		playBtn = UiKit.asActionButton(new TextButton(I18n.t("播放", "Play"), kit.accentButton));
		playBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				togglePlay();
			}
		});
		resetBtn = UiKit.asActionButton(new TextButton(I18n.t("复位", "Reset"), kit.toolButton));
		resetBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				stopToStart();
			}
		});
		loopBtn = UiKit.asActionButton(new TextButton(I18n.t("循环", "Loop"), kit.toolButton));
		loopBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (!hasCurrentAnimation()) return;
				doc.setLooping(!doc.looping);
				refreshButtons();
			}
		});
		previousFrameBtn = UiKit.asActionButton(new TextButton(I18n.t("上一帧", "Previous Frame"), kit.toolButton));
		previousFrameBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { stepFrame(-1); }
		});
		nextFrameBtn = UiKit.asActionButton(new TextButton(I18n.t("下一帧", "Next Frame"), kit.toolButton));
		nextFrameBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) { stepFrame(1); }
		});

		animBox = kit.selectBox();
		animBox.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (updates.active()) return;
				playSelectedAnimation();
			}
		});
		skinBox = kit.selectBox();
		skinBox.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (updates.active()) return;
				String selected = skinBox.getSelected();
				if (doc.setSkin(selected)) {
					if (onSkinChanged != null) onSkinChanged.run();
				}
			}
		});
		skinCombinationBtn = UiKit.asActionButton(new TextButton(I18n.t("组合…", "Compose..."), kit.toolButton));
		skinCombinationBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				SkinCombinationDialog.show(getStage(), kit, doc, history, onSkinChanged);
			}
		});

		timeSlider = new Slider(0f, doc.maxTime, 0.01f, false, kit.sliderStyle);
		timeSlider.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (updates.active()) return;
				boolean modeChanged = doc.poseMode || doc.playing;
				if (!doc.scrubTo(timeSlider.getValue())) return;
				refreshTimeLabel();
				refreshButtons();
				if (modeChanged && onModeChanged != null) onModeChanged.run();
			}
		});

		speedSlider = new Slider(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED, 0.05f, false, kit.sliderStyle);
		speedSlider.setValue(1f);
		speedSlider.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (updates.active()) return;
				setPlaybackSpeed(speedSlider.getValue());
			}
		});

		frameField = new TextField(Integer.toString(DEFAULT_FRAME), kit.textFieldStyle);
		frameField.setMessageText(I18n.t("帧", "Frame"));
		frameField.setMaxLength(10);
		frameField.setAlignment(Align.center);
		frameField.setTextFieldFilter((field, character) -> Character.isDigit(character));
		frameField.addListener(restoreFrameTextOnFocusLost(() -> refreshTimeLabel(true)));
		frameField.addListener(new InputListener() {
			@Override public boolean keyDown(InputEvent event, int keycode) {
				return switch (UiDialogs.keyAction(keycode)) {
				case CONFIRM -> {
					if (seekToFrame()) clearKeyboardFocus();
					yield true;
				}
				case CANCEL -> {
					refreshTimeLabel(true);
					clearKeyboardFocus();
					showStatus("已取消帧定位", false);
					yield true;
				}
				case NONE -> false;
				};
			}
		});
		timeLabel = kit.controlLabel("0.00 / 0.00s");
		speedResetBtn = UiKit.asActionButton(
			new TextButton(playbackSpeedLabel(DEFAULT_PLAYBACK_SPEED), kit.toolButton));
		speedResetBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				setPlaybackSpeed(DEFAULT_PLAYBACK_SPEED);
			}
		});
		frameResetBtn = UiKit.asActionButton(new TextButton(I18n.t("帧", "Frame"), kit.toolButton));
		frameResetBtn.addListener(new ChangeListener() {
			@Override public void changed(ChangeEvent event, Actor actor) {
				stopToStart();
			}
		});

		animationLabel = kit.controlLabel(I18n.t("动画", "Animation"));
		skinLabel = kit.controlLabel(I18n.t("皮肤", "Skin"));

		Table transport = controlGroup();
		transport.add(resetBtn).height(26).padRight(3);
		transport.add(previousFrameBtn).height(26).padRight(3);
		transport.add(playBtn).height(26).padRight(3);
		transport.add(nextFrameBtn).height(26).padRight(3);
		transport.add(loopBtn).height(26);
		Table skin = controlGroup();
		skin.add(skinLabel).left().padRight(5);
		skin.add(skinBox).width(105).height(26);
		skin.add(skinCombinationBtn).width(70f).height(26).padLeft(3f);
		Table timing = controlGroup();
		timing.add(frameResetBtn).height(26).padRight(4);
		timing.add(frameField).width(FRAME_FIELD_WIDTH).height(26).padRight(7);
		timing.add(timeLabel).right().width(102).padRight(7);
		timing.add(timeSlider).growX().minWidth(160);
		Table speed = controlGroup();
		speed.add(speedResetBtn).height(26).padRight(5);
		speed.add(speedSlider).width(RATE_CONTROL_SLIDER_WIDTH);
		add(transport).height(30).padRight(5);
		add(skin).height(30).padRight(5);
		add(timing).growX().minWidth(0f).height(30).padRight(5);
		add(speed).height(30);
		refreshButtons();
	}

	private Table controlGroup() {
		Table group = new Table();
		group.setBackground(kit.scrollBg);
		group.pad(2);
		return group;
	}

	/** 文档加载后刷新动画/皮肤列表 */
	public void rebuild() {
		String keepAnimation = doc.getCurrentAnimationName();
		String keepSkin = doc.getCurrentSkinName();
		updates.run(() -> {
			Array<String> animations = new Array<>();
			Array<String> skins = new Array<>();
			if (doc.data != null) {
				if (doc.data.getAnimations() != null && doc.data.getAnimations().size > 0) {
					for (com.esotericsoftware.spine.Animation a : doc.data.getAnimations()) {
						animations.add(a.getName());
					}
				}
				if (doc.data.getSkins() != null && doc.data.getSkins().size > 0) {
					for (com.esotericsoftware.spine.Skin s : doc.data.getSkins()) {
						skins.add(s.getName());
					}
				}
			}
			animBox.setItems(animations);
			skinBox.setItems(skins);
			if (keepAnimation != null && !keepAnimation.isEmpty() && animations.contains(keepAnimation, false))
				animBox.setSelected(keepAnimation);
			else if (animations.notEmpty()) animBox.setSelectedIndex(0);
			if (keepSkin != null && !keepSkin.isEmpty() && skins.contains(keepSkin, false))
				skinBox.setSelected(keepSkin);
			else if (skins.notEmpty()) skinBox.setSelectedIndex(0);
		});
		if (doc.getCurrentAnimationName().isEmpty() && doc.shouldAutoSelectAnimation()) playSelectedAnimation();
		refreshRange(true);
		refreshPlaybackSpeed();
		refreshButtons();
	}

	public void togglePlay() {
		if (!hasCurrentAnimation()) return;
		if (doc.poseMode) {
			doc.setPoseMode(false);
			if (onModeChanged != null) onModeChanged.run();
		}
		if (doc.playing) {
			doc.setPlaying(false);
			playBtn.setText(I18n.t("播放", "Play"));
		} else {
			resume();
		}
	}

	/** Pauses playback and seeks exactly one project frame in either direction. */
	public void stepFrame(int direction) {
		if (direction == 0 || !hasCurrentAnimation()) return;
		if (doc.poseMode) doc.setPoseMode(false);
		doc.setPlaying(false);
		float fps = UiNumbers.effectiveFps(doc.data == null ? 0f : doc.data.getFps());
		float time = UiNumbers.adjacentFrameTime(doc.getCurrentTime(), doc.getCurrentDuration(), fps, direction);
		doc.seek(time);
		updates.run(() -> timeSlider.setValue(time));
		refreshTimeLabel();
		refreshButtons();
		if (onModeChanged != null) onModeChanged.run();
	}

	private void resume() {
		if (!hasCurrentAnimation()) return;
		if (!doc.looping && doc.getCurrentTime() >= doc.getCurrentDuration() - 0.0001f)
			doc.seek(0f);
		doc.setPlaying(true);
		playBtn.setText(I18n.t("暂停", "Pause"));
	}

	private void stopToStart() {
		if (!hasCurrentAnimation()) return;
		doc.setPoseMode(false);
		doc.setPlaying(false);
		doc.seek(0f);
		updates.run(() -> timeSlider.setValue(0f));
		refreshTimeLabel(true);
		refreshButtons();
		if (onModeChanged != null) onModeChanged.run();
	}

	private void playSelectedAnimation() {
		if (doc.animationState == null || doc.data == null) return;
		String name = animBox.getSelected();
		if (name == null || name.isEmpty()) return;
		if (!doc.setAnimation(name)) return;
		refreshRange();
		refreshTimeLabel();
		refreshButtons();
		if (onAnimationChanged != null) onAnimationChanged.run();
	}

	private boolean seekToFrame() {
		if (!hasCurrentAnimation()) return false;
		Integer requestedFrame = parseFrame(frameField.getText());
		float duration = doc.getCurrentDuration();
		float fps = UiNumbers.effectiveFps(doc.data == null ? 0f : doc.data.getFps());
		int lastFrame = maximumFrame(duration, fps);
		if (requestedFrame == null || requestedFrame < 0 || requestedFrame > lastFrame) {
			showStatus("帧必须是 0 到 " + lastFrame + " 之间的整数", true);
			return false;
		}
		boolean modeChanged = doc.poseMode || doc.playing;
		float time = timeForFrame(requestedFrame, duration, fps);
		if (!doc.scrubTo(time)) {
			showStatus("无法跳转到指定帧", true);
			return false;
		}
		updates.run(() -> timeSlider.setValue(time));
		refreshTimeLabel(true);
		refreshButtons();
		if (modeChanged && onModeChanged != null) onModeChanged.run();
		showStatus("已跳转到 " + requestedFrame + " 帧", false);
		return true;
	}

	static Integer parseFrame(String text) {
		if (text == null || text.isBlank()) return null;
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	static int frameAtTime(float time, float fps) {
		if (!Float.isFinite(time) || time <= 0f) return 0;
		double frame = time * (double)UiNumbers.effectiveFps(fps);
		return frame >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)Math.round(frame + 0.0001d);
	}

	static int maximumFrame(float duration, float fps) {
		if (!Float.isFinite(duration) || duration <= 0f) return 0;
		double frame = duration * (double)UiNumbers.effectiveFps(fps);
		return frame >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int)Math.ceil(frame - 0.0001d));
	}

	static int displayFrame(float time, float duration, float fps) {
		if (Float.isFinite(duration) && duration > 0f && time >= duration - 0.0001f)
			return maximumFrame(duration, fps);
		return frameAtTime(time, fps);
	}

	static float timeForFrame(int frame, float duration, float fps) {
		float end = Float.isFinite(duration) ? Math.max(0f, duration) : 0f;
		if (frame <= 0 || end == 0f) return 0f;
		return Math.min(end, frame / UiNumbers.effectiveFps(fps));
	}

	static FocusListener restoreFrameTextOnFocusLost(Runnable restore) {
		if (restore == null) throw new IllegalArgumentException("恢复操作不能为空");
		return new FocusListener() {
			@Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
				if (!focused) restore.run();
			}
		};
	}

	private void refreshPlaybackSpeed() {
		setPlaybackSpeed(doc.playbackSpeed);
	}

	private void setPlaybackSpeed(float requestedSpeed) {
		float speed = normalizedPlaybackSpeed(requestedSpeed);
		doc.playbackSpeed = speed;
		updates.run(() -> speedSlider.setValue(speed));
		speedResetBtn.setText(playbackSpeedLabel(speed));
	}

	static float normalizedPlaybackSpeed(float speed) {
		if (!Float.isFinite(speed)) return DEFAULT_PLAYBACK_SPEED;
		return Math.max(MIN_PLAYBACK_SPEED, Math.min(MAX_PLAYBACK_SPEED, speed));
	}

	static String playbackSpeedLabel(float speed) {
		return String.format(Locale.ROOT, I18n.t("速度: %.2f×", "Speed: %.2f×"),
			normalizedPlaybackSpeed(speed));
	}

	/** 每帧由主循环调用，驱动时间线滑块跟随 */
	public void update() {
		if (updates.active() || doc.poseMode || doc.animationState == null) return;
		float t = doc.getCurrentTime();
		if (doc.playing && !doc.looping && t >= doc.getCurrentDuration() - 0.0001f) {
			doc.setPlaying(false);
			refreshButtons();
			if (onModeChanged != null) onModeChanged.run();
		}
		float fps = UiNumbers.effectiveFps(doc.data == null ? 0f : doc.data.getFps());
		float displayTime = Math.min(doc.getCurrentDuration(), UiNumbers.snapTime(t, fps));
		if (Math.abs(displayTime - timeSlider.getValue()) > 0.0001f) {
			updates.run(() -> timeSlider.setValue(displayTime));
			refreshTimeLabel();
		}
	}

	private void refreshTimeLabel() {
		refreshTimeLabel(false);
	}

	private void refreshTimeLabel(boolean forceFrameField) {
		float duration = hasCurrentAnimation() ? doc.getCurrentDuration() : 0f;
		float fps = UiNumbers.effectiveFps(doc.data == null ? 0f : doc.data.getFps());
		int frame = displayFrame(timeSlider.getValue(), duration, fps);
		if (forceFrameField || getStage() == null || getStage().getKeyboardFocus() != frameField)
			frameField.setText(Integer.toString(frame));
		timeLabel.setText(String.format(Locale.ROOT, "%.2f / %.2fs", timeSlider.getValue(), duration));
	}

	private void refreshRange() {
		refreshRange(false);
	}

	private void refreshRange(boolean forceFrameField) {
		float duration = hasCurrentAnimation() ? doc.getCurrentDuration() : 0f;
		doc.maxTime = duration;
		float fps = UiNumbers.effectiveFps(doc.data == null ? 0f : doc.data.getFps());
		updates.run(() -> {
			timeSlider.setStepSize(1f / fps);
			timeSlider.setRange(0f, Math.max(0.01f, duration));
			timeSlider.setValue(Math.min(duration, doc.getCurrentTime()));
		});
		refreshTimeLabel(forceFrameField);
	}

	private void refreshButtons() {
		boolean hasAnimationItems = doc.animationState != null && !animBox.getItems().isEmpty();
		boolean hasAnimation = hasCurrentAnimation();
		boolean hasSkin = doc.skeleton != null && !skinBox.getItems().isEmpty();
		animBox.setDisabled(!hasAnimationItems);
		skinBox.setDisabled(!hasSkin);
		skinCombinationBtn.setDisabled(!hasSkin);
		skinCombinationBtn.setText(doc.getPreviewSkinNames().size() > 1
			? I18n.t("组合 ", "Mix ") + doc.getPreviewSkinNames().size() : I18n.t("组合…", "Compose..."));
		skinCombinationBtn.setStyle(doc.getPreviewSkinNames().size() > 1 ? kit.activeToolButton : kit.toolButton);
		playBtn.setDisabled(!hasAnimation);
		previousFrameBtn.setDisabled(!hasAnimation);
		nextFrameBtn.setDisabled(!hasAnimation);
		resetBtn.setDisabled(!hasAnimation);
		loopBtn.setDisabled(!hasAnimation);
		timeSlider.setDisabled(!hasAnimation);
		speedSlider.setDisabled(!hasAnimation);
		speedResetBtn.setDisabled(!hasAnimation);
		frameResetBtn.setDisabled(!hasAnimation);
		frameField.setDisabled(!hasAnimation);
		playBtn.setText(hasAnimation && doc.playing && !doc.poseMode
			? I18n.t("暂停", "Pause") : I18n.t("播放", "Play"));
		loopBtn.setText(I18n.t("循环", "Loop"));
		loopBtn.setStyle(doc.looping ? kit.activeToolButton : kit.toolButton);
	}

	/** Relabels persistent playback controls after the interface language changes. */
	public void refreshLanguage() {
		animationLabel.setText(I18n.t("动画", "Animation"));
		skinLabel.setText(I18n.t("皮肤", "Skin"));
		frameResetBtn.setText(I18n.t("帧", "Frame"));
		frameField.setMessageText(I18n.t("帧", "Frame"));
		previousFrameBtn.setText(I18n.t("上一帧", "Previous Frame"));
		nextFrameBtn.setText(I18n.t("下一帧", "Next Frame"));
		resetBtn.setText(I18n.t("复位", "Reset"));
		speedResetBtn.setText(playbackSpeedLabel(doc.playbackSpeed));
		refreshButtons();
	}

	private boolean hasCurrentAnimation() {
		if (doc.data == null || doc.animationState == null) return false;
		String name = doc.getCurrentAnimationName();
		return name != null && !name.isEmpty() && doc.data.findAnimation(name) != null;
	}

	public void setOnAnimationChanged(Runnable r) { onAnimationChanged = r; }
	public void setOnModeChanged(Runnable r) { onModeChanged = r; }
	public void setOnSkinChanged(Runnable r) { onSkinChanged = r; }
	public void setHistory(EditHistory history) { this.history = history; }
	public void setOnStatus(BiConsumer<String, Boolean> listener) { onStatus = listener; }

	private void showStatus(String message, boolean error) {
		if (onStatus != null) onStatus.accept(message, error);
	}

	private void clearKeyboardFocus() {
		if (getStage() != null) getStage().setKeyboardFocus(null);
	}

	public void refreshFromDocument() {
		updates.run(() -> {
			if (!skinItemsMatchDocument()) {
				Array<String> skins = new Array<>();
				if (doc.data != null) for (var item : doc.data.getSkins()) skins.add(item.getName());
				skinBox.setItems(skins);
			}
			String animation = doc.getCurrentAnimationName();
			if (animation != null && !animation.isEmpty() && animBox.getItems().contains(animation, false))
				animBox.setSelected(animation);
			String skin = doc.getCurrentSkinName();
			if (skin != null && !skin.isEmpty() && skinBox.getItems().contains(skin, false))
				skinBox.setSelected(skin);
		});
		refreshRange();
		refreshPlaybackSpeed();
		refreshButtons();
	}

	/** Skin creation and its undo can keep the current skeleton data while changing the available choices. */
	private boolean skinItemsMatchDocument() {
		Array<String> items = skinBox.getItems();
		if (doc.data == null) return items.isEmpty();
		var skins = doc.data.getSkins();
		if (items.size != skins.size) return false;
		for (int index = 0; index < skins.size; index++)
			if (!skins.get(index).getName().equals(items.get(index))) return false;
		return true;
	}

	static final class UpdateGuard {
		private int depth;

		boolean active() {
			return depth > 0;
		}

		void run(Runnable update) {
			depth++;
			try {
				update.run();
			} finally {
				depth--;
			}
		}
	}
}
