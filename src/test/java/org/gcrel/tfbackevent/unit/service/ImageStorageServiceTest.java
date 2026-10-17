package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.exception.UnsupportedImageTypeException;
import org.gcrel.tfbackevent.event.service.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ImageStorageServiceTest {
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10 };
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };
    private static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path directory;

    private ImageStorageService storage;

    @BeforeEach
    void setUp() {
        // Un subdirectorio que todavía no existe: el servicio debe crearlo.
        storage = new ImageStorageService(directory.resolve("images").toString());
    }

    @Test
    void store_DetectsTheFormatFromTheContentAndIgnoresTheClientName() throws Exception {
        String jpg = storage.store(file("../../etc/passwd.png", "image/png", JPEG));
        String png = storage.store(file("foto.jpg", "image/jpeg", PNG));
        String webp = storage.store(file("foto", "application/octet-stream", WEBP));

        assertTrue(jpg.matches("[0-9a-f-]{36}\\.jpg"));
        assertTrue(png.matches("[0-9a-f-]{36}\\.png"));
        assertTrue(webp.matches("[0-9a-f-]{36}\\.webp"));
        assertArrayEquals(JPEG, Files.readAllBytes(directory.resolve("images").resolve(jpg)));
    }

    @Test
    void store_RejectsSvgAndFilesDisguisedAsImages() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] html = "<html><body>hola</body></html>".getBytes(StandardCharsets.UTF_8);

        assertThrows(UnsupportedImageTypeException.class,
                () -> storage.store(file("logo.svg", "image/svg+xml", svg)));
        assertThrows(UnsupportedImageTypeException.class,
                () -> storage.store(file("foto.png", "image/png", html)));
        assertThrows(UnsupportedImageTypeException.class,
                () -> storage.store(file("corta.jpg", "image/jpeg", new byte[] { (byte) 0xFF })));
    }

    @Test
    void store_RejectsEmptyFiles() {
        assertThrows(BadRequestException.class,
                () -> storage.store(file("vacia.png", "image/png", new byte[0])));
    }

    @Test
    void delete_RemovesTheFileAndToleratesMissingOrNullKeys() throws Exception {
        String key = storage.store(file("foto.png", "image/png", PNG));

        storage.delete(key);

        assertFalse(Files.exists(directory.resolve("images").resolve(key)));
        assertDoesNotThrow(() -> storage.delete(key));
        assertDoesNotThrow(() -> storage.delete(null));
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}
