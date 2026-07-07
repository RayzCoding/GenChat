package com.genchat.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileParserServiceTest {

    private final FileParserService fileParserService = new FileParserService();

    @Test
    void parseXlsxExtractsSheetContent() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Score");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("Alice");
            data.createCell(1).setCellValue(95);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            String text = fileParserService.parse(out.toByteArray(), "sample.xlsx");

            assertTrue(text.contains("Name"));
            assertTrue(text.contains("Alice"));
            assertTrue(text.contains("95"));
        }
    }

    @Test
    void parseXlsExtractsSheetContent() throws Exception {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Product");
            row.createCell(1).setCellValue("Qty");
            Row row2 = sheet.createRow(1);
            row2.createCell(0).setCellValue("Widget");
            row2.createCell(1).setCellValue(42);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            String text = fileParserService.parse(out.toByteArray(), "legacy.xls");

            assertTrue(text.contains("Product"));
            assertTrue(text.contains("Widget"));
            assertTrue(text.contains("42"));
        }
    }
}
