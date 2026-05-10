-- =========================================================
-- V12 · 品牌图标预设(2026-05-10)· FR-1 + FR-34 升级
-- =========================================================
-- 给 family 加 logo_preset(icon1..icon4),默认 icon2 — 同时驱动 web 头部 / favicon /
-- iOS apple-touch-icon / PWA manifest 4 处图标,实现"切一次同步全平台"。
-- 优先级:logo_path(自定义 WebP)用于 web,但 click 预设按钮会一并清空 path,
-- 实现"预设赢一切统一"。iOS / PWA 永远只看 logo_preset(WebP 不适合做 iOS icon)。
-- =========================================================

ALTER TABLE family
  ADD COLUMN logo_preset VARCHAR(16) NOT NULL DEFAULT 'icon2'
  AFTER logo_path;

UPDATE family
   SET logo_preset = 'icon2'
 WHERE logo_preset IS NULL OR logo_preset = '';
