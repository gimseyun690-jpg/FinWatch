package com.finwatch.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.ai.domain.AiAnalysis;
import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.content.dto.ContentFeedRequest;
import com.finwatch.content.dto.ContentFeedResponses.ContentFeedItem;
import com.finwatch.content.dto.ContentFeedResponses.ContentFeedPage;
import com.finwatch.news.content.ContentSource;
import com.finwatch.news.content.RightsProfile;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.news.service.NewsPublisherName;
import com.finwatch.stock.repository.StockRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Service
@Transactional(readOnly = true)
public class ContentFeedService {

    private static final Set<String> MARKETS = Set.of("KRX", "NASDAQ", "NYSE");
    private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9._-]{1,30}");
    private static final Pattern SOURCE = Pattern.compile("[A-Z0-9._-]{1,50}");
    private static final int SUMMARY_PREVIEW_MAX = 180;
    private static final String FEATURE_TYPE = "NEWS_SUMMARY";

    private final NewsArticleRepository newsArticleRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final StockRepository stockRepository;
    private final Clock clock;

    @Autowired
    public ContentFeedService(
            NewsArticleRepository newsArticleRepository,
            AiAnalysisRepository aiAnalysisRepository,
            StockRepository stockRepository) {
        this(newsArticleRepository, aiAnalysisRepository, stockRepository, Clock.systemUTC());
    }

    ContentFeedService(
            NewsArticleRepository newsArticleRepository,
            AiAnalysisRepository aiAnalysisRepository,
            StockRepository stockRepository,
            Clock clock) {
        this.newsArticleRepository = newsArticleRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.stockRepository = stockRepository;
        this.clock = clock;
    }

    public ContentFeedPage get(ContentFeedRequest request) {
        Query query = normalize(request);
        validateStock(query.market(), query.symbol());
        Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, "publishedAt").and(Sort.by(direction, "id"));
        Page<NewsArticle> result = newsArticleRepository.findAll(
                specification(query),
                PageRequest.of(query.page(), query.size(), sort));

        Map<Long, AiAnalysis> analyses = latestAnalyses(result.getContent());
        List<ContentFeedItem> items = result.getContent().stream()
                .map(article -> item(article, analyses.get(article.getId())))
                .toList();
        return new ContentFeedPage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasPrevious(),
                result.hasNext(),
                query.sort());
    }

    private Query normalize(ContentFeedRequest request) {
        Kind kind = enumValue(request.kind(), Kind.class, Kind.ALL, "kind");
        Analysis analysis = enumValue(request.analysis(), Analysis.class, Analysis.ALL, "analysis");
        Period period = enumValue(request.period(), Period.class, Period.SEVEN_DAYS, "period");
        String market = normalizeMarket(request.market());
        String symbol = normalizeSymbol(request.symbol(), market);
        String query = normalizeQuery(request.query());
        String source = normalizeSource(request.source());
        int page = integer(request.page(), 0, "page");
        int size = integer(request.size(), 20, "size");
        if (page < 0) throw invalid("PAGE_INVALID", "page must be zero or greater.");
        if (size < 1 || size > 50) throw invalid("PAGE_INVALID", "size must be between 1 and 50.");
        String sort = request.sort() == null || request.sort().isBlank()
                ? "publishedAt,desc"
                : request.sort().trim();
        boolean ascending;
        if ("publishedAt,desc".equals(sort)) ascending = false;
        else if ("publishedAt,asc".equals(sort)) ascending = true;
        else throw invalid("SORT_NOT_ALLOWED", "Only publishedAt,desc and publishedAt,asc are allowed.");
        TimeRange timeRange = timeRange(period, request.from(), request.to());
        return new Query(kind, market, symbol, query, source, analysis,
                page, size, sort, ascending, timeRange.from(), timeRange.toExclusive());
    }

    private Specification<NewsArticle> specification(Query query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.kind() != Kind.ALL) {
                predicates.add(builder.equal(root.get("contentKind"), query.kind().name()));
            }
            if (query.market() != null) {
                predicates.add(builder.equal(root.get("stock").get("market"), query.market()));
            }
            if (query.symbol() != null) {
                predicates.add(builder.equal(root.get("stock").get("symbol"), query.symbol()));
            }
            if (query.query() != null) {
                String pattern = "%" + escapeLike(query.query().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title").as(String.class)), pattern, '\\'),
                        builder.like(builder.lower(root.get("publisher").as(String.class)), pattern, '\\')));
            }
            if (query.source() != null) {
                predicates.add(builder.equal(root.get("source"), query.source()));
            }
            predicates.add(builder.greaterThanOrEqualTo(root.get("publishedAt"), query.from()));
            predicates.add(builder.lessThan(root.get("publishedAt"), query.toExclusive()));
            addAnalysisPredicate(query.analysis(), root, criteriaQuery, builder, predicates);
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addAnalysisPredicate(
            Analysis analysis,
            Root<NewsArticle> root,
            CriteriaQuery<?> criteriaQuery,
            CriteriaBuilder builder,
            List<Predicate> predicates) {
        if (analysis == Analysis.METADATA_ONLY) {
            predicates.add(builder.equal(root.get("contentSource"), ContentSource.METADATA_ONLY));
            return;
        }
        if (analysis == Analysis.AI_ALLOWED) {
            predicates.add(aiAllowed(root, builder));
            return;
        }
        if (analysis == Analysis.AI_COMPLETED) {
            Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
            Root<AiAnalysis> ai = subquery.from(AiAnalysis.class);
            subquery.select(ai.get("id"));
            subquery.where(
                    builder.equal(ai.get("news").get("id"), root.get("id")),
                    builder.equal(ai.get("featureType"), FEATURE_TYPE));
            predicates.add(builder.exists(subquery));
        }
    }

    private Predicate aiAllowed(Root<NewsArticle> root, CriteriaBuilder builder) {
        Predicate storedContent = builder.and(
                root.get("rightsProfile").in(
                        RightsProfile.TRANSIENT_AI,
                        RightsProfile.STORE_FOR_AI,
                        RightsProfile.STORE_AND_DISPLAY),
                builder.notEqual(root.get("contentSource"), ContentSource.METADATA_ONLY),
                builder.isNotNull(root.get("content")),
                builder.notEqual(builder.trim(root.get("content").as(String.class)), ""));
        Predicate onDemandNews = builder.and(
                builder.equal(root.get("contentKind"), "NEWS"),
                builder.isNotNull(root.get("canonicalUrl")),
                builder.or(
                        builder.like(builder.lower(root.get("canonicalUrl").as(String.class)), "https://%"),
                        builder.like(builder.lower(root.get("canonicalUrl").as(String.class)), "http://%")));
        return builder.or(storedContent, onDemandNews);
    }

    private Map<Long, AiAnalysis> latestAnalyses(List<NewsArticle> articles) {
        List<Long> ids = articles.stream().map(NewsArticle::getId).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, AiAnalysis> result = new LinkedHashMap<>();
        aiAnalysisRepository
                .findAllByNewsIdInAndFeatureTypeOrderByGeneratedAtDescIdDesc(ids, FEATURE_TYPE)
                .forEach(analysis -> result.putIfAbsent(analysis.getNews().getId(), analysis));
        return result;
    }

    private ContentFeedItem item(NewsArticle article, AiAnalysis analysis) {
        boolean allowed = article.isAiSummaryRequestAllowed();
        String status = analysis != null ? "COMPLETED" : allowed ? "AVAILABLE" : "UNAVAILABLE";
        return new ContentFeedItem(
                article.getId(),
                article.getContentKind(),
                article.getStock().getMarket(),
                article.getStock().getSymbol(),
                article.getStock().getName(),
                article.getTitle(),
                NewsPublisherName.resolve(article.getPublisher(), article.getCanonicalUrl()),
                article.getSource(),
                article.getPublishedAt(),
                article.getDisclosureType(),
                article.getContentSource().name(),
                article.getRightsProfile().name(),
                allowed,
                status,
                analysis == null ? null : preview(analysis.getSummary()),
                article.getCanonicalUrl());
    }

    private void validateStock(String market, String symbol) {
        if (symbol == null) return;
        stockRepository.findByMarketAndSymbolAndActiveTrue(market, symbol)
                .orElseThrow(() -> new ContentFeedException(
                        HttpStatus.NOT_FOUND,
                        "STOCK_NOT_FOUND",
                        "The active stock selected by the content filter was not found."));
    }

    private String normalizeMarket(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!MARKETS.contains(normalized)) {
            throw invalid("CONTENT_FILTER_INVALID", "The market filter is invalid.");
        }
        return normalized;
    }

    private String normalizeSymbol(String raw, String market) {
        if (raw == null || raw.isBlank()) return null;
        if (market == null) {
            throw invalid("CONTENT_FILTER_INVALID", "symbol requires a specific market.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL.matcher(normalized).matches()) {
            throw invalid("CONTENT_FILTER_INVALID", "The symbol filter format is invalid.");
        }
        return normalized;
    }

    private String normalizeQuery(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2 || normalized.length() > 100
                || normalized.chars().anyMatch(value -> value < 32)) {
            throw invalid(
                    "CONTENT_FILTER_INVALID",
                    "The query must be between 2 and 100 characters without control characters.");
        }
        return normalized;
    }

    private String normalizeSource(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE.matcher(normalized).matches()) {
            throw invalid("CONTENT_FILTER_INVALID", "The source filter format is invalid.");
        }
        return normalized;
    }

    private TimeRange timeRange(Period period, String rawFrom, String rawTo) {
        Instant now = clock.instant();
        if (period != Period.CUSTOM) {
            if ((rawFrom != null && !rawFrom.isBlank()) || (rawTo != null && !rawTo.isBlank())) {
                throw invalid("CONTENT_FILTER_INVALID", "from and to can only be used with the CUSTOM period.");
            }
            ZonedDateTime utc = now.atZone(ZoneOffset.UTC);
            Instant from = switch (period) {
                case ONE_DAY -> now.minusSeconds(86_400);
                case SEVEN_DAYS -> now.minusSeconds(7L * 86_400);
                case ONE_MONTH -> utc.minusMonths(1).toInstant();
                case THREE_MONTHS -> utc.minusMonths(3).toInstant();
                case CUSTOM -> throw new IllegalStateException("CUSTOM must use explicit dates");
            };
            return new TimeRange(from, now.plusNanos(1));
        }
        if (rawFrom == null || rawFrom.isBlank() || rawTo == null || rawTo.isBlank()) {
            throw invalid("CONTENT_FILTER_INVALID", "The CUSTOM period requires both from and to.");
        }
        try {
            LocalDate from = LocalDate.parse(rawFrom.trim());
            LocalDate to = LocalDate.parse(rawTo.trim());
            if (from.isAfter(to)) {
                throw invalid("CONTENT_FILTER_INVALID", "from cannot be after to.");
            }
            return new TimeRange(
                    from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (DateTimeParseException exception) {
            throw invalid("CONTENT_FILTER_INVALID", "from and to must use the ISO date format YYYY-MM-DD.");
        }
    }

    private int integer(String raw, int defaultValue, String field) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalid("PAGE_INVALID", field + " must be an integer.");
        }
    }

    private <T extends Enum<T>> T enumValue(String raw, Class<T> type, T defaultValue, String field) {
        if (raw == null || raw.isBlank()
                || ("ALL".equalsIgnoreCase(raw.trim()) && "ALL".equals(defaultValue.name()))) {
            return defaultValue;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (type == Period.class) {
            normalized = switch (normalized) {
                case "24H" -> "ONE_DAY";
                case "7D" -> "SEVEN_DAYS";
                case "1M" -> "ONE_MONTH";
                case "3M" -> "THREE_MONTHS";
                default -> normalized;
            };
        }
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException exception) {
            throw invalid("CONTENT_FILTER_INVALID", "The " + field + " filter is invalid.");
        }
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String preview(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= SUMMARY_PREVIEW_MAX
                ? normalized
                : normalized.substring(0, SUMMARY_PREVIEW_MAX - 1) + "…";
    }

    private ContentFeedException invalid(String code, String message) {
        return new ContentFeedException(HttpStatus.BAD_REQUEST, code, message);
    }

    private enum Kind { ALL, NEWS, DISCLOSURE }
    private enum Analysis { ALL, METADATA_ONLY, AI_ALLOWED, AI_COMPLETED }
    private enum Period { ONE_DAY, SEVEN_DAYS, ONE_MONTH, THREE_MONTHS, CUSTOM }

    private record TimeRange(Instant from, Instant toExclusive) { }

    private record Query(
            Kind kind,
            String market,
            String symbol,
            String query,
            String source,
            Analysis analysis,
            int page,
            int size,
            String sort,
            boolean ascending,
            Instant from,
            Instant toExclusive) { }
}
