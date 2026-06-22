package com.genchat.controller;

import com.genchat.application.FileApplication;
import com.genchat.common.Result;
import com.genchat.dto.FileInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequestMapping("/file")
@RestController
@Slf4j
@RequiredArgsConstructor
public class FileController {

    private final FileApplication fileApplication;

    @GetMapping
    public Result<List<FileInfo>> list() {
        log.debug("Listing files");
        return Result.success(fileApplication.listFiles());
    }

    @PostMapping("/upload")
    public Result<FileInfo> upload(@RequestParam("file") MultipartFile file) {
        log.debug("Uploading file name: {}, file size: {}", file.getOriginalFilename(), file.getSize());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty.");
        }
        return Result.success(fileApplication.upload(file));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        log.debug("Deleting file: {}", id);
        fileApplication.deleteFileById(id);
        return Result.success();
    }
}
