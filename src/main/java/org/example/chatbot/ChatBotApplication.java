package org.example.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class ChatBotApplication {
    public static void main(String[] args) {
        // 🔄 Carga variables desde .env y las expone como propiedades del sistema
        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();
        // Exporta cada variable para que Spring pueda leerla con ${NOMBRE}
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );

        SpringApplication.run(ChatBotApplication.class, args);
    }

}
