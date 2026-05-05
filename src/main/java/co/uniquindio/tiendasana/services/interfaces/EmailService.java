package co.uniquindio.tiendasana.services.interfaces;

import co.uniquindio.tiendasana.dto.EmailDTO;

import java.io.IOException;

public interface EmailService {

    /**
     * Sends an email
     * @param emailDTO Email data
     * @throws Exception
     */
    void sendEmail(EmailDTO emailDTO) throws Exception;

    /**
     * Sends an email synchronously (useful for internal/local test endpoints).
     * @param emailDTO Email data
     * @throws Exception
     */
    void sendEmailNow(EmailDTO emailDTO) throws Exception;

    /**
     * Sends an email with html content
     * @param emailDTO Email data
     * @param qrCodeImage Qr code image data
     * @param qrCodeContentId
     * @throws Exception
     */
    void sendEmailHtmlWithAttachment(EmailDTO emailDTO, byte[] qrCodeImage, String qrCodeContentId) throws Exception;

    /**
     * Sends an email with html content synchronously (useful for internal/local test endpoints).
     * @param emailDTO Email data
     * @param qrCodeImage Qr code image data
     * @param qrCodeContentId
     * @throws Exception
     */
    void sendEmailHtmlWithAttachmentNow(EmailDTO emailDTO, byte[] qrCodeImage, String qrCodeContentId) throws Exception;

    /**
     * Obtains ann image
     * @param imageUrl Image irl
     * @return The image data
     * @throws IOException
     */
    byte[] downloadImage(String imageUrl) throws IOException;
}
