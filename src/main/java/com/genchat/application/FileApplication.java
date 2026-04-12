package com.genchat.application;

import com.genchat.common.FileUtil;
import com.genchat.dto.FileInfo;
import com.genchat.entity.FileStatus;
import com.genchat.service.FileParserService;
import com.genchat.service.FileService;
import com.genchat.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileApplication {
    private final FileService fileService;
    private final MinioService minioService;
    private final FileParserService fileParserService;
    private final OpenAiChatModel multimodalChatModel;

    @Transactional(rollbackFor = Exception.class)
    public FileInfo upload(MultipartFile file) {
        var originalFilename = file.getOriginalFilename();
        log.info("Start processing file name: {}, file size: {}", originalFilename, file.getSize());
        var fileInfo = FileInfo.initFile(file);
        fileInfo = fileService.saveFileInfo(fileInfo);
        try {
            var minioPath = minioService.upload(file);
            fileInfo.setPath(minioPath);
            fileInfo.setStatus(FileStatus.SUCCESS);
            fileService.updateFileInfo(fileInfo);

            if (FileUtil.isTextFile(originalFilename)) {
                try {
                    var extractedText = fileParserService.parse(file);
                    fileInfo.setExtractedText(extractedText);
                    fileService.updateFileInfo(fileInfo);
                    log.info("File parsed successfully, file id: {}", fileInfo.getId());
                    if (FileUtil.isLargeTextFile(extractedText)) {
                        log.info("Start handling large text file, file id: {}", fileInfo.getId());
                        try {
                            fileInfo.setEmbed(true);
                            fileService.updateFileInfo(fileInfo);
                            log.info("Large File embedding successfully, file id: {}", fileInfo.getId());
                        } catch (Exception e) {
                            log.error("Error while handling large text file, file id: {}", fileInfo.getId(), e);
                        }
                    }
                } catch (Exception e) {
                    log.error("File parsing failed, file id:{}, error: {}", fileInfo.getId(), e.getMessage());
                    fileInfo.setStatus(FileStatus.FAILED);
                    fileService.updateFileInfo(fileInfo);
                    throw new RuntimeException("File Pase failed:" + e.getMessage(), e);
                }
                return fileInfo;
            }

            if (FileUtil.isImageFile(originalFilename)) {
                try {
                    var extractedText = image2Text(file);
                    fileInfo.setExtractedText(extractedText);
                    fileService.updateFileInfo(fileInfo);
                    log.info("Image file parsed successfully, file id: {}", fileInfo.getId());
                } catch (Exception e) {
                    log.error("Error while handling image file, file id: {}, error: {}", fileInfo.getId(), e.getMessage());
                    fileInfo.setStatus(FileStatus.FAILED);
                    fileService.updateFileInfo(fileInfo);
                    throw new RuntimeException("File Pase failed:" + e.getMessage(), e);
                }
                return fileInfo;
            }

            log.info("File is not image and text, file name:{}",  originalFilename);
            return fileInfo;
        } catch (Exception e) {
            log.error("Error while processing file,file name:{} error: {}", originalFilename, e.getMessage());
            var fileInfoOptional = fileService.getFileInfoById(fileInfo.getId());
            if (fileInfoOptional.isPresent()) {
                fileInfo.setStatus(FileStatus.FAILED);
                fileService.updateFileInfo(fileInfo);
            }
            throw new RuntimeException("File Pase failed:" + e.getMessage(), e);
        }

    }

    private String image2Text(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] imageBytes = IOUtils.toByteArray(inputStream);

            if (imageBytes == null || imageBytes.length == 0) {
                throw new RuntimeException("The image file content is empty.");
            }

            ByteArrayResource imageResource = new ByteArrayResource(imageBytes);
            var userMessage = UserMessage.builder()
                    .text("Please describe the content of this image, including scenes, objects, layouts, colors, and text information, " +
                            "and output the plain text description directly, without unnecessary explanations.")
                    .media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageResource)))
                    .build();
            var response = multimodalChatModel.call(new Prompt(List.of(userMessage)));
            String resp = response.getResult().getOutput().getText();

            if (resp == null || resp.trim().isEmpty()) {
                return "[The image content is not recognized]";
            }
            return resp.trim();
        } catch (Exception e) {
            log.error("Image recognition is abnormal", e);
            throw new RuntimeException("Image recognition failed: " + e.getMessage(), e);
        }
    }
}
