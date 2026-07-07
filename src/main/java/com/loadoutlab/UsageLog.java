package com.loadoutlab;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * A local, append-only tally of monster searches: one tab-separated line
 * (ISO timestamp, monster label) per pick, written to
 * .runelite/loadout-lab/usage.tsv. Purely local - nothing leaves the
 * machine; count with e.g. `cut -f2 usage.tsv | sort | uniq -c`.
 */
@Slf4j
public class UsageLog
{
	private final Path file;

	public UsageLog(Path file)
	{
		this.file = file;
	}

	public static UsageLog defaultLog()
	{
		return new UsageLog(new File(RuneLite.RUNELITE_DIR, "loadout-lab/usage.tsv").toPath());
	}

	/** Append one pick; never throws - a logging failure must not break the UI. */
	public void record(String monsterLabel)
	{
		try
		{
			Files.createDirectories(file.getParent());
			Files.write(file,
				(Instant.now() + "\t" + monsterLabel + System.lineSeparator())
					.getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException ex)
		{
			log.warn("could not append to usage log {}", file, ex);
		}
	}
}
