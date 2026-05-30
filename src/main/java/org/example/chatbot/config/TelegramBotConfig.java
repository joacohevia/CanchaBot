package org.example.chatbot.config;

import org.example.chatbot.dto.ChatResponse;
import org.example.chatbot.service.ChatService;
import org.example.chatbot.service.memory.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import jakarta.annotation.PostConstruct;

@Configuration
@Slf4j
public class TelegramBotConfig {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    private final ChatService chatService;

    public TelegramBotConfig(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostConstruct
    public void registerBot() throws TelegramApiException {
        log.info("Registrando CanchaBot: @{} | Token: {}...", botUsername, botToken.substring(0, 8) + "****");

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new CanchaBotImpl(botToken, botUsername, chatService));

        log.info("CanchaBot registrado. Escuchando mensajes...");
    }

    private class CanchaBotImpl extends TelegramLongPollingBot {
        private final String token;
        private final String username;
        private final ChatService chatService;

        public CanchaBotImpl(String token, String username, ChatService chatService) {
            this.token = token;
            this.username = username;
            this.chatService = chatService;
        }

        @Override
        public String getBotUsername() { return username; }

        @Override
        public String getBotToken() { return token; }

        @Override
        public void onUpdateReceived(Update update) {
            if (!update.hasMessage() || !update.getMessage().hasText()) {
                return;
            }

            Long chatId = update.getMessage().getChatId();
            String textoUsuario = update.getMessage().getText();
            log.info("Mensaje - chatId: {} | texto: {}", chatId, textoUsuario);

            try {
                ChatResponse response = chatService.consultarConIA(textoUsuario, String.valueOf(chatId));
                enviarMensaje(chatId, response.getRespuesta());

            } catch (Exception e) {
                log.error("Error procesando mensaje (chat {}): {}", chatId, e.getMessage(), e);
                enviarMensaje(chatId, "Ocurrio un error. Intenta de nuevo.");
            }
        }

        private void enviarMensaje(Long chatId, String texto) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText(texto);
            msg.setParseMode(null);
            try {
                execute(msg);
            } catch (TelegramApiException e) {
                log.error("Error enviando mensaje: {}", e.getMessage());
            }
        }
    }
}