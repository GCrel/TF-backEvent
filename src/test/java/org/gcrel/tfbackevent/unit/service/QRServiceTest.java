package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mockConstruction;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;

import org.gcrel.tfbackevent.exception.QRGenerationException;
import org.gcrel.tfbackevent.service.QRService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class QRServiceTest {
    private QRService qrService;

    @BeforeEach
    void setUp() {
        qrService = new QRService();
    }

    @Test
    void generateQRBase64_WhenValidUUID_ReturnsValidBase64String() {
        UUID ticketId = UUID.randomUUID();

        String result = qrService.generateQRBase64(ticketId);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        assertDoesNotThrow(
                () -> Base64.getDecoder().decode(result),
                "El string devuelto debe ser decodificable en Base64");
    }

    @Test
    void generateQRBase64_WhenWriterExceptionOcurs_ThrowsQRGenerationException() {
        UUID ticketId = UUID.randomUUID();

        try (MockedConstruction<QRCodeWriter> mockedWriter =
                mockConstruction(
                        QRCodeWriter.class,
                        (mock, context) -> {
                            when(mock.encode(anyString(), any(), anyInt(), anyInt()))
                                    .thenThrow(new WriterException("Error simulado al generar QR"));
                        })) {

            assertThrows(QRGenerationException.class, () -> qrService.generateQRBase64(ticketId));
        }
    }
}
