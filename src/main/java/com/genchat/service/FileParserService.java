package com.genchat.service;

import com.genchat.common.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
        try {
            return parse(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            log.error("Failed to read uploaded file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to parse file", e);
        }
    }

    /**
     * Parse file content from raw bytes, dispatch by file type
     */
    public String parse(byte[] data, String filename) {
        String fileType = FileUtil.getFileType(filename);

        try (InputStream is = new ByteArrayInputStream(data)) {
            String text = switch (fileType) {
                case "pdf" -> parsePdf(is);
                case "doc" -> parseDoc(is);
                case "docx" -> parseDocx(is);
                case "xlsx", "xls" -> parseExcel(is);
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
     * Parse Excel (.xlsx / .xls) content using Apache POI
     */
    private String parseExcel(InputStream is) throws Exception {
        DataFormatter formatter = new DataFormatter();
        StringBuilder sb = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(is)) {
            int sheetCount = workbook.getNumberOfSheets();
            for (int sheetIndex = 0; sheetIndex < sheetCount; sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (sheet == null) {
                    continue;
                }
                if (sheetCount > 1) {
                    sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");
                }
                for (Row row : sheet) {
                    if (row == null) {
                        continue;
                    }
                    List<String> cells = new ArrayList<>();
                    int lastCellNum = row.getLastCellNum();
                    for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
                        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        cells.add(cell == null ? "" : formatter.formatCellValue(cell));
                    }
                    if (cells.stream().anyMatch(StringUtils::hasText)) {
                        sb.append(String.join("\t", cells)).append('\n');
                    }
                }
                if (sheetIndex < sheetCount - 1) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString().trim();
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
