package com.neptune.afo.converter;

import com.neptune.afo.entity.User;
import com.neptune.afo.model.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserUserResponseConverter {
  public UserResponse covert(User user) {
    return UserResponse.builder()
        .userId(user.getId())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .email(user.getEmail())
        .username(user.getUsername())
        .userRole(user.getRole().name())
        .profileImage(null)
        .build();
  }
}
