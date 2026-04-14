package com.genchat.application.tool;

import com.genchat.dto.FileInfo;
import com.genchat.entity.FileStatus;
import com.genchat.service.EmbeddingService;
import com.genchat.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileContentTool {
    private final EmbeddingService embeddingService;
    private final FileService fileService;

    @Tool(description = "Load file contents based on file IDs or perform RAG semantic retrieval. " +
            "If the file is vectorized (embed=1), it uses semantic search to return the relevant fragment," +
            " otherwise it returns the full file content directly.")
    public String loadFileContents(@ToolParam(description = "user question") String question,
                                   @ToolParam(description = "file id") Long fileId) {
        log.info("Loading file contents based on file ID: {}, question: {}", fileId, question);
        if (Objects.isNull(fileId)) {
            return "File id is not null";
        }
        try {
            var fileInfoOptional = fileService.getFileInfoById(fileId);
            if (fileInfoOptional.isEmpty()) {
                return "File is not found, file id: " + fileId;
            }
            var fileInfo = fileInfoOptional.get();
            if (fileInfo.getStatus() != FileStatus.SUCCESS) {
                return "File status is not success, status: " + fileInfo.getStatus();
            }
            var embed = fileInfo.getEmbed();
            if (embed == Boolean.TRUE) {
                return retrieveWithRAG(fileInfo, question);
            }
            return buildResponse(fileInfo, fileInfo.getExtractedText(), null);
        } catch (Exception e) {
            log.error("Error while loading file contents based on file id: {}", fileId);
            return "Error while loading file contents: " + e.getMessage();
        }

    }

    private String retrieveWithRAG(FileInfo fileInfo, String question) {
        if (!StringUtils.hasLength(question)) {
            return buildResponse(fileInfo, "Please provide specific questions for semantic retrieval.", null);
        }
        var results = embeddingService.ragRetrieve(fileInfo.getId(), question);
        if (CollectionUtils.isEmpty(results)) {
            return buildResponse(fileInfo, "No content related to the issue was retrieved.", null);
        }
        return buildResponse(fileInfo, "RAG retrieved.", results);
    }

    private String buildResponse(FileInfo fileInfo, String content, List<String> segments) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== File info ===\n");
        sb.append("file name: ").append(fileInfo.getName()).append("\n");
        sb.append("file type: ").append(fileInfo.getFileType()).append("\n");

        sb.append("\n=== File info ===\n");

        if (segments != null && !segments.isEmpty()) {
            // RAG search result format
            sb.append("Related content: ").append("\n\n");
            for (int i = 0; i < segments.size(); i++) {
                sb.append(segments.get(i)).append("\n\n");
            }
        } else if (content != null) {
            // Load content formats directly
            sb.append(content);
        } else {
            // Tip information
            sb.append("No content to display");
        }

        return sb.toString();
    }
}
