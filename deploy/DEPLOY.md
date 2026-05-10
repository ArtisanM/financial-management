# 家庭账房 · 部署手册

覆盖两个场景:**A. 首次部署**(对方机器零基础) · **B. 后续迭代**(已部署过)。

---

## 0. 假设 / 前提

- **本地**:你已经 clone 仓库 + 能跑 `mvn package`(Java 21 + Maven 3.9+)
- **目标机**:Ubuntu 22+ / Debian 12+,你有 ssh 访问 + sudo 权限
- 目标机能从公网拉 apt 包(装 JDK / MySQL 用)
- 域名反代是可选的;最小可用只需要服务器开放一个 TCP 端口(默认 20000)

> **RHEL/CentOS/Alibaba Cloud Linux 用户**:`init-prod.sh` 第 1-2 步的 `apt` 改成 `dnf` 即可,其余通用。

## 1. 命令速查表

| 场景 | 本地命令 | 目标机命令 | 耗时 |
|---|---|---|---|
| **A. 首次部署** | `bash deploy/push-to-prod.sh user@host` | `cd ~/finance-deploy && sudo bash deploy/init-prod.sh` | ~5 分钟 |
| **B. 后续迭代** | `bash deploy/push-to-prod.sh user@host` | `cd ~/finance-deploy && bash deploy/deploy-prod.sh user@host`<br/>(或本地直接 `bash deploy/deploy-prod.sh user@host`) | ~30 秒 |

---

## A. 首次部署(零基础到能用)

### A.1 本地推送

```bash
# 在本地仓库根目录
bash deploy/push-to-prod.sh user@prod.host
```

做 4 件事:
1. 校验 git tree 干净(不干净会询问是否继续)
2. 校验 ssh 可达
3. 本地 `mvn package` 出 `target/app.jar`
4. scp 全部交付物到对方 `~/finance-deploy/`(含 jar + V1..V12 SQL + apply.sh + systemd unit + nginx 模板 + backup 模板 + 三个部署脚本)

完成后会打印下一步命令。

### A.2 SSH 进对方机器,跑 init

```bash
ssh user@prod.host
cd ~/finance-deploy
sudo bash deploy/init-prod.sh
```

`init-prod.sh` 一气呵成做 12 步,**每步幂等**(失败可重跑):

| 步 | 做什么 | 失败的代价 |
|---|---|---|
| 0 | 预飞 + OS 检测 | 没动任何东西 |
| 1 | 装 Java 21(检测到已有就 skip)| 没动 |
| 2 | 装 + 启动 MySQL 8 | 没动 |
| 3 | 创建 `finance` 系统用户 | 已存在则 skip |
| 4 | 建 `/opt/finance/`、`/var/finance/uploads/`、`/var/backup/finance/` | 已存在则 skip |
| 5 | 建 MySQL DB + finance 用户 + GRANT(交互输入密码,回车自动生成 24 字符)| 已存在用现有 |
| 6 | 写 `/etc/finance.env`(权限 640,owner root:finance)| 已存在 skip |
| 7 | 把 `db/apply.sh` + V1..V12 拷到 `/opt/finance/db/`,跑 apply.sh,走 `schema_history` 表幂等 | 已 apply 的自动 skip |
| 8 | 检查是否含 dev seed 数据(>5 个账户),警告 + 给清理 SQL | 不强制 |
| 9 | 把 `app.jar` 拷到 `/opt/finance/app.jar` | 文件覆盖 |
| 10 | 装 `/etc/systemd/system/finance.service`(自动校准 java 二进制路径)+ `/etc/sudoers.d/finance` 允许 finance 用户走 NOPASSWD 的 cp/restart 命令(供后续 deploy-prod.sh)| 已有 skip / 覆盖 |
| 11 | 装 `finance-backup.timer`(systemd 定时 mysqldump)| 模板缺则 skip |
| 12 | `systemctl restart finance` + 轮询 `/health` 30s 上限 | 起不来打印 30 行 journalctl |

完成时打印:
- 服务监听 URL
- 默认账号 `diwa / demo1234`(V2 seed 灌入,**首次登录立刻去 `/profile/password` 改**)
- 如果有 demo 数据,给清理 SQL
- 后续迭代部署的命令

### A.3 浏览器收尾

1. 访问 `http://<server-ip>/login`(若装了 nginx 走 :80)或 `http://<server-ip>:20000/login`(没装 nginx),用 `diwa / demo1234` 登录
2. **立刻**去 `/profile/password` 改密码
3. (可选)`/admin/family` 上传自定义 logo 或选 4 张预设图标之一
4. (可选)如果是干净 prod 库要清 demo 数据,跑 `init-prod.sh` 输出的 TRUNCATE SQL,然后到 `/admin/periods` 点"立即开下一周期"

### A.4 nginx 反代(`init-prod.sh` 步 13 自动跑;漏跑 / 后补也 OK)

`init-prod.sh` 第 13 步会问 `现在装 nginx 反代到 :80 吗? [Y/n]`,默认 Y。
回 N 跳过的话,后续随时可在 prod 机器上跑:

```bash
cd ~/finance-deploy
sudo bash deploy/nginx-setup.sh                       # 默认 server_name=_(任意 Host)
sudo bash deploy/nginx-setup.sh finance.example.com   # 指定域名
```

`nginx-setup.sh` 做 5 件事:
1. 装 nginx(若没装)
2. 渲染配置到 `/etc/nginx/sites-available/finance.conf`(自动从 `/etc/finance.env` 读 `SERVER_PORT`)
3. enable + 移除默认 80 站点(避免端口冲突)
4. **把 Spring 绑回 `127.0.0.1`**(往 `/etc/finance.env` 加 `SERVER_ADDRESS=127.0.0.1` + restart finance),关 :20000 公网入口
5. `nginx -t` 校验 + `systemctl reload nginx` + 双向健康检查(`:20000/health` + `:80/health` 都 200)

完成后:
- 公网只能从 `:80` 进,`:20000` 只接受 nginx 走 loopback 的反代
- **强烈建议**云控制台「安全组」删掉 :20000 的公网入站规则(或 `sudo ufw deny 20000/tcp`),双保险
- 上 HTTPS:`sudo apt install certbot python3-certbot-nginx && sudo certbot --nginx -d your-domain.com`,certbot 自动改 finance.conf 加 `listen 443 ssl` + 续签 cron

### A.4 验收清单

- [ ] `curl http://127.0.0.1:20000/health` 返回 `{"status":"UP"}`
- [ ] `/dashboard` 登录后渲染完整(含 5 张 KPI 卡 + 4 张图表)
- [ ] `/admin/family` 见 4 缩略图 gallery,默认 icon2 高亮
- [ ] `/entry` 余额 input 含 `onfocus="this.select()"` + ✕ 清空按钮
- [ ] 切币种 USD → 数字真正变(首次 ~ 1.5s 等 frankfurter)
- [ ] iPhone Safari "添加到主屏" 看到 icon2(或当前选择的 preset)硬币图标

---

## B. 后续迭代(已部署过)

### B.1 本地修改 + commit

```bash
# 改了代码后
git add -A
git commit -m "..."
git push origin master    # 强烈建议先推 git remote,异地备份
```

### B.2 本地部署一键

```bash
bash deploy/deploy-prod.sh user@prod.host
```

这一行做 7 步,任意一步失败立即停 + 打印明确的回滚步骤:

| 步 | 做什么 | 失败的代价 |
|---|---|---|
| 0 | 预飞:本地 git clean / ssh / `/etc/finance.env` 就位 | 没动远程 |
| 1 | 本地 `mvn package` | 没动远程 |
| 2 | 远程 `mysqldump | gzip` 到 `/var/backup/finance/pre-deploy-{ts}.sql.gz` | 不动 prod 数据 |
| 3 | scp 新 jar 到 `app.jar.new`(不动旧 jar)+ 同步迁移 SQL | 旧 jar 仍跑 |
| 4 | 远程 `db/apply.sh`,走 `schema_history` 幂等 | 旧 jar 还在跑(若新增了列,旧 jar 不读它,无副作用) |
| 5 | 备份旧 jar 到 `app.jar.prev` + 切新 jar + restart | jar 已切但还没起 |
| 6 | 轮询 `/health`(每 2s 一次,24s 上限) | 起不来 → 立即按下方回滚 |
| 7 | 烟测:登录 + `/dashboard` 渲染完整 | 路由破了 → 立即按下方回滚 |

成功后输出 DB 备份位置 + 旧 jar 位置,24h 后可清。

### B.3 回滚(deploy-prod.sh 失败时已自动打印,这里再贴一份)

```bash
# 1. 还原 jar
ssh user@host "sudo /bin/systemctl stop finance \
            && sudo /bin/cp /opt/finance/app.jar.prev /opt/finance/app.jar \
            && sudo /bin/systemctl start finance"

# 2. 若新迁移已 apply 且想回退 schema:
ssh user@host "gunzip < /var/backup/finance/pre-deploy-YYYYMMDD-HHMMSS.sql.gz \
            | mysql -ufinance -p\$PASS finance"

# 3. 若只是想"假装从没 apply 过 V12",让下次 deploy 重跑:
ssh user@host "mysql -ufinance -p\$PASS finance \
            -e \"DELETE FROM schema_history WHERE filename='V12__family_logo_preset.sql';\""
```

---

## C. 文件总览

```
deploy/
├── push-to-prod.sh        ← 本地构建 + 推送(scenario A 用 / scenario B 用)
├── init-prod.sh           ← 对方机器零基础首次部署(scenario A)
├── deploy-prod.sh         ← 对方已 init 过,后续迭代发版(scenario B)
├── finance.service        ← systemd unit 模板(init-prod 自动校准 java 路径后装到 /etc/systemd)
├── finance.env.example    ← /etc/finance.env 配置示例
├── finance-backup.service ← 备份 systemd 单元(配 timer 定时跑 backup.sh)
├── finance-backup.timer   ← 备份调度(每天 03:30)
├── backup.sh              ← mysqldump | gzip → /var/backup/finance/
├── nginx-finance.conf.example  ← nginx 反代模板(可选)
├── gen-icons.sh           ← 一次性:从 apple-touch-icon.svg 渲三档默认 PNG(已不用,保留兼容)
├── gen-presets.sh         ← 一次性:从 icons/icon{1..4}.* 缩出 16 张预设 PNG
└── DEPLOY.md              ← 本文件

db/
├── apply.sh               ← 幂等 migration runner(用 schema_history 表)
└── migration/
    ├── V1__init.sql       ← schema(prod 必须)
    ├── V2__seed.sql       ← 1 family + 2 member + 13 模板 + 8 现金流类别(prod 必须)
    ├── V3__step2_dev_data.sql      ← 11 demo 账户 + 2026-05 OPEN(dev,prod 可清)
    ├── V4__step3_data.sql          ← 12 历史周期数据(dev,prod 可清)
    ├── V5__step4_admin_data.sql    ← backup_log 样例 + fx_rate 补全(dev,prod 可清)
    ├── V6..V11             ← 各种 schema 演进(都 prod 必须)
    └── V12__family_logo_preset.sql ← v0.2 加 logo_preset 列(prod 必须)
```

---

## D. 故障排查 cheat sheet

| 症状 | 排查 |
|---|---|
| `init-prod.sh` 第 5 步 `mysql` 失败 | 检查 `mysql -uroot` 能否本地登入(默认 socket 鉴权)。Ubuntu 默认 root 走 unix_socket,sudo 进 mysql 即可 |
| 第 7 步迁移卡 V12 (`Duplicate column logo_preset`)| 说明列已存在但 schema_history 没记录。手动:`UPDATE schema_history SET ... ` 或 `INSERT INTO schema_history (filename, checksum) VALUES ('V12__...', '<actual sha256>')` |
| 第 12 步 `/health` 30s 起不来 | `journalctl -u finance --no-pager -n 100`。常见:DB_PASS 错(看 `Access denied`)/ 端口被占(`Address already in use`)/ Java 参数错 |
| 切币种 USD 显示 ¥ 数字 | 见 v0.2 第三轮 BUG-FIX:`fx_rate` 缺失会即时调 frankfurter API。若 prod 防火墙挡了出网,需要管理员到 `/admin/fx` 手填一行 |
| iOS 主屏图标不更新 | iOS OS 级缓存,用户需删主屏图标重新"添加到主屏"。无解 |
| `deploy-prod.sh` 第 3 步 scp 卡密码 | 配 ssh key:`ssh-copy-id user@host` |
| `deploy-prod.sh` 第 5 步 sudo 卡密码 | `init-prod.sh` 写过 `/etc/sudoers.d/finance`,登入 prod 后用 `sudo -ln` 验下 NOPASSWD 是否生效 |

---

## E. 安全 / 加固(可选)

- **改默认密码**:`diwa / demo1234`(seed 出厂)登入后立即在 `/profile/password` 改
- **限制访问**:nginx 上加 `allow <家庭固定 IP>; deny all;` 或上 OAuth 反代
- **TLS**:Let's Encrypt + nginx;`deploy/nginx-finance.conf.example` 是 80 端口模板,自己加 `listen 443 ssl` + `ssl_certificate`
- **定时备份**:`init-prod.sh` 已 enable `finance-backup.timer`(每日 03:30 跑 `backup.sh` → `/var/backup/finance/finance-{date}.sql.gz`,保留 56 天)。**异地备份**自己加(rclone 到对象存储 / scp 到第二台机)
- **systemd 加固**:`finance.service` 已含 `NoNewPrivileges=true`、`ProtectSystem=strict`、`ReadWritePaths=...`,基本够。再硬可加 `IPAddressAllow=` 白名单
- **MySQL 加固**:`mysql_secure_installation` 走一遍(删 anonymous user / 远程 root / test DB)
