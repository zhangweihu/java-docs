//! 第五章实战：
//!   1) Box<Expr> 递归类型表达式树求值
//!   2) Rc<RefCell<Cache>> 单线程共享可变的缓存（内部可变性）
use std::cell::RefCell;
use std::collections::HashMap;
use std::rc::Rc;

// ---------- 实战 A：表达式树（Box 间接层让递归类型大小确定） ----------

#[derive(Debug)]
enum Expr {
    Num(f64),
    Add(Box<Expr>, Box<Expr>),
    Sub(Box<Expr>, Box<Expr>),
    Mul(Box<Expr>, Box<Expr>),
    Div(Box<Expr>, Box<Expr>),
}

impl Expr {
    fn num(n: f64) -> Self {
        Expr::Num(n)
    }
    fn add(a: Expr, b: Expr) -> Self {
        Expr::Add(Box::new(a), Box::new(b))
    }
    fn mul(a: Expr, b: Expr) -> Self {
        Expr::Mul(Box::new(a), Box::new(b))
    }

    /// 求值：遇到除零返回 None（Result 风格，不 panic）
    fn eval(&self) -> Option<f64> {
        match self {
            Expr::Num(n) => Some(*n),
            Expr::Add(a, b) => Some(a.eval()? + b.eval()?),
            Expr::Sub(a, b) => Some(a.eval()? - b.eval()?),
            Expr::Mul(a, b) => Some(a.eval()? * b.eval()?),
            Expr::Div(a, b) => {
                let divisor = b.eval()?;
                if divisor == 0.0 {
                    None
                } else {
                    Some(a.eval()? / divisor)
                }
            }
        }
    }
}

// ---------- 实战 B：共享可变的缓存（Rc<RefCell<HashMap>>） ----------

/// 单线程缓存中心：多个持有者（Rc 副本）都可读写内部状态（RefCell）
struct Cache {
    store: RefCell<HashMap<String, String>>,
}

impl Cache {
    fn new() -> Rc<Cache> {
        Rc::new(Cache {
            store: RefCell::new(HashMap::new()),
        })
    }

    fn put(&self, key: &str, value: &str) {
        self.store
            .borrow_mut()
            .insert(key.to_string(), value.to_string());
    }

    fn get(&self, key: &str) -> Option<String> {
        self.store.borrow().get(key).cloned()
    }
}

fn main() {
    // A：表达式 (3 + 4) * 2 = 14
    let expr = Expr::mul(Expr::add(Expr::num(3.0), Expr::num(4.0)), Expr::num(2.0));
    println!("(3+4)*2 = {:?}", expr.eval());

    // 除零 → None
    let invalid = Expr::Div(Box::new(Expr::num(1.0)), Box::new(Expr::num(0.0)));
    println!("1/0 = {:?}（None 表示除零错误）", invalid.eval());

    // B：多持有者共享缓存
    let cache = Cache::new();
    let worker_a = Rc::clone(&cache);
    let worker_b = Rc::clone(&cache);

    worker_a.put("theme", "dark");
    worker_b.put("lang", "rust");
    println!("theme = {:?}", worker_a.get("theme"));
    println!("lang  = {:?}", worker_b.get("lang"));
    println!("强引用数: {}", Rc::strong_count(&cache));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 表达式树求值() {
        let e = Expr::mul(Expr::add(Expr::num(3.0), Expr::num(4.0)), Expr::num(2.0));
        assert_eq!(e.eval(), Some(14.0));
    }

    #[test]
    fn 除零返回None() {
        let e = Expr::Div(Box::new(Expr::num(1.0)), Box::new(Expr::num(0.0)));
        assert_eq!(e.eval(), None);
    }

    #[test]
    fn 缓存共享读写() {
        let cache = Cache::new();
        let a = Rc::clone(&cache);
        let b = Rc::clone(&cache);
        a.put("k", "v");
        assert_eq!(b.get("k").as_deref(), Some("v"));
    }
}
