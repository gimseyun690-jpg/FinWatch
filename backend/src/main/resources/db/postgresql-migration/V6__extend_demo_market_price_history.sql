WITH stock_config(symbol, market, history_start, base_price, daily_step, wave_size, base_volume) AS (
    VALUES
        ('000660', 'KRX', DATE '2025-01-02', 1650000::numeric, 3100::numeric, 19000::numeric, 2200000::numeric),
        ('005930', 'KRX', DATE '2025-01-02', 245000::numeric, 260::numeric, 2600::numeric, 1450000::numeric),
        ('NVDA', 'NASDAQ', DATE '2025-01-02', 118::numeric, 0.28::numeric, 2.8::numeric, 1550000::numeric),
        ('AAPL', 'NASDAQ', DATE '2025-01-02', 205::numeric, 0.21::numeric, 2.2::numeric, 1500000::numeric)
), existing_start AS (
    SELECT stock_id, MIN(recorded_at)::date AS first_recorded_date
    FROM market_prices
    WHERE price_interval = '1D'
    GROUP BY stock_id
), generated AS (
    SELECT
        s.id AS stock_id,
        c.symbol,
        c.market,
        day::date AS trading_day,
        (day::date - c.history_start) AS sequence_no,
        c.base_price,
        c.daily_step,
        c.wave_size,
        c.base_volume
    FROM stock_config c
    JOIN stocks s ON s.symbol = c.symbol AND s.market = c.market
    JOIN existing_start e ON e.stock_id = s.id
    CROSS JOIN LATERAL generate_series(
        c.history_start::timestamp,
        (e.first_recorded_date - 1)::timestamp,
        INTERVAL '1 day'
    ) AS day
    WHERE EXTRACT(ISODOW FROM day) BETWEEN 1 AND 5
), closes AS (
    SELECT
        *,
        base_price
            + sequence_no * daily_step
            + ((sequence_no % 19) - 9) * wave_size / 9 AS close_price
    FROM generated
), ohlcv AS (
    SELECT
        *,
        close_price * (1 + ((sequence_no % 5) - 2) / 1000.0) AS open_price
    FROM closes
)
INSERT INTO market_prices (
    stock_id, price_interval, open_price, high_price, low_price, close_price, volume, recorded_at, source
)
SELECT
    stock_id,
    '1D',
    ROUND(open_price, 4),
    ROUND(GREATEST(open_price, close_price) * 1.009, 4),
    ROUND(LEAST(open_price, close_price) * 0.991, 4),
    ROUND(close_price, 4),
    ROUND(base_volume + (sequence_no % 11) * 93000, 4),
    (trading_day::timestamp
        + CASE WHEN market = 'KRX' THEN TIME '06:00:00' ELSE TIME '20:00:00' END) AT TIME ZONE 'UTC',
    'DEMO'
FROM ohlcv
ON CONFLICT (stock_id, price_interval, recorded_at) DO NOTHING;
