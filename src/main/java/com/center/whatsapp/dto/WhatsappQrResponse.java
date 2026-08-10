package com.center.whatsapp.dto;

/**
 * A QR poll result. {@code type} mirrors Green API: {@code qrCode} (message is a
 * base64 PNG), {@code alreadyLogged} (already authorized), or {@code error}.
 */
public record WhatsappQrResponse(String type, String message) {
}
