package com.genchat.converter;

import com.genchat.dto.AiChatSession;
import com.genchat.entity.AiChatSessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface AiChatSessionConverter {

    AiChatSessionConverter INSTANCE = Mappers.getMapper(AiChatSessionConverter.class);

    AiChatSession toDto(AiChatSessionEntity entity);

    List<AiChatSession> toDtoList(List<AiChatSessionEntity> entities);

    AiChatSessionEntity toEntity(AiChatSession dto);
}
