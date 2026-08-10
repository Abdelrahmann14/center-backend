package com.center.parent.dto;

/** A linked person on a detail page: a name plus one detail line (phone or teacher). */
public record LinkedPersonResponse(String name, String detail) {
}
