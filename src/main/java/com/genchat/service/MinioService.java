package com.genchat.service;

import com.genchat.config.MinioConfig;
import io.minio.*;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * Upload file to MinIO and return the storage path
     */
    public String upload(MultipartFile file) {
        String bucketName = minioConfig.getBucketName();
        ensureBucketExists(bucketName);

        String originalFilename = file.getOriginalFilename();
        String objectName = UUID.randomUUID() + "/" + originalFilename;

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("File uploaded successfully: {}/{}", bucketName, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("File upload failed: {}", originalFilename, e);
            throw new RuntimeException("File upload failed", e);
        }
    }

    /**
     * Delete file from MinIO
     */
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
            log.info("File deleted successfully: {}", objectName);
        } catch (Exception e) {
            log.error("File deletion failed: {}", objectName, e);
            throw new RuntimeException("File deletion failed", e);
        }
    }

    /**
     * Download file from MinIO and return an InputStream
     */
    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("File download failed: {}", objectName, e);
            throw new RuntimeException("File download failed", e);
        }
    }

    /**
     * Ensure the bucket exists, create it if not
     */
    private void ensureBucketExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
                log.info("Bucket created successfully: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Bucket check/creation failed: {}", bucketName, e);
            throw new RuntimeException("Bucket operation failed", e);
        }
    }
}
