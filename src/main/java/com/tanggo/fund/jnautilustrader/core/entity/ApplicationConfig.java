package com.tanggo.fund.jnautilustrader.core.entity;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Data
public class ApplicationConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);
    //行情
    private List<Actor> mdClients;
    //交易
    private List<Actor> tradeClients;
    //应用服务
    private Actor service;
    private volatile boolean isRunning = false;

    /**
     * 启动所有组件
     * 按照：市场数据客户端 → 策略 → 交易客户端 的顺序启动，确保依赖关系正确
     */
    public synchronized void start() {
        if (isRunning) {
            logger.warn("Process已经在运行中，忽略启动请求");
            return;
        }

        logger.info("开始启动交易系统组件...");

        try {
            // 启动市场数据客户端
            startMarketDataClients();

            // 启动策略
            startAppService();

            // 启动交易客户端
//            startTradeClients();

            isRunning = true;
            logger.info("所有组件启动成功，交易系统已就绪");

        } catch (Exception e) {
            logger.error("系统启动失败，正在关闭已启动的组件", e);
            stop();
            throw new RuntimeException("系统启动失败", e);
        }
    }

    /**
     * 启动市场数据客户端
     */
    private void startMarketDataClients() {
        if (mdClients == null || mdClients.isEmpty()) {
            logger.warn("未配置市场数据客户端");
            return;
        }

        logger.info("启动市场数据客户端，共 {} 个", mdClients.size());

        for (Actor actor : mdClients) {
            actor.start_link();
        }
    }

    /**
     * 启动策略
     */
    private void startAppService() {
        if (service == null) {
            logger.warn("未配置策略");
            return;
        }

        logger.info("启动策略: {}", service.getClass().getSimpleName());
        service.start_link();
    }

    /**
     * 启动交易客户端
     */
    private void startTradeClients() {
        if (tradeClients == null || tradeClients.isEmpty()) {
            logger.warn("未配置交易客户端");
            return;
        }

        logger.info("启动交易客户端，共 {} 个", tradeClients.size());

        for (Actor actor : tradeClients) {
            logger.debug("启动交易客户端: {}", actor.getClass().getSimpleName());
            actor.start_link();
            logger.debug("交易客户端启动成功: {}", actor.getClass().getSimpleName());
        }
    }

    /**
     * 停止所有组件
     * 按照：交易客户端 → 策略 → 市场数据客户端 的逆序停止，确保资源正确释放
     */
    public synchronized void stop() {
        if (!isRunning) {
            logger.warn("Process未在运行中，忽略停止请求");
            return;
        }

        logger.info("开始停止交易系统组件...");

        try {
            // 停止交易客户端
            stopTradeClients();

            // 停止策略
            stopStrategy();

            // 停止市场数据客户端
            stopMarketDataClients();


            isRunning = false;
            logger.info("所有组件停止成功");

        } catch (Exception e) {
            logger.error("系统停止失败", e);
        }
    }

    private void stopTradeClients() {
        if (tradeClients == null || tradeClients.isEmpty()) {
            return;
        }

        for (Actor actor : tradeClients) {
            try {
                logger.debug("停止交易客户端: {}", actor.getClass().getSimpleName());
                actor.stop();
                logger.debug("交易客户端停止成功: {}", actor.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("停止交易客户端失败: {}", actor.getClass().getSimpleName(), e);
            }
        }
    }

    private void stopStrategy() {
        if (service == null) {
            return;
        }

        try {
            logger.debug("停止策略: {}", service.getClass().getSimpleName());
            service.stop();
            logger.debug("策略停止成功: {}", service.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("停止策略失败: {}", service.getClass().getSimpleName(), e);
        }
    }

    private void stopMarketDataClients() {
        if (mdClients == null || mdClients.isEmpty()) {
            return;
        }

        for (Actor actor : mdClients) {
            try {
                logger.debug("停止市场数据客户端: {}", actor.getClass().getSimpleName());
                actor.stop();
                logger.debug("市场数据客户端停止成功: {}", actor.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("停止市场数据客户端失败: {}", actor.getClass().getSimpleName(), e);
            }
        }
    }


    /**
     * 检查系统状态
     */
    public boolean isRunning() {
        return isRunning;
    }


}
