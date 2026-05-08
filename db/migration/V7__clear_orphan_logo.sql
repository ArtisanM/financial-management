-- V7: 清掉早期种子数据里"指向不存在文件"的 family.logo_path,
--     让 UI 在未上传 logo 时正常回退到默认 SVG(/img/default-logo.svg)。
-- 仅清形如 'family-N/logo.*' 但物理文件可能缺失的种子值;真实上传过的不会触发(因为种子数据写死 family-1/logo.webp)。

UPDATE family
   SET logo_path = NULL
 WHERE logo_path = 'family-1/logo.webp';
