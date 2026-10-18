
package com.pbl.propertyauth.service;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentService {

    public String sha256(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(bytes);
        return HexFormat.of().formatHex(hash);
    }

    public String extractText(MultipartFile file) throws Exception {
        String name = Objects.toString(
                file.getOriginalFilename(), "")
                .toLowerCase(Locale.ROOT);

        byte[] bytes = file.getBytes();

        if (name.endsWith(".pdf")) {
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                String text = new PDFTextStripper().getText(pdf);

                if (text != null
                        && text.replaceAll("\\s", "").length() > 30) {
                    return text;
                }

                Tesseract ocr = createOCR();
                PDFRenderer renderer = new PDFRenderer(pdf);
                StringBuilder result = new StringBuilder();

                for (int i = 0;
                     i < Math.min(pdf.getNumberOfPages(), 20); i++) {
                    BufferedImage image =
                            renderer.renderImageWithDPI(i, 200);
                    result.append(ocr.doOCR(image)).append("\n");
                }

                return result.toString();
            }
        }

        if (name.matches(".*\\.(png|jpg|jpeg|tif|tiff)$")) {
            BufferedImage image = ImageIO.read(
                    new ByteArrayInputStream(bytes));

            if (image == null) {
                throw new IllegalArgumentException(
                        "Invalid or unsupported image file.");
            }

            return createOCR().doOCR(image);
        }

        throw new IllegalArgumentException(
                "Upload a PDF, JPG, PNG or TIFF file.");
    }

    private Tesseract createOCR() {
    Tesseract tesseract = new Tesseract();

    tesseract.setDatapath(
        "C:\\Program Files\\Tesseract-OCR\\tessdata"
    );

    tesseract.setLanguage("eng");

    return tesseract;
}

    public Map<String, String> extractFields(String text) {
        Map<String, String> fields = new LinkedHashMap<>();

        fields.put("ownerName", capture(text,
                "(?im)\\b(?:owner(?:\\s+name)?|name of owner)"
                + "\\s*[:#-]\\s*([^\\r\\n]+)"));

        fields.put("propertyId", capture(text,
                "(?im)\\b(?:property\\s*(?:id|no\\.?|number)"
                + "|survey\\s*(?:no\\.?|number))"
                + "\\s*[:#-]\\s*([^\\r\\n]+)"));

        fields.put("registrationNumber", capture(text,
                "(?im)\\b(?:registration\\s*(?:no\\.?|number|id)"
                + "|document\\s*(?:no\\.?|number))"
                + "\\s*[:#-]\\s*([^\\r\\n]+)"));

        fields.put("address", capture(text,
                "(?im)\\b(?:property\\s+address|address|location)"
                + "\\s*[:#-]\\s*([^\\r\\n]+)"));

        return fields;
    }

    private String capture(String text, String regex) {
        if (text == null) {
            return "";
        }

        Matcher matcher = Pattern.compile(regex).matcher(text);

        return matcher.find()
                ? matcher.group(1).trim()
                    .replaceAll("\\s{2,}", " ")
                : "";
    }

    public List<String> analyze(
            MultipartFile file,
            String text,
            Map<String, String> fields) throws Exception {

        List<String> findings = new ArrayList<>();

        // 1. Check the file extension against its signature.
        String name = Objects.toString(
                file.getOriginalFilename(), "")
                .toLowerCase(Locale.ROOT);

        byte[] bytes = file.getBytes();

        boolean validFormat = hasValidSignature(name, bytes);

        if (!validFormat) {
            findings.add(
                    "SUSPICIOUS: File content does not match "
                    + "a supported format or its file extension.");
        }

        // 2. Check extracted text.
        if (text == null || text.isBlank()) {
            findings.add(
                    "REVIEW REQUIRED: No readable text was extracted.");
        } else {
            if (text.replaceAll("\\s", "").length() < 100) {
                findings.add(
                        "REVIEW REQUIRED: Very little text was extracted.");
            }

            String lower = text.toLowerCase(Locale.ROOT);

            for (String word : new String[]{
                    "sample", "specimen", "draft", "void",
                    "cancelled", "not for legal use",
                    "fake", "forged"}) {
                if (lower.contains(word)) {
                    findings.add(
                            "SUSPICIOUS: Document contains the term '"
                            + word + "'.");
                }
            }

            // 3. Check for conflicting owner names.
            Matcher matcher = Pattern.compile(
                    "(?im)^\\s*(?:name of owner|owner name|owner)"
                    + "\\s*[:#-]\\s*([^\\r\\n]+)")
                    .matcher(text);

            Set<String> names = new HashSet<>();

            while (matcher.find()) {
                names.add(matcher.group(1).trim()
                        .replaceAll("\\s+", " ")
                        .toLowerCase(Locale.ROOT));
            }

            if (names.size() > 1) {
                findings.add(
                        "REVIEW REQUIRED: Owner names differ "
                        + "across document sections.");
            }
        }

        // 4. Check required fields.
        Map<String, String> labels = Map.of(
                "ownerName", "Owner name",
                "propertyId", "Property/survey number",
                "registrationNumber", "Registration number",
                "address", "Property address");

        for (Map.Entry<String, String> entry : labels.entrySet()) {
            String value = fields.get(entry.getKey());

            if (value == null || value.isBlank()) {
                findings.add(
                        "REVIEW REQUIRED: Missing "
                        + entry.getValue() + ".");
            }
        }

        // 5. Check basic identifier formatting.
        checkPattern(fields, "propertyId",
                "[A-Za-z0-9/-]{1,40}",
                "Property/survey number has an unusual format.",
                findings);

        checkPattern(fields, "registrationNumber",
                "[A-Za-z0-9/-]{1,50}",
                "Registration number has an unusual format.",
                findings);

        if (findings.isEmpty()) {
            findings.add(
                    "NO ISSUES DETECTED: Basic format and field "
                    + "checks passed.");
        }

        findings.add(
                "LIMITATION: These checks cannot prove legal ownership "
                + "or reliably detect all document edits. Compare with "
                + "official records and manually review suspicious areas.");

        return findings;
    }

    private boolean hasValidSignature(String name, byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }

        if (name.endsWith(".pdf")) {
            return bytes.length >= 5
                    && bytes[0] == '%'
                    && bytes[1] == 'P'
                    && bytes[2] == 'D'
                    && bytes[3] == 'F'
                    && bytes[4] == '-';
        }

        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
        }

        if (name.endsWith(".png")) {
            return bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4E
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0D
                    && bytes[5] == 0x0A
                    && bytes[6] == 0x1A
                    && bytes[7] == 0x0A;
        }

        if (name.endsWith(".tif") || name.endsWith(".tiff")) {
            return (bytes[0] == 0x49 && bytes[1] == 0x49
                    && bytes[2] == 0x2A && bytes[3] == 0x00)
                    || (bytes[0] == 0x4D && bytes[1] == 0x4D
                    && bytes[2] == 0x00 && bytes[3] == 0x2A)
                    || (bytes[0] == 0x49 && bytes[1] == 0x49
                    && bytes[2] == 0x2B && bytes[3] == 0x00)
                    || (bytes[0] == 0x4D && bytes[1] == 0x4D
                    && bytes[2] == 0x00 && bytes[3] == 0x2B);
        }

        return false;
    }

    private void checkPattern(
            Map<String, String> fields,
            String key,
            String regex,
            String message,
            List<String> findings) {

        String value = fields.get(key);

        if (value != null && !value.isBlank()
                && !value.matches(regex)) {
            findings.add("REVIEW REQUIRED: " + message);
        }
    }

    public String resultStatus(
            List<String> findings,
            Map<String, String> fields) {

        if (findings.stream().anyMatch(
                f -> f.startsWith("SUSPICIOUS:"))) {
            return "SUSPICIOUS";
        }

        if (findings.stream().anyMatch(
                f -> f.startsWith("REVIEW REQUIRED:"))) {
            return "REVIEW REQUIRED";
        }

        return "NO ISSUES DETECTED";
    }
}

