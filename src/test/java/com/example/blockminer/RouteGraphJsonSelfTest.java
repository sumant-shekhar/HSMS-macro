package com.example.blockminer;

import com.example.blockminer.handler.GraphHandler;
import com.example.blockminer.util.helper.graph.Graph;
import com.example.blockminer.util.helper.route.RouteWaypoint;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class RouteGraphJsonSelfTest {
	private static final List<String> GRAPH_RESOURCES = List.of(
			"mightyminer/Commission Macro.json",
			"mightyminer/Glacial Macro.json"
	);

	private RouteGraphJsonSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		for (String resource : GRAPH_RESOURCES) {
			try (InputStream stream = RouteGraphJsonSelfTest.class.getClassLoader().getResourceAsStream(resource)) {
				if (stream == null) {
					throw new AssertionError("Missing route graph resource: " + resource);
				}
				Graph<RouteWaypoint> graph = GraphHandler.parseGraph(
						new InputStreamReader(stream, StandardCharsets.UTF_8));
				if (graph.map.isEmpty()) {
					throw new AssertionError("Route graph parsed with no nodes: " + resource);
				}
				for (Map.Entry<RouteWaypoint, List<RouteWaypoint>> entry : graph.map.entrySet()) {
					if (entry.getKey() == null || entry.getValue() == null) {
						throw new AssertionError("Route graph contains a null node or edge list: " + resource);
					}
					if (entry.getValue().stream().anyMatch(java.util.Objects::isNull)) {
						throw new AssertionError("Route graph contains a null edge: " + resource);
					}
				}
				String json = GraphHandler.gson().toJson(graph,
						new com.google.common.reflect.TypeToken<Graph<RouteWaypoint>>() { }.getType());
				Graph<RouteWaypoint> roundTrip = GraphHandler.parseGraph(new java.io.StringReader(json));
				if (roundTrip.map.isEmpty()) {
					throw new AssertionError("Route graph round-trip parsed with no nodes: " + resource);
				}
				System.out.println(resource + ": " + graph.map.size() + " nodes parsed");
			}
		}
	}
}
