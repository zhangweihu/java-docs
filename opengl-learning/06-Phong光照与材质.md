# 第六章：Phong 光照模型与材质

> 目标：进入进阶方向的第一章。理解真实光照的三种分量（环境/漫反射/高光），用 Phong 模型在片元着色器里“手写”光照；引入法线与法线矩阵；用材质属性让同一个立方体呈现不同观感。
>
> 前置：[返回 README](./README.md) ｜ 上一章：[05-摄像机与自由漫游.md](./05-摄像机与自由漫游.md) ｜ 下一章：[07-高级渲染特性.md](./07-高级渲染特性.md)

## 6.1 光照为什么难：颜色 = 光 × 反射

真实世界的光照是物理量（辐射度），GPU 不会替你算——**你得写公式**。Phong 模型是 1990 年代至今最经典的“手工光照”，把一点上的光照拆成三份相加：

```text
最终颜色 = 环境光(Ambient) + 漫反射(Diffuse) + 高光(Specular)
           （几乎无方向      按表面朝向        按视线与反射光夹角
            的全局微光）      余弦衰减          的“亮斑”）
```

- **物体颜色不是“物体自身的颜色”**，而是“物体反射哪些光”。上章棋盘纹理是“满亮纯白光照下的漫反射色”——光照章节后它们会暗下来、出现明暗面；
- 实际渲染时：逐片元算光照（Phong 明暗，平滑）或逐顶点算再插值（Gouraud，棱角感），本专题做**逐片元**。

## 6.2 需要的向量：Normal、LightDir、ViewDir

对一个片元 P，我们只需要三组方向：

```text
       光源
        │  LightDir = normalize(lightPos - P)
        ▼
    ┌────────┐
    │   P    │ ← Normal n（表面法线，顶点属性插值而来）
    │  面    │    ViewDir = normalize(viewPos - P)
    └────────┘    反射高光与“视线多正对反射方向”有关
```

- 法线 `Normal`：每个顶点一个（立方体每个面顶点法线=面朝向），片元中自动插值；
- 三个向量每帧都要**归一化**——长度错误会让余弦计算失真；
- 实现时最好都算在 **世界空间**，统一坐标系（模型矩阵乘出来的）。

## 6.3 动手做 1：第一个被照亮的立方体

沿用第五章工程（摄像机 + 立方体）。上章纹理着色的“满亮”观感，本章改为“纯色材质 + 光照”，先去掉纹理贴图让光照公式最直观；下一节加回纹理。

### 新增 `shaders/lighting.vert`

```glsl
#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;    // 新增：法线

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;

out vec3 vWorldPos;      // 片元世界坐标（光照计算用）
out vec3 vNormal;        // 变换后的法线

void main()
{
    vec4 world = uModel * vec4(aPos, 1.0);
    vWorldPos = world.xyz;
    // 法线不能被模型矩阵直接乘！缩放会歪——用“法线矩阵”（见 6.4）
    vNormal = mat3(transpose(inverse(uModel))) * aNormal;

    gl_Position = uProjection * uView * world;
}
```

### 新增 `shaders/lighting.frag`

```glsl
#version 330 core
in vec3 vWorldPos;
in vec3 vNormal;
out vec4 FragColor;

// 光源参数
uniform vec3 uLightPos;
uniform vec3 uLightColor;

// 观察者（摄像机）位置——算视线方向
uniform vec3 uViewPos;

// 材质：物体对三路光的“响应度”
uniform vec3 uMatAmbient;
uniform vec3 uMatDiffuse;
uniform vec3 uMatSpecular;
uniform float uMatShininess;   // 高光锐利度（越大越亮越集中）

void main()
{
    vec3 normal = normalize(vNormal);

    // ① 环境光：常量弱光，保证背光面不至于全黑
    vec3 ambient = uMatAmbient * uLightColor;

    // ② 漫反射：法线点乘光方向，夹角越大越暗
    vec3 lightDir = normalize(uLightPos - vWorldPos);
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = uMatDiffuse * diff * uLightColor;

    // ③ 高光：半程向量法（Blinn-Phong，比反射法更快更好看）
    vec3 viewDir = normalize(uViewPos - vWorldPos);
    vec3 halfwayDir = normalize(lightDir + viewDir);
    float spec = pow(max(dot(normal, halfwayDir), 0.0),
                     uMatShininess);
    vec3 specular = uMatSpecular * spec * uLightColor;

    vec3 result = ambient + diffuse + specular;
    FragColor = vec4(result, 1.0);
}
```

### 修改 `src/main.cpp`：给立方体补法线

顶点从 5 分量（pos+uv）变成 **6 分量（pos + normal）**。立方体是规则体：直接用 4.8 的 36 顶点表，把每行的 `uv` 换成该面的法线（前 `0,0,1`，后 `0,0,-1`，左 `-1,0,0`…）。属性设置改为：

```cpp
    // 每个顶点 6 个 float：位置 3 + 法线 3
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 6*sizeof(float), (void*)0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 6*sizeof(float),
                          (void*)(3*sizeof(float)));
    glEnableVertexAttribArray(1);
```

> 偷懒提示：可以把第 4.8 的 36 顶点表里每行后两个 uv 值替换为法线——前、后面各 6 行全写同一法线值即可。嫌手改烦，直接给“6 分量的完整立方体表”见 6.6。

初始化材质与光源 uniform（每帧只改位置/时间变化量）：

```cpp
    // 光源摆右上方，照亮立方体
    glm::vec3 lightPos(1.2f, 1.5f, 2.0f);

    // 材质：砖红色漫反射、偏白高光、中高光锐利度
    glm::vec3 matAmbient(0.3f, 0.1f, 0.1f);
    glm::vec3 matDiffuse(0.8f, 0.3f, 0.3f);
    glm::vec3 matSpecular(1.0f, 1.0f, 1.0f);
    float shininess = 32.0f;
```

主循环中上传（把摄像机位置传给 `uViewPos`）：

```cpp
        glUseProgram(lightProgram);
        glUniform3fv(glGetUniformLocation(lightProgram, "uLightPos"),
                     1, glm::value_ptr(lightPos));
        glUniform3f(glGetUniformLocation(lightProgram, "uLightColor"),
                    1.0f, 1.0f, 1.0f);
        glUniform3fv(glGetUniformLocation(lightProgram, "uViewPos"),
                     1, glm::value_ptr(gCamera.Position));
        glUniform3fv(glGetUniformLocation(lightProgram, "uMatAmbient"),
                     1, glm::value_ptr(matAmbient));
        glUniform3fv(glGetUniformLocation(lightProgram, "uMatDiffuse"),
                     1, glm::value_ptr(matDiffuse));
        glUniform3fv(glGetUniformLocation(lightProgram, "uMatSpecular"),
                     1, glm::value_ptr(matSpecular));
        glUniform1f(glGetUniformLocation(lightProgram, "uMatShininess"),
                    shininess);
```

**预期结果**：一个暗红立方体，右上方有受光面、背光面暗但不全黑（环境光托底）、接近光源方向的面上有一个小亮斑（高光）。把光源绕着立方体转（每帧 `lightPos` 用 sin/cos），能看清三路光的边界。

## 6.4 法线矩阵：为什么法线不能直接乘 Model

模型矩阵含**缩放**。若沿 x 放大 2 倍，几何正确变宽，但“垂直面”的法线若也跟着 x 缩放会**不再垂直**（变斜），光照就错了。

修正法：法线应乘 **model 的逆转置矩阵**：

```cpp
// GLSL 里：
vNormal = mat3(transpose(inverse(uModel))) * aNormal;
```

- 代价高昂（求逆），但在顶点着色器里每顶点一次尚可接受；工程上常预计算后当 uniform 传；
- 若模型只有旋转 + 均匀缩放（无非均匀缩放），可直接 `mat3(uModel)`——多数场景安全，但本专题教你“通用正确版”；
- `mat3(...)` 是去掉平移的“线性部分”。

## 6.5 加回纹理：材质 = 纹理采样 × 光照

真实材质大多由**纹理**驱动：漫反射贴图给出每点颜色，高光贴图给出每点“反光强度”。把 6.3 的 `.frag` 的 `uMatDiffuse`/`uMatSpecular` 换成 `sampler2D`：

```glsl
uniform sampler2D uDiffuseTex;    // 物体本色（棋盘纹理即可）
uniform sampler2D uSpecularTex;   // 高光“贴图”（可用另一张纹理的亮度当强度）
```

在片元着色器里：

```glsl
vec3 albedo = texture(uDiffuseTex, vTexCoord).rgb;   // 采样出每点颜色
vec3 specVal = texture(uSpecularTex, vTexCoord).rgb;
vec3 diffuse = albedo * diff * uLightColor;          // 本色 × 光照强度
vec3 specular = specVal * spec * uLightColor;
```

- 别忘了 `.vert` 里恢复 `aTexCoord` 输出（与第三章属性布局合并：pos3 + normal3 + uv2 = 8 分量/顶点）；
- 物体立刻“长回”纹理：纹理就是它天生的漫反射材质；
- 法线与 uv 两个属性布局并存，是 3D 模型的标准姿势（下一章之后我们讨论把这两样都装进一个“Mesh”）。

> 一个 Phong 光照最常见 bug：背光面黑得看不见。先怀疑**法线反了**（绕序反了 → `normalize(vNormal)` 后点积为负被 `max` 截 0）；再检查是不是忘乘 `uLightColor`。

## 6.6 参考：6 分量（pos+normal）立方体顶点表

把 4.8 的 36 顶点表中每行末尾的 2 个 uv 换为法线三值即可。为省事，这里给出按面组织的要点：六个面分别填 `(0,0,1) / (0,0,-1) / (-1,0,0) / (1,0,0) / (0,-1,0) / (0,1,0)`，每面 6 行相同。若你更想要“带法线+uv 的 8 分量版本”，也可用 4.8 的顶点表把每行扩展成 pos3+normal3+uv2，并同步把 6.3 的属性布局加一段 location=2（uv）。

## 6.7 实战与验收

1. **让光源动起来**：`lightPos` 每帧 = `glm::vec3(sin(t)*2, 1, cos(t)*2)`，观察漫反射/高光随光源移动的变化，验证“背光面确实暗、高光总在正对反射方向处”；
2. **调材质**：把 `uMatShininess` 从 8 改到 128，观察高光从“大而糊”变“小而锐”；把 `matDiffuse` 改成绿/蓝，确认“物体颜色 = 漫反射色 × 光色”的心智；
3. **法线矩阵实验**：把 `uModel` 加 `glm::scale(model, glm::vec3(1.0f, 2.0f, 1.0f))`（压扁），对比“用逆转置矩阵”与“偷懒直接 mat3(uModel)”两种写法的光照差异——直观看到法线矩阵存在的意义；
4. **验收标准**：能默写 Phong 三行公式（ambient/diffuse/specular）并解释半程向量为什么能代替反射向量；能解释法线矩阵为什么用逆转置。

## 本章小结

- 光照是“你写公式”：环境 + 漫反射 + 高光，三路相加；
- 漫反射看 `normal·lightDir`，高光用半程向量 `normal·normalize(L+V)` 的 `pow`；
- 法线过缩放必歪 → 用逆转置矩阵（法线矩阵）；
- 材质/纹理就是给三路光提供的“响应系数”。

下一章：[07-高级渲染特性.md](./07-高级渲染特性.md)——深度测试的细节、面剔除、混合与模板，把渲染质量推向“正经引擎”。
