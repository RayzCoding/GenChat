package com.genchat.service;

import com.genchat.config.MinioConfig;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
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

    public String uploadFile(String objectName, byte[] content, String contentType) throws Exception {
        try (InputStream stream = new ByteArrayInputStream(content)) {
            var bucketName = minioConfig.getBucketName();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(stream, content.length, -1)
                            .contentType(contentType)
                            .build()
            );

            var endpoint = minioConfig.getEndpoint();
            String cleanEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            return String.format("%s/%s/%s", cleanEndpoint, bucketName, objectName);
        }
    }

    /**
     * Generate a presigned URL for downloading an object
     * @param objectName The object path in MinIO
     * @param expirySeconds URL expiry time in seconds (default 1 hour if not specified)
     * @return Presigned URL that can be used for public download
     */
    public String getPresignedUrl(String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: {}", objectName, e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    /**
     * Generate a presigned URL with default 1 hour expiry
     */
    public String getPresignedUrl(String objectName) {
        return getPresignedUrl(objectName, 3600);
    }

    /**
     * Extract object name from MinIO URL
     * e.g., "http://127.0.0.1:9000/bucket/path/to/file.png" -> "path/to/file.png"
     */
    public String extractObjectNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String bucketName = minioConfig.getBucketName();
        // Find bucket name position in URL
        int bucketIndex = url.indexOf("/" + bucketName + "/");
        if (bucketIndex == -1) {
            return null;
        }
        return url.substring(bucketIndex + bucketName.length() + 2);
    }

    /**
     * Convert a MinIO URL to a presigned URL
     */
    public String toPresignedUrl(String minioUrl) {
        String objectName = extractObjectNameFromUrl(minioUrl);
        if (objectName == null) {
            log.warn("Cannot extract object name from URL: {}", minioUrl);
            return minioUrl; // Return original URL if parsing fails
        }
        return getPresignedUrl(objectName);
    }
}
