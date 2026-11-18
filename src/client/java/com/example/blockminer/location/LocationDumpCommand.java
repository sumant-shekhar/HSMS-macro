package com.example.blockminer.location;

import com.example.blockminer.commission.CommissionTabListParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

public final class LocationDumpCommand {
	private static final Logger LOGGER = LoggerFactory.getLogger("blockminer");
	private static final Pattern VALID_LABEL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,47}");
	private static final DateTimeFormatter FILE_TIMESTAMP =
			DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);

	private LocationDumpCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmdump")
						.executes(context -> {
							context.getSource().sendFeedback(Component.literal("Usage: /mmdump <label>."));
							return 0;
						})
						.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
								.<FabricClientCommandSource, String>argument("label", StringArgumentType.word())
								.executes(context -> capture(
										context.getSource(),
										StringArgumentType.getString(context, "label")
								)))
		));
	}

	private static int capture(FabricClientCommandSource source, String label) {
		if (!VALID_LABEL.matcher(label).matches()) {
			LOGGER.warn("[blockminer] /mmdump rejected an invalid label");
			source.sendFeedback(Component.literal(
					"Invalid /mmdump label. Use 1-48 letters, digits, dots, underscores or hyphens; start with a letter or digit."
			));
			return 0;
		}

		Minecraft client = Minecraft.getInstance();
		try {
			Path directory = client.gameDirectory.toPath().resolve("mmcaptures");
			Files.createDirectories(directory);
			Path capture = directory.resolve(label + "-" + FILE_TIMESTAMP.format(Instant.now()) + ".txt");
			Files.writeString(
					capture,
					buildCapture(client),
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE_NEW,
					StandardOpenOption.WRITE
			);
			source.sendFeedback(Component.literal("Wrote /mmdump capture: " + capture.getFileName()));
			return 1;
		} catch (IOException | IllegalStateException | SecurityException exception) {
			LOGGER.error("[blockminer] failed to write /mmdump capture for label {}", label, exception);
			source.sendFeedback(Component.literal("Failed to write /mmdump capture. See the client log for details."));
			return 0;
		}
	}

	private static String buildCapture(Minecraft client) {
		List<LocationInputAdapter.TabListRow> tabRows = LocationInputAdapter.readPlayerListRows(client);
		List<String> footerLines = LocationInputAdapter.readTabFooter(client);
		List<String> sidebarRows = LocationInputAdapter.readSidebarLines(client);
		String sidebarTitle = LocationInputAdapter.readSidebarTitle(client);
		String footerRaw = LocationInputAdapter.readTabFooterRaw(client);
		GameStateHandler state = LocationTracker.getGameState();
		CommissionTabListParser.ParseResult commissions = CommissionTabListParser.parse(
				tabRows.stream().map(LocationInputAdapter.TabListRow::plainText).toList()
		);

		StringBuilder output = new StringBuilder();
		output.append("# BlockMiner local TAB LIST and scoreboard capture\n");
		output.append("# WARNING: TAB LIST rows contain other players' usernames. Review and redact before sharing.\n");
		output.append("captured_at_utc=").append(Instant.now()).append('\n');
		output.append("tab_row_raw_format=serialized Minecraft Component JSON preserving style, or plain text if encoding fails\n");
		output.append("tab_row_plain_format=Component.getString() text with styling removed\n");
		output.append("parsed_location=").append(state.getCurrentLocation()).append('\n');
		output.append("parsed_sub_location=").append(state.getCurrentSubLocation()).append('\n');
		output.append("parsed_area=").append(escape(state.getAreaName())).append('\n');
		output.append("tab_rows.count=").append(tabRows.size()).append('\n');
		for (int index = 0; index < tabRows.size(); index++) {
			LocationInputAdapter.TabListRow row = tabRows.get(index);
			output.append("tab_rows[").append(index).append("].raw_text=")
					.append(escape(row.rawText())).append('\n');
			output.append("tab_rows[").append(index).append("].plain_text=")
					.append(escape(row.plainText())).append('\n');
		}
		output.append("tab_footer.raw_component_json_or_plain_text=").append(escape(footerRaw)).append('\n');
		output.append("tab_footer.lines.count=").append(footerLines.size()).append('\n');
		for (int index = 0; index < footerLines.size(); index++) {
			output.append("tab_footer.lines[").append(index).append("]=")
					.append(escape(footerLines.get(index))).append('\n');
		}
		output.append("sidebar.title=").append(escape(sidebarTitle)).append('\n');
		output.append("sidebar.rows.count=").append(sidebarRows.size()).append('\n');
		for (int index = 0; index < sidebarRows.size(); index++) {
			output.append("sidebar.rows[").append(index).append("]=")
					.append(escape(sidebarRows.get(index))).append('\n');
		}
		output.append("commissions.section_found=").append(commissions.sectionFound()).append('\n');
		output.append("commissions.raw_section_lines.count=").append(commissions.rawSectionLines().size()).append('\n');
		for (int index = 0; index < commissions.rawSectionLines().size(); index++) {
			output.append("commissions.raw_section_lines[").append(index).append("]=")
					.append(escape(commissions.rawSectionLines().get(index))).append('\n');
		}
		output.append("commissions.entries.count=").append(commissions.entries().size()).append('\n');
		for (int index = 0; index < commissions.entries().size(); index++) {
			CommissionTabListParser.Entry entry = commissions.entries().get(index);
			output.append("commissions.entries[").append(index).append("].name=")
					.append(escape(entry.name())).append('\n');
			output.append("commissions.entries[").append(index).append("].progress_type=")
					.append(entry.progress().type()).append('\n');
			if (entry.progress().type() == CommissionTabListParser.ProgressType.PERCENT) {
				output.append("commissions.entries[").append(index).append("].progress_percent=")
						.append(entry.progress().percent()).append('\n');
			}
			output.append("commissions.entries[").append(index).append("].raw_line=")
					.append(escape(entry.rawLine())).append('\n');
		}
		return output.toString();
	}

	private static String escape(String text) {
		if (text == null) {
			return "<null>";
		}
		return text.replace("\\", "\\\\")
				.replace("\t", "\\t")
				.replace("\r", "\\r")
				.replace("\n", "\\n");
	}
}
