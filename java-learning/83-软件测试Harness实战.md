# 第八十三章 软件测试 Harness 实战：JUnit 5 + Mockito + Spring Boot Test + Testcontainers

> **章节定位**：Java 工具库扩展专题（高级测试）。**Test Harness（测试装置）** 一词源自 NASA 1970s，用于指"为被测系统提供可控运行环境的整套基础设施"。在现代 Java 工程里，Harness 包含单元测试框架（JUnit）、Mock 框架（Mockito）、容器化测试（Testcontainers）、API E2E（REST Assured）、覆盖率（JaCoCo）、CI 编排等。
>
> **学习目标**：能在 Spring Boot 工程里搭一套"单元 + 集成 + E2E"完整测试 Harness，能用 Testcontainers 跑真实依赖（PG/Redis/Kafka），能用 REST Assured 测 REST 接口，能用 JaCoCo 卡覆盖率门禁，能在面试讲清 Harness 与测试金字塔。
>
> **前置知识**：Java 基础、Maven/Gradle、Spring Boot 基础、Linux 容器基础（Docker）。

---

## 一、为什么需要 Test Harness

一个 Spring Boot 项目如果只写 `main()` 跑一下就发布，会面临 4 个典型问题：

1. **接口契约无法守护**：Controller 改个字段，前端崩了没人发现；
2. **业务逻辑只能手动测**：开发"上传文件 → 解析 → 入库"，每次都启动服务、走 UI、传文件、看库表——10 分钟；
3. **依赖环境混乱**：开发用 MySQL 8、测试用 H2、上线 PG 14，SQL 方言差异踩坑；
4. **重构恐惧症**：没人敢动核心模块，因为不知道改完会不会挂。

Test Harness 的价值：**用代码守护代码**——5 秒跑完 200 个测试 + 1 秒跑 100 个接口 + 100% 真实数据库 + 覆盖率门禁，让"敢重构"成为可能。

---

## 二、5 款 Java 测试框架对比矩阵

| 框架 | 定位 | 优势 | 劣势 | 适用场景 |
|---|---|---|---|---|
| **JUnit 5** | 单元测试事实标准 | 注解丰富、生态完整、IDE 友好 | 无 Mock（需配合 Mockito） | **本主角，主流** |
| TestNG | 高级测试框架 | 注解驱动 + 数据驱动 + 依赖测试 | 社区已分裂 | 历史项目 |
| Spock | BDD 风格（Groovy DSL） | `given/when/then` 表达力极强 | 需学 Groovy、Kotlin/Java 团队门槛 | Groovy/Kotlin 项目 |
| Kotest | Kotlin 原生 | 强大断言 + DSL | 仅 Kotlin | Kotlin 项目 |
| AssertJ | 断言库（非测试框架） | 流式断言、可读性强 | 不替代 JUnit | 与 JUnit 配合 |

**选择建议**：Java Spring Boot 项目 = **JUnit 5 + Mockito + AssertJ + Spring Boot Test + Testcontainers + REST Assured**，这是当前最稳的"全家桶"组合。

---

## 三、环境与依赖

```1:a:java-learning/83-软件测试Harness实战.md
<!-- 单元测试 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- Mock -->
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- 断言 -->
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>

<!-- Spring Boot Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- 容器化测试 -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>

<!-- E2E API 测试 -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>5.4.0</version>
    <scope>test</scope>
</dependency>

<!-- 覆盖率 -->
<dependency>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
</dependency>
```

---

## 四、Harness 的 4 类核心组件

```
Test Harness
├── Test Fixture   测试夹具（@BeforeEach 准备 / @AfterEach 清理）
├── Test Runner    测试运行器（Maven Surefire / JUnit Platform）
├── Test Driver    测试驱动（Mock / Stub / Fake / Spy）
└── Test Reporter  测试报告（Surefire Report / JaCoCo / Allure）
```

记忆口诀：**Fixture 备料，Runner 启动，Driver 模拟，Reporter 反馈**。

---

## 五、JUnit 5 基础

### 5.1 标准生命周期

```1:a:java-learning/83-软件测试Harness实战.md
import org.junit.jupiter.api.*;

class CalculatorTest {

    private Calculator calculator;       // Fixture

    @BeforeAll
    static void initAll() {              // 类级别一次性
        System.out.println("测试类开始");
    }

    @BeforeEach
    void init() {                         // 每个测试前
        calculator = new Calculator();
    }

    @AfterEach
    void tearDown() {                     // 每个测试后
        calculator = null;
    }

    @AfterAll
    static void tearDownAll() {           // 类级别一次性
        System.out.println("测试类结束");
    }

    @Test
    @DisplayName("1 + 2 应该等于 3")
    void shouldAdd() {
        assertEquals(3, calculator.add(1, 2));
    }

    @Test
    @Disabled("功能未完成")
    void shouldDivide() {
        assertEquals(2, calculator.divide(4, 2));
    }
}
```

### 5.2 嵌套测试（@Nested）

```1:a:java-learning/83-软件测试Harness实战.md
@DisplayName("计算器")
class CalculatorTest {

    @Nested
    @DisplayName("加法")
    class Add {
        @Test void shouldAddPositive() { /* ... */ }
        @Test void shouldAddNegative() { /* ... */ }
    }

    @Nested
    @DisplayName("除法")
    class Divide {
        @Test void shouldDivide() { /* ... */ }
        @Test void shouldThrowOnZeroDivisor() { /* ... */ }
    }
}
```

### 5.3 参数化测试

```1:a:java-learning/83-软件测试Harness实战.md
@ParameterizedTest(name = "{0} + {1} = {2}")
@CsvSource({
    "1, 2, 3",
    "5, 7, 12",
    "-1, 1, 0"
})
void shouldAdd(int a, int b, int expected) {
    assertEquals(expected, calculator.add(a, b));
}

@ParameterizedTest
@MethodSource("provideEdgeCases")
void shouldHandleEdgeCases(int a, int b, int expected) {
    assertEquals(expected, calculator.add(a, b));
}

static Stream<Arguments> provideEdgeCases() {
    return Stream.of(
        Arguments.of(Integer.MAX_VALUE, 1, Integer.MIN_VALUE),
        Arguments.of(0, 0, 0)
    );
}
```

### 5.4 动态测试

```1:a:java-learning/83-软件测试Harness实战.md
@TestFactory
Stream<DynamicTest> dynamicTests() {
    return Stream.of(1, 2, 3, 4, 5)
        .map(n -> DynamicTest.dynamicTest("测试 " + n,
            () -> assertTrue(calculator.isPositive(n))));
}
```

### 5.5 标签与过滤

```1:a:java-learning/83-软件测试Harness实战.md
@Test
@Tag("fast")        // 快速测试（CI 主流程）
void fastTest() { /* ... */ }

@Test
@Tag("slow")        // 慢测试（夜间 CI）
void slowTest() { /* ... */ }
```

Maven 命令：
```1:a:java-learning/83-软件测试Harness实战.md
mvn test -Dgroups=fast         # 只跑快速测试
mvn test -DexcludedGroups=slow # 排除慢测试
```

---

## 六、Mockito Harness

### 6.1 4 种测试替身（Test Double）

| 类型 | 含义 | Mockito 写法 |
|---|---|---|
| **Dummy** | 仅填充参数，不被使用 | `mock(Object.class)` 但不调 |
| **Stub** | 返回固定值 | `when(mock.method()).thenReturn(x)` |
| **Spy** | 基于真实对象的部分模拟 | `spy(realObj)` + `doReturn().when()` |
| **Mock** | 完全模拟行为并可验证 | `mock + verify(mock).method()` |
| **Fake** | 轻量实现（如内存数据库） | H2、in-memory Redis |

### 6.2 基础用法

```1:a:java-learning/83-软件测试Harness实战.md
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;     // 自动 mock

    @InjectMocks
    private UserService userService;            // 自动注入依赖

    @Test
    void shouldCreateUser() {
        // Given
        User user = new User("张三", "zhangsan@example.com");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        User saved = userService.create(user);

        // Then
        assertThat(saved.getName()).isEqualTo("张三");
        verify(userRepository, times(1)).save(any(User.class));
        verifyNoMoreInteractions(userRepository);
    }
}
```

### 6.3 参数捕获 ArgumentCaptor

```1:a:java-learning/83-软件测试Harness实战.md
@Captor
private ArgumentCaptor<User> userCaptor;

@Test
void shouldSaveUserWithEncryptedPassword() {
    userService.create(new User("张三", "pwd"));

    verify(userRepository).save(userCaptor.capture());
    User saved = userCaptor.getValue();

    assertThat(saved.getPassword()).isEqualTo("encrypted_pwd");
}
```

### 6.4 静态方法 Mock（Mockito 5）

```1:a:java-learning/83-软件测试Harness实战.md
@Test
void shouldMockStaticMethod() {
    try (MockedStatic<LocalDateTime> mocked = Mockito.mockStatic(LocalDateTime.class)) {
        mocked.when(LocalDateTime::now).thenReturn(LocalDateTime.of(2024, 1, 1, 0, 0));

        // 被测代码里 LocalDateTime.now() 会返回 2024-01-01
        Order order = orderService.create();

        assertThat(order.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0));
    }
}
```

### 6.5 Spy（半真半 Mock）

```1:a:java-learning/83-软件测试Harness实战.md
@Test
void shouldSpyRealObject() {
    List<String> list = new ArrayList<>();
    List<String> spy = spy(list);

    // 部分模拟
    doReturn(100).when(spy).size();

    spy.add("a");                  // 真实方法
    spy.add("b");
    assertThat(spy.size()).isEqualTo(100);  // mock 返回
    assertThat(spy).containsExactly("a", "b");  // 真实状态
}
```

---

## 七、Spring Boot Test Harness

### 7.1 四类切片注解

| 注解 | 加载范围 | 用时 | 用途 |
|---|---|---|---|
| `@SpringBootTest` | 整个 Context | 5~15 s | 集成测试 |
| `@WebMvcTest(UserController.class)` | Controller 层 | 1~3 s | MockMvc 测 Controller |
| `@DataJpaTest` | JPA + Repository | 2~5 s | 测 Repository |
| `@DataJdbcTest` | JDBC + Repository | 1~3 s | 测 JdbcTemplate |
| `@RestClientTest` | RestTemplate/Feign | 1~3 s | 测 HTTP 客户端 |

### 7.2 完整集成测试

```1:a:java-learning/83-软件测试Harness实战.md
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers          // 启用 Testcontainers
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("test")
                .withUsername("test")
                .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateUser() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "张三", "email": "zhangsan@example.com" }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("张三"));
    }
}
```

### 7.3 Controller 切片测试（@WebMvcTest）

```1:a:java-learning/83-软件测试Harness实战.md
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void shouldReturnUser() throws Exception {
        when(userService.findById(1L))
            .thenReturn(new User(1L, "张三", "zhangsan@example.com"));

        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("张三"));
    }
}
```

### 7.4 Repository 切片测试（@DataJpaTest）

```1:a:java-learning/83-软件测试Harness实战.md
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository repository;

    @Test
    void shouldFindByEmail() {
        User user = new User(null, "张三", "zhangsan@example.com");
        repository.save(user);

        Optional<User> found = repository.findByEmail("zhangsan@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("张三");
    }
}
```

### 7.5 @TestConfiguration（局部 Bean 替换）

```1:a:java-learning/83-软件测试Harness实战.md
@SpringBootTest
class TestConfigIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public EmailService mockEmailService() {
            return Mockito.mock(EmailService.class);
        }
    }

    @Autowired
    private EmailService emailService;

    @Test
    void shouldUseMockEmail() {
        assertThat(Mockito.mockingDetails(emailService).isMock()).isTrue();
    }
}
```

---

## 八、Testcontainers 实战

### 8.1 为什么需要 Testcontainers

传统集成测试用 H2 内存数据库代替 PG，但 H2 ≠ PG：
- H2 不支持 `ON CONFLICT DO UPDATE`（PG UPSERT）；
- H2 不支持 `JSONB`、数组类型；
- H2 不支持 `RETURNING` 子句。

**Testcontainers 让测试用真实 PG/Redis/Kafka**，避免方言差异。

### 8.2 多容器编排

```1:a:java-learning/83-软件测试Harness实战.md
@Testcontainers
class FullStackIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

### 8.3 容器复用（dev/ci 同启一份）

```1:a:java-learning/83-软件测试Harness实战.md
@Testcontainers
class UserRepositoryIT {                    // IT = Integration Test
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15")
            .withReuse(true);               // 跨测试类复用
}
```

`~/.testcontainers.properties`：
```1:a:java-learning/83-软件测试Harness实战.md
testcontainers.reuse.enable=true
```

### 8.4 镜像拉取加速

```1:a:java-learning/83-软件测试Harness实战.md
@Testcontainers(disabledWithoutDocker = true)
class MyTest {

    static {
        // 国内 CI 加速
        DockerClientFactory.instance()
            .getDockerClient()
            .setEndpoint("tcp://mirrors.example.com:2375");
    }
}
```

---

## 九、REST Assured E2E Harness

### 9.1 BDD 风格

```1:a:java-learning/83-软件测试Harness实战.md
import static io.restassured.RestAssured.*;

class UserApiE2ETest {

    @BeforeAll
    static void setup() {
        baseURI = "http://localhost";
        port = 8080;
    }

    @Test
    void shouldLoginAndGetUser() {
        given()
            .contentType("application/json")
            .body("""
                { "username": "admin", "password": "admin123" }
                """)
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("token", notNullValue())
            .body("user.name", equalTo("admin"))
            .extract().path("token");

        // 第二个请求带上 Token
        given()
            .auth().oauth2(token)
        .when()
            .get("/api/users/me")
        .then()
            .statusCode(200)
            .body("email", equalTo("admin@example.com"));
    }
}
```

### 9.2 JSON Schema 校验

```1:a:java-learning/83-软件测试Harness实战.md
@Test
void shouldMatchJsonSchema() {
    given()
    .when()
        .get("/api/users/1")
    .then()
        .statusCode(200)
        .body(matchesJsonSchemaInClasspath("user-schema.json"));
}
```

`src/test/resources/user-schema.json`：
```1:a:java-learning/83-软件测试Harness实战.md
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["id", "name", "email"],
  "properties": {
    "id": { "type": "integer" },
    "name": { "type": "string" },
    "email": { "type": "string", "format": "email" }
  }
}
```

### 9.3 Spring Boot 集成 + Spec 重用

```1:a:java-learning/83-软件测试Harness实战.md
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UserApiE2ETest extends BaseApiTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldCreateUser() {
        UserDTO dto = new UserDTO("张三", "zhangsan@example.com");

        given()
            .spec(authSpec())         // 复用鉴权 Spec
            .body(dto)
        .when()
            .post("/api/users")
        .then()
            .spec(commonResponseSpec())    // 复用 Response 校验
            .body("name", equalTo("张三"));
    }
}

abstract class BaseApiTest {
    @LocalServerPort
    protected int port;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    protected RequestSpecification authSpec() {
        return new RequestSpecBuilder()
            .addHeader("Authorization", "Bearer " + loginAndGetToken())
            .setContentType(ContentType.JSON)
            .build();
    }
}
```

---

## 十、覆盖率与质量门禁

### 10.1 JaCoCo 配置

```1:a:java-learning/83-软件测试Harness实战.md
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>     <!-- 80% 行覆盖率 -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 10.2 SonarQube 集成

```1:a:java-learning/83-软件测试Harness实战.md
<plugin>
    <groupId>org.sonarsource.scanner.maven</groupId>
    <artifactId>sonar-maven-plugin</artifactId>
</plugin>
```

```1:a:java-learning/83-软件测试Harness实战.md
mvn clean verify sonar:sonar \
    -Dsonar.projectKey=mall-order \
    -Dsonar.host.url=https://sonar.example.com \
    -Dsonar.login=$SONAR_TOKEN
```

Sonar 质量门禁（Quality Gate）：
- 新代码覆盖率 ≥ 80%
- 重复代码 ≤ 3%
- 圈复杂度 ≤ 10
- 新增漏洞 = 0
- 新增 Code Smell ≤ 5

---

## 十一、企业级 Harness 架构

### 11.1 测试金字塔

```
                  ╱╲
                 ╱  ╲
                ╱ E2E╲             <- REST Assured + Selenium（少量、慢）
               ╱______╲
              ╱        ╲
             ╱ 集成测试  ╲          <- @SpringBootTest + Testcontainers（中等）
            ╱____________╲
           ╱              ╲
          ╱    单元测试     ╲       <- JUnit + Mockito（大量、快）
         ╱__________________╲
```

**比例建议**：70% 单元 / 20% 集成 / 10% E2E。

### 11.2 Maven 多模块测试分层

```
mall-order/
├── src/main/java       <- 业务代码
├── src/test/java       <- 单元测试（JUnit + Mockito）
└── src/it/java         <- 集成测试（@SpringBootTest + Testcontainers）
```

pom.xml 配置 Surefire/Failsafe：
```1:a:java-learning/83-软件测试Harness实战.md
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>**/it/**/*IT.java</exclude>     <!-- Surefire 跳过 IT -->
        </excludes>
    </configuration>
</plugin>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/it/**/*IT.java</include>     <!-- Failsafe 只跑 IT -->
        </includes>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

CI 命令：
```1:a:java-learning/83-软件测试Harness实战.md
mvn test          # 跑单元测试
mvn verify        # 跑单元 + 集成测试 + 覆盖率门禁
```

### 11.3 GitHub Actions / Jenkins 集成

```1:a:java-learning/83-软件测试Harness实战.md
# .github/workflows/test.yml
name: Test
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - run: ./mvnw verify -B
      - uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: target/site/jacoco
      - uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: target/surefire-reports
```

---

## 十二、踩坑与最佳实践

### 12.1 十二大经典坑

1. **H2 ≠ PG，PG 特性挂掉**
   - 测试用 H2，线上用 PG，SQL 方言差异；解决：用 Testcontainers 跑真实 PG。

2. **`@SpringBootTest` 启动 30 秒**
   - 加载整个 Context 太慢；解决：用切片注解（`@WebMvcTest` / `@DataJpaTest`）。

3. **Testcontainers 启动 5 秒/类**
   - 每个测试类都启一个新容器；解决：开 `.withReuse(true)` 复用容器。

4. **测试数据残留**
   - 上一条测试数据影响下一条；解决：`@BeforeEach` 调 `repository.deleteAll()`。

5. **静态 `LocalDateTime.now()` 难测**
   - 时间相关逻辑难断言；解决：`Clock` 注入 + Mockito `mockStatic()`。

6. **`@Transactional` 测试回滚失效**
   - 测试方法上 `@Transactional` 应回滚但没回滚；解决：Failsafe 阶段不要加 `@Transactional`，自己手动清理。

7. **Mockito `when().thenReturn()` 对 void 方法无效**
   - `void` 方法用 `doNothing().when(mock).method()`。

8. **跨测试 Bean 状态污染**
   - 静态 `@MockBean` 跨类残留；解决：每个测试类单独 `@ExtendWith(MockitoExtension.class)` 或 `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)`。

9. **REST Assured 端口冲突**
   - 多测试类用同一个固定端口 8080 冲突；解决：`webEnvironment = RANDOM_PORT` + `@LocalServerPort`。

10. **覆盖率统计漏测 main 方法**
    - JaCoCo 默认不统计 `src/main/java` 之外的；解决：配置 `includes` 包含 `com/example/**`。

11. **MockMvc 不走过滤器**
    - Spring Security 过滤器链没生效；解决：`@AutoConfigureMockMvc` + `@WithMockUser`。

12. **测试金字塔倒挂**
    - 90% E2E、10% 单元，运行 1 小时；解决：调整比例到 70/20/10。

### 12.2 性能调优清单

| 场景 | 调优点 |
|---|---|
| 启动 Context 慢 | 切片注解 + `@MockBean` 替代真实依赖 |
| Testcontainers 慢 | 复用容器 + 镜像预拉 |
| DB 准备慢 | `@Sql(scripts = "/data.sql")` 一次性导入 |
| 大量 mock | `@Mock` 替代 `@MockBean`（不重新加载 Context） |
| 断言慢 | AssertJ 流式 + `usingRecursiveComparison()` |
| CI 慢 | 并行 Surefire + 拆分模块 |

---

## 十三、面试常问 5 题

**Q1：JUnit 4 vs JUnit 5 的核心差异？**
A：JUnit 5 三大特性：
- **Jupiter API** 替代单一 `@Test`；
- **扩展模型** `@ExtendWith` 替代 `@RunWith`（可组合多个扩展）；
- **嵌套 + 参数化 + 动态测试** 原生支持。
生产建议：Spring Boot 3.x 强制 JUnit 5。

**Q2：@SpringBootTest vs @WebMvcTest 区别？**
A：
- `@SpringBootTest` 加载**整个 Context**，启动慢但真实，适合集成测试；
- `@WebMvcTest` 只加载 **Controller + Web 层**，需配 `@MockBean` 模拟 Service，启动快，适合 Controller 切片。
生产建议：70% 单元 + 20% 集成（`@SpringBootTest`）+ 10% E2E。

**Q3：H2 内存数据库够用吗？**
A：**不够**。H2 ≠ PG/MySQL，方言差异（UPSERT、JSONB、RETURNING、窗口函数）。**生产级集成测试必须用 Testcontainers 跑真实 PG/MySQL**。

**Q4：Testcontainers 与 Docker Compose 的区别？**
A：
- **Docker Compose** 适合全局共享环境，开发/CI 同启；
- **Testcontainers** 适合测试隔离，每个测试类独立容器；
- 生产建议：本地开发用 Compose + CI/E2E 用 Testcontainers。

**Q5：Mockito 与 Spring Mock 的区别？**
A：
- **Mockito** 是通用 Mock 框架，`@Mock` 创建纯模拟对象；
- **Spring `@MockBean`** 把 Mockito Mock 对象注册到 Spring Context，**会重建 Context（慢）**；
- 生产建议：纯单元测试用 `@Mock`，需要 Spring 上下文时才用 `@MockBean`。

---

## 十四、与本仓库其他章节的衔接

| 主线章节 | 本章关联 |
|---|---|
| 第 12 章 IO/网络 | 资源文件 `application-test.yml` 加载 |
| 第 16 章 Spring Boot 实战 | `@SpringBootTest` 测试启动 |
| 第 17 章 异常处理 | `@ControllerAdvice` 的测试用例 |
| 第 22 章 数据库与事务 | `@DataJpaTest` + Testcontainers PG |
| 第 25 章 缓存 | Testcontainers Redis 测试 |
| 第 28 章 消息队列 | Testcontainers Kafka 测试 |
| 第 31 章 Spring Boot 项目实战（mall-boot） | 项目中的 harness 实践 |

---

## 十五、收官总结

软件测试 Harness 是"用代码守护代码"的工程实践。本章覆盖了：

- 5 类核心组件：Fixture / Runner / Reporter / Driver / Slice
- 5 款主流框架对比：JUnit 5 / TestNG / Spock / Kotest / AssertJ
- 实战：JUnit 5 注解 + Mockito 5 + Spring Boot Test + Testcontainers + REST Assured
- 覆盖率门禁：JaCoCo + SonarQube
- 企业架构：测试金字塔 + 多模块 + CI/CD
- 12 大踩坑：H2 方言、Context 慢、容器复用、状态污染等
- 5 道面试题

掌握本章后，企业 Spring Boot 项目的 80% 测试需求能独立完成（剩余 20% 需性能测试 JMeter/Gatling、安全测试 OWASP ZAP）。