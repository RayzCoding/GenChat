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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    /** Presigned URL expiry for user-facing download links (7 days). */
    public static final int DOWNLOAD_PRESIGN_EXPIRY_SECONDS = 7 * 24 * 3600;

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * Upload file to MinIO and return the storage path
     */
    public String upload(MultipartFile file) {
        try {
            return upload(file.getBytes(), file.getOriginalFilename(), file.getContentType());
        } catch (Exception e) {
            log.error("File upload failed: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("File upload failed", e);
        }
    }

    /**
     * Upload raw file bytes to MinIO and return the storage path
     */
    public String upload(byte[] data, String originalFilename, String contentType) {
        String bucketName = minioConfig.getBucketName();
        ensureBucketExists(bucketName);

        String objectName = UUID.randomUUID() + "/" + originalFilename;

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
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
     * Query strings from presigned URLs are ignored.
     */
    public String extractObjectNameFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String bucketName = minioConfig.getBucketName();
        String pathPrefix = "/" + bucketName + "/";

        try {
            URI uri = URI.create(url.strip());
            String path = uri.getPath();
            if (path != null && path.contains(pathPrefix)) {
                int idx = path.indexOf(pathPrefix);
                return decodeObjectName(path.substring(idx + pathPrefix.length()));
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to legacy parsing
        }

        int bucketIndex = url.indexOf(pathPrefix);
        if (bucketIndex == -1) {
            return null;
        }
        String objectName = url.substring(bucketIndex + pathPrefix.length());
        int queryIndex = objectName.indexOf('?');
        if (queryIndex >= 0) {
            objectName = objectName.substring(0, queryIndex);
        }
        int hashIndex = objectName.indexOf('#');
        if (hashIndex >= 0) {
            objectName = objectName.substring(0, hashIndex);
        }
        return decodeObjectName(objectName);
    }

    private static String decodeObjectName(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return objectName;
        }
        try {
            String decoded = URLDecoder.decode(objectName, StandardCharsets.UTF_8);
            int queryIndex = decoded.indexOf('?');
            if (queryIndex >= 0) {
                decoded = decoded.substring(0, queryIndex);
            }
            int hashIndex = decoded.indexOf('#');
            if (hashIndex >= 0) {
                decoded = decoded.substring(0, hashIndex);
            }
            return decoded;
        } catch (Exception ignored) {
            return objectName;
        }
    }

    /**
     * Convert a MinIO URL to a presigned URL
     */
    public String toPresignedUrl(String minioUrl) {
        return toPresignedUrl(minioUrl, 3600);
    }

    /**
     * Convert a MinIO URL to a presigned URL with custom expiry
     */
    public String toPresignedUrl(String minioUrl, int expirySeconds) {
        String objectName = extractObjectNameFromUrl(minioUrl);
        if (objectName == null) {
            log.warn("Cannot extract object name from URL: {}", minioUrl);
            return minioUrl;
        }
        return getPresignedUrl(objectName, expirySeconds);
    }
}
