package com.schwab.urlshortener.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62EncoderTest {

    private Base62Encoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new Base62Encoder();
    }

    @Test
    void testEncodeAndDecode_Success() {
        long originalId = 125000L;
        String encoded = encoder.encode(originalId);
        long decoded = encoder.decode(encoded);

        assertThat(encoded).isNotEmpty();
        assertThat(decoded).isEqualTo(originalId);
    }

    @Test
    void testEncode_NegativeOrZeroId_ThrowsException() {
        assertThatThrownBy(() -> encoder.encode(0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> encoder.encode(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecode_InvalidCharacter_ThrowsException() {
        assertThatThrownBy(() -> encoder.decode("invalid!code"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}