package com.neptune.afo.controller;

import com.neptune.afo.model.AuthenticationRequest;
import com.neptune.afo.model.AuthenticationResponse;
import com.neptune.afo.model.CommonResponse;
import com.neptune.afo.model.RegistrationRequest;
import com.neptune.afo.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/auth")
public class AuthenticationController {

  @Autowired AuthenticationService authenticationService;

  @PostMapping("register")
  @ResponseStatus(HttpStatus.CREATED)
  public CommonResponse<String> register(@RequestBody RegistrationRequest registrationDetails) {
    authenticationService.register(registrationDetails);
    return CommonResponse.create("Registration successfully completed.");
  }

  @PostMapping("login")
  public AuthenticationResponse login(@RequestBody AuthenticationRequest authenticationDetails) {
    return authenticationService.authenticate(authenticationDetails);
  }
}
