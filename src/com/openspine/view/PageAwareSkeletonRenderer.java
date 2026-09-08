/******************************************************************************
 * Based on Spine Runtimes 4.3 SkeletonRenderer.
 * Copyright (c) 2013-2025, Esoteric Software LLC.
 * Distributed under the Spine Runtimes License Agreement included with the
 * bundled spine-libgdx runtime.
 *****************************************************************************/
package com.openspine.view;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.NumberUtils;
import com.badlogic.gdx.utils.ShortArray;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.SlotPose;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.ClippingAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.Sequence;
import com.esotericsoftware.spine.attachments.SkeletonAttachment;
import com.esotericsoftware.spine.utils.SkeletonClipping;
import com.openspine.io.AtlasAlphaModes;

/** Spine renderer variant that chooses color and blend PMA from each attachment texture. */
public final class PageAwareSkeletonRenderer extends SkeletonRenderer {
	private static final short[] QUAD_TRIANGLES = {0, 1, 2, 2, 3, 0};
	private final FloatArray vertices = new FloatArray(32);
	private final SkeletonClipping clipper = new SkeletonClipping();

	@Override public void draw(PolygonSpriteBatch batch, Skeleton skeleton) {
		if (batch == null) throw new IllegalArgumentException("batch cannot be null.");
		if (skeleton == null) throw new IllegalArgumentException("skeleton cannot be null.");

		BlendMode appliedBlendMode = null;
		boolean appliedPmaBlend = false;
		boolean blendApplied = false;
		int verticesLength = 0;
		float[] vertices = null, uvs = null;
		short[] triangles = null;
		Color color = null, skeletonColor = skeleton.getColor();
		float r = skeletonColor.r, g = skeletonColor.g, b = skeletonColor.b, a = skeletonColor.a;
		Array<Slot> drawOrder = skeleton.getDrawOrder().getAppliedPose();
		Slot[] slots = drawOrder.items;
		for (int i = 0, n = drawOrder.size; i < n; i++) {
			Slot slot = slots[i];
			if (slot.getBone().isActive()) {
				SlotPose pose = slot.getAppliedPose();
				Attachment attachment = pose.getAttachment();
				if (attachment != null) {
					Texture texture = null;
					if (attachment instanceof RegionAttachment region) {
						verticesLength = 20;
						vertices = this.vertices.items;
						Sequence sequence = region.getSequence();
						int sequenceIndex = sequence.resolveIndex(pose);
						region.computeWorldVertices(slot, sequence.getOffsets(sequenceIndex), vertices, 0, 5);
						triangles = QUAD_TRIANGLES;
						texture = sequence.getRegion(sequenceIndex).getTexture();
						uvs = sequence.getUVs(sequenceIndex);
						color = region.getColor();
					} else if (attachment instanceof MeshAttachment mesh) {
						int count = mesh.getWorldVerticesLength();
						verticesLength = (count >> 1) * 5;
						vertices = this.vertices.setSize(verticesLength);
						mesh.computeWorldVertices(skeleton, slot, 0, count, vertices, 0, 5);
						triangles = mesh.getTriangles();
						Sequence sequence = mesh.getSequence();
						int sequenceIndex = sequence.resolveIndex(pose);
						texture = sequence.getRegion(sequenceIndex).getTexture();
						uvs = sequence.getUVs(sequenceIndex);
						color = mesh.getColor();
					} else if (attachment instanceof ClippingAttachment clip) {
						clipper.clipEnd(slot);
						clipper.clipStart(skeleton, slot, clip);
						continue;
					} else if (attachment instanceof SkeletonAttachment skeletonAttachment) {
						Skeleton attachmentSkeleton = skeletonAttachment.getSkeleton();
						if (attachmentSkeleton != null) draw(batch, attachmentSkeleton);
					}

					if (texture != null) {
						boolean pmaColors = AtlasAlphaModes.premultiplied(texture,
							getPremultipliedAlphaColors());
						boolean pmaBlendModes = AtlasAlphaModes.premultiplied(texture,
							getPremultipliedAlphaBlendModes());
						Color slotColor = pose.getColor();
						float alpha = a * slotColor.a * color.a * 255;
						float multiplier = pmaColors ? alpha : 255;
						BlendMode slotBlendMode = slot.getData().getBlendMode();
						if (slotBlendMode == BlendMode.additive && pmaColors) {
							slotBlendMode = BlendMode.normal;
							alpha = 0;
						}
						if (!blendApplied || slotBlendMode != appliedBlendMode || pmaBlendModes != appliedPmaBlend) {
							appliedBlendMode = slotBlendMode;
							appliedPmaBlend = pmaBlendModes;
							blendApplied = true;
							slotBlendMode.apply(batch, pmaBlendModes);
						}

						float packedColor = NumberUtils.intToFloatColor((int)alpha << 24
							| (int)(b * slotColor.b * color.b * multiplier) << 16
							| (int)(g * slotColor.g * color.g * multiplier) << 8
							| (int)(r * slotColor.r * color.r * multiplier));
						if (clipper.isClipping()
							&& clipper.clipTriangles(vertices, triangles, triangles.length, uvs,
								packedColor, 0, false, 5)) {
							FloatArray clippedVertices = clipper.getClippedVertices();
							ShortArray clippedTriangles = clipper.getClippedTriangles();
							batch.draw(texture, clippedVertices.items, 0, clippedVertices.size,
								clippedTriangles.items, 0, clippedTriangles.size);
						} else {
							for (int v = 2, u = 0; v < verticesLength; v += 5, u += 2) {
								vertices[v] = packedColor;
								vertices[v + 1] = uvs[u];
								vertices[v + 2] = uvs[u + 1];
							}
							batch.draw(texture, vertices, 0, verticesLength, triangles, 0, triangles.length);
						}
					}
				}
			}
			clipper.clipEnd(slot);
		}
		clipper.clipEnd();
	}

	@Override public SkeletonClipping getSkeletonClipping() {
		return clipper;
	}
}
