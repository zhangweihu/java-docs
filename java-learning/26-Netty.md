# 第二十六章 Netty 网络编程

> 本章目标：理解传统 IO 模型的瓶颈与多路复用原理，掌握 Netty 核心组件（EventLoop/Channel/Pipeline/ByteBuf），能写出完整的服务端/客户端程序，处理粘包拆包、心跳、编解码等真实场景。
>
> 前置知识：第五章多线程、第六章 IO 流与网络编程、第二十四章 JVM（内存管理呼应）。

## 26.1 为什么需要 Netty

### 26.1.1 传统 Socket 编程的问题

```java
// 第六章的写法：一个连接一个线程
public static void main(String[] args) throws IOException {
    ServerSocket server = new ServerSocket(8080);
    while (true) {
        Socket socket = server.accept();       // 阻塞等待连接
        new Thread(() -> handle(socket)).start();  // 每连接一线程
    }
}
```

| 问题 | 后果 |
| --- | --- |
| 阻塞 IO | 线程卡在 `read()` 上，啥也不干还占资源 |
| 一连接一线程 | 1 万连接 = 1 万线程，内存/上下文切换爆炸 |
| 线程池替代 | 连接数 > 线程数就排队，依然会耗尽 |
| 可伸缩性差 | C10K 问题（1 万并发）就扛不住 |

### 26.1.2 Netty 是什么

```
Netty = 异步、事件驱动的网络应用框架（Java 界最流行）
      "高性能的 NIO 框架封装"

谁在用：Dubbo、RocketMQ、Elasticsearch、Hadoop、Spring WebFlux、gRPC
       —— 几乎所有 Java 中间件的高性能网络层都是 Netty
```

**Netty 的优势**：
1. **多路复用**：一个线程处理成千上万连接（NIO 模型）
2. **API 简单**：对比手写 NIO 的 Selector 模板代码，Netty 一行搞定
3. **自带编解码**：解决粘包拆包（内置多种编解码器）
4. **内存优化**：零拷贝、池化 ByteBuf（呼应 JVM 内存章节）
5. **生态完善**：心跳、重连、SSL、HTTP/2 等开箱即用

### 26.1.3 Netty vs 直接 NIO

```java
// 手写 NIO 的 Selector 模板（光注册+循环就要几十行，还容易写错）
Selector selector = Selector.open();
ServerSocketChannel ssc = ServerSocketChannel.open();
ssc.configureBlocking(false);
ssc.register(selector, SelectionKey.OP_ACCEPT);
while (selector.select() > 0) {
    // 遍历 key、判断类型、读数据、处理半包...非常繁琐
}

// Netty：BootStrap 链式配置，几十行搞定完整服务端
```

## 26.2 IO 模型基础（BIO / NIO / AIO）

### 26.2.1 三种模型对比

| 模型 | 阻塞 | 代表 | 适用 |
| --- | --- | --- | --- |
| BIO | 阻塞读、阻塞写 | 传统 Socket | 连接少、简单 |
| **NIO** | 非阻塞，多路复用 | Netty | **高并发主流** |
| AIO | 异步，回调 | NIO.2 | 实际用得少（Netty 也没用 AIO） |

### 26.2.2 多路复用原理（NIO 核心）

```
传统模型：10000 个连接 = 10000 个线程，每个线程阻塞等着读

多路复用：1 个 Selector 线程同时监听 10000 个 Channel
   ┌─────────────────────────────────────────┐
   │  Selector（大管家）                        │
   │  问每个 Channel：你有数据了吗？             │
   │  ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐  │
   │  │ 连接1  │ │ 连接2  │ │ 连接3  │ │ 连接10000│ │
   │  └───────┘ └───────┘ └───────┘ └───────┘  │
   └─────────────────────────────────────────┘
   只有"有数据"的 Channel 才分发到线程池处理
```

**三种多路复用器（面试常问）**：

| 实现 | 平台 | 数量级 | 特点 |
| --- | --- | --- | --- |
| Selector | 所有 | 千级 | JDK 自带，最简单 |
| Epoll | Linux | **百万级** | 事件驱动，性能最好 |
| KQueue | macOS/BSD | 十万级 | Mac 上 Netty 默认用 |

```java
// Netty 会自动选择：Linux 用 Epoll，Mac 用 KQueue，其他用 Selector
// 可强制指定：
EventLoopGroup bossGroup = new EpollEventLoopGroup();  // Linux 专用
```

## 26.3 Netty 核心组件

### 26.3.1 架构总览

```
客户端 ──► [Bootstrap] ──► Channel（连接通道）
                              │
                    ChannelPipeline（管道）
                    ┌────────────────────────────┐
                    │ Inbound:  解码器 → 业务Handler │
                    │ Outbound: 编码器 → 发送         │
                    └────────────────────────────┘
                              │
                        EventLoop（线程）
                              │
                       Selector（多路复用）
```

### 26.3.2 五大核心组件

| 组件 | 职责 |
| --- | --- |
| **EventLoopGroup** | 线程组。Boss 负责 accept 连接，Worker 负责处理 IO 事件 |
| **Channel** | 连接通道（等价于 Socket 的封装），读写数据 |
| **ChannelPipeline** | 责任链管道，一堆 Handler 按顺序处理数据 |
| **ChannelHandler** | 处理器（业务逻辑都写在里面） |
| **ByteBuf** | 字节缓冲（Netty 自己实现的内存结构） |

### 26.3.3 事件循环（EventLoop）模型

```
Boss EventLoop（1 个线程）：只负责 accept 新连接，分发给 Worker
       │
       ▼
Worker EventLoop（默认 2×CPU 核数 个线程）
  ┌────────┬────────┬────────┬────────┐
  │ 线程1   │ 线程2   │ 线程3   │ 线程N   │
  │ 连接A   │ 连接B   │ 连接C   │ ...    │
  │ 连接D   │        │        │        │
  └────────┴────────┴────────┴────────┘
  每个线程绑定一批 Channel（一个连接固定在一个线程上，保证无锁）
```

> 关键设计：**一个 Channel 只属于一个 EventLoop 线程**，该连接的所有事件都由这个线程处理 → 无需加锁，天然线程安全。**不要在 Handler 里做耗时操作**（会阻塞该线程上的所有连接），耗时操作要丢到业务线程池。

## 26.4 第一个 Netty 程序

### 26.4.1 依赖

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-all</artifactId>
    <version>4.1.100.Final</version>
</dependency>
```

### 26.4.2 服务端

```java
public class NettyServer {

    public static void main(String[] args) throws Exception {
        // 1. 两个线程组：boss 接连接，worker 处理 IO
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)   // 服务端通道
                    .option(ChannelOption.SO_BACKLOG, 128)   // 连接队列大小
                    .childOption(ChannelOption.SO_KEEPALIVE, true)  // TCP 保活
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new StringDecoder())  // 字符串解码器
                                    .addLast(new StringEncoder())  // 字符串编码器
                                    .addLast(new ServerHandler()); // 业务处理器
                        }
                    });

            // 2. 绑定端口并同步等待
            ChannelFuture future = bootstrap.bind(8080).sync();
            System.out.println("Netty 服务端启动：8080");

            // 3. 关闭：等待通道关闭
            future.channel().closeFuture().sync();
        } finally {
            // 4. 优雅关闭线程组
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
```

### 26.4.3 业务处理器

```java
public class ServerHandler extends SimpleChannelInboundHandler<String> {

    // 收到消息时回调
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        System.out.println("收到客户端消息：" + msg);
        // 给客户端回消息（自动编码器编码成字节发出）
        ctx.writeAndFlush("服务端已收到：" + msg);
    }

    // 连接建立时回调
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("客户端连接：" + ctx.channel().remoteAddress());
    }

    // 异常时回调
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();   // 异常时关闭连接
    }
}
```

### 26.4.4 客户端

```java
public class NettyClient {

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)     // 客户端通道
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new StringDecoder())
                                    .addLast(new StringEncoder())
                                    .addLast(new ClientHandler());
                        }
                    });

            Channel channel = bootstrap.connect("127.0.0.1", 8080).sync().channel();

            // 发 10 条消息
            for (int i = 1; i <= 10; i++) {
                channel.writeAndFlush("你好，Netty " + i);
                Thread.sleep(1000);
            }
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
```

**运行结果**：

```
服务端输出：
客户端连接：/127.0.0.1:50890
收到客户端消息：你好，Netty 1
收到客户端消息：你好，Netty 2
...
客户端输出：
收到服务端消息：服务端已收到：你好，Netty 1
```

## 26.5 粘包与拆包

### 26.5.1 问题来源

TCP 是**字节流**，没有消息边界。发送方可能把多条消息合并发送（粘包），也可能一条消息被拆成多段（拆包）。

```
发送：["ABC", "DEF", "GHI"]
可能收到：["ABCDEF", "GHI"]  → 粘包
可能收到：["A", "BCDEFG", "HI"]  → 拆包
```

### 26.5.2 解决方案（编解码器）

| 方案 | 适用 |
| --- | --- |
| **LengthFieldBasedFrameDecoder** | 自定义协议：长度字段（推荐，最通用） |
| LineBasedFrameDecoder | 以换行符分隔（文本协议） |
| DelimiterBasedFrameDecoder | 自定义分隔符 |
| FixedLengthFrameDecoder | 固定长度 |

**自定义协议（长度字段）**：

```
消息格式：| 4字节长度 | 业务数据 |
          0x0000000C  Hello World
```

```java
// 服务端 Pipeline 加解码器（前 4 字节是长度，长度值包含数据部分）
ch.pipeline()
        .addLast(new LengthFieldBasedFrameDecoder(
                1024,          // 帧最大长度
                0,             // 长度字段偏移
                4,             // 长度字段长度
                0,             // 长度调整
                4))            // 剥离长度字段
        .addLast(new LengthFieldPrepender(4))   // 编码器：自动加 4 字节长度头
        .addLast(new StringDecoder())
        .addLast(new StringEncoder())
        .addLast(new ServerHandler());
```

```java
// 发送端写法不变，Netty 自动加长度头
channel.writeAndFlush("Hello World");
// 实际发到网络：| 00 00 00 0B | 48 65 6C 6C 6F 20 57 6F 72 6C 64 |
```

## 26.6 心跳机制与断线重连

### 26.6.1 为什么需要心跳

TCP 是长连接，但**连接假死**（网络断、机器挂、防火墙清连接）客户端不知道。需要定期发心跳包确认对方存活。

### 26.6.2 服务端空闲检测

```java
// 服务端 Pipeline 添加空闲检测
ch.pipeline()
        .addLast(new IdleStateHandler(
                60,    // 读空闲：60 秒没收到数据
                0,     // 写空闲：不检测
                0,     // 读写空闲：不检测
                TimeUnit.SECONDS))
        .addLast(new ServerHandler());
```

```java
public class ServerHandler extends SimpleChannelInboundHandler<String> {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            // 60 秒没收到数据 → 判定客户端失联
            System.out.println("客户端失联：" + ctx.channel().remoteAddress());
            ctx.close();   // 关掉连接，释放资源
        }
    }
}
```

### 26.6.3 客户端心跳发送

```java
public class ClientHandler extends ChannelInboundHandlerAdapter {

    // 连接建立后，每隔 30 秒发一次心跳
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.executor().scheduleWithFixedDelay(() ->
                ctx.writeAndFlush("PING"),   // 心跳包
                0, 30, TimeUnit.SECONDS);
    }

    // 心跳超时/断线 → 触发重连
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("连接断开，5 秒后重连...");
        Thread.sleep(5000);
        connect();   // 重新调用 Bootstrap 连接（封装重连逻辑）
        super.channelInactive(ctx);
    }
}
```

## 26.7 ByteBuf 与内存优化

### 26.7.1 ByteBuf vs 传统 ByteBuffer

| 对比 | ByteBuffer（JDK） | ByteBuf（Netty） |
| --- | --- | --- |
| 读写指针 | 一个 position，读写要 flip() | **readerIndex + writerIndex 分离**，不用 flip |
| 扩容 | 要手动建新 Buffer | 自动扩容 |
| 池化 | 无 | 池化复用，减少 GC |
| 零拷贝 | 部分 | CompositeByteBuf 等 |

### 26.7.2 常用操作

```java
ByteBuf buf = ctx.alloc().buffer();          // 自动扩容
buf.writeInt(1);                             // 写：移动 writerIndex
buf.writeBytes("hello".getBytes());
buf.readInt();                               // 读：移动 readerIndex
int readable = buf.readableBytes();          // 可读字节数
buf.release();                               // 引用计数减一，释放（池化内存必须释放！）
```

### 26.7.3 池化与零拷贝

```java
// 启动参数开启池化（Netty 4.1 默认开启）
-Dio.netty.allocator.type=pooled

// 零拷贝（不复制数据，共享引用）：
CompositeByteBuf composite = Unpooled.compositeBuffer();
// 多个 ByteBuf 组合成一个逻辑视图，避免多次复制
```

> **重要**：使用池化 ByteBuf 时，Handler 处理完记得 `release()`，否则内存泄漏。Netty 自带泄漏检测：`-Dio.netty.leakDetection.level=paranoid`（生产环境建议 `advanced`）。

## 26.8 实战：简易聊天室

把前面所有知识串起来：粘包处理 + 群发 + 心跳。

```java
public class ChatServer {

    // 保存所有在线连接
    private static final ConcurrentHashMap<String, Channel> ONLINE = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    // 解决粘包拆包：长度字段
                                    .addLast(new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4))
                                    .addLast(new LengthFieldPrepender(4))
                                    .addLast(new StringDecoder())
                                    .addLast(new StringEncoder())
                                    // 心跳检测：60 秒无数据断开
                                    .addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS))
                                    .addLast(new ChatHandler(ONLINE));
                        }
                    });
            bootstrap.bind(8080).sync().channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
```

```java
public class ChatHandler extends SimpleChannelInboundHandler<String> {

    private final ConcurrentHashMap<String, Channel> online;

    public ChatHandler(ConcurrentHashMap<String, Channel> online) { this.online = online; }

    // 上线：注册到在线表，广播通知
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        online.put(ctx.channel().id().asShortText(), ctx.channel());
        broadcast("系统", ctx.channel().id().asShortText() + " 上线了");
    }

    // 收到消息：广播给所有人（模拟群聊）
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        broadcast(ctx.channel().id().asShortText(), msg);
    }

    // 下线：从在线表移除
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        online.remove(ctx.channel().id().asShortText());
        broadcast("系统", ctx.channel().id().asShortText() + " 下线了");
    }

    // 心跳超时：关闭连接
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            ctx.close();
        }
    }

    // 群发：遍历在线表发送
    private void broadcast(String from, String msg) {
        online.values().forEach(ch ->
                ch.writeAndFlush("[" + from + "] " + msg));
    }
}
```

## 26.9 Netty 应用场景（面试加分）

| 场景 | 说明 |
| --- | --- |
| **RPC 框架** | Dubbo、gRPC 底层传输层（呼应第十五章） |
| **消息中间件** | RocketMQ、Kafka 网络层 |
| **搜索/存储** | Elasticsearch、Redis 客户端 |
| 网关 | Spring Cloud Gateway 基于 WebFlux + Netty（呼应第十五章） |
| 推送服务 | IM、WebSocket、游戏服务端 |
| 协议服务器 | HTTP/HTTPS/WebSocket 服务器 |

## 26.10 小结与练习

**本章重点**：
- BIO 痛点 → NIO 多路复用（一个线程管千万连接）
- Epoll（Linux，百万级）/ KQueue / Selector
- 核心组件：EventLoopGroup（boss/worker）、Channel、Pipeline、Handler、ByteBuf
- **一个 Channel 固定一个 EventLoop 线程**，无需加锁；Handler 别做耗时操作
- 粘包拆包：长度字段协议（`LengthFieldBasedFrameDecoder`）
- 心跳：`IdleStateHandler` 空闲检测 + 客户端定时心跳 + 断线重连
- ByteBuf：读写双指针、池化、`release()` 防泄漏、零拷贝
- 经典架构：Boss（接客）→ Worker（干活）→ Pipeline（流水线）

**面试题参考**：
1. BIO、NIO、AIO 的区别？为什么 Netty 用 NIO 不用 AIO？
2. 什么是多路复用？和"一连接一线程"比好在哪？
3. Netty 的核心组件有哪些？EventLoop 模型是怎么工作的？
4. Netty 为什么性能高？（多路复用 + 零拷贝 + 池化 + 无锁设计）
5. 什么是粘包拆包？怎么解决？
6. Netty 如何做心跳检测和断线重连？
7. Netty 的 ByteBuf 和 JDK 的 ByteBuffer 有什么区别？
8. 一个连接为什么会卡住其他连接？（Handler 里做耗时操作）
9. 哪些框架底层用了 Netty？

**课后练习**：
1. 运行本章服务端 + 客户端，观察消息收发。
2. 不配粘包解码器，循环快速发送 100 条消息，观察粘包现象；加上 `LengthFieldBasedFrameDecoder` 再验证。
3. 给服务端加 `IdleStateHandler`，客户端 90 秒不发数据，观察服务端自动断开。
4. 实现客户端断线重连（`channelInactive` 中定时重连）。
5. 扩展聊天室：加入用户名登录、私聊功能。

上一章：[25-MySQL调优.md](./25-MySQL调优.md) | 下一章：[27-Kafka.md](./27-Kafka.md) | 返回目录：[README.md](./README.md)
