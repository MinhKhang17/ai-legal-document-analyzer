package com.analyzer.api.util;

import com.analyzer.api.exception.validation.InvalidDocumentUploadException;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DocumentUploadValidator {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Map<String, Set<String>> ALLOWED_MIME_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "doc", Set.of("application/msword", "application/vnd.ms-word"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    private static final byte[] PDF_MAGIC = { 0x25, 0x50, 0x44, 0x46, 0x2D };
    private static final byte[] OLE_MAGIC = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };

    private DocumentUploadValidator() {
    }

    public static void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw invalid("DOCUMENT_FILE_EMPTY", "The uploaded document must not be empty");
        }
        String fileName = file.getOriginalFilename();
        String extension = extension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw invalid("DOCUMENT_FILE_TYPE_NOT_ALLOWED", "Only PDF, DOC, and DOCX documents are supported");
        }

        String declaredMime = normalizeMime(file.getContentType());
        if (!declaredMime.isEmpty() && !"application/octet-stream".equals(declaredMime)
                && !ALLOWED_MIME_TYPES.get(extension).contains(declaredMime)) {
            throw invalid("DOCUMENT_FILE_MIME_MISMATCH", "The declared file type does not match its extension");
        }

        byte[] bytes = file.getBytes();
        boolean validContent = switch (extension) {
            case "pdf" -> startsWith(bytes, PDF_MAGIC);
            case "doc" -> isLegacyWord(bytes);
            case "docx" -> isWordOpenXml(bytes);
            default -> false;
        };
        if (!validContent) {
            throw invalid("DOCUMENT_FILE_CONTENT_INVALID",
                    "The file content is invalid or does not match the PDF, DOC, or DOCX extension");
        }
    }

    private static String extension(String fileName) {
        if (fileName == null || fileName.isBlank())
            return "";
        String normalized = fileName.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('.');
        return separator < 0 || separator == normalized.length() - 1 ? "" : normalized.substring(separator + 1);
    }

    private static String normalizeMime(String mime) {
        if (mime == null)
            return "";
        int parameters = mime.indexOf(';');
        return (parameters >= 0 ? mime.substring(0, parameters) : mime).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isWordOpenXml(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 0x50 || bytes[1] != 0x4B)
            return false;
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entries++ < 10_000) {
                String name = entry.getName().replace('\\', '/');
                if ("[Content_Types].xml".equals(name))
                    hasContentTypes = true;
                if ("word/document.xml".equals(name))
                    hasWordDocument = true;
                if (hasContentTypes && hasWordDocument)
                    return true;
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    private static boolean isLegacyWord(byte[] bytes) {
        if (!startsWith(bytes, OLE_MAGIC))
            return false;
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
            return fileSystem.getRoot().hasEntry("WordDocument");
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length)
            return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index])
                return false;
        }
        return true;
    }

    private static InvalidDocumentUploadException invalid(String code, String message) {
        return new InvalidDocumentUploadException(code, message);
    }
}
