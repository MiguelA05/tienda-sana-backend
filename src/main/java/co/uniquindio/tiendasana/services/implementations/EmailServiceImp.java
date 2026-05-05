package co.uniquindio.tiendasana.services.implementations;

import co.uniquindio.tiendasana.dto.EmailDTO;
import co.uniquindio.tiendasana.services.interfaces.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;


@Service
public class EmailServiceImp implements EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailServiceImp.class);

    @Value("${resend.api.key:}")
    private String resendApiKeyProp;

    @Value("${resend.from:}")
    private String resendFromProp;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    @Async
    public void sendEmail(EmailDTO emailDTO) throws Exception {
        String apiKey = getResendApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("resend.api.key no configurada");
        }
        sendViaResendPlain(emailDTO, apiKey);
    }

    @Override
    @Async
    public void sendEmailHtmlWithAttachment(EmailDTO emailDTO, byte[] qrCodeImage, String qrCodeContentId) throws Exception {
        String apiKey = getResendApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("resend.api.key no configurada");
        }
        sendViaResendHtmlWithAttachment(emailDTO, qrCodeImage, apiKey);
    }

    private String getResendApiKey() {
        if (resendApiKeyProp != null && !resendApiKeyProp.isBlank()) {
            return resendApiKeyProp.trim();
        }
        String env = System.getenv("RESEND_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return null;
    }

    private String getResendFrom() {
        if (resendFromProp != null && !resendFromProp.isBlank()) {
            return resendFromProp.trim();
        }
        String env = System.getenv("RESEND_FROM");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return null;
    }

    private void sendViaResendPlain(EmailDTO dto, String apiKey) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("from", resolveFromAddress());
        ArrayNode to = objectMapper.createArrayNode();
        to.add(dto.receiver());
        body.set("to", to);
        body.put("subject", dto.subject());
        body.put("text", dto.body());

        sendResendRequest(body, apiKey);
    }

    private void sendViaResendHtmlWithAttachment(EmailDTO dto, byte[] attachment, String apiKey) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("from", resolveFromAddress());
        ArrayNode to = objectMapper.createArrayNode();
        to.add(dto.receiver());
        body.set("to", to);
        body.put("subject", dto.subject());
        body.put("html", dto.body());
        if (attachment != null && attachment.length > 0) {
            ArrayNode attachments = objectMapper.createArrayNode();
            ObjectNode a = objectMapper.createObjectNode();
            a.put("type", "image/png");
            a.put("filename", "qrcode.png");
            a.put("data", Base64.getEncoder().encodeToString(attachment));
            attachments.add(a);
            body.set("attachments", attachments);
        }

        sendResendRequest(body, apiKey);
    }

    private String resolveFromAddress() {
        String from = getResendFrom();
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("resend.from no configurado");
        }
        return from;
    }

    private void sendResendRequest(ObjectNode body, String apiKey) throws Exception {
        String url = "https://api.resend.com/messages";
        String json = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            LOG.error("Resend API returned {}: {}", code, resp.body());
            throw new java.io.IOException("Failed to send email via Resend: HTTP " + code);
        }
        LOG.info("Email sent via Resend to {}: HTTP {}", body.get("to"), code);
    }

    @Override
    public byte[] downloadImage(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        try (InputStream in = url.openStream()) {
            return in.readAllBytes();
        }
    }
}
