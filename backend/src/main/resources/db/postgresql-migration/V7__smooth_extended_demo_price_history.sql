WITH stock_config(symbol, market, history_start, generated_end, base_price, daily_step, wave_size, base_volume) AS (
    VALUES
        ('000660', 'KRX', DATE '2025-01-02', DATE '2026-04-14', 1650000::numeric, 1500::numeric, 19000::numeric, 2200000::numeric),
        ('005930', 'KRX', DATE '2025-01-02', DATE '2026-05-14', 245000::numeric, 110::numeric, 2600::numeric, 1450000::numeric),
        ('NVDA', 'NASDAQ', DATE '2025-01-02', DATE '2026-05-14', 118::numeric, 0.125::numeric, 2.8::numeric, 1550000::numeric),
        ('AAPL', 'NASDAQ', DATE '2025-01-02', DATE '2026-05-14', 205::numeric, 0.12::numeric, 2.2::numeric, 1500000::numeric)
), targets AS (
    SELECT
        s.id AS stock_id,
        c.market,
        c.history_start,
        c.generated_end,
        c.base_price,
        c.daily_step,
        c.wave_size,
        c.base_volume,
        mp.recorded_at,
        (mp.recorded_at::date - c.history_start) AS sequence_no
    FROM stock_config c
    JOIN stocks s ON s.symbol = c.symbol AND s.market = c.market
    JOIN market_prices mp ON mp.stock_id = s.id AND mp.price_interval = '1D'
    WHERE mp.recorded_at::date BETWEEN c.history_start AND c.generated_end
), closes AS (
    SELECT
        *,
        base_price
            + sequence_no * daily_step
            + ((sequence_no % 19) - 9) * wave_size / 9 AS corrected_close
    FROM targets
), corrected AS (
    SELECT
        *,
        corrected_close * (1 + ((sequence_no % 5) - 2) / 1000.0) AS corrected_open
    FROM closes
)
UPDATE market_prices mp
SET
    open_price = ROUND(c.corrected_open, 4),
    high_price = ROUND(GREATEST(c.corrected_open, c.corrected_close) * 1.009, 4),
    low_price = ROUND(LEAST(c.corrected_open, c.corrected_close) * 0.991, 4),
    close_price = ROUND(c.corrected_close, 4),
    volume = ROUND(c.base_volume + (c.sequence_no % 11) * 93000, 4)
FROM corrected c
WHERE mp.stock_id = c.stock_id
  AND mp.recorded_at = c.recorded_at;
