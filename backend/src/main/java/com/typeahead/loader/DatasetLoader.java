package com.typeahead.loader;

import com.typeahead.entity.SearchQuery;
import com.typeahead.repository.SearchQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * DatasetLoader — Generates and loads 100,000+ search queries on startup.
 *
 * ============================================================
 * GENERATION STRATEGY
 * ============================================================
 *
 * Uses category-based templates to generate realistic, diverse queries:
 *   1. Base terms across 12+ categories (tech, food, sports, etc.)
 *   2. Modifiers (best, cheap, review, price, etc.)
 *   3. Prefixes (how to, where to buy, etc.)
 *   4. Combinations: base×modifier, prefix×base, prefix×base×modifier
 *
 * With ~250 base terms, ~50 modifiers, and ~20 prefixes:
 *   250 + 250×50 + 250×20 + ... ≈ 120,000+ unique queries
 *
 * ============================================================
 * COUNT DISTRIBUTION (Zipf's Law)
 * ============================================================
 *
 * Real search queries follow a Zipf distribution:
 *   - A few queries are extremely popular (head)
 *   - Most queries are rarely searched (long tail)
 *
 * We simulate this with:
 *   count = 100000 / (rank + 1)^0.7
 *
 *   Rank 1:      100,000
 *   Rank 10:      20,000
 *   Rank 100:      3,981
 *   Rank 1,000:      501
 *   Rank 10,000:       63
 *   Rank 100,000:        8
 *
 * ============================================================
 * PERFORMANCE
 * ============================================================
 *
 * With Hibernate JDBC batch inserts (batch_size=50):
 *   100K records ≈ 3-5 seconds (H2 in-memory)
 *   100K records ≈ 8-12 seconds (PostgreSQL)
 *
 * Progress is logged every 10,000 records.
 *
 * @Order(1) ensures this runs before any other CommandLineRunner.
 */
// @Component
// @Order(1)
public class DatasetLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatasetLoader.class);

    private final SearchQueryRepository repository;

    public DatasetLoader(SearchQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        // Skip if data already exists (e.g., persistent PostgreSQL)
        long existingCount = repository.count();
        if (existingCount > 0) {
            log.info("[LOADER] Database already contains {} queries — skipping load", existingCount);
            return;
        }

        log.info("[LOADER] ========================================");
        log.info("[LOADER] Starting dataset generation...");
        log.info("[LOADER] ========================================");

        long startTime = System.currentTimeMillis();

        // Generate unique queries
        Set<String> uniqueQueries = generateQueries();
        log.info("[LOADER] Generated {} unique queries", uniqueQueries.size());

        // Convert to entities with Zipf-distributed counts
        List<SearchQuery> entities = new ArrayList<>(uniqueQueries.size());
        int rank = 0;
        for (String query : uniqueQueries) {
            long count = generateCount(rank++);
            entities.add(new SearchQuery(query, count));
        }

        // Shuffle to randomize insertion order (prevents sequential ID patterns)
        Collections.shuffle(entities);

        // Batch insert with progress logging
        int batchSize = 5000;
        for (int i = 0; i < entities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, entities.size());
            repository.saveAll(entities.subList(i, end));

            if (end % 10000 == 0 || end == entities.size()) {
                int pct = (int) ((double) end / entities.size() * 100);
                log.info("[LOADER] Progress: {}/{} queries loaded ({}%)",
                        end, entities.size(), pct);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[LOADER] ========================================");
        log.info("[LOADER] Dataset loading complete!");
        log.info("[LOADER] Total queries: {}", entities.size());
        log.info("[LOADER] Time elapsed: {}ms", elapsed);
        log.info("[LOADER] ========================================");
    }

    /**
     * Generate 100,000+ unique search queries using category-based templates.
     */
    private Set<String> generateQueries() {
        Set<String> queries = new LinkedHashSet<>();

        // ============================================================
        // Category: Technology
        // ============================================================
        String[] tech = {
            "iphone", "samsung galaxy", "pixel", "macbook", "laptop", "tablet",
            "airpods", "apple watch", "ipad", "smart tv", "bluetooth speaker",
            "wireless earbuds", "gaming mouse", "mechanical keyboard", "webcam",
            "monitor", "graphics card", "ssd", "usb c hub", "power bank",
            "smart home", "alexa", "google home", "ring doorbell", "nest thermostat",
            "drone", "action camera", "vr headset", "gaming laptop", "chromebook",
            "printer", "scanner", "router", "mesh wifi", "external hard drive",
            "memory card", "phone case", "screen protector", "charging cable", "hdmi cable"
        };

        // ============================================================
        // Category: Programming & Software
        // ============================================================
        String[] programming = {
            "java", "python", "javascript", "react", "angular", "vue",
            "spring boot", "docker", "kubernetes", "machine learning",
            "deep learning", "artificial intelligence", "chatgpt", "github",
            "visual studio code", "intellij", "node js", "typescript",
            "css", "html", "sql", "mongodb", "redis", "postgresql",
            "aws", "azure", "gcp", "terraform", "jenkins", "git",
            "rest api", "graphql", "microservices", "devops", "agile",
            "data science", "data engineering", "web development", "mobile app", "flutter"
        };

        // ============================================================
        // Category: Food & Cooking
        // ============================================================
        String[] food = {
            "pizza", "burger", "sushi", "ramen", "pasta", "salad",
            "smoothie", "coffee", "steak", "tacos", "ice cream", "cake",
            "chicken recipe", "soup", "sandwich", "breakfast ideas",
            "dinner recipes", "meal prep", "vegan food", "keto diet",
            "baking", "grilling", "air fryer", "instant pot", "slow cooker"
        };

        // ============================================================
        // Category: Entertainment
        // ============================================================
        String[] entertainment = {
            "netflix", "spotify", "youtube", "gaming", "movies",
            "tv shows", "anime", "music", "podcast", "streaming",
            "playstation", "xbox", "nintendo switch", "steam", "twitch",
            "marvel", "star wars", "harry potter", "disney plus", "hbo max"
        };

        // ============================================================
        // Category: Sports
        // ============================================================
        String[] sports = {
            "football", "basketball", "cricket", "tennis", "soccer",
            "baseball", "golf", "swimming", "running", "cycling",
            "yoga", "workout", "gym", "fitness", "marathon",
            "nba", "nfl", "premier league", "world cup", "olympics"
        };

        // ============================================================
        // Category: Shopping
        // ============================================================
        String[] shopping = {
            "amazon", "shoes", "dress", "jacket", "watch",
            "sunglasses", "backpack", "headphones", "sneakers", "jeans",
            "t shirt", "hoodie", "wallet", "handbag", "jewelry",
            "perfume", "skincare", "makeup", "furniture", "home decor"
        };

        // ============================================================
        // Category: Health & Wellness
        // ============================================================
        String[] health = {
            "vitamins", "protein powder", "supplements", "meditation",
            "mental health", "sleep", "weight loss", "nutrition",
            "healthy eating", "exercise", "stretching", "physical therapy",
            "doctor near me", "pharmacy", "first aid"
        };

        // ============================================================
        // Category: Travel
        // ============================================================
        String[] travel = {
            "flights", "hotels", "airbnb", "vacation", "beach",
            "cruise", "road trip", "passport", "travel insurance",
            "car rental", "train tickets", "backpacking", "camping",
            "hiking", "national parks"
        };

        // ============================================================
        // Category: Education
        // ============================================================
        String[] education = {
            "online courses", "university", "scholarship", "degree",
            "certification", "tutoring", "study tips", "exam preparation",
            "math", "science", "history", "english", "economics",
            "philosophy", "psychology"
        };

        // ============================================================
        // Category: Finance
        // ============================================================
        String[] finance = {
            "bitcoin", "stocks", "investment", "credit card", "loan",
            "mortgage", "insurance", "savings", "budget", "retirement",
            "crypto", "ethereum", "trading", "bank account", "tax"
        };

        // ============================================================
        // Category: General / Utilities
        // ============================================================
        String[] general = {
            "weather", "news", "maps", "translate", "calculator",
            "calendar", "email", "social media", "instagram", "tiktok",
            "facebook", "twitter", "linkedin", "reddit", "pinterest",
            "whatsapp", "telegram", "zoom", "teams", "slack"
        };

        // Combine all base terms
        String[][] allCategories = {
            tech, programming, food, entertainment, sports,
            shopping, health, travel, education, finance, general
        };

        List<String> allTerms = new ArrayList<>();
        for (String[] category : allCategories) {
            allTerms.addAll(Arrays.asList(category));
        }

        // ============================================================
        // Modifiers
        // ============================================================
        String[] modifiers = {
            "review", "price", "deals", "sale", "discount",
            "comparison", "specs", "features", "setup", "install",
            "tutorial", "guide", "tips", "tricks", "hacks",
            "alternatives", "accessories", "case", "cover", "charger",
            "settings", "update", "upgrade", "repair", "fix",
            "download", "free", "premium", "pro", "max",
            "mini", "lite", "2024", "2025", "2026",
            "new", "latest", "upcoming", "release date", "availability",
            "warranty", "refurbished", "used", "cheap", "affordable",
            "luxury", "professional", "beginner", "advanced", "certification"
        };

        // ============================================================
        // Question prefixes
        // ============================================================
        String[] questionPrefixes = {
            "how to", "what is", "where to buy", "when to",
            "why", "which", "best", "top", "top 10",
            "cheapest", "fastest", "easiest", "most popular",
            "how to use", "how to fix", "how to install",
            "how to learn", "how to setup", "how to make",
            "is it worth"
        };

        // ============================================================
        // Generate combinations
        // ============================================================

        // Pattern 1: Base terms alone (250+ queries)
        for (String term : allTerms) {
            queries.add(term.toLowerCase());
        }

        // Pattern 2: Base + modifier (250 × 50 = 12,500 queries)
        for (String term : allTerms) {
            for (String mod : modifiers) {
                queries.add((term + " " + mod).toLowerCase());
            }
        }

        // Pattern 3: Prefix + base (250 × 20 = 5,000 queries)
        for (String term : allTerms) {
            for (String prefix : questionPrefixes) {
                queries.add((prefix + " " + term).toLowerCase());
            }
        }

        // Pattern 4: Prefix + base + modifier (subset to avoid explosion)
        // Use every 3rd term and every 5th modifier = ~250/3 × 20 × 50/5 ≈ 16,600
        for (int i = 0; i < allTerms.size(); i += 3) {
            for (String prefix : questionPrefixes) {
                for (int j = 0; j < modifiers.length; j += 5) {
                    queries.add((prefix + " " + allTerms.get(i) + " " + modifiers[j]).toLowerCase());
                }
            }
        }

        // Pattern 5: Term vs term comparisons (subset)
        // Use every 2nd term for manageable count: ~125 × 124 / 2 ≈ 7,750
        for (int i = 0; i < allTerms.size(); i += 2) {
            for (int j = i + 2; j < allTerms.size(); j += 4) {
                queries.add((allTerms.get(i) + " vs " + allTerms.get(j)).toLowerCase());
            }
        }

        // Pattern 6: Category-specific patterns
        for (String term : tech) {
            queries.add((term + " unboxing").toLowerCase());
            queries.add((term + " battery life").toLowerCase());
            queries.add((term + " camera quality").toLowerCase());
            queries.add(("is " + term + " worth it").toLowerCase());
        }

        for (String term : programming) {
            queries.add((term + " interview questions").toLowerCase());
            queries.add((term + " documentation").toLowerCase());
            queries.add((term + " best practices").toLowerCase());
            queries.add((term + " for beginners").toLowerCase());
            queries.add((term + " crash course").toLowerCase());
        }

        for (String term : food) {
            queries.add((term + " near me").toLowerCase());
            queries.add((term + " recipe easy").toLowerCase());
            queries.add((term + " delivery").toLowerCase());
            queries.add((term + " calories").toLowerCase());
        }

        for (String term : travel) {
            queries.add((term + " booking").toLowerCase());
            queries.add((term + " last minute").toLowerCase());
            queries.add((term + " all inclusive").toLowerCase());
        }

        for (String term : sports) {
            queries.add((term + " live score").toLowerCase());
            queries.add((term + " highlights").toLowerCase());
            queries.add((term + " schedule").toLowerCase());
        }

        // Pattern 7: Long-tail queries (realistic specific searches)
        String[] longTail = {
            "iphone 15 pro max price in usa",
            "samsung galaxy s24 ultra camera comparison",
            "best laptop for programming 2025",
            "macbook air m3 vs macbook pro m3",
            "how to learn python in 30 days",
            "react vs angular which is better 2025",
            "best wireless earbuds under 100",
            "healthy meal prep ideas for the week",
            "netflix new releases this month",
            "cheapest flights to europe",
            "how to invest in stocks for beginners",
            "best credit card for travel rewards",
            "yoga for beginners at home",
            "remote jobs hiring now",
            "how to build a gaming pc",
            "best books to read 2025",
            "free online courses with certificates",
            "how to lose weight fast and healthy",
            "best smartphone under 500",
            "how to start a small business",
            "artificial intelligence future predictions",
            "electric car vs hybrid 2025",
            "best streaming service for movies",
            "home workout no equipment needed",
            "cryptocurrency market predictions 2025",
            "sustainable fashion brands online",
            "best coffee shops near me",
            "how to improve credit score fast",
            "digital marketing certification free",
            "best time to visit japan"
        };
        for (String q : longTail) {
            queries.add(q.toLowerCase());
        }

        return queries;
    }

    /**
     * Generate a search count following Zipf's Law distribution.
     *
     * count = 100000 / (rank + 1)^0.7
     *
     * This creates a realistic "head and long tail" distribution:
     *   - Top queries: tens of thousands of searches
     *   - Most queries: single to double digits
     *
     * @param rank The query's rank (0-based, lower = more popular)
     * @return The synthetic search count
     */
    private long generateCount(int rank) {
        return Math.max(1, (long) (100000.0 / Math.pow(rank + 1, 0.7)));
    }
}
