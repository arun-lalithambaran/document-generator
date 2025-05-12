package com.neptune.afo.service;

import com.neptune.afo.converter.UserUserResponseConverter;
import com.neptune.afo.entity.User;
import com.neptune.afo.enums.Role;
import com.neptune.afo.model.AuthenticationRequest;
import com.neptune.afo.model.AuthenticationResponse;
import com.neptune.afo.model.RegistrationRequest;
import com.neptune.afo.model.UserResponse;
import com.neptune.afo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired AuthenticationManager authenticationManager;
  @Autowired JwtService jwtService;
  @Autowired UserUserResponseConverter userResponseConverter;

  public void register(RegistrationRequest registrationRequest) {
    var user =
        User.builder()
            .firstName(registrationRequest.getFirstName())
            .lastName(registrationRequest.getLastName())
            .email(registrationRequest.getEmail())
            .password(passwordEncoder.encode(registrationRequest.getPassword()))
            .role(Role.STANDARD_USER)
            .build();
    userRepository.save(user);
  }

  public AuthenticationResponse authenticate(AuthenticationRequest authRequest) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword()));
    var user =
        userRepository
            .findByEmail(authRequest.getEmail())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    var accessToken = jwtService.generateToken(user);
    UserResponse userResponse = userResponseConverter.covert(user);
    return AuthenticationResponse.builder()
        .userDetails(userResponse)
        .accessToken("Bearer " + accessToken)
        .build();
  }
}
