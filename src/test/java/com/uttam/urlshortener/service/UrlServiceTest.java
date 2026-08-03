package com.uttam.urlshortener.service;

import com.uttam.urlshortener.entity.UrlMapping;
import com.uttam.urlshortener.kafka.ClickEventProducer;
import com.uttam.urlshortener.repository.UrlRepository;
import com.uttam.urlshortener.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private ClickEventProducer clickEventProducer;

    @InjectMocks
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testShortenUrlNewMapping() {
        String originalUrl = "https://example.com/long/path";
        when(urlRepository.findByOriginalUrl(originalUrl)).thenReturn(Optional.empty());

        UrlMapping tempMapping = new UrlMapping();
        tempMapping.setId(100L);
        tempMapping.setOriginalUrl(originalUrl);
        tempMapping.setShortCode("temp");

        when(urlRepository.save(any(UrlMapping.class))).thenReturn(tempMapping);

        UrlMapping result = urlService.shortenUrl(originalUrl, null);

        assertNotNull(result);
        verify(redisCacheService, times(1)).cacheUrl(anyString(), eq(originalUrl));
    }

    @Test
    void testGetOriginalUrlCacheHit() {
        String shortCode = "g8";
        String originalUrl = "https://example.com/long/path";

        when(redisCacheService.getCachedUrl(shortCode)).thenReturn(Optional.of(originalUrl));

        Optional<String> result = urlService.getOriginalUrl(shortCode, "Mozilla/5.0", "127.0.0.1");

        assertTrue(result.isPresent());
        assertEquals(originalUrl, result.get());
        verify(clickEventProducer, times(1)).publishClickEvent(any());
        verifyNoInteractions(urlRepository); // Zero DB calls on cache hit!
    }
}
