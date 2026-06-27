package com.drewburr.mcprom.core;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.drewburr.mcprom.core.dto.DimensionStats;
import com.drewburr.mcprom.core.dto.EntityTypeCount;
import com.drewburr.mcprom.core.dto.PlayerInfo;

import io.prometheus.client.Collector.MetricFamilySamples;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the platform-agnostic collector against a fake {@link
 * ServerStatsProvider}, with no Minecraft on the classpath.
 */
class MinecraftCollectorTest {

	/** A configurable fake provider. */
	private static final class FakeProvider implements ServerStatsProvider {
		List<PlayerInfo> players = List.of();
		List<DimensionStats> dimensions = List.of();
		boolean dimTickEvents = false;

		@Override public List<PlayerInfo> getOnlinePlayers() { return this.players; }
		@Override public List<DimensionStats> getDimensionStats(boolean withEntities) { return this.dimensions; }
		@Override public boolean supportsDimensionTickEvents() { return this.dimTickEvents; }
	}

	private static Map<String, MetricFamilySamples> byName(List<MetricFamilySamples> samples) {
		return samples.stream().collect(Collectors.toMap(s -> s.name, s -> s));
	}

	@Test
	void exportsPlayersChunksAndEntities() {
		FakeProvider provider = new FakeProvider();
		provider.players = List.of(new PlayerInfo("uuid-1", "alice"), new PlayerInfo("uuid-2", "bob"));
		provider.dimensions = List.of(
			new DimensionStats(DimensionIds.OVERWORLD, "overworld", 42,
				List.of(new EntityTypeCount("ZOMBIE", 3), new EntityTypeCount("Item", 7))),
			new DimensionStats(DimensionIds.THE_NETHER, "the_nether", 9, List.of())
		);

		ExporterConfig config = new ExporterConfig();
		MinecraftCollector collector = new MinecraftCollector(config, provider);
		collector.updateCache();

		Map<String, MetricFamilySamples> metrics = byName(collector.collect());

		MetricFamilySamples players = metrics.get("mc_player_list");
		assertNotNull(players);
		assertEquals(2, players.samples.size());

		MetricFamilySamples chunks = metrics.get("mc_dimension_chunks_loaded");
		assertNotNull(chunks);
		assertEquals(2, chunks.samples.size());
		assertTrue(chunks.samples.stream().anyMatch(s ->
			s.labelValues.equals(List.of("0", "overworld")) && s.value == 42.0));
		assertTrue(chunks.samples.stream().anyMatch(s ->
			s.labelValues.equals(List.of("-1", "the_nether")) && s.value == 9.0));

		MetricFamilySamples entities = metrics.get("mc_entities_total");
		assertNotNull(entities);
		assertEquals(2, entities.samples.size());
		assertTrue(entities.samples.stream().anyMatch(s ->
			s.labelValues.equals(List.of("overworld", "0", "Item")) && s.value == 7.0));
	}

	@Test
	void entitiesDisabledOmitsEntityMetric() {
		FakeProvider provider = new FakeProvider();
		provider.dimensions = List.of(new DimensionStats(0, "overworld", 1, List.of()));

		ExporterConfig config = new ExporterConfig();
		config.collector_mc_entities = false;
		MinecraftCollector collector = new MinecraftCollector(config, provider);
		collector.updateCache();

		assertNull(byName(collector.collect()).get("mc_entities_total"));
	}

	@Test
	void noDimensionTicksWhenEventsUnsupported() {
		// Paper-style provider: per-world tick timing can't be measured, so the
		// collector must not emit per-dimension tick metrics at all.
		FakeProvider provider = new FakeProvider();
		provider.dimTickEvents = false;
		provider.dimensions = List.of(new DimensionStats(0, "overworld", 1, List.of()));

		MinecraftCollector collector = new MinecraftCollector(new ExporterConfig(), provider);
		collector.updateCache();

		assertNull(byName(collector.collect()).get("mc_dimension_tick_seconds"));
	}

	@Test
	void recordsDimensionTickEvents() {
		FakeProvider provider = new FakeProvider();
		provider.dimTickEvents = true;

		MinecraftCollector collector = new MinecraftCollector(new ExporterConfig(), provider);
		collector.startDimensionTick(0, "overworld");
		collector.stopDimensionTick(0, "overworld");

		MetricFamilySamples dimTicks = byName(collector.collect()).get("mc_dimension_tick_seconds");
		assertNotNull(dimTicks);
		assertTrue(dimTicks.samples.stream().anyMatch(s ->
			s.name.equals("mc_dimension_tick_seconds_count")
				&& s.labelValues.equals(List.of("0", "overworld"))
				&& s.value == 1.0));
	}

	@Test
	void observeServerTickRecordsDuration() {
		MinecraftCollector collector = new MinecraftCollector(new ExporterConfig(), new FakeProvider());
		collector.observeServerTick(0.05);
		collector.observeServerTick(0.05);

		MetricFamilySamples ticks = byName(collector.collect()).get("mc_server_tick_seconds");
		assertNotNull(ticks);
		assertTrue(ticks.samples.stream().anyMatch(s ->
			s.name.equals("mc_server_tick_seconds_count") && s.value == 2.0));
		assertTrue(ticks.samples.stream().anyMatch(s ->
			s.name.equals("mc_server_tick_seconds_sum") && Math.abs(s.value - 0.10) < 1e-9));
	}

	@Test
	void observeServerTickAlsoRecordsRate() {
		MinecraftCollector collector = new MinecraftCollector(new ExporterConfig(), new FakeProvider());
		collector.observeServerTick(0.05);  // 50ms tick -> 20 TPS
		collector.observeServerTick(0.10);  // 100ms tick -> 10 TPS

		Map<String, MetricFamilySamples> metrics = byName(collector.collect());
		MetricFamilySamples rate = metrics.get("mc_server_tick_rate");
		assertNotNull(rate);
		assertTrue(rate.samples.stream().anyMatch(s ->
			s.name.equals("mc_server_tick_rate_count") && s.value == 2.0));
		// 20 + 10 = 30 TPS observed total.
		assertTrue(rate.samples.stream().anyMatch(s ->
			s.name.equals("mc_server_tick_rate_sum") && Math.abs(s.value - 30.0) < 1e-9));
	}

	@Test
	void strictPolicyThrowsOnUnbalancedTick() {
		ExporterConfig config = new ExporterConfig();
		config.collector_mc_dimension_tick_errors = TickErrorPolicy.STRICT;
		MinecraftCollector collector = new MinecraftCollector(config, new FakeProvider());

		try {
			collector.stopDimensionTick(0, "overworld");
			throw new AssertionError("expected IllegalStateException");
		} catch (IllegalStateException expected) {
			// Good.
		}
	}
}
