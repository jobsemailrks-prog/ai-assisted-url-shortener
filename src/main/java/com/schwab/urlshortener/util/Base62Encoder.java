package com.schwab.urlshortener.util;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {

    private static final String BASE62_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = BASE62_ALPHABET.length(); // 62

    /**
     * Converts a numeric database ID into a Base62 short code.
     */
    public String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be positive to encode to Base62");
        }

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            int remainder = (int) (id % BASE);
            sb.append(BASE62_ALPHABET.charAt(remainder));
            id /= BASE;
        }

        return sb.reverse().toString();
    }

    /**
     * Converts a Base62 short code back into its numeric database ID.
     */
    public long decode(String shortCode) {
        if (shortCode == null || shortCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Short code must not be null or empty");
        }

        long id = 0;
        for (int i = 0; i < shortCode.length(); i++) {
            char c = shortCode.charAt(i);
            int digit = BASE62_ALPHABET.indexOf(c);
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            id = id * BASE + digit;
        }

        return id;
    }
}