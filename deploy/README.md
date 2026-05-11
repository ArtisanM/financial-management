# 家庭账房 · 部署

参考 openclash / vaultwarden 等自托管项目的部署形态:**服务器上 git clone + 一行命令**,无所谓本机推送、SSH 串行执行那些复杂套路。

## 部署条件

- 一台公网 Linux 服务器:Ubuntu 22+ / Debian 12+ / RHEL 9+ / Alibaba Cloud Linux 都行
- 你能 SSH 进去 + 有 sudo
- 一个公网 80 端口(可选 443),能从公网拉 apt 包

---

## 首次部署(2 步)

```bash
# 1. 把本仓库拉到服务器上(任意位置;建议 /opt/src 或 ~)
sudo apt install -y git    # Ubuntu/Debian;RHEL 系是 dnf
git clone https://gitlab.com/xblteam/financial-management.git
cd financial-management

# 2. 跑一次部署
sudo bash deploy/deploy.sh
```

`deploy.sh` 会:

1. 装 JDK 21 / Maven / MySQL 8 / nginx / 辅助工具
2. 创建 finance 系统用户 + 目录(`/opt/finance`、`/var/finance/uploads`、`/var/backup/finance`)
3. 建 MySQL 库 + 用户(交互输密码或自动生成 24 字符)
4. 写 `/etc/finance.env`(系统配置,640 权限)
5. 编译 jar(`mvn package`)
6. 应用所有 `V*__*.sql` 迁移
7. 设种子用户临时密码(`diwa` / `wangergou` + 你设的密码,登入后强制改)
8. 清掉 V3/V4/V5 灌的 dev 演示数据(`sentinel` + "真实数据探测"双保险,不会误删真用户数据)
9. 装 systemd unit(`finance.service`)+ NOPASSWD sudoers
10. 启服务 + `/health` 健康检查
11. 装 nginx 反代 :80 → :20000(交互式 prompt;不要也行)

完事后浏览器访问 `http://<server-ip>/` 即可。

---

## 后续发版迭代(1 步)

每次代码更新后,**在服务器上**:

```bash
cd ~/financial-management              # 仓库目录
git pull                                # 拉新代码
sudo bash deploy/deploy.sh              # 一键迭代
```

`deploy.sh` 检测到已上线,进入「迭代模式」,做:

1. `mysqldump | gzip` 备份 → `/var/backup/finance/pre-deploy-{ts}.sql.gz`(+ `gunzip -t` 完整性校验)
2. 列出待 apply 的增量 `V*.sql`,**交互确认才执行**(`schema_history` 表防重)
3. `mvn package` 编译新 jar
4. 旧 jar 备份到 `app.jar.prev` + 新 jar 切入
5. `systemctl restart finance`
6. `/health` 30s 轮询 + `/login` 烟测
7. **任意步骤失败 → 自动 `app.jar.prev → app.jar` + restart + 健康复检**(DB 备份保留不动,因 schema 多数 backward-compat,老 jar 兼容新表)

---

## 回滚(失败时一行)

```bash
sudo bash deploy/rollback.sh
```

把 `app.jar.prev` 还原到 `app.jar` + restart + 健康检查。**不动 DB**(若 DB 也要还原见脚本输出的 `gunzip ... | mysql` 提示)。

---

## 常用命令

```bash
sudo systemctl status finance          # 服务状态
sudo journalctl -u finance -f          # 实时日志
sudo systemctl restart finance         # 手动重启(deploy.sh 内部已经做)
mysql -ufinance -p$DB_PASS finance     # 进 DB(密码在 /etc/finance.env)
ls /var/backup/finance/                # 看 DB 备份历史
```

---

## 自动备份

`deploy.sh` 装了 `finance-backup.timer`(systemd 定时器),每天 03:30 自动 `mysqldump | gzip` 到 `/var/backup/finance/`,默认保留 56 天(`RETENTION_DAYS` 在 `/etc/finance.env`)。

```bash
sudo systemctl list-timers finance-backup  # 看下次跑的时间
sudo systemctl start finance-backup        # 手动触发一次
```

---

## HTTPS(可选,但 prod 推荐)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

certbot 自动改 `finance.conf` 加 `listen 443 ssl` + 配 cron 续签。`deploy.sh` 重跑时检测到 `ssl_certificate` 会避让 certbot 改的配置,不冲突。

---

## 故障排查

| 症状 | 解 |
|---|---|
| `deploy.sh` 步 7 mysql 失败 | `sudo mysql` 看能不能进(Ubuntu 默认 socket 鉴权) |
| `deploy.sh` 步 14 服务 30s 不起 | 看 `journalctl -u finance --no-pager -n 100`(脚本自动打了 30 行);DB 密码错 / 端口被占最常见 |
| 切币种 USD 显示 ¥ | `fx_rate` 缺,见 `/admin/fx`;或服务器拉不通 frankfurter.dev(防火墙) |
| 自动备份没生成 | `sudo systemctl status finance-backup.timer` 看 |
| 想重置成"刚装好"状态 | 删 `/opt/finance/.prod-cleaned` + 重跑 `deploy.sh`(真实数据探测会拦你,见警告 SQL) |

---

## 文件清单

```
deploy/
├── deploy.sh                       ← 主脚本(首装 + 迭代都用它)
├── rollback.sh                     ← 紧急回滚
├── nginx-setup.sh                  ← nginx 单独配置(deploy.sh 内部会调)
├── finance.service                 ← systemd unit 模板
├── finance.env.example             ← /etc/finance.env 配置示例(参考用)
├── nginx-finance.conf.example      ← nginx 反代模板(__PORT__ __SERVER_NAME__ 占位)
├── backup.sh                       ← 备份脚本(finance-backup.timer 调)
├── finance-backup.{service,timer}  ← systemd 定时备份
├── gen-presets.sh                  ← 一次性:生成 4 套图标 PNG
├── gen-icons.sh                    ← 历史:生成默认 icon PNG(已被 gen-presets 取代)
└── README.md                       ← 本文件
```
