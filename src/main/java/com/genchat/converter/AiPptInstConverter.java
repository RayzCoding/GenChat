package com.genchat.converter;

import com.genchat.dto.AiPptInst;
import com.genchat.entity.AiPptInstEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface AiPptInstConverter {

    AiPptInstConverter INSTANCE = Mappers.getMapper(AiPptInstConverter.class);

    AiPptInst toDto(AiPptInstEntity entity);

    List<AiPptInst> toDtoList(List<AiPptInstEntity> entities);

    AiPptInstEntity toEntity(AiPptInst dto);
}
