package com.uttam.urlshortener;

import com.uttam.urlshortener.kafka.ClickEventConsumer;
import com.uttam.urlshortener.kafka.ClickEventProducer;
import com.uttam.urlshortener.service.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class UrlShortenerApplicationTests {

    @MockitoBean
    private RedisCacheService redisCacheService;

    @MockitoBean
    private ClickEventProducer clickEventProducer;

    @MockitoBean
    private ClickEventConsumer clickEventConsumer;

    @Test
    void contextLoads() {
    }
}
