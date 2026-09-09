package com.engine.order.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderEngineOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Event-Driven Order Processing & Workflow Engine API")
                        .description("Hexagonal Architecture REST API for distributed order management, state machine transitions, and saga fulfillment.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Order Engine Team")
                                .email("dev@engine.order.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
