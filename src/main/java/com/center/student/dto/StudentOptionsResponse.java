package com.center.student.dto;

import java.util.List;

/**
 * Everything the student form needs that is NOT a single student: the
 * suggestion lists and the number the next student will get. Exists so the form
 * never has to pull the whole student table just to fill a datalist.
 *
 * @param nextSerial best-effort preview only - the real value is assigned by a
 *                   database sequence on insert
 */
public record StudentOptionsResponse(
        List<String> schools,
        List<String> cities,
        int nextSerial) {
}
