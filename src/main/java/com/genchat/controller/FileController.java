package com.genchat.controller;

import com.genchat.application.FileApplication;
import com.genchat.common.Result;
import com.genchat.dto.FileInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping
@RestController("/file")
@Slf4j
@RequiredArgsConstructor
public class FileController {

    private final FileApplication fileApplication;

    @PostMapping("/upload")
    public Result<FileInfo> upload(@RequestParam("file") MultipartFile file) {
        log.debug("Uploading file name: {}, file size: {}", file.getOriginalFilename(), file.getSize());
        try {
            if (file.isEmpty()) {
                return Result.fail("File is not null.");
            }
            return Result.success(fileApplication.upload(file));
        } catch (Exception ex) {
            log.error("Upload file failed.", ex);
            return Result.fail("Upload file failed" + ex.getMessage());
        }
    }
}
