package com.uttam.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    @Test
    void testEncodeDecodeSingleDigit() {
        long id = 1L;
        String encoded = Base62Encoder.encode(id);
        assertEquals("1", encoded);
        assertEquals(id, Base62Encoder.decode(encoded));
    }

    @Test
    void testEncodeDecodeLargeId() {
        long id = 123456789L;
        String encoded = Base62Encoder.encode(id);
        assertNotNull(encoded);
        assertFalse(encoded.isBlank());
        assertEquals(id, Base62Encoder.decode(encoded));
    }

    @Test
    void testEncodeZeroOrNegativeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.encode(0));
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.encode(-5));
    }

    @Test
    void testDecodeInvalidCharacterThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.decode("abc@123"));
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.decode(null));
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.decode(""));
    }
}
