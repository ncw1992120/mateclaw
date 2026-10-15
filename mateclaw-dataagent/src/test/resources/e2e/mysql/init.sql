CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY,
    order_date DATE NOT NULL,
    region VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL
);

INSERT INTO orders (id, order_date, region, status, amount) VALUES
    (1, '2026-01-01', 'east', 'PAID', 120.50),
    (2, '2026-01-02', 'west', 'PAID', 80.00),
    (3, '2026-01-03', 'east', 'CANCELLED', 30.00),
    (4, '2026-01-04', 'south', 'PAID', 210.00)
ON DUPLICATE KEY UPDATE
    region = VALUES(region), status = VALUES(status), amount = VALUES(amount);
