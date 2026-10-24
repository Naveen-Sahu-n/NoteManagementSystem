
package com.notes.pdf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class PdfStorageService {

    private static final long MAX_FILE_SIZE =
            500L * 1024L * 1024L;

    private final Path storageDirectory;

    public PdfStorageService(
            @Value("${notes.storage.directory:storage/pdf}")
            String storageDirectory) {

        this.storageDirectory = Path.of(storageDirectory)
                .toAbsolutePath()
                .normalize();
    }

    public StoredPdf store(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select a non-empty PDF file."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "PDF size must not exceed 500 MB."
            );
        }

        String originalName =
                sanitizeOriginalFilename(file.getOriginalFilename());

        if (originalName == null
                || !originalName.toLowerCase(Locale.ROOT)
                .endsWith(".pdf")) {

            throw new IllegalArgumentException(
                    "Only PDF files are allowed."
            );
        }

        validatePdfSignature(file);

        Files.createDirectories(storageDirectory);

        String storageName = UUID.randomUUID() + ".pdf";
        Path destination = resolveStoredPath(storageName);

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupException) {
                ex.addSuppressed(cleanupException);
            }

            throw ex;
        }

        long actualSize = Files.size(destination);

        if (actualSize == 0 || actualSize > MAX_FILE_SIZE) {
            Files.deleteIfExists(destination);

            throw new IllegalArgumentException(
                    "The PDF must be non-empty and no larger than 500 MB."
            );
        }

        return new StoredPdf(
                storageName,
                originalName,
                actualSize
        );
    }

    public Path resolveStoredPath(String storageName) {

        if (storageName == null
                || !storageName.matches(
                        "[0-9a-fA-F-]{36}\\.pdf")) {

            throw new IllegalArgumentException(
                    "Invalid stored PDF identifier."
            );
        }

        Path resolved = storageDirectory
                .resolve(storageName)
                .normalize();

        if (!storageDirectory.equals(resolved.getParent())) {
            throw new IllegalArgumentException(
                    "Invalid PDF storage path."
            );
        }

        return resolved;
    }

    public void delete(String storageName) throws IOException {

        if (storageName != null) {
            Files.deleteIfExists(resolveStoredPath(storageName));
        }
    }

    /*
     * Temporarily moves a PDF out of its normal storage name before
     * changing its database metadata. This helps avoid leaving a
     * database record pointing to a file that has already been deleted.
     */
    public QuarantinedPdf quarantine(String storageName)
            throws IOException {

        Path original = resolveStoredPath(storageName);

        if (!Files.exists(original)) {
            return null;
        }

        String quarantineName = UUID.randomUUID() + ".pending";
        Path quarantinePath = storageDirectory
                .resolve(quarantineName)
                .normalize();

        if (!storageDirectory.equals(quarantinePath.getParent())) {
            throw new IOException("Invalid quarantine path.");
        }

        try {
            Files.move(
                    original,
                    quarantinePath,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(original, quarantinePath);
        }

        return new QuarantinedPdf(storageName, quarantineName);
    }

    public void restore(QuarantinedPdf quarantinedPdf)
            throws IOException {

        if (quarantinedPdf == null) {
            return;
        }

        Path quarantinePath =
                resolveQuarantinePath(quarantinedPdf.quarantineName());

        Path original =
                resolveStoredPath(quarantinedPdf.storageName());

        if (Files.exists(quarantinePath)) {
            Files.move(quarantinePath, original);
        }
    }

    public void discard(QuarantinedPdf quarantinedPdf)
            throws IOException {

        if (quarantinedPdf != null) {
            Files.deleteIfExists(
                    resolveQuarantinePath(
                            quarantinedPdf.quarantineName()
                    )
            );
        }
    }

    private Path resolveQuarantinePath(String quarantineName) {

        if (quarantineName == null
                || !quarantineName.matches(
                        "[0-9a-fA-F-]{36}\\.pending")) {

            throw new IllegalArgumentException(
                    "Invalid quarantine identifier."
            );
        }

        Path resolved = storageDirectory
                .resolve(quarantineName)
                .normalize();

        if (!storageDirectory.equals(resolved.getParent())) {
            throw new IllegalArgumentException(
                    "Invalid quarantine path."
            );
        }

        return resolved;
    }

    private void validatePdfSignature(MultipartFile file)
            throws IOException {

        byte[] signature = new byte[5];

        try (InputStream input = file.getInputStream()) {

            int totalRead = 0;

            while (totalRead < signature.length) {
                int count = input.read(
                        signature,
                        totalRead,
                        signature.length - totalRead
                );

                if (count == -1) {
                    break;
                }

                totalRead += count;
            }

            if (totalRead != 5
                    || signature[0] != '%'
                    || signature[1] != 'P'
                    || signature[2] != 'D'
                    || signature[3] != 'F'
                    || signature[4] != '-') {

                throw new IllegalArgumentException(
                        "The uploaded file does not have a PDF signature."
                );
            }
        }
    }

    private String sanitizeOriginalFilename(String filename) {

        if (filename == null || filename.isBlank()) {
            return null;
        }

        String normalized = filename.replace('\\', '/');

        String name = normalized.substring(
                normalized.lastIndexOf('/') + 1
        );

        if (name.isBlank()
                || name.contains("..")
                || name.chars().anyMatch(
                        Character::isISOControl)) {

            throw new IllegalArgumentException(
                    "Invalid PDF filename."
            );
        }

        return name;
    }

    public record StoredPdf(
            String storageName,
            String originalFilename,
            long fileSize) {
    }

    public record QuarantinedPdf(
            String storageName,
            String quarantineName) {
    }
}
