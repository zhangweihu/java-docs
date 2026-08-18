# 第六章 IO 流、文件操作、网络编程与 JDBC

> 本章掌握：文件读写（字符流/字节流）、文件操作、Socket 网络编程、JDBC 数据库操作。
> 这些是与真实业务打交道的基础能力。

## 6.1 IO 流体系

```
字节流（处理所有文件，如图片、视频）
├── InputStream（读）
│   ├── FileInputStream    文件字节输入流
│   └── BufferedInputStream 缓冲
└── OutputStream（写）
    ├── FileOutputStream    文件字节输出流
    └── BufferedOutputStream

字符流（处理文本文件，按字符读写）
├── Reader（读）
│   ├── FileReader          文件字符输入流
│   └── BufferedReader      缓冲（支持 readLine）
└── Writer（写）
    ├── FileWriter          文件字符输出流
    └── BufferedWriter
```

**记忆口诀**：字节流管一切，字符流管文本，缓冲流提效率，文本行读用 Buffer。

## 6.2 文件操作（File 类）

```java
import java.io.File;

public class FileDemo {
    public static void main(String[] args) throws Exception {
        // 创建 File 对象（不一定存在）
        File file = new File("d:/project/java/docs/java-learning/test.txt");

        // 创建新文件
        if (!file.exists()) {
            boolean created = file.createNewFile();
            System.out.println("创建文件：" + created);
        }

        // 文件信息
        System.out.println("文件名：" + file.getName());
        System.out.println("路径：" + file.getPath());
        System.out.println("绝对路径：" + file.getAbsolutePath());
        System.out.println("大小（字节）：" + file.length());
        System.out.println("是否文件：" + file.isFile());
        System.out.println("是否可读：" + file.canRead());
        System.out.println("最后修改时间：" + new java.util.Date(file.lastModified()));

        // 目录操作
        File dir = new File("d:/project/java/docs/java-learning/sub");
        if (!dir.exists()) {
            dir.mkdir();        // 创建单级目录
            // dir.mkdirs();     // 创建多级目录
        }

        // 列出目录下的文件
        File parent = new File("d:/project/java/docs/java-learning");
        File[] files = parent.listFiles();
        if (files != null) {
            for (File f : files) {
                System.out.println((f.isDirectory() ? "[目录] " : "[文件] ") + f.getName());
            }
        }

        // 删除文件
        // file.delete();
        // 删除后运行 Java 会重新创建，方便演示
    }
}
```

## 6.3 文件读写

### 6.3.1 字符流读写（文本文件）

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

public class FileReadWriteDemo {
    public static void main(String[] args) {
        // ========== 写入文件 ==========
        String path = "d:/project/java/docs/java-learning/output.txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            // try-with-resources：JDK7+ 自动关闭资源，无需手动 close
            writer.write("第一行：Hello Java");
            writer.newLine();                // 换行
            writer.write("第二行：学习 IO 流");
            writer.newLine();
            writer.write("第三行：写完啦");
            System.out.println("写入成功！");
        } catch (Exception e) {
            System.out.println("写入失败：" + e.getMessage());
        }

        // ========== 读取文件（逐行读）==========
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                System.out.println("第" + lineNo + "行：" + line);
            }
        } catch (Exception e) {
            System.out.println("读取失败：" + e.getMessage());
        }
    }
}
```

> `try-with-resources` 是 JDK7+ 的推荐写法：实现 `AutoCloseable` 的资源在 try 结束后自动关闭，无需 finally 手动 `close()`。

### 6.3.2 字节流读写（任意文件，含图片/视频）

```java
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class ByteStreamDemo {
    public static void main(String[] args) {
        String src = "d:/project/java/docs/java-learning/input.bin";
        String dest = "d:/project/java/docs/java-learning/copy.bin";

        // 模拟源文件：先写一些数据
        try (FileOutputStream fos = new FileOutputStream(src)) {
            byte[] data = {65, 66, 67, 68, 69};   // A B C D E
            fos.write(data);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 复制文件
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest)) {

            byte[] buffer = new byte[1024];   // 缓冲区 1KB
            int len;
            while ((len = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, len);    // 写入实际读取的长度
            }
            System.out.println("文件复制完成！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### 6.3.3 编码问题（中文乱码解决）

```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class EncodingDemo {
    public static void main(String[] args) throws Exception {
        // 问题：默认使用平台编码（Windows 是 GBK），中文可能乱码
        // 方案一：用 InputStreamReader 指定编码
        // new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));

        // 方案二：用 Files + Charset（推荐，NIO）
        String path = "d:/project/java/docs/java-learning/output.txt";
        java.util.List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        for (String line : lines) {
            System.out.println(line);
        }

        // 使用 Files 写入
        Files.write(Paths.get(path),
                java.util.Arrays.asList("新写入的内容1", "新写入的内容2"),
                StandardCharsets.UTF_8);
        System.out.println("NIO 写入成功");
    }
}
```

## 6.4 序列化（对象持久化）

对象需要通过网络传输或存入文件时，将其转换为字节流（序列化）。

```java
import java.io.*;

// 必须实现 Serializable 接口（标记接口）
class User implements Serializable {
    // 序列化版本号（强烈建议显式声明）
    private static final long serialVersionUID = 1L;

    private String name;
    private int age;

    // transient：该字段不参与序列化
    private transient String password;

    public User(String name, int age, String password) {
        this.name = name;
        this.age = age;
        this.password = password;
    }

    @Override
    public String toString() {
        return "User{name='" + name + "', age=" + age + ", password='" + password + "'}";
    }
}

public class SerializeDemo {
    public static void main(String[] args) {
        String path = "d:/project/java/docs/java-learning/user.ser";

        // 序列化：对象 → 文件
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(new User("张三", 25, "123456"));
            System.out.println("序列化完成");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 反序列化：文件 → 对象
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            User user = (User) ois.readObject();
            System.out.println("反序列化结果：" + user);   // password 为 null（transient）
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## 6.5 网络编程（Socket）

Socket 实现两台机器之间的通信：**ServerSocket（服务端）** + **Socket（客户端）**。

### 6.5.1 简单聊天程序

```java
// ========== 服务端（先启动）==========
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServer {
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8888)) {
            System.out.println("服务端已启动，监听端口 8888...");

            // accept() 阻塞等待客户端连接
            Socket socket = serverSocket.accept();
            System.out.println("客户端已连接：" + socket.getInetAddress());

            // 接收数据
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // 发送数据
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("客户端说：" + line);
                out.println("服务端收到：" + line);   // 回显
                if ("bye".equalsIgnoreCase(line)) {
                    break;
                }
            }
            System.out.println("服务端关闭");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

```java
// ========== 客户端 ==========
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class TcpClient {
    public static void main(String[] args) {
        try (Socket socket = new Socket("127.0.0.1", 8888);
             Scanner sc = new Scanner(System.in)) {

            // 发送数据
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            // 接收数据
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("已连接服务端，输入消息（输入 bye 退出）：");
            String input;
            while (sc.hasNextLine()) {
                input = sc.nextLine();
                out.println(input);
                String response = in.readLine();
                System.out.println("服务端回复：" + response);
                if ("bye".equalsIgnoreCase(input)) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

**运行步骤**：
1. 先运行 `TcpServer`（监听 8888 端口）。
2. 再运行 `TcpClient`，输入文字即可通信。

## 6.6 JDBC 数据库操作

JDBC（Java Database Connectivity）是 Java 访问数据库的标准 API。

### 6.6.1 准备工作

```xml
<!-- Maven 项目中引入 MySQL 驱动（pom.xml） -->
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

```sql
-- 创建测试数据库和表
CREATE DATABASE IF NOT EXISTS java_demo DEFAULT CHARSET utf8mb4;
USE java_demo;

CREATE TABLE student (
    id      INT PRIMARY KEY AUTO_INCREMENT,
    name    VARCHAR(50) NOT NULL,
    age     INT,
    score   DOUBLE
);

INSERT INTO student (name, age, score) VALUES ('张三', 20, 85.5);
INSERT INTO student (name, age, score) VALUES ('李四', 21, 92.0);
INSERT INTO student (name, age, score) VALUES ('王五', 22, 78.5);
```

### 6.6.2 JDBC 基础操作

```java
import java.sql.*;

public class JdbcDemo {
    // 连接参数（根据实际情况修改）
    private static final String URL = "jdbc:mysql://localhost:3306/java_demo"
            + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PASSWORD = "123456";

    public static void main(String[] args) {
        // JDBC 编程 6 步走：
        // 1. 加载驱动（JDBC4+ 可省略，自动加载）
        // 2. 获取连接 Connection
        // 3. 创建 Statement / PreparedStatement
        // 4. 执行 SQL
        // 5. 处理结果集 ResultSet
        // 6. 关闭资源

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 步骤 2：获取连接
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("数据库连接成功！");

            // ===== 查询 =====
            String sql = "SELECT id, name, age, score FROM student";
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            System.out.println("---- 学生列表 ----");
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                int age = rs.getInt("age");
                double score = rs.getDouble("score");
                System.out.printf("id=%d, name=%s, age=%d, score=%.1f%n", id, name, age, score);
            }

            // ===== 新增（用 ? 占位符防止 SQL 注入）=====
            ps = conn.prepareStatement("INSERT INTO student(name, age, score) VALUES(?, ?, ?)");
            ps.setString(1, "赵六");
            ps.setInt(2, 23);
            ps.setDouble(3, 88.0);
            int insertCount = ps.executeUpdate();
            System.out.println("插入 " + insertCount + " 行");

            // ===== 修改 =====
            ps = conn.prepareStatement("UPDATE student SET score = ? WHERE name = ?");
            ps.setDouble(1, 95.5);
            ps.setString(2, "赵六");
            int updateCount = ps.executeUpdate();
            System.out.println("更新 " + updateCount + " 行");

            // ===== 删除 =====
            ps = conn.prepareStatement("DELETE FROM student WHERE name = ?");
            ps.setString(1, "赵六");
            int deleteCount = ps.executeUpdate();
            System.out.println("删除 " + deleteCount + " 行");

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            // 步骤 6：关闭资源（先开的后关）
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
```

### 6.6.3 事务处理

```java
import java.sql.*;

public class TransactionDemo {
    public static void main(String[] args) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/java_demo?useSSL=false&serverTimezone=Asia/Shanghai",
                    "root", "123456");

            // 关闭自动提交（开启事务）
            conn.setAutoCommit(false);

            // 模拟转账：张三扣 100，李四加 100
            ps = conn.prepareStatement("UPDATE student SET score = score - ? WHERE name = ?");
            ps.setDouble(1, 100);
            ps.setString(2, "张三");
            ps.executeUpdate();

            // 模拟中间出错
            // int x = 1 / 0;

            ps = conn.prepareStatement("UPDATE student SET score = score + ? WHERE name = ?");
            ps.setDouble(1, 100);
            ps.setString(2, "李四");
            ps.executeUpdate();

            conn.commit();   // 全部成功：提交事务
            System.out.println("转账成功，事务已提交");
        } catch (Exception e) {
            e.printStackTrace();
            try {
                if (conn != null) {
                    conn.rollback();   // 出错：回滚事务
                    System.out.println("转账失败，事务已回滚");
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
```

**事务特性（ACID）**：原子性（Atomicity）、一致性（Consistency）、隔离性（Isolation）、持久性（Durability）。

### 6.6.4 DAO 模式（数据访问对象）

将数据库操作封装为类，业务层与数据层解耦。

```java
// 实体类
class Student {
    private int id;
    private String name;
    private int age;
    private double score;

    public Student() {}

    public Student(String name, int age, double score) {
        this.name = name;
        this.age = age;
        this.score = score;
    }

    // getter / setter（省略，实际开发必须写）
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    @Override
    public String toString() {
        return "Student{id=" + id + ", name='" + name + "', age=" + age + ", score=" + score + "}";
    }
}
```

```java
// DAO 接口：定义规范
interface StudentDao {
    List<Student> findAll();
    Student findById(int id);
    boolean add(Student student);
    boolean update(Student student);
    boolean delete(int id);
}
```

```java
// DAO 实现类：负责具体 SQL 操作
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

class StudentDaoImpl implements StudentDao {
    private static final String URL = "jdbc:mysql://localhost:3306/java_demo?useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PASSWORD = "123456";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    @Override
    public List<Student> findAll() {
        List<Student> list = new ArrayList<>();
        String sql = "SELECT * FROM student";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Student s = new Student();
                s.setId(rs.getInt("id"));
                s.setName(rs.getString("name"));
                s.setAge(rs.getInt("age"));
                s.setScore(rs.getDouble("score"));
                list.add(s);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Student findById(int id) {
        String sql = "SELECT * FROM student WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Student s = new Student();
                    s.setId(rs.getInt("id"));
                    s.setName(rs.getString("name"));
                    s.setAge(rs.getInt("age"));
                    s.setScore(rs.getDouble("score"));
                    return s;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean add(Student student) {
        String sql = "INSERT INTO student(name, age, score) VALUES(?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getName());
            ps.setInt(2, student.getAge());
            ps.setDouble(3, student.getScore());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean update(Student student) {
        String sql = "UPDATE student SET name=?, age=?, score=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getName());
            ps.setInt(2, student.getAge());
            ps.setDouble(3, student.getScore());
            ps.setInt(4, student.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM student WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
```

## 6.7 小结与练习

**本章重点**：
- File 文件操作
- 字节流 / 字符流 / 缓冲流的读写
- try-with-resources 自动关闭资源
- 序列化与 transient
- Socket 网络编程
- JDBC 六步、PreparedStatement 防注入、事务、DAO 模式

**课后练习**：
1. 编写程序统计一个文本文件中每个字符出现的次数并输出。
2. 编写程序复制一个目录下的所有 `.txt` 文件到新目录。
3. 实现一个多线程聊天服务器（支持多个客户端同时连接）。
4. 用 JDBC 实现学生信息管理（增删改查）的 DAO。

上一章：[05-多线程与并发.md](./05-多线程与并发.md) | 下一章：[07-综合实战.md](./07-综合实战.md)
