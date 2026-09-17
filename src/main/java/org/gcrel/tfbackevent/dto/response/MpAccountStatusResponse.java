package org.gcrel.tfbackevent.dto.response;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MpAccountStatusResponse {

    private boolean linked;
    private String mpEmail;
    private LocalDateTime linkedAt;

    public MpAccountStatusResponse(boolean linked, String mpEmail, LocalDateTime linkedAt) {
        this.linked = linked;
        this.mpEmail = mpEmail;
        this.linkedAt = linkedAt;
    }

    public MpAccountStatusResponse() {
        this.linked = false;
        this.mpEmail = null;
        this.linkedAt = null;
    }
}
