package com.genchat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genchat.converter.FileInfoConverter;
import com.genchat.dto.FileInfo;
import com.genchat.entity.FileInfoEntity;
import com.genchat.repository.FileInfoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FileService extends ServiceImpl<FileInfoRepository, FileInfoEntity> {

    /**
     * Save file info to database
     *
     * @return file info
     */
    public FileInfo saveFileInfo(FileInfo fileInfo) {
        var entity = FileInfoConverter.INSTANCE.toEntity(fileInfo);
        save(entity);
        return FileInfoConverter.INSTANCE.toDto(entity);
    }

    /**
     * Query file info by id
     */
    public Optional<FileInfo> getFileInfoById(Long id) {
        return Optional.ofNullable(getById(id))
                .map(FileInfoConverter.INSTANCE::toDto);
    }

    /**
     * Update file info
     */
    public void updateFileInfo(FileInfo fileInfo) {
        var entity = FileInfoConverter.INSTANCE.toEntity(fileInfo);
        updateById(entity);
    }

    /**
     * Delete file info by id
     */
    public void deleteFileInfoById(Long id) {
        removeById(id);
    }

    /**
     * Check if file info exists by id
     */
    public boolean existsById(Long id) {
        return getById(id) != null;
    }

    /**
     * List file summaries ordered by create time descending (without extracted text).
     */
    public List<FileInfo> listSummaries() {
        var wrapper = new QueryWrapper<FileInfoEntity>()
                .select("id", "name", "path", "file_type", "size", "embed", "status", "create_time", "update_time")
                .orderByDesc("create_time");
        return FileInfoConverter.INSTANCE.toDtoList(list(wrapper));
    }

    /**
     * Get all file info records
     */
    public List<FileInfo> listAll() {
        return FileInfoConverter.INSTANCE.toDtoList(list());
    }

    /**
     * Get total file count
     */
    public long totalCount() {
        return count();
    }
}
