package org.imakhnyk.streetviewcars;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;

/**
 * My attempt to solve # Hash Code with google puzzle
 * 
 * Street View Routing Hash Code 2014, Final Round
 * 
 * For details see https://hashcode.withgoogle.com/past_editions.html
 *
 */
public class App {

	// variable to store all junctions (loaded from input file)
	public static ArrayList<Junction> junctions = new ArrayList<Junction>();

	// variable to store all streets (loaded from input file)
	public static ArrayList<Street> streets = new ArrayList<Street>();

	// generated mapping to know which junctions can be accessed from key indexed junction
	public static Map<Integer, ArrayList<Connection>> connections = new HashMap<Integer, ArrayList<Connection>>();

	// store best (the longest track) path for each car
	public static ArrayList<LinkedList<Connection>> carPathes = new ArrayList<LinkedList<Connection>>();

	// store total length of unique visited streets
	public static ArrayList<Integer> carPathesLength = new ArrayList<Integer>();

	// total junctions count (loaded from input file)
	public static int JUNCTIONS = 0;

	// total streets count (loaded from input file)
	public static int STREETS = 0;

	// total seconds count for each car (loaded from input file)
	public static int SECONDS = 0;

	// cars count (loaded from input file)
	public static int CARS = 0;

	// start point junction index (loaded from input file)
	public static int START = 0;

	public static int MAX_CONNECTION_COUNT = 20000;
	public static double NOT_VISITED_JUNCTION = 0;
	public static int MILISECONDS_FOR_EACH_CAR = 1000;

	/**
	 * Main method
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String inputFileName = "input.txt";
		loadFromFile(inputFileName);
		initializeData();

		// find best path for each car
		for (carIndex = 0; carIndex < CARS; carIndex++) {
			System.out.print("\nCar #" + carIndex);

			// initialize variables before run analyzer
			stop = false;
			maxLength = 0;
			path.clear();

			// add start junction
			path.add(new Connection(START, 0, 0, MAX_CONNECTION_COUNT - 1));
			// reset markers in visited array
			for (int i = 0; i < visited.length; i++) {
				visited[i] = NOT_VISITED_JUNCTION;
			}

			// mark visited streets (by previous cars)
			for (LinkedList<Connection> prevPath : carPathes) {
				if (prevPath != null) {
					for (Connection c : prevPath) {
						visited[c.streetIndex] += 1;
					}
				}
			}

			// set deadline for path searching
			deadline = System.currentTimeMillis() + MILISECONDS_FOR_EACH_CAR;

			// run the car from the start junction
			run(0, 0);
		}

		// Display Human readable results
		int totalLength = 0;
		for (int carIndex = 0; carIndex < CARS; carIndex++) {
			long time = 0;
			if (carPathes.get(carIndex) != null) {
				for (Connection c : carPathes.get(carIndex)) {
					time += c.time;
				}
			}
			int length = carPathesLength.get(carIndex);
			System.out.println("Car #" + carIndex + "\t Path: " + carPathes.get(carIndex).size() + "\t Time: " + time
					+ "\t Length: " + length);
			totalLength += length;
		}
		System.out.println("Total length: " + totalLength);

		outputResult();

		// Generate SVG file with map and car's tracks
		generateSVG("cars");
	}

	private static void outputResult() {
		// Submission file output
		System.out.println("*********************** Submission file content [BEGIN] ****************************");
		StringBuffer sb = new StringBuffer();
		sb.append(CARS).append("\n");// cars count
		for (int carIndex = 0; carIndex < CARS; carIndex++) {
			sb.append(carPathes.get(carIndex).size()).append("\n"); // junctions count
			for (Connection c : carPathes.get(carIndex)) {
				sb.append(c.to).append("\n");// junction
			}
		}
		System.out.println(sb.toString());
		System.out.println("***********************  Submission file content [END]  ****************************");
		//save to output file
		try (PrintWriter out = new PrintWriter("output.txt")) {
			out.println(sb.toString());
			out.flush();
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static LinkedList<Connection> path = new LinkedList<Connection>();
	public static double[] visited = new double[MAX_CONNECTION_COUNT];
	public static int maxLength = 0;
	public static int carIndex = 0;
	public static long deadline = 0;
	public static boolean stop = false;
	public static int counter = 0;

	public static void run(Integer timeSpent, Integer totalLength) {
		if (stop) {
			return;
		}
		if (timeSpent + getCurrent().time > SECONDS) {
			if (maxLength < totalLength) {
				maxLength = totalLength;
				System.out.print(" " + maxLength);
				LinkedList<Connection> pathToStore = new LinkedList<Connection>(path);
				pathToStore.removeLast();
				carPathes.set(carIndex, pathToStore);
				carPathesLength.set(carIndex, totalLength);
			}
			return;
		}
		counter++;
		counter %= 100;
		if ((counter == 0) && System.currentTimeMillis() > deadline) {
			stop = true;
			return;
		}
		int streetIndex = getCurrent().streetIndex;
		double wasVisited = visited[streetIndex];
		visited[getCurrent().streetIndex] += 1;
		visited[getCurrent().streetIndex] *= 2;
		int nextTimeSpent = timeSpent + getCurrent().time;
		int nextLength = (wasVisited > NOT_VISITED_JUNCTION) ? totalLength : totalLength + getCurrent().length;
		ArrayList<Connection> conns = new ArrayList<Connection>(connections.get(getCurrent().to));
		conns.sort(new Comparator<Connection>() {
			public int compare(Connection o1, Connection o2) {
				int result = Double.compare(visited[o1.streetIndex], visited[o2.streetIndex]);
				if (result == 0) {
					// compare two points by length and time
					double d1 = (double) o1.length / (double) o1.time;
					double d2 = (double) o2.length / (double) o2.time;
					result = Double.compare(d2, d1);
				}
				return result;
			}
		});
		for (Connection c : conns) {
			path.addLast(c);
			run(nextTimeSpent, nextLength);
			path.removeLast();
			if (stop) {
				return;
			}
		}
		visited[streetIndex] = wasVisited;
	}

	public static Connection getCurrent() {
		return path.getLast();
	}

	public static String pathToString() {
		StringBuffer sb = new StringBuffer();
		for (Connection c : path) {
			sb.append("" + c.to);
			sb.append(',');
		}
		return sb.toString();
	}

	private static void loadFromFile(String inputFileName) throws FileNotFoundException, IOException {
		BufferedReader br = new BufferedReader(new FileReader(inputFileName));

		// load general constants
		String[] params = br.readLine().split(" ");
		JUNCTIONS = Integer.parseInt(params[0]);
		STREETS = Integer.parseInt(params[1]);
		SECONDS = Integer.parseInt(params[2]);
		CARS = Integer.parseInt(params[3]);
		START = Integer.parseInt(params[4]);

		// load JUNCTIONS
		for (int i = 0; i < JUNCTIONS; i++) {
			params = br.readLine().split(" ");
			Junction j = new Junction();
			j.x = Double.parseDouble(params[0]);
			j.y = Double.parseDouble(params[1]);
			junctions.add(j);
		}

		// load STREETS
		for (int i = 0; i < STREETS; i++) {
			params = br.readLine().split(" ");
			Street s = new Street();
			s.start = Integer.parseInt(params[0]);
			s.finish = Integer.parseInt(params[1]);
			s.direction = Integer.parseInt(params[2]);
			s.time = Integer.parseInt(params[3]);
			s.length = Integer.parseInt(params[4]);
			streets.add(s);
			// create one direction connection between two junctions
			addConnection(s.start, s.finish, s, i);
			if (s.direction > 1) {// in case two directional street
				// create connection with opposite direction between two junctions
				addConnection(s.finish, s.start, s, i);
			}
		}
		br.close();
	}

	public static void initializeData() {
		// populate initial state for cars info
		for (int carIndex = 0; carIndex < CARS; carIndex++) {
			carPathes.add(new LinkedList<Connection>());
			carPathesLength.add(0);
		}

		// populate minimal distance to junctions
		junctions.get(START).minimalTime = 0;
		for (int t = 0; t < 1000; t++) {
			for (Entry<Integer, ArrayList<Connection>> e : connections.entrySet()) {
				Junction from = junctions.get(e.getKey().intValue());
				if (from.minimalTime < 1000000) {
					for (Connection c : e.getValue()) {
						Junction j = junctions.get(c.to);
						if (j.minimalTime > from.minimalTime + c.time) {
							j.minimalTime = from.minimalTime + c.time;
						}
					}
				}
			}
		}
	}

	public static void addConnection(Integer start, Integer finish, Street s, Integer streetIndex) {
		ArrayList<Connection> conns = connections.get(start);
		if (conns == null) {
			conns = new ArrayList<Connection>();
		}
		Connection connection = new Connection(finish, streetIndex, s.time, s.length);
		conns.add(connection);
		connections.put(start, conns);
	}

	// ***********************************
	// *************** SVG ***************
	// ***********************************
	public static String[] colors = { "deeppink", "indigo", "mediumblue", "lawngreen", "orange", "green", "brown",
			"coral", "olivedrab", "red" };

	public static void generateSVG(String filename) throws Exception {
		double minx = 100;
		double maxx = 0;
		double miny = 100;
		double maxy = 0;

		for (Junction j : junctions) {
			if (minx > j.x)
				minx = j.x;
			if (maxx < j.x)
				maxx = j.x;
			if (miny > j.y)
				miny = j.y;
			if (maxy < j.y)
				maxy = j.y;
		}
		DOMImplementation impl = SVGDOMImplementation.getDOMImplementation();
		String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
		SVGDocument doc = (SVGDocument) impl.createDocument(svgNS, "svg", null);

		Element canvas = doc.getDocumentElement();
		double scale = 10000;
		canvas.setAttributeNS(null, "width", "" + (maxy - miny) * scale);
		canvas.setAttributeNS(null, "height", "" + (maxx - minx) * scale);

		Element g = doc.createElementNS(svgNS, "g");
		g.setAttributeNS(null, "transform", "translate(1,1)");
		canvas.appendChild(g);
		for (int i = 0; i < STREETS; i++) {
			Street street = streets.get(i);
			Element line = doc.createElementNS(svgNS, "line");
			line.setAttributeNS(null, "x1", "" + (junctions.get(street.start).y - miny) * scale);
			line.setAttributeNS(null, "y1", "" + (maxx - junctions.get(street.start).x) * scale);
			line.setAttributeNS(null, "x2", "" + (junctions.get(street.finish).y - miny) * scale);
			line.setAttributeNS(null, "y2", "" + (maxx - junctions.get(street.finish).x) * scale);

			int car = isUnderCar(i);
			if (car < 0) {
				// line.setAttributeNS(null, "stroke", "black");
				int v = junctions.get(street.finish).minimalTime;
				int red = v * 255 / 1694;
				line.setAttributeNS(null, "stroke", "rgb(" + (255 - red) + ",200," + red + ")");
				line.setAttributeNS(null, "stroke-width", "0.3");
			} else {
				line.setAttributeNS(null, "stroke", colors[car]);
				line.setAttributeNS(null, "stroke-width", "1");
			}

			g.appendChild(line);
		}
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		FileOutputStream fos = new FileOutputStream(filename + ".svg");
		StreamResult result = new StreamResult(fos);

		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

		transformer.transform(source, result);
		fos.close();

		SVGGraphics2D svgGenerator = new SVGGraphics2D(doc);
		Writer out = new FileWriter("drawTest.svg");
		svgGenerator.stream(out, true);
		out.close();
	}

	public static int isUnderCar(Integer streetIndex) {
		int result = -1;
		for (int carIndex = 0; carIndex < CARS; carIndex++) {
			if (carPathes.get(carIndex) != null) {
				for (Connection c : carPathes.get(carIndex)) {
					if (c.streetIndex == streetIndex) {
						return carIndex;
					}
				}
			}
		}

		return result;
	}
}

class Street {
	public int start;
	public int finish;
	public int direction;
	public int time;
	public int length;
}

class Connection {
	public int to;
	public int streetIndex;
	public int time;
	public int length;

	public Connection(int to, int streetIndex, int time, int length) {
		super();
		this.to = to;
		this.streetIndex = streetIndex;
		this.time = time;
		this.length = length;
	}

	@Override
	public String toString() {
		return "Conn [to=" + to + ", time=" + time + ", length=" + length + "]";
	}
}

class Junction {
	public double x;
	public double y;
	public int minimalTime = 1000000;
}