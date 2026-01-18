package com.tanggo.fund.jnautilustrader.stragety.c2;

import com.tanggo.fund.jnautilustrader.stragety.cross.CrossArbitrageParams;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CrossActor 测试类 - 直接测试 CrossActor 的功能
 *
 * 该测试类测试 CrossActor 的基本功能，包括：
 * 1. CrossActor 的创建
 * 2. 策略启动和停止
 * 3. 策略状态管理
 * 4. 参数设置
 *
 * @author JNautilusTrader
 * @version 1.0
 */
public class CrossActorTest {

    private static final Logger logger = LoggerFactory.getLogger(CrossActorTest.class);

    @Test
    public void testCrossActorCreation() {
        logger.info("测试 CrossActor 的创建...");

        // 测试默认参数创建
        CrossActor actor1 = new CrossActor();
        assertNotNull(actor1, "CrossActor 应该能够使用默认参数创建");

        // 测试自定义参数创建
        CrossArbitrageParams params = CrossArbitrageParams.defaultParams();
        params.setSymbol("BTCUSDT");
        params.setOrderQuantity(0.001);
        params.setArbitrageThreshold(0.1);

        CrossActor actor2 = new CrossActor(params);
        assertNotNull(actor2, "CrossActor 应该能够使用自定义参数创建");

        logger.info("CrossActor 创建测试通过");
    }

    @Test
    public void testStrategyStateManagement() {
        logger.info("测试策略状态管理...");

        CrossActor actor = new CrossActor();

        // 检查初始状态
        assertFalse(actor.getState().isRunning(), "策略初始状态应为未运行");
        assertEquals("初始化完成", actor.getState().getStatusInfo());

        // 测试启动策略
        actor.start();
        assertTrue(actor.getState().isRunning(), "策略启动后应处于运行状态");
        assertEquals("策略运行中", actor.getState().getStatusInfo());
        assertNotNull(actor.getState().getParams(), "策略参数不应为null");

        logger.info("策略状态管理测试通过");
    }

    @Test
    public void testActorStatus() {
        logger.info("测试 Actor 状态...");

        CrossActor actor = new CrossActor();

        // 检查初始状态
        assertEquals("IDLE", actor.getActorStatus().toString(), "Actor 初始状态应为 IDLE");

        // 测试启动
        actor.start();
        assertEquals("RUNNING", actor.getActorStatus().toString(), "Actor 启动后应为 RUNNING 状态");

        logger.info("Actor 状态测试通过");
    }

    @Test
    public void testParameterConfiguration() {
        logger.info("测试参数配置...");

        CrossArbitrageParams params = CrossArbitrageParams.defaultParams();
        params.setSymbol("ETHUSDT");
        params.setOrderQuantity(0.01);
        params.setArbitrageThreshold(0.2);
        params.setDebugMode(false);

        CrossActor actor = new CrossActor(params);

        // 检查参数是否正确设置
        assertEquals("ETHUSDT", actor.getState().getParams().getSymbol(), "符号参数应正确设置");
        assertEquals(0.01, actor.getState().getParams().getOrderQuantity(), 0.0001);
        assertEquals(0.2, actor.getState().getParams().getArbitrageThreshold(), 0.0001);
        assertFalse(actor.getState().getParams().isDebugMode());

        logger.info("参数配置测试通过");
    }

    @Test
    public void testStrategyStop() {
        logger.info("测试策略停止...");

        CrossActor actor = new CrossActor();

        // 启动策略
        actor.start();
        assertTrue(actor.getState().isRunning());

        // 停止策略
        actor.stop();
        assertFalse(actor.getState().isRunning());
        assertEquals("策略已停止", actor.getState().getStatusInfo());

        logger.info("策略停止测试通过");
    }

    @Test
    public void testStrategyInitialization() {
        logger.info("测试策略初始化...");

        CrossActor actor = new CrossActor();

        // 检查所有状态字段是否已初始化
        assertNotNull(actor.getState());
        assertEquals(0, actor.getState().getArbitrageCount());
        assertEquals(0, actor.getState().getSuccessfulArbitrageCount());
        assertEquals(0, actor.getState().getFailedArbitrageCount());
        assertEquals(0.0, actor.getState().getTotalProfit(), 0.0001);
        assertEquals(0.0, actor.getState().getCurrentPosition(), 0.0001);
        assertEquals(0.0, actor.getState().getTotalVolume(), 0.0001);

        logger.info("策略初始化测试通过");
    }
}