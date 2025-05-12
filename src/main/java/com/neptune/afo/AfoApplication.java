package com.neptune.afo;

import com.neptune.afo.constant.AppConstant;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(
		info =
		@Info(
				title = "Neptune",
				version = "1.0",
				description = "AFO-Neptune Base Application"),
		servers = {@Server(url = "/")})
@SecurityScheme(name = AppConstant.AUTHORIZATION, scheme = AppConstant.BEARER, in = SecuritySchemeIn.HEADER, type = SecuritySchemeType.APIKEY)
@SpringBootApplication
public class AfoApplication {

	public static void main(String[] args) {
		SpringApplication.run(AfoApplication.class, args);
	}

}
