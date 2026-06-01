package org.example.chatbot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")  // ← Aplica CORS solo a tus endpoints de API
                        // ✅ Dominios permitidos (Vercel + desarrollo local)
                        .allowedOriginPatterns(
                                "https://*.vercel.app",      // Todos los subdominios de Vercel
                                "http://localhost:*",         // Desarrollo local con cualquier puerto
                                "https://chat-bot-front-seven.vercel.app" //dominio específico
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders("*")              // Permitir todos los headers
                        .exposedHeaders("Content-Type")   // Headers que el frontend puede leer
                        .allowCredentials(true)           // Permitir cookies/auth si las usas
                        .maxAge(3600);                    // Cache de preflight: 1 hora
            }
        };
    }
}