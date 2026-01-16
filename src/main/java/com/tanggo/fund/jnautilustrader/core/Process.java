package com.tanggo.fund.jnautilustrader.core;

import com.tanggo.fund.jnautilustrader.StrategyConfig;
import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Data
public class Process {

    private static final Logger logger = LoggerFactory.getLogger(Process.class);
    private final List<Future<?>> runningFutures = new ArrayList<>();
    private ExecutorService marketDataExecutorService;
    private ExecutorService tradingExecutorService;
    private ExecutorService strategyExecutorService;
    private StrategyConfig strategyConfig;
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
            startStrategy();

            // 启动交易客户端
            startTradeClients();

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
        List<Actor> mdClients = strategyConfig.getMdClients();
        if (mdClients == null || mdClients.isEmpty()) {
            logger.warn("未配置市场数据客户端");
            return;
        }

        logger.info("启动市场数据客户端，共 {} 个", mdClients.size());

        for (Actor actor : mdClients) {
            try {
                Future<?> future = marketDataExecutorService.submit(() -> {
                    try {
                        logger.debug("启动市场数据客户端: {}", actor.getClass().getSimpleName());
                        actor.start();
                        logger.debug("市场数据客户端启动成功: {}", actor.getClass().getSimpleName());
                    } catch (Exception e) {
                        logger.error("市场数据客户端运行失败: {}", actor.getClass().getSimpleName(), e);
                        // 可以选择是否停止整个系统
                        handleActorFailure(actor, e);
                    }
                });

                runningFutures.add(future);
                logger.info("市场数据客户端启动成功: {}", actor.getClass().getSimpleName());

            } catch (Exception e) {
                logger.error("启动市场数据客户端失败: {}", actor.getClass().getSimpleName(), e);
                throw e;
            }
        }
    }

    /**
     * 启动策略
     */
    private void startStrategy() {
        Actor strategy = strategyConfig.getStrategy();
        if (strategy == null) {
            logger.warn("未配置策略");
            return;
        }

        logger.info("启动策略: {}", strategy.getClass().getSimpleName());

        try {
            Future<?> future = strategyExecutorService.submit(() -> {
                try {
                    logger.debug("启动策略: {}", strategy.getClass().getSimpleName());
                    strategy.start();
                    logger.debug("策略启动成功: {}", strategy.getClass().getSimpleName());
                } catch (Exception e) {
                    logger.error("策略运行失败: {}", strategy.getClass().getSimpleName(), e);
                    handleActorFailure(strategy, e);
                }
            });

            runningFutures.add(future);
            logger.info("策略启动成功: {}", strategy.getClass().getSimpleName());

        } catch (Exception e) {
            logger.error("启动策略失败: {}", strategy.getClass().getSimpleName(), e);
            throw e;
        }
    }

    /**
     * 启动交易客户端
     */
    private void startTradeClients() {
        List<Actor> tradeClients = strategyConfig.getTradeClients();
        if (tradeClients == null || tradeClients.isEmpty()) {
            logger.warn("未配置交易客户端");
            return;
        }

        logger.info("启动交易客户端，共 {} 个", tradeClients.size());

        for (Actor actor : tradeClients) {
            try {
                Future<?> future = tradingExecutorService.submit(() -> {
                    try {
                        logger.debug("启动交易客户端: {}", actor.getClass().getSimpleName());
                        actor.start();
                        logger.debug("交易客户端启动成功: {}", actor.getClass().getSimpleName());
                    } catch (Exception e) {
                        logger.error("交易客户端运行失败: {}", actor.getClass().getSimpleName(), e);
                        handleActorFailure(actor, e);
                    }
                });

                runningFutures.add(future);
                logger.info("交易客户端启动成功: {}", actor.getClass().getSimpleName());

            } catch (Exception e) {
                logger.error("启动交易客户端失败: {}", actor.getClass().getSimpleName(), e);
                throw e;
            }
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

            // 取消所有运行中的任务
            cancelAllFutures();

            isRunning = false;
            logger.info("所有组件停止成功");

        } catch (Exception e) {
            logger.error("系统停止失败", e);
        }
    }

    private void stopTradeClients() {
        List<Actor> tradeClients = strategyConfig.getTradeClients();
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
        Actor strategy = strategyConfig.getStrategy();
        if (strategy == null) {
            return;
        }

        try {
            logger.debug("停止策略: {}", strategy.getClass().getSimpleName());
            strategy.stop();
            logger.debug("策略停止成功: {}", strategy.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("停止策略失败: {}", strategy.getClass().getSimpleName(), e);
        }
    }

    private void stopMarketDataClients() {
        List<Actor> mdClients = strategyConfig.getMdClients();
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
     * 取消所有运行中的任务
     */
    private void cancelAllFutures() {
        for (Future<?> future : runningFutures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        runningFutures.clear();
    }

    /**
     * 处理Actor失败
     */
    private void handleActorFailure(Actor actor, Exception e) {
        logger.error("Actor失败，尝试停止整个系统: {}", actor.getClass().getSimpleName(), e);
        stop();
    }

    /**
     * 检查系统状态
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 获取运行中的任务数量
     */
    public int getRunningTaskCount() {
        return (int) runningFutures.stream().filter(future -> !future.isDone()).count();
    }

    // Setter methods for Spring dependency injection

    public void setMarketDataExecutorService(ExecutorService marketDataExecutorService) {
        this.marketDataExecutorService = marketDataExecutorService;
    }

    public void setTradingExecutorService(ExecutorService tradingExecutorService) {
        this.tradingExecutorService = tradingExecutorService;
    }

    public void setStrategyExecutorService(ExecutorService strategyExecutorService) {
        this.strategyExecutorService = strategyExecutorService;
    }

    public void setStrategyConfig(StrategyConfig strategyConfig) {
        this.strategyConfig = strategyConfig;
    }
}
