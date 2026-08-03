package com.uttam.urlshortener.util;

/**
 * Encodes a numeric ID into a Base62 string and vice versa.
 *
 * Base62 uses the alphabet: 0-9, a-z, A-Z (62 characters total).
 * This gives us short, URL-safe codes — e.g., ID 1000 → "g8"
 *
 * Why Base62 over random strings?
 * - Deterministic: same ID always produces the same code (collision-free)
 * - Reversible: we can decode the short code back to the original ID
 * - Short: a 6-char Base62 string can represent up to 62^6 = 56.8 billion unique URLs
 */
public final class Base62Encoder {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length(); // 62

    // Prevent instantiation — this is a utility class
    private Base62Encoder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Encodes a positive long ID into a Base62 string.
     *
     * Examples:
     *   encode(1)    → "1"
     *   encode(62)   → "10"
     *   encode(1000) → "g8"
     *
     * @param id the numeric ID to encode (must be > 0)
     * @return the Base62-encoded string
     * @throws IllegalArgumentException if id is not positive
     */
    public static String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be positive, got: " + id);
        }

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % BASE)));
            id /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to the original numeric ID.
     *
     * Examples:
     *   decode("1")  → 1
     *   decode("10") → 62
     *   decode("g8") → 1000
     *
     * @param shortCode the Base62-encoded string
     * @return the original numeric ID
     * @throws IllegalArgumentException if shortCode contains invalid characters
     */
    public static long decode(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException("Short code cannot be null or blank");
        }

        long id = 0;
        for (char c : shortCode.toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            id = id * BASE + index;
        }
        return id;
    }
}
