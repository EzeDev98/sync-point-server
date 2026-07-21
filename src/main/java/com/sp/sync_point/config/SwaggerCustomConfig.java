package com.sp.sync_point.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerCustomConfig {

    @Value("${base-url}")
    private String baseUrl;

    @Bean
    public OpenAPI openAPI() {

        Server server = new Server()
                .url(baseUrl)
                .description("SyncPoint API Server");

        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Sync Point API")
                        .description("REST APIs for the Sync Point Application")
                        .version("1.0")
                        .contact(new Contact()
                                .name("COGNIFY")
                                .email("ezemosesjohn21997@gmail.com")
                                .url("https://www.cognify.com"))
                        .license(new License()
                                .name("API License")
                                .url("https://www.cognify.com")))
                .servers(List.of(server))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", bearerAuth))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("Bearer Authentication")
                );
    }
}