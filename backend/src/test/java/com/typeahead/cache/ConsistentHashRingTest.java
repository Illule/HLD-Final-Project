package com.typeahead.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConsistentHashRingTest — Unit tests for the consistent hashing implementation.
 *
 * Validates:
 * 1. Key distribution across nodes (uniformity)
 * 2. Deterministic routing (same key → same node)
 * 3. Minimum key movement when nodes are added/removed
 * 4. Edge cases (empty ring, single node)
 */
class ConsistentHashRingTest {

    private ConsistentHashRing ring;

    @BeforeEach
    void setUp() {
        ring = new ConsistentHashRing(150); // 150 virtual nodes per physical node
    }

    @Test
    @DisplayName("Should distribute keys roughly evenly across 4 nodes")
    void testDistribution() {
        // Add 4 nodes
        ring.addNode("Node-1");
        ring.addNode("Node-2");
        ring.addNode("Node-3");
        ring.addNode("Node-4");

        // Map 10000 keys to nodes
        Map<String, Integer> distribution = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            String node = ring.getNode("prefix-" + i);
            distribution.merge(node, 1, Integer::sum);
        }

        // Each node should get roughly 25% (±10%) of keys
        // With 150 virtual nodes, distribution is typically within ±5%
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            double percentage = entry.getValue() / 100.0; // out of 10000 → percentage
            assertTrue(percentage > 15 && percentage < 35,
                    entry.getKey() + " got " + percentage + "% — expected 15-35%");
        }

        assertEquals(4, distribution.size(), "All 4 nodes should have keys");
    }

    @Test
    @DisplayName("Same key should always route to the same node (deterministic)")
    void testDeterministicRouting() {
        ring.addNode("Node-1");
        ring.addNode("Node-2");
        ring.addNode("Node-3");

        String node1 = ring.getNode("test-key-abc");
        String node2 = ring.getNode("test-key-abc");
        String node3 = ring.getNode("test-key-abc");

        assertEquals(node1, node2, "Same key should route to same node");
        assertEquals(node2, node3, "Same key should route to same node");
    }

    @Test
    @DisplayName("Adding a node should only move ~1/N keys (minimum disruption)")
    void testMinimumKeyMovement() {
        // Start with 3 nodes
        ring.addNode("Node-1");
        ring.addNode("Node-2");
        ring.addNode("Node-3");

        // Record initial mapping for 1000 keys
        Map<String, String> initialMapping = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            initialMapping.put(key, ring.getNode(key));
        }

        // Add a 4th node
        ring.addNode("Node-4");

        // Count how many keys moved
        int movedKeys = 0;
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            if (!initialMapping.get(key).equals(ring.getNode(key))) {
                movedKeys++;
            }
        }

        // With consistent hashing, approximately 1/N keys should move
        // Going from 3 to 4 nodes: ~25% of keys should move (±15% tolerance)
        double movedPercentage = movedKeys / 10.0;
        assertTrue(movedPercentage < 45,
                "Too many keys moved: " + movedPercentage + "% (expected <45%)");
        assertTrue(movedPercentage > 5,
                "Too few keys moved: " + movedPercentage + "% (expected >5%)");

        System.out.println("Keys moved when adding Node-4: " + movedKeys + "/1000 ("
                + movedPercentage + "%)");
    }

    @Test
    @DisplayName("Removing a node should only affect keys mapped to that node")
    void testRemoveNode() {
        ring.addNode("Node-1");
        ring.addNode("Node-2");
        ring.addNode("Node-3");
        ring.addNode("Node-4");

        // Record initial mapping
        Map<String, String> initialMapping = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            initialMapping.put(key, ring.getNode(key));
        }

        // Remove Node-3
        ring.removeNode("Node-3");

        // Only keys that were on Node-3 should have moved
        int movedKeys = 0;
        int movedFromNode3 = 0;
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            String oldNode = initialMapping.get(key);
            String newNode = ring.getNode(key);

            if (!oldNode.equals(newNode)) {
                movedKeys++;
                if (oldNode.equals("Node-3")) {
                    movedFromNode3++;
                }
            }
        }

        // All moved keys should have been from Node-3
        assertEquals(movedKeys, movedFromNode3,
                "Only keys from the removed node should move");
        // And no key should be mapped to Node-3 anymore
        for (int i = 0; i < 1000; i++) {
            assertNotEquals("Node-3", ring.getNode("key-" + i));
        }

        System.out.println("Keys remapped after removing Node-3: " + movedKeys + "/1000");
    }

    @Test
    @DisplayName("Empty ring should return null")
    void testEmptyRing() {
        assertNull(ring.getNode("any-key"), "Empty ring should return null");
    }

    @Test
    @DisplayName("Single node should get all keys")
    void testSingleNode() {
        ring.addNode("Node-1");

        for (int i = 0; i < 100; i++) {
            assertEquals("Node-1", ring.getNode("key-" + i),
                    "Single node should receive all keys");
        }
    }

    @Test
    @DisplayName("Ring size should reflect virtual nodes")
    void testRingSize() {
        ring.addNode("Node-1");
        assertEquals(150, ring.getRingSize(), "Should have 150 virtual nodes");

        ring.addNode("Node-2");
        assertEquals(300, ring.getRingSize(), "Should have 300 virtual nodes");

        ring.removeNode("Node-1");
        assertEquals(150, ring.getRingSize(), "Should have 150 virtual nodes after removal");
    }

    @Test
    @DisplayName("Hash function should be deterministic")
    void testHashDeterminism() {
        ring.addNode("Node-1"); // Need at least one node to use getHash

        int hash1 = ring.getHash("test-prefix");
        int hash2 = ring.getHash("test-prefix");

        assertEquals(hash1, hash2, "Same input should always produce same hash");
    }

    @Test
    @DisplayName("Different keys should generally map to different hash values")
    void testHashDistribution() {
        Set<Integer> hashes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            hashes.add(ring.getHash("prefix-" + i));
        }

        // With a good hash function, collisions should be extremely rare
        assertTrue(hashes.size() > 990,
                "Expected <10 collisions in 1000 keys, got " + (1000 - hashes.size()));
    }
}
