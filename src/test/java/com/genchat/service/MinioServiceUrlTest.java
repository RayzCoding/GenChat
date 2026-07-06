package com.genchat.service;

import com.genchat.config.MinioConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioServiceUrlTest {

    @Mock
    private MinioConfig minioConfig;

    private MinioService minioService;

    @BeforeEach
    void setUp() {
        minioService = new MinioService(null, minioConfig);
        when(minioConfig.getBucketName()).thenReturn("rag-test2");
    }

    @Test
    void extractObjectNameFromPlainUrl() {
        var url = "http://127.0.0.1:9000/rag-test2/ppt/uuid/ppt_5.pptx";

        assertEquals("ppt/uuid/ppt_5.pptx", minioService.extractObjectNameFromUrl(url));
    }

    @Test
    void extractObjectNameStripsPresignedQueryParams() {
        var url = "http://127.0.0.1:9000/rag-test2/ppt/uuid/ppt_5.pptx"
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=minioadmin%2F20260706%2Fus-east-1%2Fs3%2Faws4_request"
                + "&X-Amz-Date=20260706T134146Z"
                + "&X-Amz-Expires=604800"
                + "&X-Amz-SignedHeaders=host"
                + "&X-Amz-Signature=fc0beb114851d5ff251fd278e79515bde6cf47d9974d8f1b1a7790573e082801";

        assertEquals("ppt/uuid/ppt_5.pptx", minioService.extractObjectNameFromUrl(url));
    }

    @Test
    void extractObjectNameStripsEncodedQueryInLegacyPath() {
        var url = "http://127.0.0.1:9000/rag-test2/ppt/uuid/ppt_5.pptx%3FX-Amz-Algorithm%3DAWS4-HMAC-SHA256";

        assertEquals("ppt/uuid/ppt_5.pptx", minioService.extractObjectNameFromUrl(url));
    }
}
