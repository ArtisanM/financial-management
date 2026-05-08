# 家庭账房 · v0.1

家庭资产管理 Web 应用。详见:
- 产品需求 · [`prd/v0.1.md`](prd/v0.1.md)(v0.1 已封板)
- 技术设计 · [`tech-design/v0.1.md`](tech-design/v0.1.md)
- 预览稿 · [`preview/index.html`](preview/index.html)

## 进度

- [x] **Step 1** · 项目骨架 + DDL + 登录
- [x] **Step 2** · 账户 / 模板向导 / 周期 / 待办 / 填报骨架
- [x] **Step 3** · FactView + 13 指标 + Dashboard + Reports + 单测 16/16
- [x] **Step 4** · 多币种 / Logo 上传 / 12 admin 子页 / 备份元数据
- [x] **Step 5** · systemd / nginx / backup.sh / 部署 SOP

## 本地开发

### 前置

- JDK 21+
- Maven 3.9+
- MySQL 8(本地或 Docker 都行)

### 一次性环境

```bash
# 起 MySQL(本地 apt 装的):
sudo systemctl start mysql

# 建库 + 用户:
mysql -u root -p <<'SQL'
CREATE DATABASE finance CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'finance'@'localhost' IDENTIFIED BY 'finance';
GRANT ALL ON finance.* TO 'finance'@'localhost';
FLUSH PRIVILEGES;
SQL
```

### 启动

```bash
# 1) 跑 schema 迁移
DB_USER=finance DB_PASS=finance DB_NAME=finance ./db/apply.sh

# 2) 启动应用(默认 dev profile)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn spring-boot:run
```

打开 http://localhost:8080/login,登录:

| 用户名 | 密码 |
|---|---|
| `zhangwei` | `demo1234` |
| `lijing`   | `demo1234` |

> 启动时 `DevSeedRunner`(仅 dev profile)检测到 `password_hash` 为 `PLACEHOLDER...` 会自动设为 `demo1234` 的 bcrypt 值。

## 单元测试

```bash
mvn -B test       # 16 calc/factview 纯函数测试
```

## 路由清单(共 26 个)

**公共**
- `GET  /login`, `POST /login`, `POST /logout`

**核心日常**
- `GET  /` → redirect to `/dashboard`
- `GET  /dashboard?range=&accounts=&currency=` (HTMX 局部刷新)
- `GET  /reports`
- `GET  /accounts`, `POST /accounts/{id}/edit`, `POST /accounts/{id}/archive` 等
- `GET  /entry?period=&mine=`, `POST /entry/{accountId}/balance` (HTMX)
- `GET  /my-todos`

**管理**
- `GET  /admin`
- `GET  /admin/family`, `POST /admin/family`(更新)
- `POST /admin/family/logo`(FE Canvas 压缩 → multipart 上传)
- `POST /admin/family/logo/remove`
- `GET  /admin/members`, `POST /admin/members/{id}`, `POST /admin/members/{id}/reset-password`
- `GET  /admin/account-templates`(只读)
- `GET  /admin/cash-flow-categories`(只读)
- `GET  /admin/periods`, `POST /admin/periods/{id}/reopen`
- `GET  /admin/reminders`(只读)
- `GET  /admin/fx`, `POST /admin/fx/override`, `POST /admin/fx/fetch`
- `GET  /admin/backup`(查看 backup_log)
- `GET  /admin/audit?type=`
- `GET  /admin/calc-tweaks`(只读)

## 生产部署

### 1. 服务器一次性准备

```bash
# 服务器:Ubuntu 24 / Debian 12 + JDK 21 + MySQL 8 + nginx
sudo apt-get install -y openjdk-21-jdk-headless mysql-server-8.0 nginx

# 应用用户
sudo useradd -r -s /bin/false finance
sudo mkdir -p /opt/finance/{db/migration,logs} /var/finance/uploads /var/backup/finance
sudo chown -R finance:finance /opt/finance /var/finance/uploads /var/backup/finance

# DB
sudo mysql -e "CREATE DATABASE finance CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
sudo mysql -e "CREATE USER 'finance'@'localhost' IDENTIFIED BY 'CHANGE_ME';"
sudo mysql -e "GRANT ALL ON finance.* TO 'finance'@'localhost'; FLUSH PRIVILEGES;"

# 配置
sudo cp deploy/finance.env.example /etc/finance.env
sudo chmod 600 /etc/finance.env
sudo chown finance:finance /etc/finance.env
sudo nano /etc/finance.env       # 填好 DB_PASS / REMEMBER_ME_KEY 等

# systemd unit + timer
sudo install -m 644 deploy/finance.service              /etc/systemd/system/
sudo install -m 644 deploy/finance-backup.service       /etc/systemd/system/
sudo install -m 644 deploy/finance-backup.timer         /etc/systemd/system/
sudo systemctl daemon-reload

# nginx
sudo install -m 644 deploy/nginx-finance.conf.example /etc/nginx/sites-available/finance.conf
# 改 server_name 为你的实际域名
sudo ln -sf /etc/nginx/sites-available/finance.conf /etc/nginx/sites-enabled/finance.conf
sudo nginx -t && sudo systemctl reload nginx
```

### 2. 首次部署

本地终端:
```bash
REMOTE=user@yourserver ./deploy/deploy.sh
# 自动:打 jar → scp → ssh apply.sh → ssh restart finance → 烟测 GET /login
```

部署后第一次访问 `/login`,种子用户 `zhangwei` / `lijing` 的 password_hash 仍为 `PLACEHOLDER` — **生产没 dev seed,你需要手动重置一次密码**:

```bash
# 在服务器上,用 mysql 把 PLACEHOLDER 换成 bcrypt hash:
sudo -u finance java -jar /opt/finance/app.jar --reset-pw zhangwei NEWPASS  # ← 计划项,v0.1 简化:直接改 SQL
```

或者最简单:在本地用 Spring Boot 的 BCryptPasswordEncoder 算出 hash,直接 UPDATE。

### 3. 启用备份 timer

```bash
sudo systemctl enable --now finance-backup.timer
systemctl list-timers finance-backup    # 确认下次触发时间
sudo systemctl start finance-backup.service   # 手动跑一次验证
sudo journalctl -u finance-backup -f          # 看输出
```

之后每周日 03:00 会自动备份,并在 `backup_log` 表 + `/admin/backup` 页可见状态。

## 升级流程(含数据库迁移)

```bash
# 本地 — 假设新增了 V6__add_xxx.sql
git pull
./deploy/deploy.sh   # 自动:打包 → scp → ssh apply.sh(只跑 V6)→ 重启 → 烟测
```

`db/apply.sh` 会跳过已执行的 V*__*.sql,只跑新文件;sha256 校验防意外修改已发布版本。

## 目录结构

```
financial-management/
├── prd/v0.1.md                   # PRD(已封板)
├── tech-design/v0.1.md           # TDD
├── preview/                      # 静态 HTML 预览(15 页,Tailwind via CDN)
├── pom.xml                       # Maven · Spring Boot 3.3 + Java 21 + MyBatis
├── src/main/java/com/family/finance/
│   ├── FinanceApplication.java
│   ├── auth/                     # 登录 · Security · MemberPrincipal
│   ├── domain/                   # POJO + enum (家庭/成员/账户/周期/快照/现金流/转账/汇率/备份/审计)
│   ├── repository/               # MyBatis @Mapper 接口
│   ├── service/                  # 业务服务 + Calculators + FactViewServiceImpl
│   ├── factview/                 # 大宽表抽象(PRD § 5.0)+ FactProjector
│   ├── calc/                     # 纯函数:PnL/Reconciliation/XIRR/TWR/IdentityVerifier
│   ├── web/{account,dashboard,entry,report,todo,admin}/
│   ├── common/                   # HomeController + GlobalModelAdvice
│   └── config/                   # AppProperties + WebMvcConfig + DevSeedRunner
├── src/main/resources/
│   ├── application.yml           # dev/prod profile,DB/Mybatis/multipart 配置
│   ├── mapper/FactMapper.xml     # 复杂 fact view 查询(动态 SQL)
│   ├── static/css/style.css      # 共享样式(从 preview 同步)
│   └── templates/{auth,fragments,accounts,dashboard,entry,reports,admin,...}/  # Thymeleaf
├── src/test/java/                # 16 calc/factview 单测
├── db/
│   ├── migration/V[1-5]__*.sql   # schema + 种子 + step 数据
│   └── apply.sh                  # sha256 防修改的顺序应用器
├── deploy/
│   ├── finance.service           # systemd unit
│   ├── finance.env.example       # 生产环境变量模板
│   ├── nginx-finance.conf.example
│   ├── deploy.sh                 # 本地打包 + ssh 部署
│   ├── backup.sh                 # 每周备份脚本(写 backup_log)
│   ├── finance-backup.service    # systemd oneshot
│   └── finance-backup.timer      # systemd timer · Sun 03:00
└── README.md                     # 本文件
```

## 关键技术决策(详见 TDD)

- **MyBatis** 替代 JPA + JdbcTemplate(简单 CRUD 用注解,FactMapper 复杂查询用 XML)
- **大宽表 fact view 抽象**(TDD § 5.0):所有 Dashboard / Reports / 筛选 / 计算都从 `factview/FactSlice` 投影而来,避免 N 个特殊 SQL
- **TLS 在用户上游脱掉**,本服务接收 HTTP;Cookie `Secure=false`
- **版本化 SQL + sha256 校验**(无 Flyway/Liquibase 依赖)
- **Logo 前端 Canvas 压缩**:浏览器 toBlob('image/webp', 0.82),后端只校验 RIFF magic + 200KB 上限
- **v0.1 不含**:邮件 / 微信 / 短信 / 短信通知 / 服务端图像处理库 / Flyway

## 路线图(v0.2+)

- 邮件 SMTP + 周报推送
- 显示币种切换的 USD 状态 polish + 汇率失败邮件告警
- Reports 移动端 polish(Sankey 在窄屏不可读)
- v0.3:孩子账户 / 多家庭 UI / 持仓级颗粒度
