package org.gcrel.tfbackevent.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
}
