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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

/**
 * Main backend for LDM. 
 * Does not provide realtime saving.
 * Saves only when save is called.
 */
public class LDMIOTrack implements ILDMIOHandler {
	String filename;
	
	//Denotes the highest supported .track version
	int protVersionNumber = 3;
	
	ArrayList<GpsPoint> gpspath;
	ArrayList<Marker> markers;
	long time;
	
	/**
	 * Reads track format.
	 */
	@SuppressWarnings("unchecked")
	private boolean readTrackFile(ObjectInputStream ois) throws IOException {
		protVersionNumber = ois.readInt();
		try {
			switch (protVersionNumber) {
			case 3:
				gpspath = (ArrayList<GpsPoint>) ois.readObject();
				markers = (ArrayList<Marker>) ois.readObject();
				break;
			default:
				System.err.println("Unknown file version number: "
						+ protVersionNumber
						+ " encountered while reading DataManager.");
				return false;
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private void readBinary(String pathToFile) throws IOException {
		File dbgTrackfile = new File(pathToFile);
		FileInputStream fis = new FileInputStream(dbgTrackfile);
		ObjectInputStream ois = new ObjectInputStream(fis);
		readTrackFile(ois);
		ois.close();
		fis.close();
	}
	
	public LDMIOTrack(String pathToFile) throws IOException {
		filename = pathToFile;
		File dbgTrackfile = new File(pathToFile);
		boolean isFileValid = dbgTrackfile.exists();
		
		if (isFileValid) {
			BufferedReader br = new BufferedReader(new FileReader(dbgTrackfile));
			br.mark(1);
			int first_byte = br.read();
			br.reset();
			if (first_byte < '0' || first_byte > '9') {
				readBinary(pathToFile);
			} else {
				gpspath = new ArrayList<GpsPoint>();
				markers = new ArrayList<Marker>();
				int version = Integer.valueOf(br.readLine());
				int gpspath_size = Integer.valueOf(br.readLine());
				for (int i = 0; i < gpspath_size; i++) {
					String line = br.readLine();
					String[] values = line.split(" ");
					gpspath.add(new GpsPoint(Double.valueOf(values[1]), Double.valueOf(values[0]), Long.valueOf(values[2])));
				}
				int markers_size = Integer.valueOf(br.readLine());
				for (int i = 0; i < markers_size; i++) {
					String line = br.readLine();
					String[] values = line.split(" ");
					Point2D p = new Point2D(Double.valueOf(values[0]), Double.valueOf(values[1]));
					GpsPoint g = new GpsPoint(Double.valueOf(values[3]), Double.valueOf(values[2]), Long.valueOf(values[4]));
					markers.add(new Marker(p, Long.valueOf(values[5]), g));
				}
				br.close();
			}
		}
		if (!isFileValid || gpspath == null || markers == null) {
			gpspath = new ArrayList<GpsPoint>();
			markers = new ArrayList<Marker>();
		}
	}
	
	@Override
	public ArrayList<Marker> getAllMarkers() {
		return markers;
	}

	@Override
	public Marker getMarker(Point2D imgpoint) {
		for (Marker m : markers) {
			if (m.imgpoint.equals(imgpoint))
				return m;
		}
		return null;
	}

	@Override
	public Marker getMarker(GpsPoint realpoint) {
		for (Marker m : markers) {
			if (m.realpoint.equals(realpoint))
				return m;
		}
		return null;
	}
	
	@Override
	public Marker getLastMarker() {
		if (markers.size() == 0)
			return null;
		else
			return markers.get(markers.size()-1);
	}

	@Override
	public boolean removeMarker(Point2D imgpoint) {
		Marker m = getMarker(imgpoint);
		if (m == null)
			return false;
		markers.remove(m);
		return true;
	}

	@Override
	public boolean removeMarker(GpsPoint realpoint) {
		Marker m = getMarker(realpoint);
		if (m == null)
			return false;
		markers.remove(m);
		return true;
	}
	
	@Override
	public ArrayList<GpsPoint> getAllGpsPoints() {
		return gpspath;
	}
	
	@Override
	public GpsPoint getLastGpsPoint() {
		if (gpspath.size() == 0)
			return null;
		else
			return gpspath.get(gpspath.size()-1);
	}

	@Override
	public boolean removeMarker(Marker m) {
		return markers.remove(m);
	}

	@Override
	public void removeAllMarkers() {
		markers = new ArrayList<Marker>();
	}

	@Override
	public boolean removeGpsPoint(GpsPoint p) {
		return gpspath.remove(p);
	}

	@Override
	public void removeAllGpsPoints() {
		gpspath = new ArrayList<GpsPoint>();
	}

	@Override
	public void addMarker(Marker m) {
		markers.add(m);
	}

	@Override
	public void addGpsPoint(GpsPoint p) {
		gpspath.add(p);
	}
	
	@Override
	public void setLastGpsPointTime(long unixTime) {
		time = unixTime;
	}

	@Override
	public long getLastGpsPointTime() {
		return this.time;
	}
	
	/**
	 * Prints two csv tables to ps.
	 * The first represents all the known GpsPoints
	 * while the second consists of all Markers.
	 * @param ps
	 */
	public void printValuesAsCSV(PrintStream ps) {
		ps.println("type,name,latitude,longitude");
		for (GpsPoint p : getAllGpsPoints())
			ps.println("P, " + p.time + ", " + p.latitude + ", " + p.longitude);
		ps.println("name,name2,latitude,longitude");
		for (Marker m : getAllMarkers())
			ps.println(m.imgpoint.x + ", " + m.imgpoint.y + ", "
					+ m.realpoint.latitude + ", " + m.realpoint.longitude);
	}
	
	/**
	 * Writes into track format.
	 */
	private void writeTrackFile(ObjectOutputStream oos) throws IOException {
		oos.writeInt(protVersionNumber);
		oos.writeObject(gpspath);
		oos.writeObject(markers);
		oos.writeLong(time);
	}
	
	@Override
	public void save() {
		File dbgTrackfile = new File(filename);
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(dbgTrackfile));
			bw.write("4\n");
			bw.write(gpspath.size() + "\n");
			for (GpsPoint p : gpspath) {
				bw.write(p.latitude + " " + p.longitude + " " + p.time + "\n");
			}
			bw.write(markers.size() + "\n");
			for (Marker m : markers) {
				bw.write(m.imgpoint.x + " " + m.imgpoint.y + " " +
				         m.realpoint.latitude + " " + m.realpoint.longitude + " " + m.realpoint.time + " " +
				         m.time + "\n");
			}
			bw.write(time + "\n");
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
