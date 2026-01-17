package com.tanggo.fund.jnautilustrader.core.util;

import org.slf4j.Logger;

/**
 * 线程安全的日志工具类 - 自动添加线程信息
 * <p>
 * 所有日志输出都会自动包含当前线程的名称和ID，方便追踪多线程环境下的日志
 * <p>
 * 使用示例：
 * <pre>
 * private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
 *
 * // 基础用法
 * ThreadLogger.info(logger, "Processing order");
 *
 * // 带参数
 * ThreadLogger.info(logger, "Processing order: {}", orderId);
 *
 * // 带异常
 * ThreadLogger.error(logger, "Failed to process order", exception);
 * </pre>
 *
 * @author JNautilusTrader
 * @version 1.0
 */
public final class ThreadLogger {

    /**
     * 私有构造函数，防止实例化
     */
    private ThreadLogger() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 获取格式化的线程信息
     * 格式: [线程名-线程ID]
     *
     * @return 格式化的线程信息字符串
     */
    private static String getThreadInfo() {
        Thread currentThread = Thread.currentThread();
        return String.format("[%s-%d]", currentThread.getName(), currentThread.getId());
    }

    /**
     * 记录 TRACE 级别日志
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息
     */
    public static void trace(Logger logger, String message) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} {}", getThreadInfo(), message);
        }
    }

    /**
     * 记录 TRACE 级别日志（带单个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param arg     参数
     */
    public static void trace(Logger logger, String message, Object arg) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} " + message, getThreadInfo(), arg);
        }
    }

    /**
     * 记录 TRACE 级别日志（带两个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param arg1    第一个参数
     * @param arg2    第二个参数
     */
    public static void trace(Logger logger, String message, Object arg1, Object arg2) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} " + message, getThreadInfo(), arg1, arg2);
        }
    }

    /**
     * 记录 TRACE 级别日志（带多个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param args    参数数组
     */
    public static void trace(Logger logger, String message, Object... args) {
        if (logger.isTraceEnabled()) {
            Object[] newArgs = new Object[args.length + 1];
            newArgs[0] = getThreadInfo();
            System.arraycopy(args, 0, newArgs, 1, args.length);
            logger.trace("{} " + message, newArgs);
        }
    }

    /**
     * 记录 DEBUG 级别日志
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息
     */
    public static void debug(Logger logger, String message) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} {}", getThreadInfo(), message);
        }
    }

    /**
     * 记录 DEBUG 级别日志（带单个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param arg     参数
     */
    public static void debug(Logger logger, String message, Object arg) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} " + message, getThreadInfo(), arg);
        }
    }

    /**
     * 记录 DEBUG 级别日志（带两个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param arg1    第一个参数
     * @param arg2    第二个参数
     */
    public static void debug(Logger logger, String message, Object arg1, Object arg2) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} " + message, getThreadInfo(), arg1, arg2);
        }
    }

    /**
     * 记录 DEBUG 级别日志（带多个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param args    参数数组
     */
    public static void debug(Logger logger, String message, Object... args) {
        if (logger.isDebugEnabled()) {
            Object[] newArgs = new Object[args.length + 1];
            newArgs[0] = getThreadInfo();
            System.arraycopy(args, 0, newArgs, 1, args.length);
            logger.debug("{} " + message, newArgs);
        }
    }

    /**
     * 记录 INFO 级别日志
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息
     */
    public static void info(Logger logger, String message) {
        logger.info("{} {}", getThreadInfo(), message);
    }

    /**
     * 记录 INFO 级别日志（带单个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param arg     参数
     */
    public static void info(Logger logger, String message, Object arg) {
        logger.info("{} " + message, getThreadInfo(), arg);
    }

    /**
     * 记录 INFO 级别日志（带两个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param arg1    第一个参数
     * @param arg2    第二个参数
     */
    public static void info(Logger logger, String message, Object arg1, Object arg2) {
        logger.info("{} " + message, getThreadInfo(), arg1, arg2);
    }

    /**
     * 记录 INFO 级别日志（带多个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param args    参数数组
     */
    public static void info(Logger logger, String message, Object... args) {
        Object[] newArgs = new Object[args.length + 1];
        newArgs[0] = getThreadInfo();
        System.arraycopy(args, 0, newArgs, 1, args.length);
        logger.info("{} " + message, newArgs);
    }

    /**
     * 记录 WARN 级别日志
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息
     */
    public static void warn(Logger logger, String message) {
        logger.warn("{} {}", getThreadInfo(), message);
    }

    /**
     * 记录 WARN 级别日志（带单个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param arg     参数
     */
    public static void warn(Logger logger, String message, Object arg) {
        logger.warn("{} " + message, getThreadInfo(), arg);
    }

    /**
     * 记录 WARN 级别日志（带两个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param arg1    第一个参数
     * @param arg2    第二个参数
     */
    public static void warn(Logger logger, String message, Object arg1, Object arg2) {
        logger.warn("{} " + message, getThreadInfo(), arg1, arg2);
    }

    /**
     * 记录 WARN 级别日志（带多个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param args    参数数组
     */
    public static void warn(Logger logger, String message, Object... args) {
        Object[] newArgs = new Object[args.length + 1];
        newArgs[0] = getThreadInfo();
        System.arraycopy(args, 0, newArgs, 1, args.length);
        logger.warn("{} " + message, newArgs);
    }

    /**
     * 记录 WARN 级别日志（带异常）
     *
     * @param logger    SLF4J Logger实例
     * @param message   日志消息
     * @param throwable 异常对象
     */
    public static void warn(Logger logger, String message, Throwable throwable) {
        logger.warn("{} {}", getThreadInfo(), message, throwable);
    }

    /**
     * 记录 ERROR 级别日志
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息
     */
    public static void error(Logger logger, String message) {
        logger.error("{} {}", getThreadInfo(), message);
    }

    /**
     * 记录 ERROR 级别日志（带单个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param arg     参数
     */
    public static void error(Logger logger, String message, Object arg) {
        logger.error("{} " + message, getThreadInfo(), arg);
    }

    /**
     * 记录 ERROR 级别日志（带两个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param arg1    第一个参数
     * @param arg2    第二个参数
     */
    public static void error(Logger logger, String message, Object arg1, Object arg2) {
        logger.error("{} " + message, getThreadInfo(), arg1, arg2);
    }

    /**
     * 记录 ERROR 级别日志（带多个参数）
     *
     * @param logger  SLF4J Logger实例
     * @param message 日志消息模板
     * @param args    参数数组
     */
    public static void error(Logger logger, String message, Object... args) {
        Object[] newArgs = new Object[args.length + 1];
        newArgs[0] = getThreadInfo();
        System.arraycopy(args, 0, newArgs, 1, args.length);
        logger.error("{} " + message, newArgs);
    }

    /**
     * 记录 ERROR 级别日志（带异常）
     *
     * @param logger    SLF4J Logger实例
     * @param message   日志消息
     * @param throwable 异常对象
     */
    public static void error(Logger logger, String message, Throwable throwable) {
        logger.error("{} {}", getThreadInfo(), message, throwable);
    }

    /**
     * 记录 ERROR 级别日志（带参数和异常）
     *
     * @param logger    SLF4J Logger实例
     * @param message   日志消息模板
     * @param arg       参数
     * @param throwable 异常对象
     */
    public static void error(Logger logger, String message, Object arg, Throwable throwable) {
        logger.error("{} " + message, getThreadInfo(), arg, throwable);
    }
}