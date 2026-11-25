package com.kl.agent.engine;

/**
 * 计划参数中的引用无法解析时抛出。
 */
public class ReferenceResolutionException extends RuntimeException {

    public ReferenceResolutionException(String message) {
        super(message);
    }
}
