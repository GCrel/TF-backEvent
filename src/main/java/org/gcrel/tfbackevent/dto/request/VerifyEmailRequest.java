package org.gcrel.tfbackevent.dto.request;

import lombok.Data;

@Data
public class VerifyEmailRequest {
    private String code;
    private String email;
}
