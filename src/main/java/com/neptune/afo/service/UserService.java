package com.neptune.afo.service;

import com.neptune.afo.entity.User;
import com.neptune.afo.exception.NotFoundException;
import com.neptune.afo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  @Autowired private UserRepository userRepository;

  public User getUser(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new NotFoundException("User not found"));
  }
}
