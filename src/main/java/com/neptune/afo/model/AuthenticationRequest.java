package com.neptune.afo.model;

import lombok.Data;

@Data
public class AuthenticationRequest {
    private String email;
    private String password;
}
