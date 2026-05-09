-- =========================================================
-- V11 · v0.2 产品类目体系 + 风险评级 + 基准对照(FR-40d)
-- =========================================================
-- 1) 新建 product_category 静态预置表(16 行)
-- 2) account 加 product_category_code / risk_level_override 两列
-- 3) 已有 account 按 type 回填 default category(无 NULL)
-- 4) 加索引 + 外键
-- 对应 PRD § FR-40d / TDD § 决策 4
-- =========================================================

-- ============ 1. product_category 表 ============
CREATE TABLE product_category (
    code             VARCHAR(40)  NOT NULL,
    display_name     VARCHAR(80)  NOT NULL,
    risk_level       TINYINT      NOT NULL DEFAULT 0
                                  COMMENT '0=无 / 1 极低 / 2 低 / 3 中低 / 4 中 / 5 中高 / 6 极高',
    benchmark_label  VARCHAR(80)  NULL
                                  COMMENT '基准指数标签,如「沪深 300」「标普 500 / QQQ」;NULL = 无稳定基准',
    benchmark_pct    DECIMAL(5,2) NULL
                                  COMMENT '长期年化基准 % · 如 8.00 表示 8%',
    applicable_types VARCHAR(80)  NOT NULL
                                  COMMENT '逗号分隔: "CASH,WEALTH" / "STOCK" / "*"(全部)',
    description      VARCHAR(255) NULL,
    display_order    SMALLINT     NOT NULL DEFAULT 100,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='产品类目 + 风险等级 + 基准 · v0.2 引入';

-- ============ 2. 预置 16 行 ============
-- 按"风险递增"display_order 排序
INSERT INTO product_category (code, display_name, risk_level, benchmark_label, benchmark_pct, applicable_types, description, display_order) VALUES
    ('CASH_DEPOSIT',  '现金活期 / 工资卡',  1, '银行活期利率',      0.35, 'CASH',           '银行活期账户、储蓄卡、备用金',                            10),
    ('MONEY_FUND',    '货币基金',           1, '7 日年化均值',     2.00, 'CASH,WEALTH',    '余额宝类货币基金,T+0 赎回',                              20),
    ('BANK_WEALTH',   '银行理财(R2 稳健)',  2, '同业基准',          3.20, 'WEALTH',         '银行 R2 等级稳健理财',                                    30),
    ('SHORT_BOND',    '短债基金',           2, '中债短债指数',      3.50, 'WEALTH',         '短期债券基金 / 短期国债',                                  40),
    ('LONG_BOND',     '长债基金 / 国债',    3, '中债总指数',        4.50, 'WEALTH',         '长期债券基金 / 国债',                                      50),
    ('GOLD',          '黄金 / 贵金属',      3, '上海金',            6.00, 'STOCK,OTHER',    '黄金 ETF / 实物贵金属',                                    55),
    ('PROPERTY_RES',  '自住房产',           2, '70 城新建商品房价',  4.00, 'PROPERTY',       '自住住房 / 主观估值',                                      60),
    ('PROPERTY_INV',  '投资性房产(含租金)', 3, '70 城价 + 租金回报', 5.50, 'PROPERTY',       '投资性房产 / 含租金回报',                                  65),
    ('MIXED_FUND',    '混合型基金',         4, '沪深 300 60% + 中债 40%', 6.00, 'STOCK,WEALTH', '混合型基金 / 偏股偏债组合',                              70),
    ('A_STOCK',       'A 股 / A 股基金',    5, '沪深 300',          8.00, 'STOCK',          '中国 A 股 / A 股基金 / ETF',                              80),
    ('US_STOCK',      '美股 / 美股基金',    5, '标普 500 / QQQ',   10.50, 'STOCK',          '美股 / 美股基金 / 美股 ETF',                              85),
    ('HK_STOCK',      '港股 / 港股基金',    5, '恒生指数',          6.50, 'STOCK',          '港股 / 港股基金 / 港股 ETF',                              90),
    ('CRYPTO',        '加密货币',           6, 'BTC',               NULL, 'OTHER',          '加密货币 / BTC 等高波动数字资产 / 无稳定基准',            95),
    ('FUTURES',       '期货 / 衍生品',      6, NULL,                NULL, 'OTHER',          '期货 / 期权 / 杠杆衍生品 / 无稳定基准',                    96),
    ('LIABILITY',     '负债账户',           0, NULL,                NULL, 'LOAN',           '房贷 / 车贷 / 信用卡 / 任何负债',                          99),
    ('OTHER',         '其它',               3, NULL,                NULL, '*',              '未明确归类的资产',                                       100);

-- ============ 3. account 加列 ============
ALTER TABLE account
    ADD COLUMN product_category_code VARCHAR(40) NULL
        COMMENT 'v0.2 产品类目;关联 product_category.code',
    ADD COLUMN risk_level_override   TINYINT     NULL
        COMMENT 'v0.2 用户覆盖类目默认风险等级(NULL = 沿用类目)';

-- ============ 4. 已有 account 回填 default category ============
-- 按 account.type 映射:CASH→CASH_DEPOSIT / STOCK→A_STOCK / WEALTH→BANK_WEALTH
--                       LOAN→LIABILITY / PROPERTY→PROPERTY_RES / OTHER→OTHER
UPDATE account SET product_category_code = 'CASH_DEPOSIT'  WHERE type = 'CASH'     AND product_category_code IS NULL;
UPDATE account SET product_category_code = 'A_STOCK'       WHERE type = 'STOCK'    AND product_category_code IS NULL;
UPDATE account SET product_category_code = 'BANK_WEALTH'   WHERE type = 'WEALTH'   AND product_category_code IS NULL;
UPDATE account SET product_category_code = 'LIABILITY'     WHERE type = 'LOAN'     AND product_category_code IS NULL;
UPDATE account SET product_category_code = 'PROPERTY_RES'  WHERE type = 'PROPERTY' AND product_category_code IS NULL;
UPDATE account SET product_category_code = 'OTHER'         WHERE type = 'OTHER'    AND product_category_code IS NULL;

-- ============ 5. 加外键 + 索引 ============
ALTER TABLE account
    ADD CONSTRAINT fk_account_product_category
        FOREIGN KEY (product_category_code) REFERENCES product_category(code);

CREATE INDEX idx_account_product_category ON account(product_category_code);
