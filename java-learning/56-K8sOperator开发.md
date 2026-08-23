# 第五十六章 K8s Operator 开发（让应用自己管理自己，全系列收官）

> 本章目标：第四十九章学了 K8s 原理，本章教你**扩展 K8s**——理解 Operator 模式（自定义资源 CRD + 控制器 Reconcile 循环）、为什么有状态应用需要 Operator，掌握 Java Operator SDK 开发流程（定义 CRD、实现 Reconciler、处理 Finalizer/状态/事件、测试与部署），并用"数据库实例 Operator"和"定时备份 Operator"两个实战建立完整认知，为整个 Java 学习体系收官。
>
> 前置知识：第四十九章 K8s 深度专题（控制器循环/CRD 基础/RBAC）、第四十二章状态机（Reconcile 与状态机的关系）、第五十三章 GitOps（Operator 的部署与发布）、第五十四章安全（RBAC/准入）。

## 56.1 Operator 模式：把"运维专家"写进代码

### 56.1.1 为什么需要 Operator

K8s 内置控制器只能处理通用场景（Deployment 管无状态副本）。但**有状态应用**（MySQL、Kafka、ES、Redis 集群）需要一堆"人肉运维知识"：

```
部署一套 MySQL 集群，运维专家要做：
  建主从 → 初始化数据 → 配备份 → 故障切换（主挂了把从提升）
  → 扩副本要重同步 → 缩容要安全下线 → 升级要滚动且不丢数据……

这些"领域运维知识"如果只能靠人，每次操作都高危、每人都不同。
Operator = 把这些知识固化成代码，像 K8s 原生控制器一样自动执行。
```

> **一句话记忆**：Deployment 让"无状态应用"自己会扩缩容；**Operator 让"有状态/复杂应用"也会自己部署、自愈、升级**——把运维专家的脑子装进代码。

### 56.1.2 Operator 的构成

```
Operator = CRD（自定义资源，用户的"期望状态"声明）
         + Controller（Reconcile 循环，把现状收敛到期望状态）
         + 额外能力（RBAC、Webhook、状态上报、指标）

用户 ──kubectl apply──► CR 实例（如 MySqlCluster: 3副本, 版本8.0, 备份每天2点）
                                │
                                ▼
                         Controller 监听该 CR
                                │
                    Reconcile：对比现状 vs 期望
                                ├─► 建 StatefulSet/Service/备份CronJob
                                ├─► 副本数不对 → 调
                                ├─► 主挂了 → 提升从库
                                └─► 更新 CR 的 status（用户可 kubectl get 查看）
```

## 56.2 核心概念深挖

### 56.2.1 CRD 三要素

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: mysqlclusters.example.com
spec:
  group: example.com                 # API 分组
  names:
    plural: mysqlclusters            # kubectl get mysqlclusters
    singular: mysqlcluster
    kind: MySqlCluster
    listKind: MySqlClusterList
  scope: Namespaced                  # 命名空间级资源
  versions:
    - name: v1
      served: true
      storage: true                  # 存储版本
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              properties:
                replicas: { type: integer, minimum: 1 }
                version:  { type: string }
                backupAt: { type: string }
```

- **CR（Custom Resource）**：用户按 CRD 声明的实例；
- **版本管理**：多版本并存（v1/v1beta1），升级走 conversion webhook（类比第 43 章事件版本兼容）。

### 56.2.2 Reconcile 循环（第 49 章控制器循环的"自己写版"）

```
while (true) {
    events = 监听 CR + 相关资源（Pod/StatefulSet/Secret 变化）
    for (event : events) {
        result = reconcile(event)      // 你的业务逻辑
        requeue(result)                // 失败/需重试则排队重来
    }
}
```

Reconcile 设计三原则（面试必考）：

1. **幂等**：同一输入调用 N 次结果相同（网络抖动、重复事件是常态）；
2. **无状态**：控制器不保存内部状态，一切从 API Server 读（重启安全）；
3. **失败重试**：返回 `requeue`（指数退避），**永不 panic 放弃**。

### 56.2.3 控制器框架三选一

| 框架 | 语言 | 特点 |
| --- | --- | --- |
| Kubebuilder | Go | 生态最主流（Prometheus/ArgoCD 都用它） |
| Operator SDK | Go/Ansible/Helm | Red Hat 出品，支持三种模式 |
| **Java Operator SDK** | **Java** | **本系列主角**：Java 工程师零门槛，Spring 风格 |

## 56.3 Java Operator SDK 实战（一）：工程骨架

### 56.3.1 依赖与启动

```java
// pom.xml 核心依赖
io.javaoperatorsdk:operator-framework-core（最新 4.x）
operator-framework-spring-boot-starter   // Spring Boot 集成

@SpringBootApplication
@Operator(
    name = "mysql-operator",
    resourceController = MySqlClusterController.class
)
public class MysqlOperatorApplication {
    public static void main(String[] args) { SpringApplication.run(...); }
}
```

### 56.3.2 定义资源模型（@Group/@Version/@Kind 映射 CRD）

```java
@Group("example.com")
@Version("v1")
@Kind("MySqlCluster")
public class MySqlCluster extends CustomResource<MySqlClusterSpec, MySqlClusterStatus> {
}

public class MySqlClusterSpec {
    private Integer replicas = 3;       // 期望副本数
    private String version = "8.0";
    private String backupAt = "0 2 * * *";  // 备份 cron
    // getter/setter（Jackson 序列化）
}

public class MySqlClusterStatus {
    private String phase = "Pending";   // Pending → Creating → Running → Failed
    private List<String> readyPods;     // 当前就绪 Pod
    private String message;
    // getter/setter
}
```

### 56.3.3 实现 Reconciler（核心业务逻辑）

```java
@Component
public class MySqlClusterController
        implements Reconciler<MySqlCluster> {

    private final KubernetesClient k8s;      // fabric8 客户端
    private final ResourceUpdater<MySqlCluster> statusUpdater;

    @Override
    public UpdateControl<MySqlCluster> reconcile(
            MySqlCluster resource, Context<MySqlCluster> context) {

        String name = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        // ① 期望状态：按 CR spec 生成
        StatefulSet desired = buildStatefulSet(name, ns, resource.getSpec());
        k8s.apps().statefulSets().inNamespace(ns).resource(desired)
           .createOrReplace();                 // 幂等：有则更新，无则创建

        // ② 期望 Service（主库访问入口）
        k8s.services().inNamespace(ns).resource(buildService(name, ns))
           .createOrReplace();

        // ③ 期望备份 CronJob
        k8s.batch().v1().cronJobs().inNamespace(ns)
           .resource(buildBackupCronJob(name, ns, spec.getBackupAt()))
           .createOrReplace();

        // ④ 上报状态（用户可 kubectl get mysqlcluster -o yaml 查看）
        int ready = countReadyPods(ns, name);
        MySqlClusterStatus st = new MySqlClusterStatus();
        st.setPhase(ready == spec.getReplicas() ? "Running" : "Creating");
        st.setMessage("ready " + ready + "/" + spec.getReplicas());
        return UpdateControl.patchStatus(resource);   // patch status 不触发循环
    }
}
```

### 56.3.4 处理删除：Finalizer（清理"没有的东西"）

用户 `kubectl delete` 时，K8s 会先删关联资源，但**外部资源（云盘快照、DNS、第三方 API）需要你自己清理**——用 Finalizer：

```java
public static final String FINALIZER = "example.com/mysql-cleanup";

// reconcile 里：
if (resource.isMarkedForDeletion()) {          // 正在删除
    if (!resource.getMetadata().getFinalizers().contains(FINALIZER)) {
        return UpdateControl.noUpdate();       // finalizer 已被移除，结束
    }
    cleanupExternalResources(resource);        // 删云盘/快照/监控等
    return UpdateControl.updateStatusPatch(
            resource.removeFinalizer(FINALIZER)); // 移除 finalizer → K8s 真正删除
}

// 创建时（仅在首次）：resource.addFinalizer(FINALIZER) 并 update
```

**为什么需要**：没有 Finalizer 时 K8s 直接删对象，你的清理代码根本没机会跑——外部资源就成了"孤儿"。

## 56.4 Java Operator SDK 实战（二）：进阶机制

### 56.4.1 事件源与触发（哪些变化会唤醒 Reconcile）

| 机制 | 说明 |
| --- | --- |
| 主资源事件 | CR 增删改自动触发 |
| SecondaryResource 依赖 | 监听自己创建的 StatefulSet/Pod，被改动（如人为误删 Pod）也会触发 |
| Informer 过滤 | 只关心 label 匹配的资源，避免全量监听 |
| `EventSource` 自定义 | 外部事件（如配置中心变更）主动触发 |

### 56.4.2 并发与锁（多实例部署安全）

- 控制器默认可多副本运行，SDK 用 **Leader Election** 保证同一时刻只有一个实例执行 Reconcile；
- 生产环境 Operator 自己也要 HA（两个副本 + leader lease）。

### 56.4.3 条件更新：避免"无意义循环"

```java
// 仅当状态真的变化才 patch（否则每个 watch 都打一次 API，产生风暴）
if (Objects.equals(resource.getStatus().getPhase(), newPhase)) {
    return UpdateControl.noUpdate();
}
```

## 56.5 测试与部署

### 56.5.1 测试三层

```
单元测试：Reconciler 逻辑用 MockKubernetesClient 假集群
集成测试：envtest（本地起 apiserver+etcd 真 API）验证 CRD 安装与 Reconcile
E2E：真实 Kind 集群 + 安装 CRD → 创建 CR → 断言最终状态
```

### 56.5.2 部署 Operator（到集群）

```
推荐方式：Helm chart（第 49 章）或 OLM（Operator Lifecycle Manager）
  ① 打包：容器镜像（镜像安全见第 54 章）+ RBAC（ServiceAccount/Role，最小权限）
  ② 安装 CRD：kubectl apply -f crd.yaml（或随 Helm）
  ③ 部署控制器 Deployment：2 副本 + leader election
  ④ 验证：创建 CR → kubectl get mysqlcluster → 观察 phase 到 Running
发布即 GitOps：Operator 自身也走第 53 章流水线 + 灰度发布
```

### 56.5.3 安全注意（第 54 章落地）

- Operator 的 RBAC **只授权它需要的资源**（别给 cluster-admin）；
- 挂载的 Secret/KMS 凭据最小化；镜像签名 + 准入校验；
- Operator 处理外部资源时：幂等 + 审计日志。

## 56.6 生产最佳实践清单

1. **幂等优先**：createOrReplace 是标配；外部 API 调用前先查后建；
2. **状态机化**：用 `phase`（Pending→Creating→Running→Degraded）表达生命周期，与第 42 章状态机思想一致；
3. **事件上报**：`EventRecorder` 记录关键动作（K8s events，`kubectl describe` 可见），排障必备；
4. **指标**：暴露 controller 的 reconcile 次数/失败率（第 48 章 Prometheus）；
5. **速率与退避**：失败重试指数退避，防止"故障风暴"打爆 API Server；
6. **升级 CRD 版本**：schema 变更兼容（新增字段默认值、旧版本 conversion）。

## 56.7 练习与思考

1. **练习 A**：用 Java Operator SDK 写一个 `TimedBackup` 资源：用户声明备份目标与 cron，控制器创建对应 CronJob（参照 56.3）；
2. **练习 B**：给 56.3 的 MySqlCluster 控制器加上 Finalizer 清理逻辑并测试删除流程；
3. **练习 C**：用 envtest 写一个集成测试：创建 CR → 断言 StatefulSet 被创建 → 断言 status 更新；
4. **练习 D**：把控制器部署到 Kind 集群（含 RBAC），验证"误删 Pod 后自动重建"；
5. **练习 E**：思考：你负责的业务里，哪个"重复性运维操作"值得固化成 Operator？写出它的 CRD 设计（spec/status）。

## 56.8 面试考点

1. Operator 模式是什么？为什么有状态应用需要 Operator？
2. CRD 三要素？CR/CRD 关系？版本兼容怎么做？
3. Reconcile 循环三原则（幂等/无状态/失败重试）为什么重要？
4. Java Operator SDK 与 Kubebuilder 的取舍？
5. Finalizer 的作用与实现流程？没有它会怎样？
6. 如何避免 Reconcile 无意义循环/API 风暴？
7. SecondaryResource 依赖怎么用？Leader Election 解决什么？
8. Operator 如何测试（单元/envtest/E2E）？如何部署（Helm/OLM）？
9. Operator 的安全注意点（RBAC 最小权限/镜像签名）？
10. 设计一个真实场景的 Operator：CRD spec 有哪些字段？status 怎么表达生命周期？

---

至此，第 56 章 K8s Operator 开发学习完成。下一章 [57-分库分表与ShardingSphere深度实战.md](./57-分库分表与ShardingSphere深度实战.md)（数据专题四连开篇）｜ 返回：[README.md](./README.md)
