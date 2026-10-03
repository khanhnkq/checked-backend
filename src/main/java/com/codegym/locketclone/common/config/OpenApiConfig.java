package com.codegym.locketclone.common.config;

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
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI customOpenAPI(@Value("${server.port:8080}") String serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("Locket Clone API Contract")
                        .version("1.0.0")
                        .description("Tài liệu đặc tả API chuẩn OpenAPI 3.0 / Swagger UI dành cho Frontend (Web & Mobile). "
                                + "Cung cấp đầy đủ các endpoint xác thực (JWT), chia sẻ ảnh (Locket), quản lý giao dịch tài chính, kết bạn và lưu trữ ảnh Garage S3.")
                        .contact(new Contact()
                                .name("Development Team")
                                .email("dev@locketclone.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local Development Server")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Nhập Access Token (JWT) được trả về từ API /api/v1/auth/login hoặc /api/v1/auth/verify")));
    }
}
