# 第八十二章 Apache Tika 实战：内容检测、元数据提取与文件处理集成

> **章节定位**：Java 工具库扩展专题。Apache Tika 是 Apache 基金会的内容检测与提取框架，基于 1400+ 文件类型的成熟解析器生态，是企业"文件上传安全验证""全文检索预处理""电子档案元数据抽取"三大场景的事实标准。
>
> **学习目标**：能用 Tika 完成文件类型识别、元数据提取、文本抽取三大核心任务，能在 Spring Boot 中集成上传安全验证与文档检索预处理，能讲清楚 Detector/Parser/Metadata 三层架构。
>
> **前置知识**：Java IO、MIME 类型基本概念、Spring Boot、Lucene/Solr/Elasticsearch（可选，用于检索集成）。

---

## 一、为什么需要 Apache Tika

企业文件处理有 4 个典型场景：

1. **上传安全**：用户上传"图片"，实际可能是 WebShell；扩展名 `.jpg` 实际是 PHP/Python 脚本。**MIME 才是真相**。
2. **全文检索**：要把上传的 PDF/Word/Excel 内容存入 Elasticsearch；手动处理每种格式成本爆炸。
3. **元数据治理**：电子档案要提取 PDF 作者/创建时间、Office 文档的修订历史、图像的 EXIF 拍摄信息。
4. **合规审计**：金融/医疗/政务场景要记录每个文件的真实类型、来源、内容指纹。

Tika 通过 **统一 API + 1400+ Parser + 200+ 内容嗅探器** 把这些场景标准化。

---

## 二、5 款文件解析库对比矩阵

| 库 | 支持格式 | 核心能力 | 体积 | 适用场景 |
|---|---|---|---|---|
| **Apache Tika** | **1400+** | 检测 + 解析 + 元数据 | 65 MB（full） | **通用上传、检索预处理** |
| Apache POI | Office 一族（doc/xls/ppt） | 单元格级操作 | 30 MB | Office 深度编辑 |
| Apache PDFBox | PDF | 文本/表单/签名 | 10 MB | PDF 生成与解析 |
| metadata-extractor | JPEG/TIFF/RAW | EXIF/IPTC/XMP | 8 MB | 图像元数据 |
| jPod | PDF | 纯 Java PDF | 2 MB | 轻量 PDF 文本 |

**Tika vs 单独库**：
- Tika 内部集成了 PDFBox、POI、metadata-extractor 等；
- 选 Tika = 一次性引入多种格式支持；
- 选单独库 = 仅某种格式、依赖更小、可控性更强。

**选择建议**：
- 多格式混合（PDF+Word+Excel+PPT+图像+音视频）→ **Tika**（本章主角）
- 单一格式深度开发（如 Excel 公式）→ 对应单独库
- 极致体积敏感（Android/小程序）→ metadata-extractor 单独集成

---

## 三、环境与依赖

```1:a:java-learning/82-ApacheTika实战.md
<!-- 完整版（含所有解析器） -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.1</version>
</dependency>

<!-- 核心 API（必选） -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.1</version>
</dependency>
```

> Tika 2.x 不再要求 SLF4J 1.7，可与任意日志框架共存；tika-parsers-standard-package 是"全家桶"，如只需 PDF/图像可改用 `tika-parsers-text-module` + `tika-parser-image-module` 减包。

---

## 四、三大核心 API

```
tika-core
├── Tika                  // 门面类（detect + parse + extract）
├── Detector              // 内容类型嗅探（200+ 种）
├── Metadata              // 键值对元数据容器
├── AutoDetectParser      // 自动选择 Parser
├── ParsingReader         // 流式文本读取
└── BodyContentHandler    // 内容回调 Handler
```

记忆口诀：**Detector 认"是什么"、Parser 抽"写了啥"、Metadata 装"附加信息"**。

---

## 五、内容检测实战（MIME Type）

### 5.1 基础检测

```1:a:java-learning/82-ApacheTika实战.md
Tika tika = new Tika();

try (InputStream is = new FileInputStream("photo.jpg")) {
    String mime = tika.detect(is);
    System.out.println(mime);   // image/jpeg
}
```

**Tika 默认基于 magic-bytes 检测**，不看扩展名、不看文件名，能识别伪装的 `photo.php.jpg` 是 PHP 脚本。

### 5.2 文件名辅助检测

```1:a:java-learning/82-ApacheTika实战.md
Metadata metadata = new Metadata();
metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "report.pdf");

Tika tika = new Tika();
String mime = tika.detect(new FileInputStream("report.pdf"), metadata);
// image/jpeg  → 二进制嗅探为准，文件名为辅助
```

### 5.3 容器格式嗅探（ZIP 套 PDF）

```1:a:java-learning/82-ApacheTika实战.md
// 1. 一级检测：ZIP（容器）
String mime1 = tika.detect(is);   // application/zip

// 2. 用 MimeTypes 自定义容器嗅探
TikaInputStream stream = TikaInputStream.get(is);
ContainerDetector detector = new ZipContainerDetector();
MimeType mime2 = detector.detect(stream, metadata);  // application/pdf
```

### 5.4 检测 100+ 种图像格式

```1:a:java-learning/82-ApacheTika实战.md
@Test
void detectImage() throws Exception {
    Map<String, String> samples = Map.of(
        "test.jpg", "image/jpeg",
        "test.png", "image/png",
        "test.heic", "image/heic",     // iPhone 12+ 格式
        "test.cr2", "image/x-canon-cr2",  // Canon RAW
        "test.psd", "image/vnd.adobe.photoshop",
        "test.svg", "image/svg+xml"
    );
    for (var e : samples.entrySet()) {
        String detected = tika.detect(new FileInputStream("src/test/resources/" + e.getKey()));
        System.out.println(e.getKey() + " -> " + detected + " (expected " + e.getValue() + ")");
    }
}
```

---

## 六、文本提取实战

### 6.1 一行代码抽取纯文本

```1:a:java-learning/82-ApacheTika实战.md
Tika tika = new Tika();

try (InputStream is = new FileInputStream("contract.pdf")) {
    String text = tika.parseToString(is);
    System.out.println(text);
}
```

### 6.2 提取到 StringWriter（避免大文件爆内存）

```1:a:java-learning/82-ApacheTika实战.md
StringWriter writer = new StringWriter();
SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
TransformerHandler handler = factory.newTransformerHandler(new StreamResult(writer));
handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "text");

try (InputStream is = new FileInputStream("big.pdf")) {
    tika.parse(is, new BodyContentHandler(handler));
}
String text = writer.toString();
```

### 6.3 控制文本长度（防止 DoS）

```1:a:java-learning/82-ApacheTika实战.md
// 限制最大 10 万字符（推荐）
BodyContentHandler handler = new BodyContentHandler(100_000);

try (InputStream is = new FileInputStream("docx.docx")) {
    Metadata metadata = new Metadata();
    tika.parse(is, handler, metadata);
}
String truncated = handler.toString();
// 超长文件会被截断，抛出 SAXException 但可控
```

### 6.4 流式文本（按行读）

```1:a:java-learning/82-ApacheTika实战.md
try (Reader reader = tika.parse(new File("docx.docx"))) {
    BufferedReader br = new BufferedReader(reader);
    String line;
    while ((line = br.readLine()) != null) {
        // 逐行处理，适合超大文件
    }
}
```

### 6.5 HTML 抽取（保留结构）

```1:a:java-learning/82-ApacheTika实战.md
// 想保留 HTML 结构用于渲染
String html = tika.parseToString(
        new File("report.docx"),
        new Metadata(),
        -1,                              // 长度不限
        new ParseContext());             // 需指定 BodyContentHandler

// 更标准做法
BodyContentHandler handler = new BodyContentHandler(new ToHTMLContentHandler());
tika.parse(is, handler, metadata);
```

---

## 七、元数据提取实战

### 7.1 基础元数据

```1:a:java-learning/82-ApacheTika实战.md
Metadata metadata = new Metadata();
try (InputStream is = new FileInputStream("photo.jpg")) {
    tika.parse(is, metadata);
}

// Tika Core Properties（统一字段）
System.out.println("标题: " + metadata.get(TikaCoreProperties.TITLE));
System.out.println("作者: " + metadata.get(TikaCoreProperties.CREATOR));
System.out.println("创建时间: " + metadata.get(TikaCoreProperties.CREATED));
System.out.println("修改时间: " + metadata.get(TikaCoreProperties.MODIFIED));
System.out.println("字数: " + metadata.get(TikaCoreProperties.WORD_COUNT));
System.out.println("字符集: " + metadata.get(HttpHeaders.CONTENT_ENCODING));
System.out.println("MIME: " + metadata.get(HttpHeaders.CONTENT_TYPE));

// 遍历所有 metadata
for (String name : metadata.names()) {
    System.out.println(name + " = " + metadata.get(name));
}
```

### 7.2 各格式元数据示例

#### PDF 元数据

```1:a:java-learning/82-ApacheTika实战.md
Metadata metadata = new Metadata();
tika.parse(new FileInputStream("contract.pdf"), metadata);

System.out.println(metadata.get("pdf:PDFVersion"));        // 1.7
System.out.println(metadata.get("pdf:author"));            // PDF作者
System.out.println(metadata.get("xmp:CreatorTool"));        // Microsoft Word
System.out.println(metadata.get("pdf:docinfo:created"));   // 创建时间
```

#### Office 文档元数据

```1:a:java-learning/82-ApacheTika实战.md
Metadata metadata = new Metadata();
tika.parse(new FileInputStream("plan.docx"), metadata);

System.out.println(metadata.get("dc:title"));              // Dublin Core 标题
System.out.println(metadata.get("dc:creator"));            // 作者
System.out.println(metadata.get("cp:lastModifiedBy"));     // 最后修改人
System.out.println(metadata.get("Application-Name"));     // 应用名称
System.out.println(metadata.get("Application-Version"));  // 应用版本
System.out.println(metadata.get("Revision-Number"));      // 修订号
```

#### 图像 EXIF

```1:a:java-learning/82-ApacheTika实战.md
Metadata metadata = new Metadata();
tika.parse(new FileInputStream("photo.jpg"), metadata);

System.out.println(metadata.get("tiff:Make"));             // 相机厂商
System.out.println(metadata.get("tiff:Model"));            // 相机型号
System.out.println(metadata.get("exif:DateTimeOriginal")); // 拍摄时间
System.out.println(metadata.get("exif:ExposureTime"));     // 曝光时间
System.out.println(metadata.get("exif:FNumber"));          // 光圈
System.out.println(metadata.get("exif:FocalLength"));      // 焦距
System.out.println(metadata.get("geo:lat"));               // GPS 纬度
System.out.println(metadata.get("geo:long"));              // GPS 经度
System.out.println(metadata.get("tiff:ImageWidth"));       // 宽度
System.out.println(metadata.get("tiff:ImageLength"));      // 高度
```

#### 音视频元数据

```1:a:java-learning/82-ApacheTika实战.md
Metadata metadata = new Metadata();
tika.parse(new FileInputStream("song.mp3"), metadata);

System.out.println(metadata.get("id3v2:artist"));         // 演唱者
System.out.println(metadata.get("id3v2:album"));          // 专辑
System.out.println(metadata.get("id3v2:title"));          // 标题
System.out.println(metadata.get("xmpDM:duration"));       // 时长（毫秒）
System.out.println(metadata.get("channels"));             // 声道数
System.out.println(metadata.get("sampleRate"));           // 采样率
```

### 7.3 自定义元数据过滤（避免泄漏隐私）

```1:a:java-learning/82-ApacheTika实战.md
public class SafeMetadataFilter implements MetadataFilter {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "geo:lat", "geo:long",
        "exif:GPSLatitude", "exif:GPSLongitude",
        "id3v2:artist",
        "pdf:docinfo:producer",
        "dc:creator"
    );

    @Override
    public boolean accept(String key, String value) {
        return !SENSITIVE_KEYS.contains(key.toLowerCase());
    }
}

tika.parse(is, handler, metadata, new ParseContext(), new SafeMetadataFilter());
```

---

## 八、Spring Boot 文件处理集成

### 8.1 上传安全验证（防 WebShell）

```1:a:java-learning/82-ApacheTika实战.md
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final Tika tika = new Tika();
    private static final Set<String> ALLOWED_MIMES = Set.of(
        "image/jpeg", "image/png", "image/gif",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  // docx
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"         // xlsx
    );

    public FileMeta verifyAndStore(MultipartFile file, String bizType) throws IOException {
        // 1. 大小校验
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new BusinessException("文件超过 50MB 限制");
        }

        // 2. MIME 检测（核心安全步骤）
        try (InputStream is = file.getInputStream()) {
            String mime = tika.detect(is);
            if (!ALLOWED_MIMES.contains(mime)) {
                throw new BusinessException("不支持的文件类型: " + mime);
            }

            // 3. 元数据提取
            is.reset();
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
            BodyContentHandler handler = new BodyContentHandler(10_000);  // 仅取前 10k 字符做安全扫描
            tika.parse(is, handler, metadata);

            // 4. 病毒扫描（ClamAV/阿里云 OSS 病毒检测）
            // virusScanService.scan(file.getBytes());

            // 5. 存储到 OSS / 本地
            String url = storageService.upload(bizType, file);

            return FileMeta.builder()
                    .originalName(file.getOriginalFilename())
                    .mime(mime)
                    .size(file.getSize())
                    .url(url)
                    .metadata(toMap(metadata))
                    .build();
        }
    }
}
```

### 8.2 全文检索预处理（写入 Elasticsearch）

```1:a:java-learning/82-ApacheTika实战.md
@Service
@RequiredArgsConstructor
public class DocumentIndexService {

    private final Tika tika = new Tika();
    private final ElasticsearchClient esClient;

    public void indexDocument(Long docId, Path filePath) throws Exception {
        Metadata metadata = new Metadata();
        String text;

        try (InputStream is = Files.newInputStream(filePath)) {
            text = tika.parseToString(is, metadata, 100_000);  // 限 100k 字符
        }

        // 构造 ES 文档
        Map<String, Object> doc = new HashMap<>();
        doc.put("doc_id", docId);
        doc.put("title", metadata.get(TikaCoreProperties.TITLE));
        doc.put("author", metadata.get(TikaCoreProperties.CREATOR));
        doc.put("created", metadata.get(TikaCoreProperties.CREATED));
        doc.put("content", text);
        doc.put("mime", metadata.get(HttpHeaders.CONTENT_TYPE));

        esClient.index(i -> i.index("documents").id(String.valueOf(docId)).document(doc));
    }
}
```

### 8.3 Controller

```1:a:java-learning/82-ApacheTika实战.md
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService uploadService;

    @PostMapping("/upload")
    public Result<FileMeta> upload(@RequestParam("file") MultipartFile file) {
        try {
            return Result.ok(uploadService.verifyAndStore(file, "default"));
        } catch (IOException e) {
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/preview/{id}")
    public void preview(@PathVariable Long id, HttpServletResponse resp) throws IOException {
        // 提取为 HTML 直接返回
        Path path = fileRepository.findPathById(id);
        try (InputStream is = Files.newInputStream(path);
             OutputStream os = resp.getOutputStream()) {
            resp.setContentType("text/html;charset=UTF-8");
            tika.parseToString(is, os);   // 输出 HTML 预览
        }
    }
}
```

---

## 九、高级特性

### 9.1 自动选择 Parser

```1:a:java-learning/82-ApacheTika实战.md
AutoDetectParser parser = new AutoDetectParser();
BodyContentHandler handler = new BodyContentHandler();
Metadata metadata = new Metadata();
ParseContext context = new ParseContext();

try (InputStream is = new FileInputStream("docx.docx")) {
    parser.parse(is, handler, metadata, context);
}
String text = handler.toString();
```

> `AutoDetectParser` 内部用 Detector 嗅探 → 选择对应 Parser → 解析。**Tika 门面方法底层就是它**。

### 9.2 自定义 Parser

```1:a:java-learning/82-ApacheTika实战.md
public class EncryptedJsonParser extends AbstractParser {
    private static final Set<MediaType> SUPPORTED = Set.of(MediaType.APPLICATION_JSON);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException {
        // 1. 解密
        String decrypted = decrypt(stream);

        // 2. 触发 JSON 解析
        JsonParser jsonParser = (JsonParser) context.get(JsonParser.class);
        if (jsonParser == null) {
            jsonParser = new JsonParser();
            context.set(JsonParser.class, jsonParser);
        }
        jsonParser.parse(new ByteArrayInputStream(decrypted.getBytes(StandardCharsets.UTF_8)),
                handler, metadata, context);
    }
}
```

### 9.3 RecursiveParser（容器内嵌文件）

```1:a:java-learning/82-ApacheTika实战.md
// ZIP 内嵌文件
RecursiveParserWrapper wrapper = new RecursiveParserWrapper(new AutoDetectParser());

Metadata metadata = new Metadata();
metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "archive.zip");
BodyContentHandler handler = new BodyContentHandler();

try (InputStream is = new FileInputStream("archive.zip")) {
    wrapper.parse(is, handler, metadata);
}

// 内嵌文件列表
List<String> embeddedFiles = metadata.getValues(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
```

### 9.4 安全防护（必须开启）

```1:a:java-learning/82-ApacheTika实战.md
TikaInputStream stream = TikaInputStream.get(file);
ParseContext context = new ParseContext();

// 1. 限制总输入字节（防 DoS）
context.set(InputStreamFactory.class, new SecureInputStreamFactory(
        Files.newInputStream(file.toPath()),
        100 * 1024 * 1024));  // 100MB 上限

// 2. XML Bomb 防护
context.set(XMLReaderUtils.class, new XMLReaderUtils() {{
    setEntityResolverLimit(10_000);  // 限制实体扩展
}});

// 3. Zip Slip 防护（容器内嵌路径安全）
context.set(ZipContainerDetector.class, new ZipContainerDetector() {{
    setMaxFileCount(1_000);
    setMaxFileSize(50 * 1024 * 1024);
}});

AutoDetectParser parser = new AutoDetectParser();
parser.parse(stream, new BodyContentHandler(100_000), metadata, context);
```

### 9.5 OCR（图像转文本）

Tika 2.x 内置 Tesseract OCR 桥接：

```1:a:java-learning/82-ApacheTika实战.md
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parser-ocr-module</artifactId>
    <version>2.9.1</version>
</dependency>

<!-- 系统安装 tesseract-ocr -->
```

```1:a:java-learning/82-ApacheTika实战.md
TesseractOCRConfig config = new TesseractOCRConfig();
config.setLanguage("chi_sim+eng");  // 简体中文 + 英文
ParseContext context = new ParseContext();
context.set(TesseractOCRConfig.class, config);

BodyContentHandler handler = new BodyContentHandler();
Metadata metadata = new Metadata();
tika.parse(new FileInputStream("scan.jpg"), handler, metadata, context);
String text = handler.toString();  // 包含 OCR 识别的内容
```

### 9.6 自定义 Detector

```1:a:java-learning/82-ApacheTika实战.md
public class CompanyInternalFileDetector extends DefaultDetector {
    @Override
    public MediaType detect(TikaInputStream stream, Metadata metadata) throws IOException {
        MediaType type = super.detect(stream, metadata);

        // 公司内部格式：magic-bytes 是 "CMP1"
        byte[] head = new byte[4];
        if (stream.read(head) == 4 && "CMP1".equals(new String(head))) {
            return MediaType.application("x-company-internal");
        }
        return type;
    }
}
```

---

## 十、踩坑与最佳实践

### 10.1 五大经典坑

1. **`tika.detect(is)` 之后 `is` 已被消费**
   - Tika 把流读取到末尾才能完整嗅探；
   - 解决：先 `detect` → 拿 `mime` → 重开流（`MultipartFile.getBytes()` 复制、或 `TikaInputStream.get(file)` 替代）；
   - 推荐：用 `TikaInputStream.get()` 自动支持 reset/buffered。

2. **超大 PDF/Word 解析 OOM 或 CPU 100%**
   - 解决方案 1：`BodyContentHandler` 限制字符数；
   - 解决方案 2：设置超时（`TikaConfig` 的 `timeoutMillis`）；
   - 解决方案 3：异步队列分批处理（RabbitMQ / RocketMQ）。

3. **EXIF GPS 坐标泄漏隐私**
   - 用户上传照片内含 GPS 坐标；
   - 解决：用 `MetadataFilter` 在落库前过滤 `geo:lat`、`geo:long` 等敏感字段。

4. **伪装的 WebShell 通过扩展名检查**
   - 文件名是 `.jpg`，实际是 PHP 脚本；
   - 解决：用 Tika 检测 + 文件头 magic-bytes 校验（PHP 文件会以 `<?php` 开头）；这是 MIME 嗅探的核心价值。

5. **Excel 解析慢到 10 秒/文件**
   - 5000 行 × 50 列 Excel，POI 解析慢；
   - 解决：仅取前 100 行做预览；或转用 EasyExcel；Tika 在此处不是最优。

6. **Tika 输出乱码**
   - 旧版 Windows-1252 编码文件；
   - 解决：设置 `metadata.set(HttpHeaders.CONTENT_ENCODING, "GB18030")`；或用 juniversalchardet 嗅探。

7. **容器文件（zip/rar）爆内嵌文件数量**
   - ZIP 套 10000 个小文件，解析时内存爆炸；
   - 解决：设置 `ZipContainerDetector` 限制。

### 10.2 性能调优清单

| 场景 | 调优点 |
|---|---|
| 大文件上传解析 | `TikaInputStream` + `BodyContentHandler(maxChars)` + 超时控制 |
| 全文检索入库 | 仅提取首 100k 字符；用流式 `tika.parse(reader)` |
| 元数据收集 | `metadata.names()` 一次性遍历，避免循环 IO |
| OCR 处理 | 异步队列；降采样（缩到 2000px 后 OCR） |
| 检测频率 | 应用启动时检测一次，结果缓存到 Redis |
| 容器文件 | 限制内嵌文件数；限制解压层级 |

---

## 十一、面试常问 5 题

**Q1：Tika 与 PDFBox/POI 的关系？**
A：Tika 是**门面 + 编排框架**，底层依赖 PDFBox 处理 PDF、POI 处理 Office、metadata-extractor 处理图像。**选 Tika = 一次引入 1400+ 格式支持**。如仅需某种格式深度开发（如 Excel 公式），选单独库更可控。

**Q2：如何识别伪装的 WebShell？**
A：仅看扩展名不可信，必须用 Tika 做内容嗅探（magic-bytes 检测）。`photo.php.jpg` 会被识别为 `text/x-php` 或 `application/x-php`。**Tika 是上传安全的第一道防线**。

**Q3：Tika 如何防止 OOM？**
A：三层防护：
- `BodyContentHandler(maxChars)` 限制抽取文本长度；
- `ParseContext` 设置输入字节上限 + Zip Slip 防护；
- 异步队列（RabbitMQ）+ 单文件超时。

**Q4：EXIF 隐私如何处理？**
A：用 `MetadataFilter.accept(key, value)` 过滤敏感字段（GPS、相机序列号等），在落库前剔除。GDPR/CCPA 合规必备。

**Q5：Tika 与 Elasticsearch 全文检索怎么整合？**
A：流程：上传 → Tika `parseToString` 抽取文本 → 写入 ES `content` 字段 → 检索时 ES highlight 返回命中片段。注意：Tika 仅负责抽取，**分词/相关性/高亮由 ES 负责**。

---

## 十二、与本仓库其他章节的衔接

| 主线章节 | 本章关联 |
|---|---|
| 第 12 章 IO/网络 | `InputStream`/`FileInputStream` 的进阶使用 |
| 第 16 章 Spring Boot 实战 | `MultipartFile` 上传、`HttpServletResponse` 下载 |
| 第 31 章 Spring Boot 项目实战 | 完整上传下载链路中的文件类型验证 |
| 第 36 章 搜索引擎（ES） | 全文检索预处理的文本抽取 |
| 第 81 章 openCSV 实战 | 同属"文件处理工具库"，与 openCSV 形成 CSV+多格式完整方案 |

---

## 十三、收官总结

Apache Tika 凭借 **1400+ 格式 + Detector/Parser/Metadata 三件套** 成为 Java 文件处理的事实标准。本章覆盖了：

- 三大核心：Detector（识别 MIME）、Parser（抽取文本）、Metadata（附加信息）
- 实战：上传安全验证、全文检索预处理、元数据收集
- 高级：自定义 Parser、RecursiveParser、OCR、安全防护
- 五大踩坑：流被消费、OOM、隐私泄漏、WebShell 伪装、容器爆文件
- 5 道面试题

掌握本章后，企业文件处理从"装摄像头（安全）"到"送检（检索）"到"画挂历（元数据治理）"全链路都能独立完成。