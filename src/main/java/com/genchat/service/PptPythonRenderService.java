package com.genchat.service;

import com.genchat.dto.AiPptInst;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PptPythonRenderService {
    private final AiPptTemplateService templateService;
    private final MinioService minioService;
    private final  static String PYTHON_SCRIPT_PATH="../resources/python/render_ppt.py";

    public String renderPpt(AiPptInst inst, String pptSchema) throws Exception {

        log.info("Starting PPT rendering: instId={}", inst.getId());

        // ---------- Get template ----------
        var templateOptional = templateService.getByTemplateCode(inst.getTemplateCode());
        if (templateOptional.isEmpty()) {
            throw new RuntimeException("Template not found: " + inst.getTemplateCode());
        }
        var template = templateOptional.get();

        String templateFilePath = template.getFilePath();
        String outputDir = getOutputDir();

        String outputFileName = "ppt_" + inst.getId() + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".pptx";

        String outputFilePath = outputDir + File.separator + outputFileName;

        File templateFile = new File(templateFilePath);
        if (!templateFile.exists()) {
            throw new RuntimeException("Template file not found: " + templateFilePath);
        }

        // ---------- Build command ----------
        List<String> command = List.of(
                "python",
                PYTHON_SCRIPT_PATH,
                "--template", templateFilePath,
                "--output", outputFilePath
        );

        log.info("Executing Python command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();

        env.put("PYTHONIOENCODING", "utf-8");

        // ---------- Handle JSON passing ----------
        // Windows environment variable length is limited (32KB), large JSON will fail
        // Auto-write to temp file if exceeds 20KB
        if (pptSchema.length() > 20000) {

            Path tempFile = Files.createTempFile("ppt_schema_", ".json");
            Files.writeString(tempFile, pptSchema, StandardOpenOption.TRUNCATE_EXISTING);

            env.put("PPT_SCHEMA_FILE", tempFile.toAbsolutePath().toString());
            log.info("JSON too large, using temp file: {}", tempFile);

        } else {
            env.put("PPT_SCHEMA", pptSchema);
        }

        // ---------- Start ----------
        Process process = pb.start();

        // ---------- Read output ----------
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("Python output: {}", line);
            }
        }

        // ---------- Wait (max 5 minutes) ----------
        long timeoutMs = 5 * 60 * 1000L;
        long startTime = System.currentTimeMillis();
        boolean finished = false;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                int exitCode = process.exitValue();
                // If exit code is available, process has finished
                finished = true;
                if (exitCode != 0) {
                    log.error("Python execution failed: {}", output);
                    throw new RuntimeException("Python script execution failed:\n" + output);
                }
                break;
            } catch (IllegalThreadStateException e) {
                // Process still running, continue waiting
                Thread.sleep(1000);
            }
        }

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Python execution timed out");
        }

        int exitCode = process.exitValue();

        if (exitCode != 0) {
            log.error("Python execution failed: {}", output);
            throw new RuntimeException("Python script execution failed:\n" + output);
        }

        // ---------- Check output ----------
        File outputFile = new File(outputFilePath);
        if (!outputFile.exists()) {
            throw new RuntimeException("PPT not generated: " + outputFilePath);
        }

        // ---------- Upload to MinIO ----------
        log.info("PPT generated successfully, starting upload to MinIO");
        byte[] fileBytes = Files.readAllBytes(outputFile.toPath());

        // Build MinIO object name: ppt/{conversationId}/{filename}
        String objectName = "ppt/" + inst.getConversationId() + "/" + outputFileName;

        String fileUrl = minioService.uploadFile(objectName, fileBytes, "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        log.info("PPT uploaded to MinIO: {}", fileUrl);

        // ---------- Delete local file ----------
        try {
            Files.deleteIfExists(outputFile.toPath());
            log.info("Local PPT file deleted: {}", outputFilePath);
        } catch (Exception e) {
            log.warn("Failed to delete local file: {}", outputFilePath, e);
        }

        return fileUrl;
    }

    /**
     * Get output directory (for temporary storage)
     */
    private String getOutputDir() {
        String projectRoot = System.getProperty("user.dir");
        String outputDir = projectRoot + File.separator + "output" + File.separator + "ppt";
        try {
            Path path = Paths.get(outputDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            log.error("Failed to create output directory: {}", outputDir, e);
        }
        return outputDir;
    }
}
