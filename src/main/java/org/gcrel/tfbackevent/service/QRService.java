package org.gcrel.tfbackevent.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.gcrel.tfbackevent.exception.QRGenerationException;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

import javax.imageio.ImageIO;

@Service
public class QRService {
    private static final int QR_WIDTH = 300;
    private static final int QR_HEIGHT = 300;

    public String generateQRBase64(UUID ticketId) {
        try {
            String content = ticketId.toString();

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix =
                    qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT);

            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (WriterException | IOException e) {
            throw new QRGenerationException();
        }
    }
}
