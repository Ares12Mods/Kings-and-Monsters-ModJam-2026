package com.kingsandmonsters.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FortGridInvariantTest {
    private static final int SPACING = 36;
    private static final int SEPARATION = 12;
    private static final int SALT = 1829437114;
    private static final int SPAWN_EXCLUSION = 128;
    private static final int MINIMUM_SPACING = 80;
    @Test
    void quarterMillionPlacementDecisionsAreSwampSpawnSafeAndDefinitionIdentical() {
        Random corpus = new Random(0x4B414D5F5357414DL);
        int accepted = 0;
        for (int sample = 0; sample < 250_000; sample++) {
            long seed = corpus.nextLong();
            int regionX = corpus.nextInt(-400, 401);
            int regionZ = corpus.nextInt(-400, 401);
            FortGridPlacement.Candidate probe = FortGridPlacement.candidateCoordinatesAt(seed, regionX, regionZ, SPACING, SEPARATION, SALT);
            FortGridPlacement.SwampOracle swamp = syntheticSwamp(seed);

            boolean mapDefinition = FortGridPlacement.isAuthoritativeCandidate(seed, probe.x(), probe.z(),
                    SPACING, SEPARATION, SALT, SPAWN_EXCLUSION, MINIMUM_SPACING, swamp);
            boolean worldgenDefinition = FortGridPlacement.isAuthoritativeCandidate(seed, probe.x(), probe.z(),
                    SPACING, SEPARATION, SALT, SPAWN_EXCLUSION, MINIMUM_SPACING, swamp);
            assertEquals(worldgenDefinition, mapDefinition);
            if (mapDefinition) {
                accepted++;
                assertTrue(swamp.isSwamp(probe.x(), probe.z()), "non-swamp target");
                assertTrue((long) probe.x() * probe.x() + (long) probe.z() * probe.z()
                        >= (long) SPAWN_EXCLUSION * SPAWN_EXCLUSION, "inside spawn exclusion");
            }
        }
        assertTrue(accepted > 1_000, "corpus did not exercise enough accepted candidates");
    }

    @Test
    void acceptedCandidatesCannotClusterInsideMinimumSpacing() {
        long seed = 0x4F574E4552534849L;
        FortGridPlacement.SwampOracle swamp = syntheticSwamp(seed);
        List<FortGridPlacement.Candidate> accepted = new ArrayList<>();
        for (int regionX = -80; regionX <= 80; regionX++) {
            for (int regionZ = -80; regionZ <= 80; regionZ++) {
                FortGridPlacement.Candidate probe = FortGridPlacement.candidateCoordinatesAt(seed, regionX, regionZ, SPACING, SEPARATION, SALT);
                if (FortGridPlacement.isAuthoritativeCandidate(seed, probe.x(), probe.z(), SPACING,
                        SEPARATION, SALT, SPAWN_EXCLUSION, MINIMUM_SPACING, swamp)) accepted.add(probe);
            }
        }
        long minimumSq = (long) MINIMUM_SPACING * MINIMUM_SPACING;
        for (int i = 0; i < accepted.size(); i++) {
            for (int j = i + 1; j < accepted.size(); j++) {
                long dx = (long) accepted.get(i).x() - accepted.get(j).x();
                long dz = (long) accepted.get(i).z() - accepted.get(j).z();
                assertTrue(dx * dx + dz * dz >= minimumSq,
                        "clustered candidates " + accepted.get(i) + " and " + accepted.get(j));
            }
        }
    }

    @Test
    void nearestSearchReportsDeterministicSpawnDistribution() {
        List<Double> distances = new ArrayList<>();
        for (long seed = 0; seed < 10_000; seed++) {
            FortGridPlacement.Candidate candidate = FortGridPlacement.nearestAuthoritativeCoordinates(seed, 0, 0,
                    SPACING, SEPARATION, SALT, SPAWN_EXCLUSION, MINIMUM_SPACING, syntheticSwamp(seed));
            assertTrue(FortGridPlacement.isAuthoritativeCandidate(seed, candidate.x(), candidate.z(), SPACING,
                    SEPARATION, SALT, SPAWN_EXCLUSION, MINIMUM_SPACING, syntheticSwamp(seed)));
            distances.add(Math.hypot(candidate.x() * 16.0, candidate.z() * 16.0));
        }
        distances.sort(Comparator.naturalOrder());
        System.out.printf("Fort spawn distance blocks: min=%.0f p10=%.0f p25=%.0f p50=%.0f p75=%.0f p90=%.0f p95=%.0f p99=%.0f max=%.0f%n",
                distances.getFirst(), percentile(distances, .10), percentile(distances, .25),
                percentile(distances, .50), percentile(distances, .75), percentile(distances, .90),
                percentile(distances, .95), percentile(distances, .99), distances.getLast());
    }

    private static double percentile(List<Double> sorted, double percentile) {
        return sorted.get((int) Math.round((sorted.size() - 1) * percentile));
    }

    /** Correlated deterministic patches stand in for a noise-biome source in the pure unit corpus. */
    private static FortGridPlacement.SwampOracle syntheticSwamp(long seed) {
        return (chunkX, chunkZ) -> {
            long cellX = Math.floorDiv(chunkX, 32);
            long cellZ = Math.floorDiv(chunkZ, 32);
            long value = seed ^ cellX * 0x9E3779B97F4A7C15L ^ cellZ * 0xC2B2AE3D27D4EB4FL;
            value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
            value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
            return Long.remainderUnsigned(value ^ (value >>> 31), 10) == 0;
        };
    }
}
