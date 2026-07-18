package com.finwatch.ai.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class NewsTextPreprocessor {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern AD_PHRASE = Pattern.compile("(?i)(광고|ADVERTISEMENT|무단 전재|재배포 금지)[^.!?]*[.!?]?");
    private static final int MAX_LENGTH = 6_000;

    public String preprocess(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String cleaned = HTML_TAG.matcher(content).replaceAll(" ");
        cleaned = URL.matcher(cleaned).replaceAll(" ");
        cleaned = AD_PHRASE.matcher(cleaned).replaceAll(" ");
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim();

        Set<String> uniqueSentences = new LinkedHashSet<>();
        for (String sentence : cleaned.split("(?<=[.!?])\\s+")) {
            String normalized = sentence.trim();
            if (!normalized.isBlank()) {
                uniqueSentences.add(normalized);
            }
        }

        String result = String.join(" ", uniqueSentences);
        return result.length() <= MAX_LENGTH ? result : result.substring(0, MAX_LENGTH).trim();
    }
}

