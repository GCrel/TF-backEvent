package org.gcrel.tfbackevent.payment.dto.response;

import java.time.LocalDateTime;

public record MpAccountStatusResponse(
        boolean linked,
        String mpEmail,
        LocalDateTime linkedAt) {

    public MpAccountStatusResponse() {
        this(false, null, null);
    }
}
