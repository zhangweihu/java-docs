package com.example.review.core.exception;

/**
 * 评审过程中所有异常的基类。
 */
public class ReviewException extends RuntimeException {
    public ReviewException(String message) { super(message); }
    public ReviewException(String message, Throwable cause) { super(message, cause); }
}