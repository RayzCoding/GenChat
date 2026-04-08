package com.genchat.converter;

import com.genchat.dto.FileInfo;
import com.genchat.entity.FileInfoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface FileInfoConverter {

    FileInfoConverter INSTANCE = Mappers.getMapper(FileInfoConverter.class);

    FileInfo toDto(FileInfoEntity entity);

    List<FileInfo> toDtoList(List<FileInfoEntity> entities);

    FileInfoEntity toEntity(FileInfo dto);
}
