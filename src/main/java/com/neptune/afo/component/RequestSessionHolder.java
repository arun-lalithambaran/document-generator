package com.neptune.afo.component;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@RequestScope
@Component
@Getter
@Setter
public class RequestSessionHolder {
    private UserDetails userDetails;
}
