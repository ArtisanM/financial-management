-- V8: 修订种子成员的 must_change_pw 标志。
--     V2 种子 + DevSeedRunner 之前一直把 must_change_pw 留为 1,导致登录后立即被强制改密 Interceptor 拦截。
--     本期把"密码已被 DevSeedRunner 改过(已不是 PLACEHOLDER)"的成员标记为不强制改。

UPDATE member
   SET must_change_pw = 0
 WHERE must_change_pw = 1
   AND password_hash NOT LIKE 'PLACEHOLDER%';
