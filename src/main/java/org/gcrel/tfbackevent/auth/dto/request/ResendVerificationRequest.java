package org.gcrel.tfbackevent.auth.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResendVerificationRequest {
    private String email;
}
