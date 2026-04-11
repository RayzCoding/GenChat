package com.genchat.service;

import com.genchat.common.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * File content parser service
 */
@Slf4j
@Service
public class FileParserService {

    private static final int MAX_TEXT_LENGTH = 10000;

    /**
     * Parse file content from MultipartFile, dispatch by file type
     */
    public String parse(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String fileType = FileUtil.getFileType(filename);

        try (InputStream is = file.getInputStream()) {
            String text = switch (fileType) {
                case "pdf" -> parsePdf(is);
                case "doc" -> parseDoc(is);
                case "docx" -> parseDocx(is);
                default -> {
                    if (FileUtil.isTextFile(filename)) {
                        yield parseText(is);
                    }
                    throw new RuntimeException("Unsupported file type: " + fileType);
                }
            };
            return truncate(text);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse file: {}", filename, e);
            throw new RuntimeException("Failed to parse file", e);
        }
    }

    /**
     * Parse PDF content using Apache PDFBox
     */
    private String parsePdf(InputStream is) throws Exception {
        try (PDDocument document = Loader.loadPDF(is.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Parse legacy .doc content using Apache POI HWPFDocument
     */
    private String parseDoc(InputStream is) throws Exception {
        try (HWPFDocument document = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * Parse .docx content using Apache POI XWPFDocument
     */
    private String parseDocx(InputStream is) throws Exception {
        try (XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * Read plain text content directly
     */
    private String parseText(InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Truncate text to MAX_TEXT_LENGTH if it exceeds the limit
     */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            log.warn("Parsed text exceeds max length ({}), truncating to {} characters", text.length(), MAX_TEXT_LENGTH);
            return text.substring(0, MAX_TEXT_LENGTH);
        }
        return text;
    }
}
