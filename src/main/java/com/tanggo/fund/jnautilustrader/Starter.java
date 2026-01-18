package com.tanggo.fund.jnautilustrader;

import com.tanggo.fund.jnautilustrader.core.entity.ApplicationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * 交易系统启动器
 * 通过Spring XML配置文件加载应用上下文并启动Process
 */
public class Starter {

    private static final Logger logger = LoggerFactory.getLogger(Starter.class);
    private static final String DEFAULT_CONFIG_LOCATION = "applicationContext.xml";

    public static void main(String[] args) {
        ApplicationContext context = null;
        ApplicationConfig applicationConfig = null;
        try {
            // 确定配置文件路径
            String configLocation = args.length > 0 ? args[0] : DEFAULT_CONFIG_LOCATION;
            logger.info("加载Spring配置文件: {}", configLocation);

            // 通过Spring XML获取ApplicationContext
            context = new ClassPathXmlApplicationContext(configLocation);
            logger.info("Spring容器初始化成功");

            // 从Spring容器中获取策略应用实例，可以获取任何实例
            applicationConfig = context.getBean("crossAppServiceConfig", ApplicationConfig.class);

            logger.info("获取applicationConfig实例成功: {}", applicationConfig.getClass().getName());

            // 注册JVM关闭钩子，确保优雅关闭
            registerShutdownHook(applicationConfig, context);

            // 启动交易系统
            logger.info("======================================");
            logger.info("开始启动交易系统...");
            logger.info("======================================");
            applicationConfig.start();

            // 保持主线程运行
            keepAlive();

        } catch (Exception e) {
            logger.error("交易系统启动失败", e);

            // 尝试关闭Process
            if (applicationConfig != null) {
                try {
                    applicationConfig.stop();
                } catch (Exception ex) {
                    logger.error("停止Process失败", ex);
                }
            }

            // 关闭Spring容器
            if (context instanceof ClassPathXmlApplicationContext) {
                ((ClassPathXmlApplicationContext) context).close();
            }

            System.exit(1);
        }
    }

    /**
     * 注册JVM关闭钩子，确保优雅关闭
     */
    private static void registerShutdownHook(final ApplicationConfig process, final ApplicationContext context) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("======================================");
            logger.info("收到关闭信号，开始优雅关闭...");
            logger.info("======================================");

            try {
                // 停止Process
                if (process != null) {
                    logger.info("停止Process...");
                    process.stop();
                    logger.info("Process已停止");
                }

                // 关闭Spring容器
                if (context instanceof ClassPathXmlApplicationContext) {
                    logger.info("关闭Spring容器...");
                    ((ClassPathXmlApplicationContext) context).close();
                    logger.info("Spring容器已关闭");
                }

                logger.info("======================================");
                logger.info("系统已优雅关闭");
                logger.info("======================================");

            } catch (Exception e) {
                logger.error("关闭过程中发生错误", e);
            }
        }, "shutdown-hook"));

        logger.info("JVM关闭钩子已注册");
    }

    /**
     * 保持主线程运行
     */
    private static void keepAlive() {
        logger.info("======================================");
        logger.info("交易系统运行中...");
        logger.info("按 Ctrl+C 停止系统");
        logger.info("======================================");

        try {
            // 保持主线程运行，直到收到中断信号
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.warn("主线程被中断", e);
            Thread.currentThread().interrupt();
        }
    }
}
