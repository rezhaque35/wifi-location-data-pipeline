// com/wifi/positioning/config/WebClientConfig.java
package com.wifi.positioning.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for WebClient used to call the positioning service.
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final IntegrationProperties properties;

    /**
     * Creates a WebClient bean configured with appropriate timeouts for the positioning service.
     * 
     * @return Configured WebClient instance
     */
    @Bean
    public WebClient webClient() {
        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 
                (int) properties.getPositioning().getConnectTimeoutMs())
            .responseTimeout(Duration.ofMillis(properties.getPositioning().getReadTimeoutMs()))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(
                    properties.getPositioning().getReadTimeoutMs(), TimeUnit.MILLISECONDS))
            );

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
