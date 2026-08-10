package com.center.settings.service;

/** Platform-wide settings the super admin can change at runtime. */
public interface SettingsService {

    /** The display name shown as the sender of notifications / WhatsApp messages. */
    String senderName();

    void setSenderName(String name);
}
