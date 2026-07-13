package com.finwatch.news.content;

public enum RightsProfile {
    METADATA_ONLY(false),
    TRANSIENT_AI(true),
    STORE_FOR_AI(true),
    STORE_AND_DISPLAY(true);

    private final boolean aiAnalysisAllowed;

    RightsProfile(boolean aiAnalysisAllowed) {
        this.aiAnalysisAllowed = aiAnalysisAllowed;
    }

    public boolean isAiAnalysisAllowed() {
        return aiAnalysisAllowed;
    }
}
