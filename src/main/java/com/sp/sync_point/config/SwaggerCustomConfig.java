package com.sp.sync_point.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class SwaggerCustomConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sync Point App")
                        .description("Restful APIs for Sync Point App")
                        .version("1.0")
                        .contact(new Contact()
                                .name("COGNIFY")
                                .email("ezemosesjohn21997@gmail.com")
                                .url("https://www.cognify.com"))
                        .license(new License()
                                .name("License of API")
                                .url("API license URL")))
                .servers(Arrays.asList(
//                        new Server()
//                                .url("http://localhost:2000")
//                                .description("Local Development Server"),
                        new Server()
                                .url("https://sync-point-server.onrender.com")
                                .description("Production Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("Bearer Authentication", Arrays.asList("read", "write"))
                );
    }
}