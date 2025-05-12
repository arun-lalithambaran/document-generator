package com.neptune.afo.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private String username;
    private String profileImage;
    private String userRole;
}
