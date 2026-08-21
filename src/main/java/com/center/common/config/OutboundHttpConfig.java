package com.center.common.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP clients every outbound integration uses.
 *
 * <p>They exist because {@code RestClient.create()} has NO timeouts: a call that
 * connects and then never answers stays open forever. That is not theoretical -
 * every one of these calls is made from a request thread or from the scheduler,
 * and several are made while a database transaction is open. One hung WhatsApp
 * socket used to be enough to pin a Tomcat thread and a Hikari connection until
 * the process was restarted, and a hang inside a scheduled job silently stopped
 * EVERY background job, because they share one scheduler thread.
 *
 * <p>Two profiles, because the deadlines genuinely differ. Messaging and contact
 * sync answer in well under a second when healthy; an LLM completion routinely
 * takes tens of seconds and a 20-second cap would simply fail every call.
 *
 * <p>The JDK factory (rather than {@code SimpleClientHttpRequestFactory}) is what
 * keeps connections pooled between calls - a broadcast opening a fresh TCP+TLS
 * connection per recipient spends more time in the handshake than in the send.
 */
@Configuration
public class OutboundHttpConfig {

    /** Meta and Google: fast services, short leash. */
    public static final String EXTERNAL = "externalRestClient";

    /** The AI variant generator: a completion is slow by nature. */
    public static final String AI = "aiRestClient";

    /**
     * Primary so the many integrations that want the short leash can inject a
     * plain {@code RestClient} with no qualifier; only the AI client asks for the
     * long one by name.
     */
    @Bean(EXTERNAL)
    @Primary
    RestClient externalRestClient(
            @Value("${app.http.connect-timeout-ms:5000}") long connectMs,
            @Value("${app.http.read-timeout-ms:20000}") long readMs) {
        return build(Duration.ofMillis(connectMs), Duration.ofMillis(readMs));
    }

    @Bean(AI)
    RestClient aiRestClient(
            @Value("${app.http.connect-timeout-ms:5000}") long connectMs,
            @Value("${app.http.ai-read-timeout-ms:45000}") long readMs) {
        return build(Duration.ofMillis(connectMs), Duration.ofMillis(readMs));
    }

    private static RestClient build(Duration connect, Duration read) {
        HttpClient jdk = HttpClient.newBuilder()
                .connectTimeout(connect)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(read);
        return RestClient.builder().requestFactory(factory).build();
    }
}
