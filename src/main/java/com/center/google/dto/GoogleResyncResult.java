package com.center.google.dto;

/** Result of a manual "sync now": how many students were processed and contacts written. */
public record GoogleResyncResult(int students, int contacts) {
}
