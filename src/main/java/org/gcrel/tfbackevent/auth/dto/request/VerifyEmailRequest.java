package org.gcrel.tfbackevent.auth.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyEmailRequest {
    private String code;
    private String email;
}
