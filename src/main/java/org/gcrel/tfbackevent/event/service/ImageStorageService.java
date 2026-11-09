package org.gcrel.tfbackevent.event.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.exception.ImageStorageException;
import org.gcrel.tfbackevent.event.exception.UnsupportedImageTypeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

// Guarda las imágenes en disco; Nginx las sirve desde el mismo volumen en /api/v1/images/{key}.
@Slf4j
@Service
public class ImageStorageService {
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private final Path directory;

    public ImageStorageService(@Value("${storage.images.path}") String directory) {
        this.directory = Path.of(directory);
    }

    // El nombre lo genera el servidor y la extensión sale del contenido real: el nombre
    // del cliente nunca llega al disco (sin path traversal, sin pisar archivos ajenos).
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("La imagen está vacía.");
        }
        String key = UUID.randomUUID() + "." + detectExtension(file);
        try {
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(key));
        } catch (IOException e) {
            log.error("No se pudo guardar la imagen {}", key, e);
            throw new ImageStorageException();
        }
        return key;
    }

    // Un fallo al borrar solo deja un archivo huérfano; no debe romper la operación.
    public void delete(String key) {
        if (key == null) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve(key));
        } catch (IOException e) {
            log.warn("No se pudo borrar la imagen {}", key, e);
        }
    }

    private String detectExtension(MultipartFile file) {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        } catch (IOException e) {
            throw new ImageStorageException();
        }
        if (startsWith(header, JPEG, 0)) {
            return "jpg";
        }
        if (startsWith(header, PNG, 0)) {
            return "png";
        }
        if (startsWith(header, RIFF, 0) && startsWith(header, WEBP, 8)) {
            return "webp";
        }
        throw new UnsupportedImageTypeException();
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
