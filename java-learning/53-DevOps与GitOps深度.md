# 第五十三章 DevOps 与 GitOps 深度（让发布成为流水线上的一个环节）

> 本章目标：第二十一章 Jenkins 带你"会用 CI/CD"，本章带你建立**工程化发布体系**——理解 DevOps 文化、CI 流水线设计（缓存/质量门禁/并行）、CD 与多环境交付、GitOps 声明式发布（Git 是唯一真相源、ArgoCD 原理与实战）、蓝绿/金丝雀/滚动在 GitOps 下的落地，以及平台工程（IDP）的演进，能独立设计并落地一套"提交代码 → 自动构建 → 自动发布 → 自动回滚"的完整流水线。
>
> 前置知识：第二十一章 Jenkins CI/CD、第三十五章 Docker 系统学习、第四十九章 K8s 深度专题、第四十八章 可观测性与灰度发布、第五十章 Service Mesh（金丝雀流量）。

## 53.1 从 CI/CD 到 DevOps：自动化只是第一步

### 53.1.1 为什么有了 Jenkins 还要"DevOps"

第 21 章你学会了搭一条 Jenkins 流水线。但真实团队里，流水线经常是"能用但没人敢改"的摆设：构建 40 分钟、发布靠人肉点按钮、回滚要翻聊天记录、配置散落在各种脚本里。

**DevOps 不是工具，是协作模式**：开发、测试、运维共享同一套自动化与可观测性，目标是"小步快跑、频繁交付、快速反馈"。

| 传统 | DevOps |
| --- | --- |
| 开发写完"扔过墙"给运维 | 开发负责到生产（You build it, you run it） |
| 发布=大版本、低频率、高风险 | 发布=小批次、高频率、可回滚 |
| 环境靠人配 | 环境即代码（IaC，Infrastructure as Code） |
| 故障靠运维救火 | 全链路可观测 + 自动恢复 |

> **一句话记忆**：CI/CD 是 DevOps 的"自动化骨架"，GitOps 是它的"声明式灵魂"，平台工程是它的"规模化形态"——三层递进，缺一不可。

### 53.1.2 四大支柱

```
① 版本控制（一切皆代码）：应用代码、IaC、流水线定义、配置全部入库
② 自动化流水线：构建 → 测试 → 扫描 → 构建镜像 → 部署 → 验证
③ 环境一致性：从 dev 到 prod 用同一镜像（"一次构建，处处运行"）
④ 快速反馈：指标门禁、自动回滚、告警收敛（第 48 章）
```

## 53.2 CI 深度：让构建又稳又快

### 53.2.1 Pipeline as Code（流水线即代码）

把流水线定义放进 Git 仓库（Jenkinsfile / .gitlab-ci.yml / GitHub Actions），好处：

- **变更可审查**：流水线的修改走代码评审，而不是在网页上瞎点；
- **多分支自动触发**：每个 MR/PR 自动跑流水线，主分支合入才发版；
- **可复制**：新服务复制一份改改就用，团队规范统一。

### 53.2.2 Java 构建提速三板斧

```
① 依赖缓存：Maven 仓库挂载到 CI 缓存卷/对象存储（省去每次全量下载）
② 增量构建：Maven/Gradle 增量编译 + Docker 多阶段构建缓存（COPY 顺序：先依赖后源码）
③ 并行化：单元测试/静态检查/镜像构建并行跑（Jenkins parallel / GitLab stages: parallel）
```

```dockerfile
# 多阶段构建：先拷贝 pom 下载依赖（依赖层缓存），再拷贝源码编译
FROM maven:3.9-eclipse-temurin-17 AS build
COPY pom.xml .
RUN mvn dependency:go-offline          # 这一步变化小，镜像层可缓存
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre
COPY --from=build target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### 53.2.3 质量门禁：不合格就不许合入

| 门禁 | 工具 | 卡点 |
| --- | --- | --- |
| 单元测试覆盖率 | JaCoCo | 覆盖率 < 80% 失败 |
| 静态代码分析 | SonarQube | 新增 Bug/严重漏洞失败 |
| 依赖漏洞扫描 | OWASP Dependency-Check / Trivy | 高危 CVE 失败 |
| 代码风格/规范 | Checkstyle/Spotless | 不达标失败 |
| 构建产物签名 | Cosign（第 54 章详述） | 未签名镜像不可部署 |

## 53.3 CD 深度：多环境交付与发布策略

### 53.3.1 环境模型：一次构建，多次部署

```
Git 提交 ──► CI：编译/测试/扫描 ──► 构建镜像（打 tag: git-sha）
                                    │
       ┌────────────────────────────┼───────────────────────────┐
       ▼                            ▼                           ▼
   dev 环境（自动部署）        staging 环境（自动部署+验证）    prod 环境（人工审批 or 自动）
   开发者自测                  冒烟/压测/验收                 蓝绿/金丝雀滚动
```

- **关键原则**：**只有一次构建**。dev 验过的镜像原样部署到 prod，杜绝"生产重新编译"这种差异；
- 环境差异（数据库地址、密钥）通过 ConfigMap/Secret（第 49 章）注入，而不是改镜像。

### 53.3.2 部署流水线的"验证闭环"

部署不等于"更新了 Deployment"，还包括：

```
deploy(新版本) ──► 等待滚动完成 ──► 探针/健康检查 ──► 金丝雀观察指标
      │                                                    │ 通过
      ▼                                                    ▼
  回滚（rollout undo）◄── 指标异常自动回滚 ◄────────── 全量放量
```

> 与第 48 章呼应：灰度发布的**指标门禁**（错误率/RT/SLO）是自动化的核心——不是"人看一眼"，而是 Prometheus 查询 + 自动决策。

## 53.4 GitOps：Git 是唯一的真相源

### 53.4.1 GitOps 三原则

```
① 声明式：集群期望状态（Deployment/ConfigMap/CRD…）全部用 YAML 存在 Git
② Git 是唯一真相源：想改集群 = 改 Git = 走 MR/PR 评审
③ 自动收敛：Agent（如 ArgoCD）持续对比"Git 里的状态"与"集群实际状态"，不一致就拉齐
```

```
开发者 ── MR ──► Git 仓库（声明式清单）
                    │ ArgoCD 监听（poll 或 webhook）
                    ▼
            对比 Git 状态 vs 集群状态
                    │ 有差异
                    ▼
           ArgoCD 执行 kubectl apply → 集群收敛到 Git 状态
                    │ 持续监控
                    ▼
              有人手动改集群？──► 视为漂移 → 自动拉回（或告警）
```

### 53.4.2 ArgoCD 核心概念

| 概念 | 说明 |
| --- | --- |
| **Application** | 一个"Git 仓库路径 → 集群命名空间"的映射（含同步策略） |
| **Sync** | 把 Git 状态应用到集群的动作（可自动/手动/定时） |
| **Sync 策略** | 自动同步 + 自动修剪（删掉 Git 里已删除的资源）+ 自愈（漂移拉回） |
| **Repo/Cluster** | 登记 Git 仓库与目标集群凭据 |
| **App of Apps** | 用一个 Application 管理一批 Application（微服务多应用编排） |

### 53.4.3 GitOps 实战三问

- **问**：CI 和 GitOps 的分工？
- **答**：**CI 负责"构建出镜像"**（代码→镜像，产物流转），**GitOps 负责"部署镜像"**（改清单→自动同步）。ArgoCD 不管编译，Jenkins/GitLab CI 不管同步。
- **问**：镜像版本怎么进 Git？
- **答**：CI 构建完镜像后**自动提交**更新 `image: mall-order:git-sha`（如 ArgoCD Image Updater），或清单里用"最新 tag + 手动更新"；
- **问**：回滚怎么做？
- **答**：`git revert` 上一笔清单变更 → ArgoCD 自动同步回滚——**回滚也是 Git 操作**，审计留痕。

## 53.5 发布策略在 GitOps 下的落地

| 策略 | GitOps 实现方式 | 适用 |
| --- | --- | --- |
| 滚动更新 | Deployment strategy 默认（第 49 章） | 默认场景 |
| 蓝绿 | 两套 Deployment + Service 切 selector（或 ArgoCD 多目标） | 需要秒级回滚、数据库兼容有风险 |
| 金丝雀 | Argo Rollouts（与 Istio 联动按权重放量，第 50 章） | 需要精细灰度 + 自动分析 |
| 回滚 | `git revert` / Argo Rollouts `rollback` | 任何发布 |

```yaml
# Argo Rollouts：金丝雀 + 自动分析（分析失败自动回滚）
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: mall-order
spec:
  replicas: 10
  strategy:
    canary:
      steps:
        - setWeight: 20          # 先放 20%
        - pause: { duration: 5m } # 观察 5 分钟
        - setWeight: 50
        - pause: { duration: 5m }
        - setWeight: 100         # 全量
      analysis:
        templates:
          - templateName: success-rate   # 引用的 AnalysisTemplate：成功率<95% 即回滚
```

## 53.6 配置与密钥的 GitOps：机密不能进 Git

GitOps 的矛盾：配置要入库，但**密钥不能明文入库**。三种解法：

| 方案 | 原理 | 适用 |
| --- | --- | --- |
| SealedSecrets | 公钥加密进 Git，集群内私钥解密为 Secret | 简单场景（K8s 原生） |
| External Secrets Operator | Git 只存"引用"，Secret 从云 KMS/Vault 拉取 | 密钥已托管在云上的团队 |
| HashiCorp Vault | 应用侧动态取密（K8s auth 注入） | 要求最高（动态密钥轮换） |

**原则**：Git 仓库里永远只有"引用/加密后的密文"，解密发生在集群内部。

## 53.7 平台工程：DevOps 的规模化形态

当团队从 10 人长到 100 人，人人都写流水线会失控——于是有了**平台工程**：

```
开发者自助平台（IDP，如 Backstage）
   ├─ 服务脚手架：点一下生成"规范的项目 + 流水线 + 清单"（黄金路径）
   ├─ 环境管理：自助申请 dev/staging 环境
   ├─ 部署与回滚：自助发布、查看发布历史
   └─ 文档/知识：统一入口
```

- **目标**：把 80% 团队的常见需求收敛成"黄金路径"，减少重复建设与认知负担；
- **对 Java 工程师的意义**：理解"平台即产品"，你写的服务模板、流水线模板就是在做平台工程。

## 53.8 实战：一条完整的 CI/CD + GitOps 流水线

```
① 开发 push / 提 MR
      │
      ▼
② GitLab CI / Jenkins：mvn 编译 + 单测(JaCoCo) + Sonar 扫描 + Trivy 镜像扫描
      │ 全绿
      ▼
③ 构建镜像 → cosign 签名 → push 到 registry（tag: <sha>）
      │
      ▼
④ 自动提交 GitOps 仓库：dev 环境清单 image 更新 → MR 合入
      │
      ▼
⑤ ArgoCD 检测到 Git 变更 → 同步到 dev 集群 → 自动部署
      │
      ▼
⑥ 人工/MR 提升：staging → prod 清单更新 → ArgoCD 金丝雀发布
      │ 指标门禁异常
      ▼
⑦ Argo Rollouts 自动回滚 + 告警（第 48 章）
```

**落地检查清单**：
1. 流水线是否 Pipeline as Code 入库？2. 是否"一次构建多处部署"？
3. GitOps 仓库是否受保护分支 + 评审？4. 密钥是否不进 Git？
5. 发布是否有指标门禁与自动回滚？6. 回滚是否也是 Git 操作（可审计）？

## 53.9 练习与思考

1. **练习 A**：把第 31 章 mall-boot 配一条 GitLab CI（或 GitHub Actions）：构建→测试→镜像→推送，实测缓存/并行对构建时间的提升；
2. **练习 B**：本地起 ArgoCD，用"App of Apps"管理 3 个服务的部署，演练"改 Git → 自动同步"与"手动改集群 → 自愈拉回"；
3. **练习 C**：给一个服务写 Argo Rollouts 金丝雀配置（20%→50%→100%），模拟指标异常触发自动回滚；
4. **练习 D**：用 SealedSecrets 把数据库密码"加密进 Git"并验证部署解密；
5. **练习 E**：画一张你所在团队的 DevOps 现状图，标出 CI/CD/GitOps/平台工程分别做到哪一层，给出三步改进计划。

## 53.10 面试考点

1. DevOps 与 CI/CD 的关系？四大支柱是什么？
2. Pipeline as Code 的好处？Java 构建提速三板斧？
3. 质量门禁有哪些？如何卡住不合格代码合入？
4. "一次构建，多次部署"为什么重要？环境差异怎么处理？
5. GitOps 三原则？ArgoCD 的 Application/Sync/自愈机制？
6. CI 与 GitOps 的分工边界？镜像版本如何进 Git？
7. 蓝绿/金丝雀/滚动在 GitOps 下怎么实现？Argo Rollouts 自动分析回滚原理？
8. 密钥为什么不能进 Git？SealedSecrets/External Secrets/Vault 区别？
9. 平台工程是什么？IDP 解决什么问题？
10. 画一条从代码提交到生产发布的完整链路，并说明每步失败如何处置？

---

至此，第 53 章 DevOps 与 GitOps 深度学习完成。下一章 [54-云原生安全专项.md](./54-云原生安全专项.md)（给云原生架构补上安全的另一半）｜ 返回：[README.md](./README.md)
