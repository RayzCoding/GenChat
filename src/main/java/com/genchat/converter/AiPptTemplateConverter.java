package com.genchat.converter;

import com.genchat.dto.AiPptTemplate;
import com.genchat.entity.AiPptTemplateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface AiPptTemplateConverter {

    AiPptTemplateConverter INSTANCE = Mappers.getMapper(AiPptTemplateConverter.class);

    AiPptTemplate toDto(AiPptTemplateEntity entity);

    List<AiPptTemplate> toDtoList(List<AiPptTemplateEntity> entities);

    AiPptTemplateEntity toEntity(AiPptTemplate dto);
}
