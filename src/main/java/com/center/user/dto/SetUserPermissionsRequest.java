package com.center.user.dto;

import java.util.List;

/**
 * The full desired set of permission codes for a user - a replace, not a merge.
 * A null/empty list clears every grant.
 */
public record SetUserPermissionsRequest(List<String> codes) {

    public List<String> codesOrEmpty() {
        return codes == null ? List.of() : codes;
    }
}
