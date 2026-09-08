package com.openspine.model;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator;

/** Alpha-mask boundaries use Java2D boolean geometry, with JTS simplification and constrained triangulation. */
final class ImageOutline {
	private static final int MAX_ALPHA_RUNS = 65536;
	private static final GeometryFactory GEOMETRY = new GeometryFactory();

	static float[] trace(int width, int height, int[] alpha, int threshold, float tolerance, int maxVertices) {
		Path2D.Double pixels = new Path2D.Double(Path2D.WIND_NON_ZERO);
		int runs = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width;) {
				if (alpha[y * width + x] < threshold) { x++; continue; }
				int left = x++;
				while (x < width && alpha[y * width + x] >= threshold) x++;
				if (++runs > MAX_ALPHA_RUNS)
					throw new IllegalArgumentException("图片轮廓过于复杂，请先减少噪点或缩小图片");
				pixels.moveTo(left, y);
				pixels.lineTo(x, y);
				pixels.lineTo(x, y + 1);
				pixels.lineTo(left, y + 1);
				pixels.closePath();
			}
		}
		Area area = new Area(pixels);
		if (area.isEmpty()) throw new IllegalArgumentException("该阈值下没有可见像素");
		if (!area.isSingular())
			throw new IllegalArgumentException("图片包含孔洞或多个分离区域；凹轮廓仅支持单一无孔区域");
		List<Coordinate> points = new ArrayList<>();
		double[] point = new double[6];
		for (PathIterator path = area.getPathIterator(null); !path.isDone(); path.next()) {
			switch (path.currentSegment(point)) {
			case PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO -> points.add(new Coordinate(point[0], point[1]));
			case PathIterator.SEG_CLOSE -> {
				if (!points.get(0).equals2D(points.get(points.size() - 1))) points.add(points.get(0).copy());
			}
			default -> throw new IllegalArgumentException("图片轮廓包含不支持的曲线");
			}
		}
		Polygon polygon = GEOMETRY.createPolygon(points.toArray(Coordinate[]::new));
		if (!polygon.isValid()) throw new IllegalArgumentException("图片轮廓自接触或相交，请修正接触点后重试");
		Polygon simplified = (Polygon)TopologyPreservingSimplifier.simplify(polygon, tolerance);
		Coordinate[] outline = simplified.getExteriorRing().getCoordinates();
		int count = outline.length - 1;
		if (!simplified.isValid() || simplified.isEmpty() || count < 3 || simplified.getArea() <= 0d)
			throw new IllegalArgumentException("简化后的轮廓无效，请降低简化容差");
		if (count > maxVertices)
			throw new IllegalArgumentException("轮廓需要 " + count + " 个顶点，超过上限 " + maxVertices
				+ "；请提高简化容差或顶点上限");
		float[] uvs = new float[count * 2];
		for (int i = 0; i < count; i++) {
			uvs[i * 2] = (float)(outline[i].x / width);
			uvs[i * 2 + 1] = (float)(outline[i].y / height);
		}
		return uvs;
	}

	static short[] triangulate(float[] uvs) {
		Coordinate[] points = new Coordinate[uvs.length / 2 + 1];
		Map<Coordinate, Integer> indices = new HashMap<>();
		for (int i = 0; i < points.length - 1; i++) {
			points[i] = new Coordinate(uvs[i * 2], uvs[i * 2 + 1]);
			indices.put(points[i], i);
		}
		points[points.length - 1] = points[0].copy();
		var triangles = new ConstrainedDelaunayTriangulator(GEOMETRY.createPolygon(points)).getTriangles();
		short[] result = new short[triangles.size() * 3];
		for (int i = 0; i < triangles.size(); i++) for (int corner = 0; corner < 3; corner++) {
			Integer index = indices.get(triangles.get(i).getCoordinate(corner));
			if (index == null) throw new IllegalArgumentException("轮廓三角化产生了未知顶点");
			result[i * 3 + corner] = (short)(int)index;
		}
		return result;
	}

	private ImageOutline() {}
}
