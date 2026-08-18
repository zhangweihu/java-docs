# 第二十二章 Nginx 反向代理与负载均衡

> 本章目标：理解 Nginx 是什么、为什么每个企业都用它，掌握反向代理、负载均衡、动静分离三大核心场景，能独立完成一个 Spring Boot 服务的 Nginx 上线配置，并了解高可用与 HTTPS 配置。
>
> 前置知识：第九章 Spring Boot（要部署的应用）、第十九章 Docker（推荐用容器跑 Nginx）。

## 22.1 为什么需要 Nginx

### 22.1.1 只有一台服务器的困境

```java
// 第九章的部署方式：一个 jar 一个端口，直接对外
java -jar demo.jar --server.port=8080
```

| 问题 | 表现 |
| --- | --- |
| 单点故障 | 这台服务器挂了，服务就没了 |
| 无法横向扩容 | 再加一台服务器，用户根本不知道往哪访问 |
| 静态资源浪费 Tomcat 连接 | 图片/JS/CSS 也走 Tomcat 线程池，高并发下 Tomcat 被拖垮 |
| 没有负载均衡 | 所有请求压在同一台机器 |
| 安全暴露 | 应用端口直接暴露公网，容易被打 |

### 22.1.2 Nginx 能干什么

```
用户请求 ──► [Nginx 入口网关] ──► 转发给后端服务器
                    │
     ├── 反向代理：隐藏后端细节，统一入口
     ├── 负载均衡：把请求分发给多台服务器
     ├── 动静分离：静态资源自己扛，动态请求转 Tomcat
     ├── 高可用：keepalived 保证 Nginx 自身不挂
     ├── HTTPS：一键开启 TLS 加密
     └── 限流缓存：防刷、加速
```

### 22.1.3 Nginx vs Tomcat（职责不同）

| 对比 | Nginx | Tomcat |
| --- | --- | --- |
| 定位 | Web 服务器 / 反向代理 / 负载均衡 | Java Servlet 容器（应用服务器） |
| 静态资源 | 性能极高（毫秒级响应） | 一般 |
| 并发能力 | 单机 5 万+（事件驱动） | 默认 200 线程，受 JVM 限制 |
| 动态请求 | 不处理，转给后端 | 执行 Java 代码 |
| 默认端口 | 80 / 443 | 8080 |

> 关键认知：Nginx 是**门口的前台**，Tomcat 是**里面的业务员**。前台负责分流接待，业务员负责干实事。

## 22.2 安装与启动

### 22.2.1 Docker 安装（推荐）

```bash
# 1. 拉取镜像（1.25 为当前主线稳定版）
docker pull nginx:1.25

# 2. 先跑一个看看默认页面
docker run -d --name nginx-demo -p 80:80 nginx:1.25

# 3. 浏览器访问 http://localhost 看到 Welcome to nginx! 即成功
```

### 22.2.2 把配置目录挂载出来（企业标准做法）

```bash
# 建目录
mkdir -p /opt/nginx/conf /opt/nginx/html /opt/nginx/logs

# 把容器里的默认配置复制到宿主机（第一次需要）
docker cp nginx-demo:/etc/nginx/nginx.conf /opt/nginx/conf/nginx.conf

# 停掉临时容器，用挂载方式正式启动
docker rm -f nginx-demo
docker run -d --name nginx \
  -p 80:80 -p 443:443 \
  -v /opt/nginx/conf/nginx.conf:/etc/nginx/nginx.conf:ro \
  -v /opt/nginx/conf/conf.d:/etc/nginx/conf.d \
  -v /opt/nginx/html:/usr/share/nginx/html \
  -v /opt/nginx/logs:/var/log/nginx \
  nginx:1.25
```

> 核心思想：**配置文件必须外挂**，否则改配置得进容器，容器一删全没了（呼应第十九章数据卷）。

### 22.2.3 常用命令速查

```bash
nginx -t                    # 检查配置语法（改完配置第一件事）
nginx -s reload             # 平滑重载（不停服生效，企业最常用）
nginx -s stop               # 快速停止
nginx -s quit               # 优雅停止（处理完当前请求再停）
nginx -V                    # 查看编译参数
```

## 22.3 核心概念与配置结构

### 22.3.1 配置文件结构

```nginx
# 全局块：运行用户、进程数、错误日志
user  nginx;
worker_processes  auto;          # 自动等于 CPU 核数

# events 块：连接数配置
events {
    worker_connections  1024;     # 每个 worker 最大连接数
}

# http 块：所有 HTTP 相关配置
http {
    include       /etc/nginx/mime.types;   # 文件类型映射
    sendfile        on;

    # server 块：一个站点/虚拟主机
    server {
        listen       80;                    # 监听端口
        server_name  demo.example.com;      # 域名

        # location 块：URL 路径匹配规则
        location / {
            root   /usr/share/nginx/html;   # 静态文件根目录
            index  index.html index.htm;
        }
    }
}
```

### 22.3.2 location 匹配规则（面试必问）

| 写法 | 含义 | 示例 |
| --- | --- | --- |
| `=` | 精确匹配，最高优先级 | `location = /index.html` |
| `^~` | 前缀匹配，匹配后不再检查正则 | `location ^~ /static/` |
| `~` | 正则匹配（区分大小写） | `location ~ \.(gif|jpg)$` |
| `~*` | 正则匹配（不区分大小写） | `location ~* \.(png)$` |
| `/` | 通用匹配，兜底 | `location /` |

**匹配优先级**：`=` 精确 > `^~` 前缀 > `~`/`~*` 正则 > `/` 通用。

```nginx
location = /favicon.ico { log_not_found off; }        # 精确：图标直接 404 静默
location ^~ /static/ { root /data/static; }           # 前缀：静态资源
location ~* \.(js|css|png|jpg)$ { expires 30d; }      # 正则：缓存 30 天
location / { proxy_pass http://backend; }             # 兜底：转后端
```

## 22.4 反向代理（最核心场景）

### 22.4.1 什么是反向代理

```
正向代理：帮你访问你访问不了的（代理客户端）   例：翻墙
反向代理：帮你挡在服务器前面接客（代理服务器） 例：Nginx
```

用户只知道 Nginx 的地址，**不知道后端有几台、在哪**，这就是"反向"。

### 22.4.2 代理一个 Spring Boot 服务

```nginx
server {
    listen       80;
    server_name  api.example.com;

    location / {
        proxy_pass http://192.168.1.10:8080;   # 转给后端 Spring Boot
        proxy_set_header Host $host;           # 透传域名
        proxy_set_header X-Real-IP $remote_addr;      # 透传真实 IP
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;  # 代理链
        proxy_set_header X-Forwarded-Proto $scheme;   # 透传 http/https

        proxy_connect_timeout 5s;    # 连接后端超时
        proxy_read_timeout    60s;   # 读取后端超时
    }
}
```

> **坑提醒**：后端拿不到用户真实 IP 时，一定是没配 `X-Real-IP`/`X-Forwarded-For`。Java 端要配合使用 `request.getHeader("X-Forwarded-For")` 或 `RemoteAddrFilter`。

### 22.4.3 部署多个应用（虚拟主机）

```nginx
server {                       # 应用 A
    listen 80;
    server_name a.example.com;
    location / { proxy_pass http://192.168.1.10:8081; }
}
server {                       # 应用 B：同一台 Nginx，不同域名
    listen 80;
    server_name b.example.com;
    location / { proxy_pass http://192.168.1.10:8082; }
}
```

## 22.5 负载均衡（高并发核心）

### 22.5.1 场景：两台后端服务器

```
            ┌──► 192.168.1.10:8080（server1）
用户 ──► Nginx ──► 192.168.1.11:8080（server2）
            └──► 192.168.1.12:8080（server3）
```

### 22.5.2 upstream 配置

```nginx
upstream demo-backend {
    server 192.168.1.10:8080 weight=3;    # 权重 3：承担 3/5 流量
    server 192.168.1.11:8080 weight=2;    # 权重 2：承担 2/5 流量
    server 192.168.1.12:8080 down;        # 手动下线（维护中）
    server 192.168.1.13:8080 backup;      # 备用：其他都挂了才启用
    keepalive 32;                         # 长连接池
}

server {
    listen 80;
    server_name api.example.com;
    location / {
        proxy_pass http://demo-backend;   # 指向 upstream 组名
    }
}
```

### 22.5.3 负载均衡策略（面试必问）

| 策略 | 配置 | 特点 | 适用场景 |
| --- | --- | --- | --- |
| 轮询 | 默认 | 请求轮流分发 | 服务器配置相同 |
| 权重 | `weight=3` | 按比例分发 | 服务器性能不同 |
| IP_Hash | `ip_hash;` | 同一 IP 固定到同一台 | 需要会话保持（无 Redis） |
| 最少连接 | `least_conn;` | 分给当前连接最少的 | 请求耗时差异大 |
| URL_Hash | `hash $request_uri;` | 相同 URL 固定一台 | 缓存命中友好 |

```nginx
upstream demo-backend {
    ip_hash;                    # 会话保持：用户登录状态不丢失
    server 192.168.1.10:8080;
    server 192.168.1.11:8080;
}
```

> 企业最佳实践：**不用 ip_hash，用 Redis 共享 Session/JWT 无状态登录**（呼应第十二、十三章），这样后端随便扩缩容都不影响会话。

### 22.5.4 健康检查（自动踢掉故障节点）

```nginx
upstream demo-backend {
    server 192.168.1.10:8080 max_fails=3 fail_timeout=10s;  # 10 秒内失败 3 次，标记不可用
    server 192.168.1.11:8080 max_fails=3 fail_timeout=10s;
}
```

## 22.6 动静分离

### 22.6.1 原理

```
/user/* 的请求 ──► 后端 Tomcat（动态）
/static/* 的请求 ──► Nginx 本地文件（静态，不走 Tomcat）
```

静态资源不占 Tomcat 线程，同时 Nginx 支持 `expires` 让浏览器缓存，图片 JS 直接本地/CDN 出。

### 22.6.2 配置

```nginx
# 静态资源：Nginx 自己返回 + 强缓存
location ^~ /static/ {
    alias /data/static/;          # alias：路径替换
    expires 30d;                  # 浏览器缓存 30 天
    access_log off;               # 静态资源不记日志，省 IO
}

# 动态请求：转后端
location / {
    proxy_pass http://demo-backend;
}
```

> **root vs alias 区别（面试常问）**：`root` 把完整 URI 拼在根目录后（`/data/static/static/a.png`）；`alias` 直接用替换后的路径（`/data/static/a.png`）。

### 22.6.3 Java 项目动静分离实战

```nginx
server {
    listen 80;
    server_name www.example.com;

    # 1. 前端页面（Vue/React 打包产物）
    location / {
        root /data/www/dist;      # npm run build 的输出目录
        try_files $uri $uri/ /index.html;   # 前端路由刷新不 404（关键！）
    }

    # 2. 后端 API
    location /api/ {
        proxy_pass http://demo-backend;     # 注意：/api/ 会原样转发
    }

    # 3. 图片等上传文件
    location /uploads/ {
        alias /data/uploads/;
    }
}
```

> Vue/React 用 history 路由时，刷新页面会 404，必须加 `try_files $uri $uri/ /index.html;`，这是前端上线最高频的坑。

## 22.7 HTTPS 配置

### 22.7.1 证书来源

- 云厂商免费证书（阿里云/腾讯云，一年期）
- Let's Encrypt（免费，自动续期）
- 自签名证书（仅测试用，浏览器会报警告）

### 22.7.2 配置示例

```nginx
server {
    listen 443 ssl;
    server_name www.example.com;

    ssl_certificate     /etc/nginx/ssl/example.com.pem;   # 证书
    ssl_certificate_key /etc/nginx/ssl/example.com.key;   # 私钥
    ssl_protocols       TLSv1.2 TLSv1.3;                  # 禁用老版本
    ssl_ciphers         HIGH:!aNULL:!MD5;

    location / {
        proxy_pass http://demo-backend;
    }
}

# HTTP 强制跳转 HTTPS
server {
    listen 80;
    server_name www.example.com;
    return 301 https://$host$request_uri;   # 301 永久跳转
}
```

### 22.7.3 证书申请验证（HTTP 文件验证）

```
1. 域名服务商处添加一条 A 记录指向服务器 IP
2. 在网站根目录放置验证文件：/.well-known/acme-challenge/xxx.txt
3. 申请后下载证书，把 .pem 和 .key 放入 ssl 目录
```

## 22.8 限流与防刷

### 22.8.1 连接数限制

```nginx
http {
    limit_conn_zone $binary_remote_addr zone=conn_limit:10m;  # 按 IP 记录

    server {
        location / {
            limit_conn conn_limit 5;      # 同一 IP 最多 5 个并发连接
        }
    }
}
```

### 22.8.2 请求速率限制

```nginx
http {
    limit_req_zone $binary_remote_addr zone=req_limit:10m rate=10r/s;  # 每秒 10 个请求

    server {
        location /api/ {
            limit_req zone=req_limit burst=20 nodelay;   # 突发允许 20 个，超出排队
        }
    }
}
```

| 参数 | 含义 |
| --- | --- |
| `rate=10r/s` | 平均速率：每秒 10 个请求 |
| `burst=20` | 突发缓冲：瞬间最多再放行 20 个 |
| `nodelay` | 突发请求不排队，直接放行（超过 burst 则 503） |

> 防刷实战：登录接口、短信接口必须限流；配合 20 章 ES、12 章 Redis 可做更细粒度的风控。

## 22.9 Nginx 高可用（Keepalived）

### 22.9.1 问题

Nginx 是入口，**Nginx 挂了整个系统就挂了**，需要双机热备：

```
        ┌──► Nginx 主（192.168.1.100）──► 后端
VIP ──► │
        └──► Nginx 备（192.168.1.101）──► 后端
```

VIP（虚拟 IP）漂移：主挂了，备自动接管 VIP，对用户无感。

### 22.9.2 关键配置

```bash
# 主备两台都装 keepalived
yum install -y keepalived
```

```vim
# /etc/keepalived/keepalived.conf（主）
global_defs { router_id nginx_master }
vrrp_instance VI_1 {
    state MASTER            # 备机写 BACKUP
    interface eth0
    virtual_router_id 51
    priority 100            # 主 100，备 90，大的优先
    advert_int 1            # 1 秒发一次心跳
    virtual_ipaddress {
        192.168.1.200       # VIP
    }
}
```

> 理解即可：keepalived 通过 VRRP 协议广播心跳，主备之间通过 priority 抢 VIP，谁有 VIP 谁对外服务。企业中一般配合云厂商的 SLB/LB 使用（更省心）。

## 22.10 实战：Nginx 上线 Spring Boot 服务完整流程

### 22.10.1 需求

- 域名 `api.example.com`
- 两台后端 `192.168.1.10:8080`、`192.168.1.11:8080`
- 前端静态文件在 `/data/www/dist`
- 需要 HTTPS

### 22.10.2 最终配置

```nginx
# /etc/nginx/conf.d/api.example.com.conf
upstream demo-backend {
    server 192.168.1.10:8080 max_fails=3 fail_timeout=10s;
    server 192.168.1.11:8080 max_fails=3 fail_timeout=10s;
}

server {
    listen 443 ssl;
    server_name api.example.com;

    ssl_certificate     /etc/nginx/ssl/example.com.pem;
    ssl_certificate_key /etc/nginx/ssl/example.com.key;

    # 动态接口：负载均衡转发
    location /api/ {
        proxy_pass http://demo-backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 前端页面
    location / {
        root /data/www/dist;
        try_files $uri $uri/ /index.html;
    }

    # 静态资源强缓存
    location ~* \.(js|css|png|jpg|svg|woff2)$ {
        expires 30d;
    }
}

server {
    listen 80;
    server_name api.example.com;
    return 301 https://$host$request_uri;
}
```

### 22.10.3 上线步骤

```bash
# 1. 语法检查
nginx -t

# 2. 平滑重载（不停服）
nginx -s reload

# 3. 验证负载均衡：查看后端日志，确认两台都在收请求
tail -f /data/logs/app.log   # 两台服务器分别看

# 4. 验证反向代理：curl 带 Host 头
curl -H "Host: api.example.com" https://api.example.com/api/hello
```

## 22.11 小结与练习

**本章重点**：
- Nginx 三大核心：反向代理、负载均衡、动静分离
- `location` 匹配优先级：`=` > `^~` > `~`/`~*` > `/`
- 负载均衡策略：轮询 / 权重 / IP_Hash / least_conn
- `root` vs `alias`、`proxy_set_header X-Real-IP`（真实 IP 透传）
- 前端 history 路由必须 `try_files ... /index.html`
- HTTPS：443 + 证书，80 强制 301 跳转
- 高可用：keepalived + VIP 漂移

**面试题参考**：
1. Nginx 和 Tomcat 有什么区别？各自的职责？
2. 什么是正向代理和反向代理？画图说明。
3. Nginx 负载均衡有哪些策略？默认是什么？
4. location 的匹配优先级是怎么样的？
5. 用户请求经过 Nginx 后，后端如何拿到真实 IP？
6. 前端页面刷新 404 是什么原因？怎么解决？
7. Nginx 是怎么做到高并发的？（事件驱动 + 多 worker 进程）
8. Nginx 挂了怎么办？（keepalived 双机热备 / VIP 漂移）

**课后练习**：
1. Docker 启动 Nginx，挂载配置文件，改端口后 `nginx -s reload` 验证生效。
2. 启动两个 Spring Boot 实例（8080/8081），用 upstream 做负载均衡，访问多次观察轮询效果。
3. 把项目的静态资源（图片/JS）配成动静分离，验证后端日志不再出现静态资源请求。
4. 给自己的域名申请免费证书（或用自签名），配置 HTTPS 并强制跳转。
5. 给登录接口配置限流（每秒 3 次），用脚本压测观察 503 效果。

上一章：[21-JenkinsCI-CD.md](./21-JenkinsCI-CD.md) | 下一章：[23-Zookeeper.md](./23-Zookeeper.md) | 返回目录：[README.md](./README.md)
