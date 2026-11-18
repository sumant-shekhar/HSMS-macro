package com.example.blockminer.util;

import com.example.blockminer.commission.CommissionTabListParser;
import com.example.blockminer.AngleUtil;
import com.example.blockminer.location.LocationTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CommissionUtil {
	private static final int PLAYER_SLOTS_IN_MENU = 36;
	private static final double DEFAULT_MOB_DISTANCE_WEIGHT = 0.6D;
	private static final double DEFAULT_MOB_ROTATION_WEIGHT = 0.1D;
	private static final Map<String, Vec3> EMISSARY_POSITIONS = Map.of(
			Strings.EMISSARY_CEANNA_NAME, new Vec3(42.50, 134.50, 22.50),
			Strings.EMISSARY_CARLTON_NAME, new Vec3(-72.50, 153.00, -10.50),
			Strings.EMISSARY_WILSON_NAME, new Vec3(171.50, 150.00, 31.50),
			Strings.EMISSARY_LILITH_NAME, new Vec3(58.50, 198.00, -8.50),
			Strings.EMISSARY_FRAISER_NAME, new Vec3(-132.50, 174.00, -50.50)
	);
	private static final Map<String, Set<String>> SLAYER_MOBS = Map.of(
			Strings.GOBLIN_SLAYER_COMMISSION,
			Set.of(Strings.GOBLIN_MOB, Strings.KNIFETHROWER_MOB, Strings.FIRESLINGER_MOB),
			Strings.MINES_SLAYER_COMMISSION,
			Set.of(Strings.GOBLIN_MOB, Strings.KNIFETHROWER_MOB, Strings.FIRESLINGER_MOB, Strings.GLACITE_WALKER_MOB),
			Strings.GLACITE_WALKER_SLAYER_COMMISSION,
			Set.of(Strings.GLACITE_WALKER_MOB)
	);

	private CommissionUtil() {
	}

	public static CommissionTabListParser.ParseResult getCurrentCommissionsFromTablist() {
		return LocationTracker.getGameState().getCommissionParse();
	}

	public static Set<String> getMobForCommission(String commissionName) {
		return SLAYER_MOBS.getOrDefault(commissionName, Set.of());
	}

	public static Optional<AbstractClientPlayer> getEmissary(Vec3 position) {
		return currentPlayers().stream()
				.filter(EntityUtil::isNpc)
				.filter(player -> !player.getName().getString().contains(Strings.NPC_SENTRY_NAME))
				.filter(player -> player.position().distanceToSqr(position) < 0.01D)
				.findFirst();
	}

	public static Optional<AbstractClientPlayer> getClosestEmissary() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return Optional.empty();
		}
		Vec3 position = getClosestEmissaryPosition(client.player.position());
		return getEmissary(position);
	}

	public static Vec3 getClosestEmissaryPosition(Vec3 from) {
		return EMISSARY_POSITIONS.values().stream()
				.min(Comparator.comparingDouble(from::distanceToSqr))
				.orElseThrow(() -> new IllegalStateException("No emissary positions are configured"));
	}

	public static Vec3 getClosestEmissaryPosition() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			throw new IllegalStateException("Finding the nearest emissary requires a local player");
		}
		return getClosestEmissaryPosition(client.player.position());
	}

	public static List<String> getEmissaryNames() {
		return List.copyOf(EMISSARY_POSITIONS.keySet());
	}

	public static int getClaimableCommissionSlot() {
		AbstractContainerMenu menu = getOpenMenu();
		int containerSlots = getContainerSlotCount(menu);
		for (int index = 0; index < containerSlots; index++) {
			for (String lore : getLore(menu.slots.get(index).getItem())) {
				if (lore.equalsIgnoreCase(Strings.COMMISSION_COMPLETE_LORE)) {
					return index;
				}
			}
		}
		return -1;
	}

	public static List<String> getCommissionNamesFromContainer() {
		AbstractContainerMenu menu = getOpenMenu();
		int containerSlots = getContainerSlotCount(menu);
		List<String> commissions = new ArrayList<>();
		for (int index = 0; index < containerSlots; index++) {
			ItemStack item = menu.slots.get(index).getItem();
			if (item.isEmpty() || !item.getHoverName().getString().startsWith(Strings.COMMISSION_ITEM_PREFIX)) {
				continue;
			}
			List<String> lore = getLore(item);
			for (int line = 0; line + 1 < lore.size(); line++) {
				if (lore.get(line).isBlank() && !lore.get(line + 1).isBlank()) {
					commissions.add(lore.get(line + 1));
					break;
				}
			}
		}
		return List.copyOf(commissions);
	}

	public static List<LivingEntity> getMobList(String mobName, Set<LivingEntity> mobsToIgnore) {
		return getMobList(mobName, mobsToIgnore, DEFAULT_MOB_DISTANCE_WEIGHT, DEFAULT_MOB_ROTATION_WEIGHT);
	}

	public static List<LivingEntity> getMobList(
			String mobName,
			Set<LivingEntity> mobsToIgnore,
			double distanceWeight,
			double rotationWeight
	) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return List.of();
		}
		AABB searchArea = player.getBoundingBox().inflate(128.0D);
		List<LivingEntity> mobs = new ArrayList<>();
		for (Entity entity : client.level.getEntities(player, searchArea,
				candidate -> candidate instanceof LivingEntity)) {
			if (!(entity instanceof LivingEntity living)
					|| !living.isAlive()
					|| !living.getName().getString().trim().equals(mobName)
					|| mobsToIgnore.contains(living)) {
				continue;
			}
			mobs.add(living);
		}
		Vec3 playerPosition = player.position();
		float playerYaw = AngleUtil.normalizeAngle(player.getYRot());
		mobs.sort(Comparator.comparingDouble(mob -> {
			Vec3 mobPosition = mob.position();
			double distanceCost = Math.hypot(
					playerPosition.x - mobPosition.x,
					playerPosition.z - mobPosition.z
			) + Math.abs(mobPosition.y - playerPosition.y) * 2.0D;
			float mobYaw = AngleUtil.getRotation(mob.position()).getYaw();
			double angleCost = Math.abs(AngleUtil.normalizeAngle(playerYaw - mobYaw));
			return distanceCost * distanceWeight + angleCost * rotationWeight;
		}));
		return List.copyOf(mobs);
	}

	public static List<MobListDebugEntry> getMobListDebug(String mobName, Set<Player> mobsToIgnore) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return List.of();
		}
		AABB searchArea = player.getBoundingBox().inflate(128.0D);
		List<MobListDebugEntry> mobs = new ArrayList<>();
		for (Entity entity : client.level.getEntities(player, searchArea,
				candidate -> candidate instanceof Player)) {
			if (!(entity instanceof Player mob)
					|| !mob.isAlive()
					|| !mob.getName().getString().trim().equals(mobName)
					|| mobsToIgnore.contains(mob)) {
				continue;
			}
			double distanceCost = player.position().distanceTo(mob.position()) * DEFAULT_MOB_DISTANCE_WEIGHT;
			double angleCost = Math.abs(AngleUtil.getNeededYawChange(
					player.getYRot(),
					AngleUtil.getRotation(mob.position()).getYaw()
			)) * DEFAULT_MOB_ROTATION_WEIGHT;
			mobs.add(new MobListDebugEntry(mob, distanceCost, angleCost));
		}
		mobs.sort(Comparator.comparingDouble(entry -> entry.distanceCost() + entry.angleCost()));
		return List.copyOf(mobs);
	}

	private static List<AbstractClientPlayer> currentPlayers() {
		Minecraft client = Minecraft.getInstance();
		return client.level == null ? List.of() : client.level.players();
	}

	private static AbstractContainerMenu getOpenMenu() {
		Screen screen = Minecraft.getInstance().gui.screen();
		return screen instanceof AbstractContainerScreen<?> containerScreen
				? containerScreen.getMenu()
				: null;
	}

	private static int getContainerSlotCount(AbstractContainerMenu menu) {
		return menu == null ? 0 : Math.max(0, menu.slots.size() - PLAYER_SLOTS_IN_MENU);
	}

	private static List<String> getLore(ItemStack item) {
		ItemLore lore = item.get(DataComponents.LORE);
		if (lore == null) {
			return List.of();
		}
		return lore.lines().stream().map(Component::getString).toList();
	}

	public record MobListDebugEntry(Player mob, double distanceCost, double angleCost) {
	}
}
