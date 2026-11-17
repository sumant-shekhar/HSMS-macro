package com.example.blockminer.handler;

import com.example.blockminer.util.Logger;
import com.example.blockminer.util.helper.route.Route;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.Expose;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class RouteHandler {
	private static final Path ROUTES_FILE = Path.of("config", "blockminer", "routes.json");
	private static final Gson GSON = new GsonBuilder()
			.excludeFieldsWithoutExposeAnnotation()
			.setPrettyPrinting()
			.create();
	private static final RouteHandler INSTANCE = new RouteHandler();

	@Expose
	private final Map<String, Route> routes = new HashMap<>();
	private Route selectedRoute;
	private boolean dirty;

	private RouteHandler() {
		routes.put("Default", new Route());
		selectedRoute = routes.get("Default");
	}

	public static RouteHandler getInstance() {
		return INSTANCE;
	}

	public synchronized Map<String, Route> getRoutes() {
		return Map.copyOf(routes);
	}

	public synchronized Route getSelectedRoute() {
		return selectedRoute;
	}

	public synchronized String getSelectedRouteName() {
		return routes.entrySet().stream()
				.filter(entry -> entry.getValue() == selectedRoute)
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse("Default");
	}

	public synchronized void selectRoute(String name) {
		Objects.requireNonNull(name, "name");
		if (name.isBlank()) {
			throw new IllegalArgumentException("Route name cannot be blank");
		}
		selectedRoute = routes.computeIfAbsent(name, ignored -> new Route());
		dirty = true;
	}

	public synchronized void createRoute(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Route name cannot be blank");
		}
		if (routes.putIfAbsent(name, new Route()) == null) {
			dirty = true;
		}
	}

	public synchronized void addToCurrentRoute(BlockPos block, WaypointType method) {
		if (selectedRoute == routes.get("Default")) {
			Logger.sendError("Cannot edit the Default route.");
			return;
		}
		RouteWaypoint waypoint = new RouteWaypoint(block, method);
		if (selectedRoute.indexOf(waypoint) == -1) {
			selectedRoute.insert(waypoint);
			dirty = true;
		}
	}

	public synchronized void removeFromCurrentRoute(int index) {
		selectedRoute.remove(index);
		dirty = true;
	}

	public synchronized void replaceInCurrentRoute(int index, RouteWaypoint waypoint) {
		selectedRoute.replace(index, waypoint);
		dirty = true;
	}

	public synchronized void deleteRoute(String name) {
		if ("Default".equals(name)) {
			throw new IllegalArgumentException("The Default route cannot be deleted");
		}
		Route removed = routes.remove(name);
		if (removed == selectedRoute) {
			selectedRoute = routes.get("Default");
		}
		if (removed != null) {
			dirty = true;
		}
	}

	public synchronized void markDirty() {
		dirty = true;
	}

	public synchronized boolean isDirty() {
		return dirty;
	}

	public synchronized void loadData() {
		if (!Files.exists(ROUTES_FILE)) {
			return;
		}
		try {
			JsonObject root = GSON.fromJson(Files.readString(ROUTES_FILE, StandardCharsets.UTF_8), JsonObject.class);
			if (root == null || !root.has("routes") || !root.get("routes").isJsonObject()) {
				throw new JsonParseException("Route file must contain a routes object");
			}
			Map<String, Route> loaded = GSON.fromJson(root.get("routes"),
					new com.google.common.reflect.TypeToken<Map<String, Route>>() { }.getType());
			if (loaded == null) {
				throw new JsonParseException("Routes must not be null");
			}
			routes.clear();
			routes.putAll(loaded);
			routes.putIfAbsent("Default", new Route());
			selectedRoute = routes.get("Default");
			dirty = false;
		} catch (IOException | RuntimeException exception) {
			Logger.sendError("Could not load routes from " + ROUTES_FILE + ": " + exception.getMessage());
		}
	}

	public synchronized void saveData() {
		if (!dirty) {
			return;
		}
		try {
			Files.createDirectories(ROUTES_FILE.getParent());
			JsonObject root = new JsonObject();
			root.add("routes", GSON.toJsonTree(routes));
			Path temporary = ROUTES_FILE.resolveSibling(ROUTES_FILE.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, ROUTES_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
						java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
				Files.move(temporary, ROUTES_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
		} catch (IOException exception) {
			Logger.sendError("Could not save routes to " + ROUTES_FILE + ": " + exception.getMessage());
		}
	}
}
