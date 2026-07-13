package com.finwatch.ai.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NewsContentSegmenter {

    private final int maxSegmentCharacters;
    private final int maxSegments;

    public NewsContentSegmenter(
            @Value("${app.ai.max-segment-characters:1800}") int maxSegmentCharacters,
            @Value("${app.ai.max-segments:4}") int maxSegments) {
        this.maxSegmentCharacters = Math.max(300, maxSegmentCharacters);
        this.maxSegments = Math.max(1, maxSegments);
    }

    public SegmentationResult segment(String content) {
        if (content == null || content.isBlank()) {
            return new SegmentationResult(List.of(), 0, false);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int consumedCharacters = 0;
        boolean truncated = false;

        for (String sentence : content.split("(?<=[.!?])\\s+")) {
            String remaining = sentence.trim();
            while (!remaining.isBlank()) {
                if (chunks.size() >= maxSegments) {
                    truncated = true;
                    break;
                }
                int separatorLength = current.isEmpty() ? 0 : 1;
                int available = maxSegmentCharacters - current.length() - separatorLength;
                if (remaining.length() <= available) {
                    if (!current.isEmpty()) {
                        current.append(' ');
                    }
                    current.append(remaining);
                    consumedCharacters += remaining.length();
                    remaining = "";
                } else if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current.setLength(0);
                } else {
                    String part = remaining.substring(0, maxSegmentCharacters).trim();
                    chunks.add(part);
                    consumedCharacters += part.length();
                    remaining = remaining.substring(Math.min(maxSegmentCharacters, remaining.length())).trim();
                }
            }
            if (truncated) {
                break;
            }
        }

        if (!current.isEmpty() && chunks.size() < maxSegments) {
            chunks.add(current.toString());
        } else if (!current.isEmpty()) {
            truncated = true;
        }

        List<ContentSegment> segments = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            segments.add(new ContentSegment("S" + (index + 1), chunks.get(index)));
        }
        return new SegmentationResult(List.copyOf(segments), consumedCharacters, truncated);
    }

    public record ContentSegment(String id, String content) {
    }

    public record SegmentationResult(List<ContentSegment> segments, int processedCharacters, boolean truncated) {
    }
}
