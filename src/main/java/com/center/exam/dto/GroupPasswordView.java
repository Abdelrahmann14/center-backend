package com.center.exam.dto;

import java.util.UUID;

/** One group's exam password, shown to the admin on the exam page. */
public record GroupPasswordView(UUID groupId, String password) {
}
