package com.finwatch.ai.cache;

import java.io.Serializable;
import com.finwatch.ai.dto.DailyChangeBriefingResponse;

public record DailyBriefingCacheValue(DailyChangeBriefingResponse originalResponse) implements Serializable { }
