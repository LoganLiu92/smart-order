-- Drop all tables (reset schema)
SET FOREIGN_KEY_CHECKS=0;

DROP TABLE IF EXISTS cart_item_options;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS carts;

DROP TABLE IF EXISTS order_item_options;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;

DROP TABLE IF EXISTS option_items;
DROP TABLE IF EXISTS option_groups;
DROP TABLE IF EXISTS dishes;
DROP TABLE IF EXISTS menu_categories;

DROP TABLE IF EXISTS table_codes;
DROP TABLE IF EXISTS tables;

DROP TABLE IF EXISTS store_ai_usage;
DROP TABLE IF EXISTS wallet_ledger;
DROP TABLE IF EXISTS wallets;
DROP TABLE IF EXISTS subscriptions;
DROP TABLE IF EXISTS platform_pricing;
DROP TABLE IF EXISTS auth_sessions;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS stores;

SET FOREIGN_KEY_CHECKS=1;
