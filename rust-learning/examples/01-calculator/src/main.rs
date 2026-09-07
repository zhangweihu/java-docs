//! 第一章实战：命令行计算器
//!
//! 运行：
//!   cargo run -- 3 + 4     -> 3 + 4 = 7
//!   cargo run -- 5 / 0     -> 除零错误（不 panic）
//!   cargo run -- 1 ^ 2     -> 未知运算符提示
use std::env;
use std::process::exit;

/// 计算失败的类型化错误：不 panic，由调用方决定如何提示
#[derive(Debug, PartialEq)]
enum CalcError {
    InvalidNumber(String),
    DivideByZero,
    UnknownOperator(String),
}

/// 纯计算逻辑（与 I/O 分离，便于单测）
fn calculate(a: f64, op: &str, b: f64) -> Result<f64, CalcError> {
    match op {
        "+" => Ok(a + b),
        "-" => Ok(a - b),
        "*" => Ok(a * b),
        "/" => {
            if b == 0.0 {
                Err(CalcError::DivideByZero)
            } else {
                Ok(a / b)
            }
        }
        _ => Err(CalcError::UnknownOperator(op.to_string())),
    }
}

/// 解析命令行：calc <a> <运算符> <b>
fn parse_args() -> Result<(f64, String, f64), String> {
    let args: Vec<String> = env::args().skip(1).collect();
    if args.len() != 3 {
        return Err("用法: calculator <a> <运算符> <b>，例如: calculator 3 + 4".to_string());
    }
    let a: f64 = args[0]
        .parse()
        .map_err(|_| format!("第一个参数不是有效数字: {}", args[0]))?;
    let op = args[1].clone();
    let b: f64 = args[2]
        .parse()
        .map_err(|_| format!("第三个参数不是有效数字: {}", args[2]))?;
    Ok((a, op, b))
}

fn main() {
    let (a, op, b) = match parse_args() {
        Ok(v) => v,
        Err(msg) => {
            eprintln!("{msg}");
            exit(1);
        }
    };

    match calculate(a, &op, b) {
        Ok(value) => println!("{a} {op} {b} = {value}"),
        Err(err) => {
            eprintln!("计算失败: {err:?}");
            exit(1);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 四则运算() {
        assert_eq!(calculate(3.0, "+", 4.0), Ok(7.0));
        assert_eq!(calculate(5.0, "-", 2.0), Ok(3.0));
        assert_eq!(calculate(2.0, "*", 3.0), Ok(6.0));
        assert_eq!(calculate(10.0, "/", 4.0), Ok(2.5));
    }

    #[test]
    fn 除零返回错误而非panic() {
        assert_eq!(calculate(1.0, "/", 0.0), Err(CalcError::DivideByZero));
    }

    #[test]
    fn 未知运算符() {
        assert_eq!(
            calculate(1.0, "^", 2.0),
            Err(CalcError::UnknownOperator("^".to_string()))
        );
    }
}
