package cn.keking;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PdfViewerCompatibilityTests {

    @Test
    void shouldLoadCompatibilityModuleBeforePdfJs() throws IOException {
        String viewerHtml = readResource("/static/pdfjs/web/viewer.html");

        assertTrue(viewerHtml.contains("<script src=\"compatibility.mjs\" type=\"module\"></script>"));
        assertTrue(viewerHtml.indexOf("compatibility.mjs") < viewerHtml.indexOf("../build/pdf.mjs"));
    }

    @Test
    void shouldLoadCompatibilityModuleInPdfWorker() throws IOException {
        String workerScript = readResource("/static/pdfjs/build/pdf.worker.mjs");

        assertTrue(workerScript.contains("import \"../web/compatibility.mjs\";"));
    }

    private String readResource(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
