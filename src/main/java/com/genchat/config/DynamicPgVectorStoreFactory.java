package com.genchat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Slf4j
public class DynamicPgVectorStoreFactory {

    private final DataSource dataSource;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public DynamicPgVectorStoreFactory(@Qualifier("pgVectorDataSource") DataSource dataSource, EmbeddingModel embeddingModel) {
        this.dataSource = dataSource;
        this.embeddingModel = embeddingModel;
    }

    public PgVectorStore createPgVectorStore(String tableName) {
        // Parameter validation
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Vector table name must not be empty");
        }
        String actualTableName = tableName.trim();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        boolean tableExists = tableExists(actualTableName);

        if (tableExists) {
            log.info("Vector table [{}] already exists, loading PgVectorStore directly", actualTableName);
        } else {
            log.info("Vector table [{}] does not exist, will auto-create and initialize PgVectorStore", actualTableName);
        }

        PgVectorStore pgVectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .removeExistingVectorStoreTable(false)
                .vectorTableName(actualTableName)
                .maxDocumentBatchSize(100)
                .build();

        try {
            pgVectorStore.afterPropertiesSet();
            log.info("PgVectorStore loaded/created successfully, table: {}", actualTableName);
        } catch (Exception e) {
            log.error("PgVectorStore initialization failed, table: {}", actualTableName, e);
            throw new RuntimeException("Failed to initialize PgVectorStore", e);
        }

        return pgVectorStore;
    }

    private boolean tableExists(String tableName) {
        try {
            String checkSql = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND LOWER(table_name) = LOWER(?)
                    );
                    """;
            Boolean exists = new JdbcTemplate(dataSource).queryForObject(checkSql, Boolean.class, tableName);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Error checking if vector table [{}] exists", tableName, e);
            return false;
        }
    }
}
