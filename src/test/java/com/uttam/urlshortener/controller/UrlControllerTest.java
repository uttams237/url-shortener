package com.uttam.urlshortener.controller;

import com.uttam.urlshortener.entity.UrlMapping;
import com.uttam.urlshortener.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UrlControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UrlService urlService;

    @InjectMocks
    private UrlController urlController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        urlController = new UrlController(urlService, "http://localhost:8080/api/v1/urls");
        mockMvc = MockMvcBuilders.standaloneSetup(urlController).build();
    }

    @Test
    void testShortenUrlEndpoint() throws Exception {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(1L);
        mapping.setOriginalUrl("https://google.com");
        mapping.setShortCode("1");
        mapping.setCreatedAt(LocalDateTime.now());

        when(urlService.shortenUrl(eq("https://google.com"), any())).thenReturn(mapping);

        mockMvc.perform(post("/api/v1/urls/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\": \"https://google.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl").value("https://google.com"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/api/v1/urls/1"));
    }

    @Test
    void testRedirectEndpoint() throws Exception {
        when(urlService.getOriginalUrl(eq("1"), any(), any()))
                .thenReturn(Optional.of("https://google.com"));

        mockMvc.perform(get("/api/v1/urls/1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://google.com"));
    }

    @Test
    void testRedirectNotFound() throws Exception {
        when(urlService.getOriginalUrl(eq("nonexistent"), any(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/urls/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
