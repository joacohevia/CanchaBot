package org.example.chatbot;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigDebug implements CommandLineRunner {

    @Value("${API_KEY_DEEPSEEK:NOT_SET}")
    private String geminiKey;

    @Value("${API_KEY_BOT:NOT_SET}")
    private String telegramToken;

    @Value("${DB_URL:NOT_SET}")
    private String dbUrl;

    @Override
    public void run(String... args) {
        log.info("🔍 === Configuración cargada ===");
        log.info("DEEPSEEK: {}", geminiKey.startsWith("NOT_SET") ? "❌ NO CARGADA" : "✅ CARGADA (***" + geminiKey.substring(geminiKey.length()-4) + ")");
        log.info("TELEGRAM: {}", telegramToken.startsWith("NOT_SET") ? "❌ NO CARGADA" : "✅ CARGADA");
        log.info("DB_URL: {}", dbUrl.startsWith("NOT_SET") ? "❌ NO CARGADA" : "✅ CARGADA");
        log.info("🔍 ===========================");
    }
}