package com.center.google.dto;

import jakarta.validation.constraints.Size;

/** The three optional contact marks for one grade. Blank = no mark. */
public record GoogleMarkRequest(
        @Size(max = 40) String studentMark,
        @Size(max = 40) String parentMark,
        @Size(max = 40) String bothMark) {
}
