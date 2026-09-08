package com.openspine.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Owns every skeleton in an editor project while keeping one stable document bound to the UI. */
public final class SkeletonProject implements AutoCloseable {
	private final SkeletonDocument activeDocument;
	private final List<Entry> entries = new ArrayList<>();
	private int activeIndex = -1;
	private List<MemberState> savedMembers = List.of();

	public SkeletonProject(SkeletonDocument activeDocument) {
		if (activeDocument == null) throw new IllegalArgumentException("Active document is required");
		this.activeDocument = activeDocument;
	}

	public void replaceAll(List<LoadedSkeleton> skeletons) {
		List<LoadedSkeleton> validated = validateLoaded(skeletons);
		clear();
		Set<String> usedNames = new HashSet<>();
		for (int index = 0; index < validated.size(); index++) {
			LoadedSkeleton loaded = validated.get(index);
			String name = uniqueName(normalizedName(loaded.name(), index), usedNames);
			entries.add(new Entry(name, loaded.document()));
		}
		activeIndex = 0;
		activeDocument.adoptLoadedContentFrom(entries.get(0).parked);
		markStructureSaved();
	}

	public int add(LoadedSkeleton loaded, boolean activate) {
		validateLoaded(java.util.Collections.singletonList(loaded));
		Set<String> usedNames = new HashSet<>();
		for (Entry entry : entries) usedNames.add(entry.name.toLowerCase(Locale.ROOT));
		String name = uniqueName(normalizedName(loaded.name(), entries.size()), usedNames);
		if (entries.isEmpty()) {
			entries.add(new Entry(name, loaded.document()));
			activeIndex = 0;
			activeDocument.adoptLoadedContentFrom(entries.get(0).parked);
			return 0;
		}
		entries.add(new Entry(name, loaded.document()));
		int index = entries.size() - 1;
		if (activate) switchTo(index);
		return index;
	}

	public boolean switchTo(int index) {
		if (index < 0 || index >= entries.size())
			throw new IllegalArgumentException("Skeleton index is out of range: " + index);
		if (index == activeIndex) return false;
		if (activeIndex >= 0) entries.get(activeIndex).parked.adoptLoadedContentFrom(activeDocument);
		activeDocument.adoptLoadedContentFrom(entries.get(index).parked);
		activeIndex = index;
		return true;
	}

	/** Returns the index of a document whose source path matches the supplied path, or -1. */
	public int indexOfSourcePath(String sourcePath) {
		String normalized = normalizedSourcePath(sourcePath);
		if (normalized == null) return -1;
		for (int index = 0; index < entries.size(); index++) {
			SkeletonDocument document = index == activeIndex ? activeDocument : entries.get(index).parked;
			if (normalized.equals(normalizedSourcePath(document.filePath))
				|| normalized.equals(normalizedSourcePath(document.savePath))) return index;
		}
		return -1;
	}

	/** True when the project has more than one tab and one can be closed. */
	public boolean canRemove() {
		return entries.size() > 1;
	}

	/** Removes a tab, preserving the active document wrapper and disposing removed resources. */
	public boolean removeAt(int index) {
		if (index < 0 || index >= entries.size())
			throw new IllegalArgumentException("Skeleton index is out of range: " + index);
		if (entries.size() <= 1) return false;
		if (index == activeIndex) {
			int replacement = index == entries.size() - 1 ? index - 1 : index + 1;
			activeDocument.adoptLoadedContentFrom(entries.get(replacement).parked);
			entries.get(index).parked.dispose();
			entries.remove(index);
			activeIndex = replacement > index ? replacement - 1 : replacement;
		} else {
			entries.get(index).parked.dispose();
			entries.remove(index);
			if (index < activeIndex) activeIndex--;
		}
		return true;
	}

	/** Moves a tab while retaining the active tab identity. */
	public boolean move(int from, int to) {
		if (from < 0 || from >= entries.size() || to < 0 || to >= entries.size())
			throw new IllegalArgumentException("Skeleton tab index is out of range");
		if (from == to) return false;
		Entry moved = entries.remove(from);
		entries.add(to, moved);
		if (activeIndex == from) activeIndex = to;
		else if (from < activeIndex && activeIndex <= to) activeIndex--;
		else if (to <= activeIndex && activeIndex < from) activeIndex++;
		return true;
	}

	public int size() { return entries.size(); }
	public int activeIndex() { return activeIndex; }
	public String activeName() { return activeIndex < 0 ? "" : entries.get(activeIndex).name; }
	public String nameAt(int index) { return entries.get(index).name; }

	/** Project names belong to the manifest, independently of runtime file names. */
	public boolean rename(int index, String requested) {
		String name = requested == null ? "" : requested.trim();
		if (name.isEmpty()) throw new IllegalArgumentException("骨架名称不能为空");
		for (int candidate = 0; candidate < entries.size(); candidate++)
			if (candidate != index && entries.get(candidate).name.equalsIgnoreCase(name))
				throw new IllegalArgumentException("骨架名称已存在：" + name);
		Entry entry = entries.get(index);
		if (entry.name.equals(name)) return false;
		entry.name = name;
		return true;
	}

	public void markStructureSaved() { savedMembers = memberState(); }
	public boolean hasStructureChanges() { return !savedMembers.equals(memberState()); }

	private List<MemberState> memberState() {
		return entries.stream().map(entry -> new MemberState(entry, entry.name)).toList();
	}

	public boolean isDirty() {
		if (hasStructureChanges()) return true;
		if (activeDocument.dirty) return true;
		for (int index = 0; index < entries.size(); index++)
			if (index != activeIndex && entries.get(index).parked.dirty) return true;
		return false;
	}

	public int firstDirtyIndex() {
		for (int index = 0; index < entries.size(); index++) {
			SkeletonDocument document = index == activeIndex ? activeDocument : entries.get(index).parked;
			if (document.dirty) return index;
		}
		return -1;
	}

	public List<SkeletonInfo> skeletons() {
		List<SkeletonInfo> result = new ArrayList<>(entries.size());
		for (int index = 0; index < entries.size(); index++) {
			SkeletonDocument document = index == activeIndex ? activeDocument : entries.get(index).parked;
			result.add(new SkeletonInfo(entries.get(index).name, document.dirty, index == activeIndex));
		}
		return List.copyOf(result);
	}

	/** Returns live documents in project order for rendering. Callers must not retain or dispose them. */
	public List<SkeletonDocument> documents() {
		List<SkeletonDocument> result = new ArrayList<>(entries.size());
		for (int index = 0; index < entries.size(); index++)
			result.add(index == activeIndex ? activeDocument : entries.get(index).parked);
		return List.copyOf(result);
	}

	public void applyInactiveAnimations(float delta) {
		for (int index = 0; index < entries.size(); index++)
			if (index != activeIndex) entries.get(index).parked.applyAnimation(delta);
	}

	public void clear() {
		activeDocument.dispose();
		for (Entry entry : entries) entry.parked.dispose();
		entries.clear();
		activeIndex = -1;
		savedMembers = List.of();
	}

	@Override public void close() { clear(); }

	public record LoadedSkeleton(String name, SkeletonDocument document) {}
	public record SkeletonInfo(String name, boolean dirty, boolean active) {}

	private List<LoadedSkeleton> validateLoaded(List<LoadedSkeleton> skeletons) {
		if (skeletons == null || skeletons.isEmpty())
			throw new IllegalArgumentException("At least one skeleton is required");
		Set<SkeletonDocument> documents = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (LoadedSkeleton loaded : skeletons) {
			if (loaded == null || loaded.document() == null || loaded.document().data == null
				|| loaded.document().skeleton == null || loaded.document().animationState == null)
				throw new IllegalArgumentException("Loaded skeleton is incomplete");
			if (!documents.add(loaded.document()))
				throw new IllegalArgumentException("A skeleton document cannot appear twice");
			if (owns(loaded.document()))
				throw new IllegalArgumentException("A skeleton document is already owned by this project");
		}
		return List.copyOf(skeletons);
	}

	private boolean owns(SkeletonDocument document) {
		if (document == activeDocument) return true;
		for (Entry entry : entries)
			if (entry.parked == document) return true;
		return false;
	}

	private static String normalizedName(String name, int index) {
		String normalized = name == null ? "" : name.trim();
		return normalized.isEmpty() ? "骨架 " + (index + 1) : normalized;
	}

	private static String uniqueName(String requested, Set<String> usedNames) {
		String candidate = requested;
		for (int suffix = 2; !usedNames.add(candidate.toLowerCase(Locale.ROOT)); suffix++)
			candidate = requested + " (" + suffix + ")";
		return candidate;
	}

	private static String normalizedSourcePath(String path) {
		if (path == null || path.isBlank() || path.startsWith("builtin:") || path.startsWith("new:")) return null;
		try {
			return java.nio.file.Path.of(path).toAbsolutePath().normalize().toString()
				.toLowerCase(Locale.ROOT);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static final class Entry {
		String name;
		final SkeletonDocument parked;

		Entry(String name, SkeletonDocument parked) {
			this.name = name;
			this.parked = parked;
		}
	}
	private record MemberState(Entry identity, String name) {}
}
