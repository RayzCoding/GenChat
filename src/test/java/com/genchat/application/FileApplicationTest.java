package com.genchat.application;

import com.genchat.common.ResourceNotFoundException;
import com.genchat.dto.FileInfo;
import com.genchat.entity.FileStatus;
import com.genchat.service.EmbeddingService;
import com.genchat.service.FileParserService;
import com.genchat.service.FileService;
import com.genchat.service.MinioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiChatModel;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileApplicationTest {

    @Mock
    private FileService fileService;
    @Mock
    private MinioService minioService;
    @Mock
    private FileParserService fileParserService;
    @Mock
    private OpenAiChatModel multimodalChatModel;
    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private FileApplication fileApplication;

    @Test
    void deleteFileByIdThrowsWhenFileMissing() {
        when(fileService.getFileInfoById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> fileApplication.deleteFileById(99L));

        verify(minioService, never()).delete(org.mockito.ArgumentMatchers.anyString());
        verify(embeddingService, never()).deleteByFileId(org.mockito.ArgumentMatchers.anyLong());
        verify(fileService, never()).deleteFileInfoById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void deleteFileByIdRemovesMinioVectorsAndDbWhenEmbedded() {
        var fileInfo = FileInfo.builder()
                .id(1L)
                .path("uploads/doc.pdf")
                .embed(true)
                .status(FileStatus.SUCCESS)
                .build();
        when(fileService.getFileInfoById(1L)).thenReturn(Optional.of(fileInfo));

        fileApplication.deleteFileById(1L);

        verify(minioService).delete("uploads/doc.pdf");
        verify(embeddingService).deleteByFileId(1L);
        verify(fileService).deleteFileInfoById(1L);
    }

    @Test
    void deleteFileByIdSkipsVectorCleanupWhenNotEmbedded() {
        var fileInfo = FileInfo.builder()
                .id(2L)
                .path("uploads/image.png")
                .embed(false)
                .status(FileStatus.SUCCESS)
                .build();
        when(fileService.getFileInfoById(2L)).thenReturn(Optional.of(fileInfo));

        fileApplication.deleteFileById(2L);

        verify(minioService).delete("uploads/image.png");
        verify(embeddingService, never()).deleteByFileId(org.mockito.ArgumentMatchers.anyLong());
        verify(fileService).deleteFileInfoById(2L);
    }

    @Test
    void deleteFileByIdSkipsMinioWhenPathBlank() {
        var fileInfo = FileInfo.builder()
                .id(3L)
                .path("")
                .embed(false)
                .status(FileStatus.SUCCESS)
                .build();
        when(fileService.getFileInfoById(3L)).thenReturn(Optional.of(fileInfo));

        fileApplication.deleteFileById(3L);

        verify(minioService, never()).delete(org.mockito.ArgumentMatchers.anyString());
        verify(fileService).deleteFileInfoById(3L);
    }
}
