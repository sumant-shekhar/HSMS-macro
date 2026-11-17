package com.example.blockminer.util.helper.graph;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonDeserializer;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GraphSerializer implements JsonSerializer<Graph<RouteWaypoint>>, JsonDeserializer<Graph<RouteWaypoint>> {
	@Override
	public JsonElement serialize(Graph<RouteWaypoint> graph, Type type, JsonSerializationContext context) {
		JsonObject result = new JsonObject();
		JsonObject map = new JsonObject();
		for (Map.Entry<RouteWaypoint, List<RouteWaypoint>> entry : graph.map.entrySet()) {
			RouteWaypoint waypoint = entry.getKey();
			String key = waypoint.getX() + "," + waypoint.getY() + "," + waypoint.getZ()
					+ "," + waypoint.getTransportMethod().name();
			map.add(key, context.serialize(entry.getValue()));
		}
		result.add("map", map);
		return result;
	}

	@Override
	public Graph<RouteWaypoint> deserialize(JsonElement json, Type type, JsonDeserializationContext context)
			throws JsonParseException {
		if (!json.isJsonObject() || !json.getAsJsonObject().has("map")
				|| !json.getAsJsonObject().get("map").isJsonObject()) {
			throw new JsonParseException("Graph JSON must contain a map object");
		}
		Graph<RouteWaypoint> graph = new Graph<>();
		JsonObject map = json.getAsJsonObject().getAsJsonObject("map");
		Type waypointListType = new TypeToken<List<RouteWaypoint>>() { }.getType();
		for (Map.Entry<String, JsonElement> entry : map.entrySet()) {
			String[] parts = entry.getKey().split(",", -1);
			if (parts.length != 4) {
				throw new JsonParseException("Invalid graph waypoint key: " + entry.getKey());
			}
			try {
				RouteWaypoint key = new RouteWaypoint(
						Integer.parseInt(parts[0]),
						Integer.parseInt(parts[1]),
						Integer.parseInt(parts[2]),
						WaypointType.valueOf(parts[3])
				);
				List<RouteWaypoint> edges = context.deserialize(entry.getValue(), waypointListType);
				if (edges == null) {
					throw new JsonParseException("Graph edge list cannot be null for " + entry.getKey());
				}
				if (edges.stream().anyMatch(Objects::isNull)) {
					throw new JsonParseException("Graph edge list contains a null waypoint for " + entry.getKey());
				}
				graph.map.put(key, edges);
			} catch (IllegalArgumentException exception) {
				throw new JsonParseException("Invalid graph waypoint key: " + entry.getKey(), exception);
			}
		}
		return graph;
	}
}
