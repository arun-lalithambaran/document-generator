package com.ddc.analysis;

import com.ddc.analysis.util.AppConstants;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients
@EnableCaching
@EnableAsync
@OpenAPIDefinition(
    info =
        @Info(
            title = "DDC ANR API",
            version = "1.0",
            description = "DDC Analysis and Reporting Endpoints"),
    servers = {@Server(url = "/")})
@SecurityScheme(
    scheme = "Bearer",
    in = SecuritySchemeIn.HEADER,
    type = SecuritySchemeType.APIKEY,
    name = AppConstants.AUTHORIZATION)
public class DdcAnalysisApplication {

  public static void main(String[] args) {
    SpringApplication.run(DdcAnalysisApplication.class, args);
  }
}
