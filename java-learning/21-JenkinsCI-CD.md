# 第二十一章 Jenkins CI/CD 持续集成与持续部署

> 本章目标：理解 CI/CD 解决什么问题、流水线是什么，会用 Jenkins 从零搭建一条"代码提交 → 自动构建 → 自动测试 → 自动部署 Docker"的完整流水线，掌握 Pipeline 语法与常见最佳实践。
>
> 前置知识：第十九章 Docker（部署环节要用）。

## 21.1 什么是 CI/CD

### 21.1.1 传统手动部署的痛点

回忆第九章的部署流程：

```bash
# 每次发版都是手动操作，重复且易错
git pull               # 1. 登录服务器拉最新代码
mvn clean package      # 2. 手动构建
scp xxx.jar 服务器      # 3. 手动上传
kill -9 旧进程          # 4. 手动杀进程
java -jar xxx.jar &    # 5. 手动启动
```

| 痛点 | 后果 |
| --- | --- |
| 手动步骤多，出错概率高 | 部署一次踩一次坑 |
| 全流程靠人肉，速度慢 | 一天只能发一两次版 |
| 没有统一规范 | "我本地是好的" |
| 没有自动化测试 | 改坏代码直接上线 |

### 21.1.2 CI / CD 概念

```
开发提交代码 ──► [CI 持续集成] ──► [CD 持续交付/部署] ──► 生产环境
                     │                  │
           自动拉代码+编译+测试       自动构建镜像+发布
                     │                  │
             发现问题立刻反馈         一键/自动上线
```

| 概念 | 英文 | 含义 |
| --- | --- | --- |
| **持续集成** | Continuous Integration | 代码每次提交都自动**编译 + 跑测试**，问题早暴露 |
| **持续交付** | Continuous Delivery | 自动构建产物，随时可手动发布到生产 |
| **持续部署** | Continuous Deployment | 测试通过后**自动部署**到生产（最激进） |

> 一句话：**把"人肉部署"变成"代码触发、机器执行"**。

### 21.1.3 主流 CI/CD 工具

| 工具 | 特点 | 场景 |
| --- | --- | --- |
| **Jenkins** | 老牌、插件生态最丰富、开源免费 | **企业主流**，本项目选它 |
| GitLab CI | 与 GitLab 深度集成，`gitlab-ci.yml` | GitLab 用户首选 |
| GitHub Actions | 云端、免运维、YAML 配置 | 开源项目、小团队 |
| Drone | 轻量容器化 CI/CD | 新项目尝鲜 |

## 21.2 Jenkins 快速开始

### 21.2.1 安装（Docker）

```bash
# 安装 Jenkins（官方 LTS 镜像，2.4xx）
docker run -d --name jenkins \
  -p 8080:8080 -p 50000:50000 \
  -v /d/data/jenkins:/var/jenkins_home \      # 数据持久化
  -v /var/run/docker.sock:/var/run/docker.sock \   # 让 Jenkins 能调用 Docker（DinD 关键）
  jenkins/jenkins:lts
```

> **关键点**：挂载宿主机 `docker.sock`，Jenkins 容器内就能执行 `docker` 命令（构建/推送镜像），这叫 Docker-in-Docker（DinD）模式。

### 21.2.2 初始化配置

```bash
# 1. 查看初始管理员密码
docker logs jenkins | grep password
# 或
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

1. 访问 `http://localhost:8080`，粘贴初始密码
2. 选择"安装推荐插件"（或自定义安装：`Git`、`Maven Integration`、`Docker Pipeline`、`Pipeline`、`Publish Over SSH`）
3. 创建管理员账号完成配置
4. 系统管理 → Tools：配置 JDK 17 与 Maven 的路径（或使用自动安装）

## 21.3 第一个 Job：手动构建部署

### 21.3.1 创建自由风格项目（Freestyle）

1. 新建任务 → 输入名称 → 选择**自由风格项目**
2. **源码管理**：选择 Git，填仓库地址 `https://gitee.com/xxx/springboot-demo.git`，配置凭证（账号密码/SSH Key）
3. **构建触发器**：暂不配置（稍后讲 Webhook）
4. **构建环境** → 构建步骤 → 增加构建步骤 → **调用顶层 Maven 目标**：

```
clean package -DskipTests
```

5. 保存后点击**立即构建**，查看控制台输出——第一次构建成功！

```
[INFO] BUILD SUCCESS
[INFO] Total time: 12.3s
```

### 21.3.2 部署到服务器（Shell 脚本）

构建成功后，加一个**执行 Shell** 步骤，自动部署到 Docker：

```bash
# 进入工作目录（Jenkins 会自动 checkout 代码在这里）
cd $WORKSPACE

# 1. 构建 Docker 镜像
docker build -t springboot-demo:$BUILD_NUMBER .

# 2. 停掉旧容器并启动新容器
docker rm -f demo-app || true
docker run -d --name demo-app \
  -p 8080:8080 \
  springboot-demo:$BUILD_NUMBER

echo "部署完成，构建号：$BUILD_NUMBER"
```

> 这样每次点击"立即构建"，就自动完成：拉代码 → 编译打包 → 打镜像 → 部署上线。**这就是最简 CI/CD**。

## 21.4 Pipeline 流水线（企业标准）

### 21.4.1 为什么用 Pipeline

自由风格项目配置都在界面上，不好版本管理、不好复用。**Pipeline 把流水线写成代码**（`Jenkinsfile`），跟随项目一起提交 Git，团队都能 review、能复用。

### 21.4.2 Jenkinsfile 入门（声明式语法）

```groovy
pipeline {
    // 指定在哪个代理上执行：any 表示任意可用节点（或用 docker 指定容器）
    agent any

    // 流水线全局环境变量
    environment {
        APP_NAME = 'springboot-demo'
        IMAGE_REPO = 'registry.example.com/demo'   // 镜像仓库地址
    }

    // 阶段一：拉取代码（Jenkins 自动完成，无需写）
    stages {
        stage('Build') {                      // 1. 构建
            steps {
                echo "开始编译 ${APP_NAME}"
                sh 'mvn clean package -DskipTests'
            }
        }
        stage('Test') {                       // 2. 测试
            steps {
                sh 'mvn test'                 // 跑单元测试
            }
        }
        stage('Docker Build') {               // 3. 构建镜像并推送
            steps {
                sh """
                  docker build -t ${IMAGE_REPO}:${BUILD_NUMBER} .
                  docker push ${IMAGE_REPO}:${BUILD_NUMBER}
                """
            }
        }
        stage('Deploy') {                     // 4. 部署到服务器
            steps {
                sh """
                  docker rm -f ${APP_NAME} || true
                  docker run -d --name ${APP_NAME} -p 8080:8080 ${IMAGE_REPO}:${BUILD_NUMBER}
                """
            }
        }
    }

    // 无论成功失败，都执行：发送通知
    post {
        success {
            echo '构建部署成功！'
            // 可以调用企业微信/钉钉/邮件插件通知
            // dingtalk(notification: '构建成功')
        }
        failure {
            echo '构建失败，请检查日志！'
        }
    }
}
```

### 21.4.3 Pipeline 语法速查

| 语法 | 作用 |
| --- | --- |
| `agent` | 在哪执行（`any` / `docker` / `node`） |
| `environment` | 环境变量 |
| `stages` / `stage` | 流水线阶段（按顺序执行） |
| `steps` | 具体步骤（`sh` 执行命令、`echo` 输出） |
| `post` | 收尾动作（success/failure/always） |
| `parameters` | 参数化构建（见下） |
| `credentials` | 凭据引用（`credentials('mysql-pwd')`） |

### 21.4.4 参数化构建（发版选环境）

```groovy
pipeline {
    agent any
    parameters {
        choice(name: 'ENV', choices: ['dev', 'test', 'prod'], description: '选择部署环境')
        string(name: 'VERSION', defaultValue: 'latest', description: '镜像版本号')
    }
    stages {
        stage('Deploy') {
            steps {
                // 根据环境参数动态加载配置（对应 Spring 多环境 profile）
                sh "docker run -d --name demo-app -e SPRING_PROFILES_ACTIVE=${params.ENV} demo-image:${params.VERSION}"
            }
        }
    }
}
```

> 构建时点"Build with Parameters"，选好环境再构建，不同环境一键发版。

## 21.5 触发器：提交代码自动构建（核心体验）

手动点击构建不算真正的 CI，**自动触发**才是：

### 21.5.1 方式一：轮询（简单）

构建触发器 → 勾选"Poll SCM"，填 `H/5 * * * *`（每 5 分钟检查一次仓库，有变更才构建）。

> 缺点：有延迟（最多等 5 分钟），还浪费资源。生产不推荐。

### 21.5.2 方式二：Webhook（推荐，实时触发）

**原理**：代码 push 到 Git 仓库 → Git 平台发 HTTP 请求通知 Jenkins → Jenkins 立即构建。

**GitHub 配置**：
1. 构建触发器 → 勾选 `GitHub hook trigger for GITScm polling`
2. 仓库 Settings → Webhooks → Add webhook：
   - Payload URL：`http://你的Jenkins地址:8080/github-webhook/`
   - Content type：`application/json`
   - 保存

**Gitee 配置**：
1. 仓库 → 管理 → WebHooks → 添加：
   - URL：`http://你的Jenkins地址:8080/gitee-project/你的任务名`
   - 勾选"Push"推送事件
2. Jenkins 安装 **Gitee Plugin**，构建触发器勾选 Gitee Webhook

> 之后每次 `git push`，流水线自动执行，全程无需人工干预。

## 21.6 发布策略与回滚（面试加分）

### 21.6.1 蓝绿部署 / 滚动发布 / 金丝雀

| 策略 | 原理 | 优点 |
| --- | --- | --- |
| **蓝绿部署** | 新旧两套环境，切流量到新环境 | 回滚快（切回去即可） |
| **滚动发布** | 一批一批替换旧实例 | 不中断服务，资源省 |
| **金丝雀（灰度）** | 先放 10% 流量给新版，验证后全量 | 风险最小 |

在 Jenkins 中的简化实现（滚动发布）：

```bash
# 新版本容器
docker run -d --name demo-app-new -p 8081:8080 demo-image:v2

# 验证新版本健康（curl 探活）
curl -f http://localhost:8081/actuator/health

# 健康则切换流量，删除旧版本
docker rm -f demo-app-old
docker rename demo-app-new demo-app
```

### 21.6.2 回滚（构建产物留档）

```groovy
// 保留最近 10 个镜像版本，随时可回滚
stage('Rollback Ready') {
    steps {
        sh """
          docker tag demo-image:${BUILD_NUMBER} demo-image:latest
          # 保留构建历史，回滚时用历史版本号重新部署即可
        """
    }
}
// 回滚操作：用上一版本号重新跑 Deploy 阶段
// docker run demo-image:上版本号
```

## 21.7 企业级流水线最佳实践

```groovy
pipeline {
    agent any
    environment {
        // 凭据管理：密码/Token 不写死在代码里
        DOCKER_HUB_CRED = credentials('docker-hub-cred')
    }
    stages {
        stage('Checkout') { steps { echo '1. 拉取代码（自动）' } }

        stage('Compile') {
            steps { sh 'mvn clean compile' }
        }
        stage('Unit Test') {
            steps { sh 'mvn test' }
            post { failure { echo '单测失败，阻断发版' } }   // 测试不过不放行
        }
        stage('SonarQube') {          // 静态代码扫描（可选）
            steps { echo '代码质量扫描' }
        }
        stage('Package') {
            steps { sh 'mvn package -DskipTests' }
        }
        stage('Build & Push Image') {
            steps {
                sh """
                    docker build -t demo-image:${BUILD_NUMBER} .
                    echo ${DOCKER_HUB_CRED_PSW} | docker login -u ${DOCKER_HUB_CRED_USR} --password-stdin
                    docker push demo-image:${BUILD_NUMBER}
                """
            }
        }
        stage('Deploy') {
            // 关键：只有指定分支才部署到生产
            when { branch 'main' }
            steps { sh '部署脚本' }
        }
    }
    post {
        always {
            // 无论成败：清理工作空间、发通知
            cleanWs()
        }
    }
}
```

**企业规范要点**：
1. **Jenkinsfile 随代码版本管理**，流水线即代码
2. **凭据进 Jenkins 凭据库**，绝不写明文密码
3. 测试失败必须**阻断**后续阶段
4. 用 `when` 按分支/环境区分构建策略（如 `main` 分支才部署生产）
5. 构建产物**留版本号 + 标签**，支持快速回滚
6. 结合 K8s：`kubectl set image deployment/demo demo-image:v123` 滚动更新（衔接第十九章）

## 21.8 小结与练习

**本章重点**：
- CI/CD 三概念：持续集成 / 持续交付 / 持续部署
- 自由风格项目（手动配置）→ Pipeline（代码化，企业标准）
- Jenkinsfile：`agent/stages/steps/post/parameters`
- 触发方式：Webhook（实时）优于轮询
- 部署策略：蓝绿 / 滚动 / 金丝雀；构建产物留档支持回滚

**面试题参考**：
1. 什么是 CI/CD？你们公司是怎么做的？
2. Jenkins Pipeline 和自由风格项目有什么区别？
3. 如何实现提交代码自动构建？（Webhook 原理）
4. 怎么保证发布失败能快速回滚？（镜像留版本 + 蓝绿/滚动）
5. Jenkins 如何和 K8s 结合？（`kubectl set image` 滚动更新）
6. 多个环境（dev/test/prod）怎么管理？（参数化构建 + 多 Jenkinsfile 分支）

**课后练习**：
1. Docker 安装 Jenkins 并完成初始化（安装 Git、Maven、Pipeline 插件）。
2. 创建一个自由风格 Job：拉取自己的项目 → Maven 打包 → 查看构建产物。
3. 写一个 Jenkinsfile（Build → Test → Docker Build → Deploy 四阶段），用 Pipeline 方式构建。
4. 给项目配置 Webhook，git push 后观察自动触发构建。
5. 实现参数化构建：选择 dev/test/prod 环境部署，并演示镜像版本回滚。

上一章：[20-Elasticsearch.md](./20-Elasticsearch.md) | 下一章：[22-Nginx.md](./22-Nginx.md) | 返回目录：[README.md](./README.md)
