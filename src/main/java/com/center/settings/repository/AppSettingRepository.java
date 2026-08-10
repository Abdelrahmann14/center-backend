package com.center.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.settings.entity.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
