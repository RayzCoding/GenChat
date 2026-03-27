CREATE TABLE IF NOT EXISTS `ai_chat_session`  (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key id',
                               `session_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'session id',
                               `question` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'user question',
                               `answer` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'AI answer',
                               `tools` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'tool name',
                               `first_response_time` bigint NULL DEFAULT NULL COMMENT 'first response time',
                               `total_response_time` bigint NULL DEFAULT NULL COMMENT 'fill response time',
                               `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
                               `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
                               `reference` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'ref link',
                               `agent_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'agent type',
                               `thinking` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'thinking',
                               `fileid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'file id',
                               `recommend` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'recommended questions',
                               PRIMARY KEY (`id`) USING BTREE,
                               INDEX `idx_session_id`(`session_id` ASC) USING BTREE COMMENT 'session id index',
                               INDEX `idx_create_time`(`create_time` ASC) USING BTREE COMMENT 'create time index'
) ENGINE = InnoDB AUTO_INCREMENT = 2030594695312510979 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Store the dialogue history between the agent and the user, supporting session isolation and memory functions' ROW_FORMAT = DYNAMIC;
