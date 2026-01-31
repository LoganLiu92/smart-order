-- Clear all data (keeps schema)
SET FOREIGN_KEY_CHECKS=0;

TRUNCATE TABLE cart_item_options;
TRUNCATE TABLE cart_items;
TRUNCATE TABLE carts;

TRUNCATE TABLE order_item_options;
TRUNCATE TABLE order_items;
TRUNCATE TABLE orders;

TRUNCATE TABLE option_items;
TRUNCATE TABLE option_groups;
TRUNCATE TABLE dishes;
TRUNCATE TABLE menu_categories;

TRUNCATE TABLE table_codes;
TRUNCATE TABLE tables;

TRUNCATE TABLE store_ai_usage;
TRUNCATE TABLE wallet_ledger;
TRUNCATE TABLE wallets;
TRUNCATE TABLE subscriptions;
TRUNCATE TABLE platform_pricing;
TRUNCATE TABLE auth_sessions;
TRUNCATE TABLE users;
TRUNCATE TABLE stores;

SET FOREIGN_KEY_CHECKS=1;
