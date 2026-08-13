# AI Image Style Transfer Studio — 后端服务（Backend）

AI 图片风格迁移工作台的后端服务，基于 Spring Boot 4.1 + Java 25 构建。

## 技术栈

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 25 (LTS) | 主开发语言 |
| 框架 | Spring Boot | 4.1.x | 应用骨架 |
| Web | Spring MVC | 7.x | RESTful API |
| 安全 | Spring Security | 7.x | 鉴权框架 |
| ORM | MyBatis-Plus | 3.5.17 | 数据访问 |
| 数据库 | MySQL | 8.4 | 业务数据存储 |
| 迁移 | Flyway | 13.x | SQL 版本管理 |
| 缓存 | Redis | 8.x | 缓存 / 限流 |
| 分布式锁 | Redisson | 4.7.0 | 并发控制 |
| 消息队列 | RabbitMQ | 4.3.x | 异步任务队列 |
| 对象存储 | MinIO SDK | 8.6.0 | 图片存储（S3 兼容） |
| JWT | jjwt (gson) | 0.12.6 | 无状态鉴权（规避 Jackson 3 冲突） |
| API 文档 | SpringDoc OpenAPI | 3.1.0 | Swagger UI |
| 工具 | Lombok / Hutool / MapStruct | — | 开发效率 |

## 前置条件

- **Java** 25 (LTS) — 推荐 Eclipse Temurin JDK 25
- **Maven** ≥ 3.9
- 基础设施服务已启动（MySQL / Redis / RabbitMQ / MinIO）→ 参见 [Infra README](../ai-image-studio-infra/README.md)

## 快速开始

```bash
# 1. 确保基础设施已启动
cd ../ai-image-studio-infra && ./scripts/start-dev.sh

# 2. 进入后端仓库
cd ../ai-image-studio-backend

# 3. 一键启动（推荐，自动加载 infra/.env 中的密码）
./scripts/start-dev.sh

# 或者手动启动（需确保环境变量已设置）
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

启动成功后访问：

| 端点 | 地址 |
|------|------|
| 健康检查 | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

## 目录结构

```
ai-image-studio-backend/
├── scripts/
│   └── start-dev.sh          # 一键启动（自动加载 infra/.env）
├── src/
│   ├── main/
│   │   ├── java/com/styletransfer/studio/
│   │   │   ├── common/        # 通用：常量、枚举、异常、统一响应
│   │   │   ├── config/        # 配置：MyBatis-Plus、Redis、MinIO、Security 等
│   │   │   ├── controller/    # REST 控制器
│   │   │   └── StyleTransferApplication.java  # 启动类
│   │   └── resources/
│   │       ├── db/migration/  # Flyway SQL 迁移脚本
│   │       ├── application.yml       # 公共配置
│   │       └── application-dev.yml   # 本地开发配置
│   └── test/
│       └── resources/
│           └── application-test.yml  # 测试配置
├── Dockerfile                # Docker 多阶段构建
├── pom.xml                   # Maven 依赖与构建配置
└── mvnw / mvnw.cmd           # Maven Wrapper
```

## 配置说明

### Profile 切换

默认使用 `dev` profile（直连本地 Docker 容器）。如需切换：

```bash
# 开发环境（默认）
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 生产环境
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### 环境变量

`application-dev.yml` 中所有配置均支持环境变量覆盖。推荐通过 `./scripts/start-dev.sh` 自动加载 `ai-image-studio-infra/.env`。

关键变量：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | MySQL 连接 | localhost:3306/style_transfer |
| `DB_USER` / `DB_PASSWORD` | MySQL 账号 | root / 需与 infra 一致 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 | localhost:6379 / 需与 infra 一致 |
| `MQ_HOST` / `MQ_PORT` / `MQ_USER` / `MQ_PASSWORD` | RabbitMQ 连接 | localhost:5672 / admin / 需与 infra 一致 |
| `MINIO_ENDPOINT` / `MINIO_AK` / `MINIO_SK` | MinIO 连接 | localhost:9000 / 需与 infra 一致 |
| `JWT_SECRET` | JWT 密钥 | 需与 infra 一致 |

### 业务配置

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `APP_QUOTA_DEFAULT` | 新用户默认额度（张） | 20 |
| `RESULT_IMAGE_RETENTION_DAYS` | 结果图保留天数 | 7 |
| `APP_UPLOAD_MAX_SIZE` | 单张图片最大尺寸 | 10MB |
| `APP_MAX_IMAGES_PER_TASK` | 单次任务最多图片数 | 9 |

## 常用命令

```bash
# 编译（跳过测试）
mvn clean package -DskipTests

# 打包成 Docker 镜像
docker build -t ai-image-studio-backend:latest .

# 运行打包后的 Jar
java -jar target/studio-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

# 运行测试
mvn test

# 检查代码风格
mvn checkstyle:check
```

## 架构设计

```
请求流程：
  Client → Controller → Service → Mapper → MySQL
                                  ↓
                              Redis (缓存/锁)
                                  ↓
                              RabbitMQ (异步任务)
                                  ↓
                              MinIO (图片存储)
                                  ↓
                              第三方 AI API (风格迁移)
```

**核心模块**：

- `common` — 通用工具：统一响应体 `Result<T>`、业务异常 `BizException`、全局异常处理器、常量与枚举
- `config` — 配置类：MyBatis-Plus 分页插件、Redis/Redisson、MinIO、RabbitMQ、Security、线程池、WebClient
- `controller` — REST 接口（当前骨架仅含健康检查，业务接口待实现）

## 故障排查

| 问题 | 解决方案 |
|------|----------|
| `Port 8080 was already in use` | `lsof -ti:8080 \| xargs kill -9` 释放端口 |
| `WRONGPASS` Redis 连接失败 | 确保密码与 `ai-image-studio-infra/.env` 一致，用 `./scripts/start-dev.sh` 启动 |
| `Unsupported Database: MySQL 8.4` | Flyway 需显式引入 `flyway-mysql` 依赖（已在 pom.xml 中配置） |
| `Cannot determine embedded database driver class` | MySQL 未启动或密码错误，检查基础设施 |
| `SSLException` WebClient 调用失败 | `WebClientConfig` 已处理，生产环境请配置合法证书 |

## License

MIT