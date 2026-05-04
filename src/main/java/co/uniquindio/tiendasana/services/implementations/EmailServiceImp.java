package co.uniquindio.tiendasana.services.implementations;


import co.uniquindio.tiendasana.dto.EmailDTO;
import co.uniquindio.tiendasana.services.interfaces.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;


@Service
public class EmailServiceImp implements EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailServiceImp.class);

    @Value("${simplejavamail.smtp.host}")
    private String SMTP_HOST;

    @Value("${simplejavamail.smtp.port}")
    private int SMTP_PORT;

    @Value("${simplejavamail.smtp.username}")
    private String SMTP_USERNAME;

    @Value("${simplejavamail.smtp.password}")
    private String SMTP_PASSWORD;

    @Value("${simplejavamail.smtp.transport.strategy:SMTP_TLS}")
    private String SMTP_TRANSPORT_STRATEGY;

    @Value("${simplejavamail.debug:false}")
    private boolean SMTP_DEBUG;

    @Override
    @Async
    public void sendEmail(EmailDTO emailDTO) throws Exception {
        validateSmtpConfig();
        Email email = EmailBuilder.startingBlank()
                .from(SMTP_USERNAME)
                .to(emailDTO.receiver())
                .withSubject(emailDTO.subject())
                //This plain text could be replaced with "withHTMLText"
                .withPlainText(emailDTO.body())
                .buildEmail();

        try (Mailer mailer = buildMailer()) {
            mailer.sendMail(email);
        } catch (Exception e) {
            LOG.error("Fallo enviando correo simple a {}: {}", emailDTO.receiver(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Async
    public void sendEmailHtmlWithAttachment(EmailDTO emailDTO, byte[] qrCodeImage, String qrCodeContentId) throws Exception {
        validateSmtpConfig();
        Email email = EmailBuilder.startingBlank()
                .from(SMTP_USERNAME)
                .to(emailDTO.receiver())
                .withSubject(emailDTO.subject())
                .withHTMLText(emailDTO.body())
                .withEmbeddedImage(qrCodeContentId, qrCodeImage, "image/png") // Adjuntar la imagen con el CID
                .buildEmail();

        try (Mailer mailer = buildMailer()) {

            mailer.sendMail(email);
        } catch (Exception e) {
            LOG.error("Fallo enviando correo HTML a {}: {}", emailDTO.receiver(), e.getMessage(), e);
            throw e;
        }
    }

    private Mailer buildMailer() {
        return MailerBuilder
                .withSMTPServer(SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD)
                .withTransportStrategy(resolveTransportStrategy())
                .withDebugLogging(SMTP_DEBUG)
                .buildMailer();
    }

    private TransportStrategy resolveTransportStrategy() {
        String strategy = SMTP_TRANSPORT_STRATEGY == null ? "SMTP_TLS" : SMTP_TRANSPORT_STRATEGY.trim().toUpperCase();
        return switch (strategy) {
            case "SMTP", "PLAIN_SMTP" -> TransportStrategy.SMTP;
            case "SMTPS", "SMTP_SSL" -> TransportStrategy.SMTPS;
            default -> TransportStrategy.SMTP_TLS;
        };
    }

    private void validateSmtpConfig() {
        if (SMTP_HOST == null || SMTP_HOST.isBlank()) {
            throw new IllegalStateException("SMTP_HOST no configurado");
        }
        if (SMTP_PORT <= 0) {
            throw new IllegalStateException("SMTP_PORT invalido: " + SMTP_PORT);
        }
        if (SMTP_USERNAME == null || SMTP_USERNAME.isBlank()) {
            throw new IllegalStateException("SMTP_USERNAME no configurado");
        }
        if (SMTP_PASSWORD == null || SMTP_PASSWORD.isBlank()) {
            throw new IllegalStateException("SMTP_PASSWORD no configurado");
        }
    }

    @Override
    public byte[] downloadImage(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        try (InputStream in = url.openStream()) {
            return in.readAllBytes();
        }
    }
}
