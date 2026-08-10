package com.center.settings.service;
import com.center.center.entity.Center;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.settings.entity.AppSetting;
import com.center.common.exception.BusinessRuleException;
import com.center.settings.repository.AppSettingRepository;
import com.center.settings.service.SettingsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private static final String SENDER_NAME = "sender_name";
    private static final String DEFAULT_SENDER = "Center System";

    private final AppSettingRepository repository;

    @Override
    @Transactional(readOnly = true)
    public String senderName() {
        return repository.findById(SENDER_NAME)
                .map(AppSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(DEFAULT_SENDER);
    }

    @Override
    @Transactional
    public void setSenderName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.isBlank()) {
            throw new BusinessRuleException("اسم المُرسِل مطلوب");
        }
        AppSetting setting = repository.findById(SENDER_NAME).orElseGet(() -> {
            AppSetting fresh = new AppSetting();
            fresh.setKey(SENDER_NAME);
            return fresh;
        });
        setting.setValue(value);
        setting.setUpdatedAt(OffsetDateTime.now());
        repository.save(setting);
    }
}
