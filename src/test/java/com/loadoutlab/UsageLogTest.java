package com.loadoutlab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UsageLogTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void appendsOneTimestampedLinePerPickAndCreatesTheDirectory()
	{
		Path file = tmp.getRoot().toPath().resolve("nested/dir/usage.tsv");
		UsageLog usage = new UsageLog(file);

		usage.record("Zulrah");
		usage.record("General Graardor (Level 624)");

		Assert.assertTrue(Files.exists(file));
		List<String> lines = readLines(file);
		Assert.assertEquals(2, lines.size());
		String[] first = lines.get(0).split("\t");
		Assert.assertEquals(2, first.length);
		// ISO-8601 instant, e.g. 2026-07-07T01:02:03.456Z
		Assert.assertTrue(first[0], first[0].matches("\\d{4}-\\d{2}-\\d{2}T.*Z"));
		Assert.assertEquals("Zulrah", first[1]);
		Assert.assertEquals("General Graardor (Level 624)", lines.get(1).split("\t")[1]);
	}

	@Test
	public void unwritableTargetDoesNotThrow()
	{
		// The parent "directory" is a file, so creation must fail - quietly.
		Path blocked;
		try
		{
			blocked = tmp.newFile("blocker").toPath().resolve("usage.tsv");
		}
		catch (Exception ex)
		{
			throw new AssertionError(ex);
		}
		new UsageLog(blocked).record("Zulrah");
	}

	private static List<String> readLines(Path file)
	{
		try
		{
			return Files.readAllLines(file);
		}
		catch (Exception ex)
		{
			throw new AssertionError(ex);
		}
	}
}
