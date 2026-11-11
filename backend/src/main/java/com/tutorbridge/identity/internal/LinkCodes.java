package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Why a one-time link code was not consumed, as the contract names it: LINK_USED, LINK_EXPIRED, or
 * LINK_INVALID for an unknown code or one made for another purpose (AC-UC01-02).
 */
final class LinkCodes {

    private LinkCodes() {}

    /** Runs in the transaction that just failed to consume the code. */
    static ApiException rejection(AccountRepository accounts, String tokenDigest, String purpose) {
        return accounts.findChallenge(tokenDigest)
                .filter(c -> purpose.equals(c.purpose()))
                .map(c -> c.consumedAt() != null
                        ? new ApiException(HttpStatus.CONFLICT, "LINK_USED", "Liên kết này đã được sử dụng.")
                        : new ApiException(HttpStatus.BAD_REQUEST, "LINK_EXPIRED",
                                "Liên kết đã hết hạn. Vui lòng yêu cầu liên kết mới."))
                .orElseGet(LinkCodes::invalid);
    }

    static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "LINK_INVALID", "Liên kết không hợp lệ.");
    }
}
