# 第十一章：unsafe 与 FFI

> 目标：理解 `unsafe` 的边界与五大能力、未定义行为（UB）的常见来源、如何安全地调用 C 库（FFI）与把 Rust 导出给 C，掌握"最小化 unsafe + 安全封装"工程原则，完成一个 **unsafe 安全封装实战**。
>
> 前置：[10-过程宏与元编程.md](./10-过程宏与元编程.md) ｜ 下一章：[12-Polars数据分析.md](./12-Polars数据分析.md)
>
> 高级专题核心认知：**`unsafe` 不是"关掉安全检查"，而是"由你承担编译器无法替你保证的那部分不变量"**。99% 的代码应该安全，`unsafe` 块越小越好。

## 11.1 unsafe 解决的现实问题

借用检查器非常强大，但有三类场景它帮不上忙：

1. **调用外部函数（FFI）**：C 库不懂 Rust 的所有权/借用规则；
2. **编译器保守拒绝但实际安全的操作**：性能热路径（如自定义集合的内存复用）、绕过别名检查的优化；
3. **硬件/内存映射等底层交互**：直接操作内存地址、与操作系统 API 对接。

### unsafe 的五大能力

在 `unsafe {}` 块（或 `unsafe fn`）中你可以：

1. 解引用裸指针 `*const T` / `*mut T`；
2. 调用标记为 `unsafe` 的函数（`unsafe fn`）；
3. 访问或修改**可变静态变量** `static mut`；
4. 实现 `unsafe trait`（如 `Send`/`Sync` 的手动实现、裸指针相关 trait）；
5. 访问 `union` 的字段。

```rust
fn main() {
    let mut value = 42;

    // 裸指针：与引用不同，编译器不保证其有效性
    let raw: *mut i32 = &mut value;
    unsafe {
        *raw += 1;              // 手动保证：指针有效、无别名冲突
    }
    println!("{value}");        // 43

    // 可变静态变量只能在 unsafe 中读写（跨线程竞争风险由你承担）
    static mut COUNTER: i32 = 0;
    unsafe {
        COUNTER += 1;
    }
}
```

> **第一个铁律：`unsafe fn` 的危险要写进文档。** `unsafe` 之所以要求"调用方也写 unsafe"，是为了让调用方知道"这里有前置条件需要我保证"——例如"指针必须非空且指向有效的 T"。

## 11.2 未定义行为（UB）：为什么 unsafe 危险

Unsafe 代码违反了不变量时，后果是**未定义行为（Undefined Behavior, UB）**：编译器假设永远不会发生 UB，因此 UB 可能导致崩溃、错误结果、安全漏洞甚至被优化器删除代码——而不是一个可预测的 panic。

最常见的 UB 来源（务必对照自查）：

| UB 来源 | 典型例子 |
| --- | --- |
| 悬垂指针 | 指针指向的值已被 drop，仍解引用 |
| 别名规则违反 | 同时存在 `&mut` 与通过裸指针的写访问 |
| 越界访问 | 用 `from_raw_parts` 造出超过实际长度的切片 |
| 非对齐访问 | 把任意字节地址转成 `&u64`（需对齐） |
| 释放后使用 / 二次释放 | 手动管理内存时重复 drop |
| 空指针/非有效指针 | 解引用 `null` 或野指针 |

```rust
// 演示（不要运行！这是 UB）
fn ub_demo() {
    let p: *const i32 = std::ptr::null();
    unsafe {
        // let x = *p;              // UB：空指针解引用
    }

    let dangling: *const i32 = {
        let v = 42;
        &v
    };                               // v 已释放，dangling 悬垂
    unsafe {
        // println!("{}", *dangling);   // UB：悬垂
    }
}
```

编译器/工具链能在一定程度上帮你捉 UB：

```bash
cargo miri run          # 安装：rustup +nightly && cargo install miri
```

Miri 是一个"解释执行 + 追踪指针来源"的检查器，**在开发期跑 unsafe 代码**能发现绝大多数 UB（C/C++ 没有等价的免费工具）。`cargo test` 前对含 unsafe 的模块跑一遍 Miri 是好习惯。

## 11.3 安全封装模式：把 unsafe 关进笼子

生产准则：**公开 API 全部安全，unsafe 只出现在内部实现，并由安全函数保证前置条件**。`Vec`/`String`/`HashMap` 的标准库实现就是如此——你从未直接写过 unsafe，却能安全使用它们。

```rust
/// 安全 API：返回 [low, high) 范围内等距采样 n 个 f64
/// 内部用 unsafe 把缓冲区直接暴露为切片（避免逐个 push 的开销）
pub fn linspace(low: f64, high: f64, n: usize) -> Vec<f64> {
    assert!(n >= 2, "n 至少为 2");
    let mut out: Vec<f64> = Vec::with_capacity(n);
    unsafe {
        // 未初始化区按 T: Copy 安全地写（Vec::spare_capacity_mut 是安全 API，可替代此写法）
        out.set_len(n);          // 手动确认：下面会完整填满 n 个元素，无越界/悬垂
    }
    for i in 0..n {
        let t = i as f64 / (n - 1) as f64;
        out[i] = low + (high - low) * t;
    }
    out
}

fn main() {
    for v in linspace(0.0, 1.0, 5) {
        println!("{v:.2}");
    }
}
```

`set_len` 前必须确保内存已初始化——上面的循环填满了每个槽位，因此是安全的。**封装的关键是"前置条件在安全层成立，unsafe 层不再检查"**。

## 11.4 FFI：调用 C 代码

Rust 调用 C 库分三步：**声明外部函数 → 定义兼容的数据布局 → 管理内存所有权**。

### 1）extern 块声明外部函数

```rust
use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int};

// 链接 libc（默认链接），声明我们要调用的 C 函数
extern "C" {
    fn strlen(s: *const c_char) -> usize;
    fn atoi(s: *const c_char) -> c_int;
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let input = CString::new("42 bytes?")?;      // CString::new 返回 Result
    let len = unsafe { strlen(input.as_ptr()) };
    println!("len = {len}");

    let number = unsafe { atoi(input.as_ptr()) };
    println!("number = {number}");
    Ok(())
}
```

要点：

- `extern "C"` 表示使用 C 的 ABI（调用约定）；声明 `*const c_char` 等 C 类型；
- **必须用 `CString`** 传字符串：Rust `String` 可能含内部 `\0` 且不保证 NUL 结尾；
- 返回 `*const c_char` 时，用 `CStr::from_ptr` 转回（生命周期需自行判断——C 返回的指针常指向静态区或库内部缓冲）；
- 调用的错误（如 errno）不会自动传播，FFI 需要按 C 惯例自己检查（返回码/`errno`/输出参数）。

### 2）repr(C)：可预测的内存布局

Rust 结构体字段顺序编译器可能重排，C 结构体是确定布局。跨 FFI 的结构体必须显式 `#[repr(C)]`：

```rust
#[repr(C)]
struct Color {
    r: u8,
    g: u8,
    b: u8,
    a: u8,
}
```

`#[repr(C, packed)]`/`#[repr(align(N))]` 控制对齐与紧凑打包，按 C 头文件逐一对应。

### 3）内存所有权：谁分配谁释放

跨边界最常见的 bug 是释放了不该释放的内存（double free）或忘记释放（泄漏）。规则：

- Rust 传入的 `Vec`/`String` 数据要给 C 用——**借用**（传 `as_ptr()`），绝不能让 C 释放；
- C 返回的堆指针——通过库提供的释放函数释放（如 `free()` / 库专用 `xxx_free()`），**不要用 Rust 的 drop**；
- 需要 C 拥有 Rust 数据时，用 `Box::into_raw` 把所有权交给 C，将来由 C 调回回调释放，或用 `Box::from_raw` 收回。

## 11.5 把 Rust 导出给 C

在 Rust 侧写库，给 C/C++（或 Python/其他语言）调用：

```rust
// src/lib.rs —— 导出给 C 的函数
use std::ffi::CStr;
use std::os::raw::{c_char, c_int};

/// 计算一个 C 字符串的字符数（不是字节数）
#[no_mangle]                              // 保持符号名不混淆
pub extern "C" fn rs_char_count(s: *const c_char) -> c_int {
    if s.is_null() {
        return -1;                        // 错误：非法输入返回错误码
    }
    let slice = unsafe { CStr::from_ptr(s) };
    match slice.to_str() {
        Ok(text) => text.chars().count() as c_int,
        Err(_) => -1,
    }
}
```

配套 `cbindgen` 自动生成 C 头文件：

```bash
cargo install cbindgen
cbindgen --lang c --output include/rust_lib.h
```

C 侧调用：

```c
#include "rust_lib.h"
int main(void) {
    int n = rs_char_count("héllo");
    return n == 5 ? 0 : 1;
}
```

**FFI 边界的 Panic 处理**：`extern "C"` 函数若 panic 会越过边界触发 UB（C 侧无 unwinding 语义）。在导出函数入口处拦截：

```rust
pub extern "C" fn safe_api(...) -> c_int {
    std::panic::catch_unwind(|| /* 真正逻辑 */).unwrap_or(-1)
}
```

## 11.6 真实绑定：bindgen / wasm

手写 FFI 声明只适合小库。对大型 C/C++ 头文件，用 **bindgen** 自动生成绑定：

```bash
# build.rs 或手动
bindgen include/mylib.h -o src/bindings.rs --use-core
```

`build.rs` 中控制链接与头文件路径；生成的 `bindings.rs` 往往还需要**安全封装一层**（11.3 模式），把它包成惯用的 Rust API。

- Rust → WebAssembly：`rustup target add wasm32-unknown-unknown`，`cargo build --target wasm32-unknown-unknown --release`；FFI 到 JS 用 `wasm-bindgen`；
- 其他语言：Kotlin/Swift 也常通过 C ABI（`#[repr(C)]` + `#[no_mangle]`）互相调用。

## 11.7 实战：安全封装一个 C 风格缓冲池

**需求**：用 unsafe 实现一个固定容量、无堆分配的**环形缓冲池**（ring buffer），对外提供完全安全的 API，内部只用一次 unsafe 初始化固定数组。

```rust
/// 固定容量环形缓冲：内部用裸指针管理一个固定数组
pub struct RingBuffer<T, const N: usize> {
    buffer: [std::mem::MaybeUninit<T>; N],   // 未初始化槽位用 MaybeUninit 表达
    head: usize,
    len: usize,
}

impl<T: Copy, const N: usize> RingBuffer<T, N> {
    pub fn new() -> Self {
        RingBuffer {
            buffer: unsafe { std::mem::MaybeUninit::uninit().assume_init() }, // 见下方说明
            head: 0,
            len: 0,
        }
    }

    pub fn is_full(&self) -> bool { self.len == N }
    pub fn is_empty(&self) -> bool { self.len == 0 }

    pub fn push(&mut self, value: T) -> Result<(), T> {
        if self.is_full() {
            return Err(value);
        }
        let index = (self.head + self.len) % N;
        // 关键不变量：index 处的槽位当前"未初始化或已死"（被 pop 消费过），
        // 因此这里写入是安全的。Rust 认为 MaybeUninit 数组元素总是有效内存。
        unsafe {
            self.buffer[index].as_mut_ptr().write(value);
        }
        self.len += 1;
        Ok(())
    }

    pub fn pop(&mut self) -> Option<T> {
        if self.is_empty() {
            return None;
        }
        // 读出后，该槽位将处于"已消费"状态，等待下一次 push 覆盖
        let value = unsafe { self.buffer[self.head].as_ptr().read() };
        self.head = (self.head + 1) % N;
        self.len -= 1;
        Some(value)
    }
}

impl<T, const N: usize> Drop for RingBuffer<T, N> {
    fn drop(&mut self) {
        // 只 drop 仍在缓冲中的元素（T 若含堆数据需清理）
        while self.len > 0 {
            unsafe {
                self.buffer[self.head].as_ptr().drop_in_place();
            }
            self.head = (self.head + 1) % N;
            self.len -= 1;
        }
    }
}

fn main() {
    let mut rb = RingBuffer::<i32, 4>::new();
    assert!(rb.push(1).is_ok());
    assert!(rb.push(2).is_ok());
    assert!(rb.pop() == Some(1));
    assert!(rb.pop() == Some(2));
    assert!(rb.pop().is_none());
    println!("环形缓冲工作正常");
}
```

> 说明：`MaybeUninit<T>` 表示"这块内存可能未初始化"，`.assume_init()` 需要小心；上面 `new()` 中 `uninit().assume_init()` 只对**不读取**的场合成立（Push 先写后读）。更严谨做法是零长或让数组整体先写入初始化哨兵——工程中推荐用 `Vec::with_capacity` 或第三方 `arrayvec` crate 替代手写，本例意在理解原理而非推广 `assume_init` 写法。

**验收清单**：

1. 理解上述每一处 `unsafe` 依赖的不变量是什么（写一条注释说明你的理解）；
2. 用 `cargo miri run`（若装有 nightly）跑一遍，确认无 UB；
3. 把 `RingBuffer<T: Copy>` 泛型去掉 Copy 限制——为 `T` 换成 `String` 时哪些地方会编译失败？思考为什么需要 `Drop`；
4. 给 `RingBuffer` 加 `peek()`（不移除地看队首），仍保持安全 API。

**扩展练习（FFI 方向，Linux/macOS 验证）**：声明 libc 的 `getenv`/`putenv`，用 `CString` 读环境变量打印；随后用 `cbindgen` 把自己的 `rs_char_count` 导出并写一个 `main.c` 编译链接运行（`cc main.c -lrust_lib`）。

> 关键收获：unsafe/FFI 的工程意义是"**在必须接触底层时仍保持边界清晰**"。API 安全、实现局部 unsafe、前置条件文档化、用 Miri 定期检查——做到这四点，unsafe 就从"危险区"变成"受控区"。下一章回到纯安全的工程世界：用 Polars 做高性能数据分析。

上一章：[10-过程宏与元编程.md](./10-过程宏与元编程.md) ｜ 下一章：[12-Polars数据分析.md](./12-Polars数据分析.md) ｜ [返回 README](./README.md)
