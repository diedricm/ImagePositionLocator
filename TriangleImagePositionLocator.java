/* Copyright (C) 2014,2015  Maximilian Diedrich
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */


package de.hu_berlin.informatik.spws2014.ImagePositionLocator;

import org.opencv.core.MatOfFloat6;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Subdiv2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements an algorithm for matching GpsPoints to
 * Point2D's based on Markers
 */
public class TriangleImagePositionLocator implements ImagePositionLocator {
	IPLSettingsContainer settings;
	
	private ArrayList<ProjectionTriangle> projs;
	private Point2D imageSize;
	
	public TriangleImagePositionLocator(Point2D imageSize, IPLSettingsContainer settings) {
		this.imageSize = imageSize;
		this.settings = settings;
	}
	
	public ArrayList<ProjectionTriangle> getProjectionTriangles() {
		return projs;
	}
	
	public Point2D getPointPosition(GpsPoint currentPosition) {
		if (projs == null || currentPosition == null)
			return null;
		
		System.out.println("\n\nNew Point : " + currentPosition.toString());
		
		FPoint2D result = new FPoint2D();
		double sum = 0;
		double distances[] = new double[projs.size()];
		double distToClosestPivot = Double.MAX_VALUE;
		
		//Get closest pivot
		for (int i = 0; i < projs.size(); i++) {
			distances[i] = projs.get(i).getPivot().getSphericalDistance(currentPosition);
			if (distToClosestPivot > distances[i])
				distToClosestPivot = distances[i];
		}
		
		for (int i = 0; i < projs.size(); i++) {
			double unnormdist = distToClosestPivot / distances[i];
			
			double tmp = distanceFallofFunction(unnormdist) * projs.get(i).getWeigth();
			
			int weight = (distances[i] < 0.01) ?
					(int) (0.01 - distances[i]) * 1000 : 1;
			
			FPoint2D dbgtmp = projs.get(i).project(currentPosition, weight);
			
			result.fma(dbgtmp, tmp);
			sum += tmp;
			
			System.out.println("Proj of " + projs.get(i).toString() + " x:" + dbgtmp.x + " y:" + dbgtmp.y + " actweight: " + tmp);
		}
		result.div(sum);
		
		System.out.println("Result: " + result.toString());
		
		return new Point2D(result);
	}
	
	/**
	 * Builds ProjectionTriangles from triangulated markers.
	 * Requires OpenCV!
	 */
	public void newMarkerAdded(List<Marker> markers) {		
		if (markers.size() < 2) return;
		
		if (markers.size() == 2) {
			//Guess third marker
			projs = new ArrayList<ProjectionTriangle>();
			projs.add(new ProjectionTriangle(markers.get(0), markers.get(1)));
		} else {
			Subdiv2D subdiv = new Subdiv2D();
			subdiv.initDelaunay(new Rect(0,0,imageSize.x,imageSize.y));
			
			for (Marker m : markers)
				System.out.println("-> " + m.realpoint.longitude + " / " + m.realpoint.latitude);
			for (Marker m : markers)
				subdiv.insert(new Point(m.imgpoint.x, m.imgpoint.y));
			
			MatOfFloat6 mafloat = new MatOfFloat6();
			subdiv.getTriangleList(mafloat);
			float[] tmparray = mafloat.toArray();
			
			ArrayList<ProjectionTriangle> tmplist = new ArrayList<ProjectionTriangle>();
			for (int i = 0; i < tmparray.length; i += 6) {
				Marker m1 = findMarkerByPoint(markers, tmparray[i], tmparray[i + 1]);
				Marker m2 = findMarkerByPoint(markers, tmparray[i + 2], tmparray[i + 3]);
				Marker m3 = findMarkerByPoint(markers, tmparray[i + 4], tmparray[i + 5]);
				
				if (m1 != null && m2 != null && m3 != null)
					tmplist.add(new ProjectionTriangle(m1, m2, m3,
							settings.getMaxDissimilarityPercent(),
							settings.getBadTriWeightPenalty(),
							settings.getMinTriAngleSize()));
			}
			
			for (ProjectionTriangle mainPt : tmplist) {
				for (ProjectionTriangle subPt : tmplist) {
					if (mainPt != subPt)
						mainPt.tryAddToProjGroup(subPt);
				}
			}
			
			projs = tmplist;
		}
	}
	
	/**
	 * Decides if the lng and lat values represent a GpsPoint of a Marker.
	 * @return The found Marker.
	 */
	private Marker findMarkerByPoint(List<Marker> markers, float x, float y) {
		for (Marker m : markers) {
			if (m.imgpoint.x == (int) x
			&& (m.imgpoint.y == (int) y)) {
				return m;
			}
		}
		return null;
	}
	
	private double distanceFallofFunction(double d) {
		if (d > 1) throw new IllegalArgumentException("The distance fallof is only defined from 0..1!");
		
		return Math.pow(d, settings.getFallofExponent());
	}
}
