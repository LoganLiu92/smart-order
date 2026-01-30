-- Minimal MySQL schema for smart-order
CREATE TABLE IF NOT EXISTS stores (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  default_language VARCHAR(16) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tables (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL,
  table_no VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_store_table (store_id, table_no)
);

CREATE TABLE IF NOT EXISTS menu_categories (
  id VARCHAR(64) PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS dishes (
  id VARCHAR(64) PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL,
  category_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  description VARCHAR(255),
  image_url VARCHAR(255),
  detail_image_url VARCHAR(255),
  tags VARCHAR(255),
  spicy_level VARCHAR(16),
  calories INT,
  ingredients VARCHAR(255),
  allergens VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS option_groups (
  id VARCHAR(64) PRIMARY KEY,
  dish_id VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  multi_select BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS option_items (
  id VARCHAR(64) PRIMARY KEY,
  group_id VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  extra_price DECIMAL(10,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
  id VARCHAR(64) PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL,
  table_no VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  payment_status VARCHAR(16) NOT NULL,
  people_count INT,
  remark VARCHAR(255),
  total_amount DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  paid_at TIMESTAMP NULL,
  paid_by VARCHAR(64),
  cleared_at TIMESTAMP NULL,
  cleared_by VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id VARCHAR(64) NOT NULL,
  dish_id VARCHAR(64) NOT NULL,
  dish_name VARCHAR(128) NOT NULL,
  qty INT NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  line_total DECIMAL(10,2) NOT NULL,
  item_remark VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS order_item_options (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_item_id BIGINT NOT NULL,
  group_id VARCHAR(64),
  group_name VARCHAR(64),
  option_id VARCHAR(64),
  option_name VARCHAR(64),
  extra_price DECIMAL(10,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(64) PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(128) NOT NULL,
  role VARCHAR(16) NOT NULL
);

CREATE TABLE IF NOT EXISTS wallets (
  store_id VARCHAR(64) PRIMARY KEY,
  balance DECIMAL(10,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS wallet_ledger (
  id VARCHAR(64) PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL,
  type VARCHAR(32) NOT NULL,
  reason VARCHAR(255),
  amount DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS subscriptions (
  store_id VARCHAR(64) PRIMARY KEY,
  status VARCHAR(16) NOT NULL,
  expire_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS platform_pricing (
  id VARCHAR(64) PRIMARY KEY,
  platform_monthly_fee DECIMAL(10,2) NOT NULL,
  store_monthly_fee DECIMAL(10,2) NOT NULL,
  ai_call_price DECIMAL(10,4) NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_sessions (
  id VARCHAR(64) PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL,
  username VARCHAR(64) NOT NULL,
  role VARCHAR(16) NOT NULL,
  refresh_token VARCHAR(128) NOT NULL,
  refresh_expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
