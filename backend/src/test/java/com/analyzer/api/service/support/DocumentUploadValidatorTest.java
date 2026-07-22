package com.analyzer.api.service.support;

import com.analyzer.api.exception.validation.InvalidDocumentUploadException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentUploadValidatorTest {

    @Test
    void acceptsPdfWhenExtensionMimeAndSignatureMatch() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII));

        assertThatCode(() -> DocumentUploadValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedExtensionBeforeStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", new byte[] {0x4D, 0x5A, 0x01});

        assertCode(file, "DOCUMENT_FILE_TYPE_NOT_ALLOWED");
    }

    @Test
    void rejectsRenamedExecutableWithPdfExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.pdf", "application/pdf", new byte[] {0x4D, 0x5A, 0x01});

        assertCode(file, "DOCUMENT_FILE_CONTENT_INVALID");
    }

    @Test
    void rejectsMimeThatDoesNotMatchExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "image/png", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        assertCode(file, "DOCUMENT_FILE_MIME_MISMATCH");
    }

    @Test
    void acceptsDocxOnlyWhenRequiredWordEntriesExist() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes.toByteArray());

        assertThatCode(() -> DocumentUploadValidator.validate(file)).doesNotThrowAnyException();
    }

    private void assertCode(MockMultipartFile file, String expectedCode) {
        assertThatThrownBy(() -> DocumentUploadValidator.validate(file))
                .isInstanceOf(InvalidDocumentUploadException.class)
                .extracting(error -> ((InvalidDocumentUploadException) error).getErrorCode())
                .isEqualTo(expectedCode);
    }
}
