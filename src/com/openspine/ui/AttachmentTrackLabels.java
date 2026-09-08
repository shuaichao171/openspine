package com.openspine.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.HasSequence;
import com.esotericsoftware.spine.attachments.VertexAttachment;

/** UI addresses describe the attachment that owns a timeline, independently of the preview skin. */
final class AttachmentTrackLabels {

	record Address(String skin, String slot, String placeholder, String attachment) {
		String path() {
			return displaySkin() + " / " + slot + " / " + placeholder
				+ (attachment.equals(placeholder) ? "" : " [" + attachment + "]");
		}
		String displaySkin() { return skin.isEmpty() ? I18n.t("未注册皮肤", "Unregistered skin") : skin; }
		String legacyKey(String kind) { return String.join("\u001f", kind, slot, placeholder, attachment); }
		String stateKey(String kind) { return legacyKey(kind) + "\u001f" + skin; }
	}

	record Target(Address address, List<Address> shared, List<Address> inherited) {
		String rowLabel(String kind) { return kind + " · " + address.displaySkin() + " · " + address.placeholder(); }
		String fullLabel(String kind) { return address.path() + " · " + kind; }
		String tooltip(String kind) {
			StringBuilder text = new StringBuilder(kind)
				.append(I18n.t("\n皮肤：", "\nSkin: ")).append(address.displaySkin())
				.append(I18n.t("\n插槽：", "\nSlot: ")).append(address.slot())
				.append(I18n.t("\n占位：", "\nPlaceholder: ")).append(address.placeholder())
				.append(I18n.t("\n附件：", "\nAttachment: ")).append(address.attachment());
			for (Address alias : shared) if (!alias.equals(address))
				text.append(I18n.t("\n共享注册：", "\nShared registration: ")).append(alias.path());
			for (Address alias : inherited)
				text.append(I18n.t("\n继承此轨道：", "\nInherits this timeline: ")).append(alias.path());
			return text.toString();
		}
		boolean matches(String kind, String query) {
			if (query == null || query.isBlank()) return true;
			return (fullLabel(kind) + "\n" + tooltip(kind)).toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
		}
	}

	/** Rebuilt with the track list, so renames refresh addresses without changing attachment identity. */
	static final class Index {
		record Binding(int slot, Attachment attachment, Target target) {}
		private final SkeletonData data;
		private final Map<Integer, IdentityHashMap<Attachment, List<Address>>> registrations = new LinkedHashMap<>();
		private final IdentityHashMap<Attachment, List<Address>> inherited = new IdentityHashMap<>();
		private final Map<Integer, IdentityHashMap<Attachment, Target>> targets = new LinkedHashMap<>();

		Index(SkeletonData data) {
			this.data = data;
			if (data == null) return;
			for (Skin skin : data.getSkins()) collect(skin);
			if (data.getDefaultSkin() != null && !data.getSkins().contains(data.getDefaultSkin(), true)) collect(data.getDefaultSkin());
			for (var slot : registrations.entrySet()) for (var attachment : slot.getValue().entrySet()) {
				List<Address> shared = sorted(attachment.getValue());
				targets.computeIfAbsent(slot.getKey(), ignored -> new IdentityHashMap<>()).put(attachment.getKey(),
					new Target(shared.get(0), shared, sorted(inherited.get(attachment.getKey()))));
			}
		}

		private void collect(Skin skin) {
			for (Skin.SkinEntry entry : skin.getAttachments()) {
				int slot = entry.getSlotIndex();
				Attachment attachment = entry.getAttachment();
				if (attachment == null || slot < 0 || slot >= data.getSlots().size) continue;
				Address address = new Address(skin.getName(), data.getSlots().get(slot).getName(), entry.getPlaceholder(), attachment.getName());
				registrations.computeIfAbsent(slot, ignored -> new IdentityHashMap<>())
					.computeIfAbsent(attachment, ignored -> new ArrayList<>()).add(address);
				Attachment owner = attachment.getTimelineAttachment();
				if (owner != null && owner != attachment)
					inherited.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(address);
			}
		}

		Target target(int slot, Attachment attachment, String fallbackName) {
			IdentityHashMap<Attachment, Target> entries = targets.get(slot);
			Target target = entries == null ? null : entries.get(attachment);
			if (target != null) return target;
			String slotName = data != null && slot >= 0 && slot < data.getSlots().size ? data.getSlots().get(slot).getName() : "";
			String name = attachment == null ? "" : attachment.getName();
			return new Target(new Address("", slotName, fallbackName == null ? name : fallbackName, name), List.of(), List.of());
		}

		Binding resolveKey(String key) {
			if (key == null) return null;
			String[] parts = key.split("\u001f", -1);
			if ((parts.length != 4 && parts.length != 5)
				|| !(parts[0].equals("deform") || parts[0].equals("sequence"))) return null;
			Binding found = null;
			for (var slot : targets.entrySet()) for (var entry : slot.getValue().entrySet()) {
				if (!eligible(entry.getKey(), parts[0])) continue;
				boolean matches = entry.getValue().shared().stream().anyMatch(address ->
					(parts.length == 4 ? address.legacyKey(parts[0]) : address.stateKey(parts[0])).equals(key));
				if (!matches) continue;
				if (found != null) return null;
				found = new Binding(slot.getKey(), entry.getKey(), entry.getValue());
			}
			return found;
		}

		/** Shared registrations can change their canonical skin when an alias is renamed or removed. */
		String migrateKey(Index previous, String key) {
			Binding current = resolveKey(key);
			if (current != null) return current.target().address().stateKey(key.substring(0, key.indexOf('\u001f')));
			Binding old = previous == null ? null : previous.resolveKey(key);
			if (old == null) return key;
			for (var entries : targets.values()) {
				Target replacement = entries.get(old.attachment());
				if (replacement != null && replacement.address().slot().equals(old.target().address().slot()))
					return replacement.address().stateKey(key.substring(0, key.indexOf('\u001f')));
			}
			return key;
		}

		/** Older keys omitted the skin. Never restore them to an arbitrary same-named owner. */
		String migrateUniqueLegacyKey(String key) {
			if (key == null) return "";
			String[] parts = key.split("\u001f", -1);
			if (parts.length != 4 || !(parts[0].equals("deform") || parts[0].equals("sequence"))) return "";
			Binding found = resolveKey(key);
			return found == null ? "" : found.target().address().stateKey(parts[0]);
		}

		private static boolean eligible(Attachment owner, String kind) {
			return owner.getTimelineAttachment() == owner && (kind.equals("deform")
				? owner instanceof VertexAttachment
				: owner instanceof HasSequence sequence && sequence.getSequence() != null);
		}
	}

	private static List<Address> sorted(List<Address> addresses) {
		if (addresses == null) return List.of();
		return addresses.stream().distinct().sorted(Comparator.comparing(Address::skin)
			.thenComparing(Address::slot).thenComparing(Address::placeholder).thenComparing(Address::attachment)).toList();
	}
	private AttachmentTrackLabels() {}
}
