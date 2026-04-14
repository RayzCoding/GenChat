package com.genchat.service;

import com.genchat.config.DynamicPgVectorStoreFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;
    private final DynamicPgVectorStoreFactory pgVectorStoreFactory;
    private final ChatModel chatModel;
    private PgVectorStore vectorStore;
    private static final int EMBEDDING_BATCH_SIZE = 9;

    @PostConstruct
    public void init(){
        vectorStore = pgVectorStoreFactory.createPgVectorStore("vector_file_info");
    }

    /**
     * Embed documents into vector representations
     */
    public List<float[]> embed(List<Document> documents) {
        return documents.stream()
                .map(document -> embeddingModel.embed(document.getText()))
                .collect(Collectors.toList());
    }

    /**
     * Embed and store documents in vector store
     */
    public void embedAndStore(List<Document> documents) {
        for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
            List<Document> batches = documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, documents.size()));
            vectorStore.doAdd(batches);
        }
    }

    /**
     * RAG retrieval - search relevant documents by file ID and question
     *
     * @param fileId   file ID
     * @param question user question
     * @return list of relevant document contents
     */
    public List<String> ragRetrieve(Long fileId, String question) {
        log.info("RAG retrieval started: fileId={}, question={}", fileId, question);

        if (fileId == null || StringUtils.isBlank(question)) {
            log.warn("RAG retrieval parameters are empty: fileId={}, question={}", fileId, question);
            return Collections.singletonList("Retrieval parameters must not be empty");
        }

        try {
            var query = Query.builder().text(question).build();

            // 1. Query compression and rewriting
            var chatClient = ChatClient.builder(chatModel).build();
            var queryTransformer = CompressionQueryTransformer.builder()
                    .chatClientBuilder(chatClient.mutate())
                    .build();

            var compressed = queryTransformer.transform(query);
            log.info("Compressed query: {}", compressed.text());

            // 2. Query expansion
            var queryExpander = MultiQueryExpander.builder()
                    .chatClientBuilder(chatClient.mutate())
                    .numberOfQueries(3)
                    .includeOriginal(true)
                    .build();

            var expandedQueries = queryExpander.expand(compressed);
            log.info("Expanded queries: {}", expandedQueries);

            // 3. Semantic vector search - filter by fileid
            var results = new ArrayList<String>();
            var seenIds = new HashSet<String>();

            var builder = new FilterExpressionBuilder();
            var filter = builder.eq("fileid", fileId).build();

            for (Query eq : expandedQueries) {
                var docs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(eq.text())
                                .topK(5)
                                .filterExpression(filter)
                                .build());

                for (Document doc : docs) {
                    if (seenIds.add(doc.getId())) {
                        results.add(doc.getText());
                    }
                }
            }

            log.info("RAG retrieval completed: fileId={}, result count={}", fileId, results.size());
            return results;

        } catch (Exception e) {
            log.error("RAG retrieval failed: fileId={}, question={}", fileId, question, e);
            return Collections.singletonList("RAG retrieval failed: " + e.getMessage());
        }
    }
}
