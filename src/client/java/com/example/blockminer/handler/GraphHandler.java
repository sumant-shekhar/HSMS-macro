package com.example.blockminer.handler;

import com.example.blockminer.util.Logger;
import com.example.blockminer.PlayerUtil;
import com.example.blockminer.util.helper.graph.Graph;
import com.example.blockminer.util.helper.graph.GraphSerializer;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GraphHandler {
	private static final String[] BUNDLED_GRAPHS = {"Commission Macro.json", "Glacial Macro.json"};
	private static final Path GRAPH_DIRECTORY = Path.of("config", "blockminer", "graphs");
	private static final java.lang.reflect.Type GRAPH_TYPE = new TypeToken<Graph<RouteWaypoint>>() { }.getType();
	private static final Gson GSON = new GsonBuilder()
			.registerTypeAdapter(GRAPH_TYPE, new GraphSerializer())
			.excludeFieldsWithoutExposeAnnotation()
			.setPrettyPrinting()
			.create();
	private static final GraphHandler INSTANCE = new GraphHandler();

	public final Map<String, Graph<RouteWaypoint>> graphs = new HashMap<>();
	private String activeGraphKey = "default";
	private boolean editing;
	private boolean dirty;
	private RouteWaypoint lastPosition;

	private GraphHandler() {
	}

	public static GraphHandler getInstance() {
		return INSTANCE;
	}

	public static Gson gson() {
		return GSON;
	}

	public static Graph<RouteWaypoint> parseGraph(Reader reader) {
		Graph<RouteWaypoint> graph = GSON.fromJson(reader, GRAPH_TYPE);
		if (graph == null) {
			throw new JsonParseException("Graph document is empty");
		}
		return graph;
	}

	public synchronized void loadBundledGraphs() {
		try {
			Files.createDirectories(GRAPH_DIRECTORY);
			for (String fileName : BUNDLED_GRAPHS) {
				String graphName = fileName.substring(0, fileName.length() - ".json".length());
				Path localFile = GRAPH_DIRECTORY.resolve(fileName);
				if (Files.exists(localFile)) {
					try (Reader reader = Files.newBufferedReader(localFile, StandardCharsets.UTF_8)) {
						graphs.put(graphName, parseGraph(reader));
					}
					continue;
				}
				try (InputStream stream = GraphHandler.class.getClassLoader()
						.getResourceAsStream("mightyminer/" + fileName)) {
					if (stream == null) {
						throw new IOException("Missing bundled graph resource " + fileName);
					}
					try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
						graphs.put(graphName, parseGraph(reader));
					}
				}
			}
		} catch (IOException | RuntimeException exception) {
			Logger.sendError("Could not load route graphs: " + exception.getMessage());
		}
	}

	public synchronized Graph<RouteWaypoint> getActiveGraph() {
		return graphs.computeIfAbsent(activeGraphKey, ignored -> new Graph<>());
	}

	public synchronized void setActiveGraph(String graphName) {
		if (graphName == null || graphName.isBlank()) {
			throw new IllegalArgumentException("Graph name cannot be blank");
		}
		activeGraphKey = graphName;
		getActiveGraph();
	}

	public synchronized String getActiveGraphKey() {
		return activeGraphKey;
	}

	public synchronized boolean isEditing() {
		return editing;
	}

	public synchronized void toggleEdit(String graphName) {
		if (!graphs.containsKey(graphName)) {
			throw new IllegalArgumentException("Unknown graph: " + graphName);
		}
		if (editing) {
			stopEditing();
		}
		setActiveGraph(graphName);
		editing = !editing;
	}

	public synchronized void toggleEdit() {
		editing = !editing;
		if (!editing) {
			save();
			lastPosition = null;
		}
	}

	public synchronized void startEditing() {
		editing = true;
	}

	public synchronized void stopEditing() {
		editing = false;
		if (dirty) {
			save();
		}
		lastPosition = null;
	}

	public synchronized void markDirty() {
		dirty = true;
	}

	public synchronized List<RouteWaypoint> findPath(BlockPos start, RouteWaypoint end) {
		Graph<RouteWaypoint> graph = getActiveGraph();
		return findPath(graph, start, end);
	}

	public synchronized List<RouteWaypoint> findPath(RouteWaypoint first, RouteWaypoint second) {
		return trimNearbyStart(getActiveGraph().findPath(first, second), currentPlayerBlock());
	}

	public synchronized List<RouteWaypoint> findPathFrom(String graphName, BlockPos start, RouteWaypoint end) {
		Graph<RouteWaypoint> graph = graphs.get(graphName);
		return graph == null ? List.of() : findPath(graph, start, end);
	}

	public synchronized List<RouteWaypoint> findPathFrom(
			String graphName,
			RouteWaypoint first,
			RouteWaypoint second
	) {
		Graph<RouteWaypoint> graph = graphs.get(graphName);
		return graph == null ? List.of() : trimNearbyStart(graph.findPath(first, second), currentPlayerBlock());
	}

	public synchronized double distance(String graphName, BlockPos start, RouteWaypoint end) {
		List<RouteWaypoint> path = findPathFrom(graphName, start, end);
		if (path.size() < 2) {
			return -1.0;
		}
		double distanceSquared = 0.0;
		for (int index = 1; index < path.size(); index++) {
			RouteWaypoint previous = path.get(index - 1);
			RouteWaypoint current = path.get(index);
			double dx = previous.getX() - current.getX();
			double dy = previous.getY() - current.getY();
			double dz = previous.getZ() - current.getZ();
			distanceSquared += dx * dx + dy * dy + dz * dz;
		}
		return distanceSquared;
	}

	public synchronized void addEdge(RouteWaypoint source, RouteWaypoint target, boolean bidirectional) {
		getActiveGraph().add(Objects.requireNonNull(source), Objects.requireNonNull(target), bidirectional);
		dirty = true;
	}

	public synchronized void updateNode(RouteWaypoint oldNode, RouteWaypoint newNode) {
		getActiveGraph().update(oldNode, newNode);
		dirty = true;
	}

	public synchronized void removeNode(RouteWaypoint node) {
		getActiveGraph().remove(node);
		dirty = true;
	}

	public synchronized void save() {
		if (!dirty) {
			return;
		}
		try {
			Files.createDirectories(GRAPH_DIRECTORY);
			Path output = GRAPH_DIRECTORY.resolve(activeGraphKey + ".json").normalize();
			if (!output.getParent().equals(GRAPH_DIRECTORY.normalize())) {
				throw new IllegalArgumentException("Invalid graph name: " + activeGraphKey);
			}
			Files.writeString(output, GSON.toJson(graphs.get(activeGraphKey), GRAPH_TYPE), StandardCharsets.UTF_8);
			dirty = false;
		} catch (IOException | RuntimeException exception) {
			Logger.sendError("Could not save graph " + activeGraphKey + ": " + exception.getMessage());
		}
	}

	private static List<RouteWaypoint> findPath(Graph<RouteWaypoint> graph, BlockPos start, RouteWaypoint end) {
		RouteWaypoint nearest = graph.map.keySet().stream()
				.min(Comparator.comparingLong(node -> squaredDistance(start, node)))
				.orElse(null);
		if (nearest == null || !graph.map.containsKey(end)) {
			return List.of();
		}
		return trimNearbyStart(graph.findPath(nearest, end), start);
	}

	private static List<RouteWaypoint> trimNearbyStart(List<RouteWaypoint> path, BlockPos start) {
		if (path.size() < 2) {
			return path;
		}
		RouteWaypoint first = path.getFirst();
		RouteWaypoint second = path.get(1);
		long fromFirst = start == null
				? 0L
				: squaredDistance(start, first);
		long firstToSecond = squaredDistance(first.toBlockPos(), second);
		if (start != null && fromFirst < firstToSecond) {
			return path.subList(1, path.size());
		}
		return path;
	}

	private static BlockPos currentPlayerBlock() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? null : PlayerUtil.getBlockStandingOn(client.player);
	}

	private static long squaredDistance(BlockPos start, RouteWaypoint node) {
		long dx = (long) start.getX() - node.getX();
		long dy = (long) start.getY() - node.getY();
		long dz = (long) start.getZ() - node.getZ();
		return dx * dx + dy * dy + dz * dz;
	}

	private static long squaredDistance(BlockPos start, BlockPos end) {
		long dx = (long) start.getX() - end.getX();
		long dy = (long) start.getY() - end.getY();
		long dz = (long) start.getZ() - end.getZ();
		return dx * dx + dy * dy + dz * dz;
	}
}
