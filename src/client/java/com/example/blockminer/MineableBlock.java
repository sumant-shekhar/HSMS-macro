package com.example.blockminer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashSet;
import java.util.Set;

public enum MineableBlock {
	QUARTZ("nether_quartz_ore"),
	DIAMOND("diamond_block"),
	EMERALD("emerald_block"),
	REDSTONE("redstone_block"),
	LAPIS("lapis_block"),
	GOLD("gold_block"),
	IRON("iron_block"),
	COAL("coal_block"),
	SULPHUR("sponge"),
	HARDSTONE("stone"),
	TITANIUM("polished_diorite"),
	GRAY_MITHRIL("gray_wool", "cyan_terracotta"),
	GREEN_MITHRIL("prismarine", "dark_prismarine", "prismarine_bricks"),
	BLUE_MITHRIL("light_blue_wool"),
	OPAL("white_stained_glass", "white_stained_glass_pane"),
	JASPER("magenta_stained_glass", "magenta_stained_glass_pane"),
	TOPAZ("yellow_stained_glass", "yellow_stained_glass_pane"),
	AMBER("orange_stained_glass", "orange_stained_glass_pane"),
	SAPPHIRE("light_blue_stained_glass", "light_blue_stained_glass_pane"),
	JADE("lime_stained_glass", "lime_stained_glass_pane"),
	AMETHYST("purple_stained_glass", "purple_stained_glass_pane"),
	RUBY("red_stained_glass", "red_stained_glass_pane"),
	AQUAMARINE("blue_stained_glass", "blue_stained_glass_pane"),
	PERIDOT("green_stained_glass", "green_stained_glass_pane"),
	ONYX("black_stained_glass", "black_stained_glass_pane"),
	CITRINE("brown_stained_glass", "brown_stained_glass_pane"),
	GLACITE("ice"),
	UMBER("brown_terracotta", "terracotta"),
	TUNGSTEN("clay", "infested_stone");

	private final String[] blockPaths;

	MineableBlock(String... blockPaths) {
		// TODO(verify): SkyBlock server disguises and gemstone color mappings need in-game confirmation.
		this.blockPaths = blockPaths;
	}

	public Set<Block> blocks() {
		Set<Block> blocks = new LinkedHashSet<>();
		for (String path : blockPaths) {
			Identifier identifier = Identifier.fromNamespaceAndPath("minecraft", path);
			Block block = BuiltInRegistries.BLOCK.getValue(identifier);
			if (block == Blocks.AIR) {
				throw new IllegalStateException("Unregistered block for mineable type " + this + ": " + identifier);
			}
			blocks.add(block);
		}
		return Set.copyOf(blocks);
	}

	public static Set<Block> allBlocks() {
		Set<Block> blocks = new LinkedHashSet<>();
		for (MineableBlock type : values()) {
			blocks.addAll(type.blocks());
		}
		return Set.copyOf(blocks);
	}
}
