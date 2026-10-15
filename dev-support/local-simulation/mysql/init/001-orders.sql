CREATE DATABASE IF NOT EXISTS mateclaw_sim;
USE mateclaw_sim;

CREATE TABLE IF NOT EXISTS customers (
  id BIGINT PRIMARY KEY,
  customer_name VARCHAR(128) NOT NULL,
  region VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY,
  customer_id BIGINT,
  order_date TIMESTAMP NOT NULL,
  status VARCHAR(32) NOT NULL,
  amount DECIMAL(18, 2) NOT NULL,
  nullable_note VARCHAR(255)
);

INSERT INTO customers (id, customer_name, region) VALUES
  (101, '演示客户甲', 'east'),
  (102, '演示客户乙', 'west'),
  (103, '演示客户丙', 'south')
ON DUPLICATE KEY UPDATE customer_name=VALUES(customer_name), region=VALUES(region);

INSERT INTO orders (id, customer_id, order_date, status, amount, nullable_note) VALUES
  (1001, 101, '2026-01-01 09:00:00', 'PAID', 120.50, NULL),
  (1002, 102, '2026-01-02 10:00:00', 'PAID', 80.00, '首单'),
  (1003, 101, '2026-01-03 11:00:00', 'CANCELLED', 30.00, NULL),
  (1004, 103, '2026-01-04 12:00:00', 'PAID', 210.00, '含中文'),
  (1005, 999, '2026-01-05 13:00:00', 'PENDING', 10.00, NULL),
  (1006, 101, '2026-01-06 14:00:00', 'SHIPPED', 45.75, '批量样本'),
  (1007, 102, '2026-01-07 15:00:00', 'CANCELLED', 66.20, NULL),
  (1008, 103, '2026-01-08 16:00:00', 'REFUNDED', 19.99, NULL),
  (1009, 999, '2026-01-09 17:00:00', 'FAILED', 5.50, '无匹配客户'),
  (1010, 101, '2026-01-10 18:00:00', 'PROCESSING', 300.00, NULL)
ON DUPLICATE KEY UPDATE customer_id=VALUES(customer_id), order_date=VALUES(order_date),
  status=VALUES(status), amount=VALUES(amount), nullable_note=VALUES(nullable_note);
