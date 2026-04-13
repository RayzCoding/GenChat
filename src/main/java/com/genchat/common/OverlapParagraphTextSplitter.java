package com.genchat.common;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OverlapParagraphTextSplitter extends TextSplitter {

    // Maximum characters per chunk
    protected final int chunkSize;

    // Overlap characters between adjacent chunks
    protected final int overlap;

    public OverlapParagraphTextSplitter(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than 0");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap must not be negative");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be less than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    protected List<String> splitText(String text) {
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }

        String[] paragraphs = text.split("\\n+");
        List<String> allChunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (StringUtils.isBlank(paragraph)) {
                continue;
            }

            int start = 0;
            while (start < paragraph.length()) {
                int remainingSpace = chunkSize - currentChunk.length();
                int end = Math.min(start + remainingSpace, paragraph.length());

                currentChunk.append(paragraph, start, end);

                // If current chunk is full, save it and start a new one
                if (currentChunk.length() >= chunkSize) {
                    allChunks.add(currentChunk.toString());

                    // Calculate overlap
                    String overlapText = "";
                    if (overlap > 0) {
                        int overlapStart = Math.max(0, currentChunk.length() - overlap);
                        overlapText = currentChunk.substring(overlapStart);
                    }

                    currentChunk = new StringBuilder();
                    if (!overlapText.isEmpty()){
                        currentChunk.append(overlapText);
                    }
                }

                start = end;
            }
        }

        if (!currentChunk.isEmpty()){
            allChunks.add(currentChunk.toString());
        }

        return allChunks;
    }

    /**
     * Batch split documents into chunks
     */
    @Override
    public List<Document> apply(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }

        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            List<String> chunks = splitText(doc.getText());
            for (String chunk : chunks) {
                result.add(new Document(chunk));
            }
        }
        return result;
    }
}
