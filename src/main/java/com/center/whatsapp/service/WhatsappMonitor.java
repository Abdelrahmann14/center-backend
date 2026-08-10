package com.center.whatsapp.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically checks every connected WhatsApp number's Green API state so a
 * dropped number is detected and its responsibilities are failed over to a backup
 * without waiting for someone to open the Services page.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WhatsappMonitor {

    private final WhatsappInstanceService instances;

    /** Runs a minute after the last run finished; the first run is delayed on boot. */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT60S")
    public void poll() {
        try {
            instances.monitor();
        } catch (Exception ex) {
            log.warn("WhatsApp monitor poll failed: {}", ex.getMessage());
        }
    }
}
