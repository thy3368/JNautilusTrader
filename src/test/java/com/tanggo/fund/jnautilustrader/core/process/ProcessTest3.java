package com.tanggo.fund.jnautilustrader.core.process;

import com.tanggo.fund.jnautilustrader.adapter.event_repo.BlockingQueueEventRepo;
import com.tanggo.fund.jnautilustrader.adapter.mdgw.bn.BNMDGWWebSocketClient;
import com.tanggo.fund.jnautilustrader.adapter.tradegw.bn.BNTradeGWWebSocketClient;
import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import com.tanggo.fund.jnautilustrader.core.entity.MarketData;
import com.tanggo.fund.jnautilustrader.core.entity.TradeCmd;
import com.tanggo.fund.jnautilustrader.core.process.stoikov.AvellanedaStoikovStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process 事件处理器测试
 */
@ExtendWith(MockitoExtension.class)
class ProcessTest3 {

    private static final Logger logger = LoggerFactory.getLogger(ProcessTest2.class);

    private Actor mdgwActor;

    private Actor tradegwActor;

    private AvellanedaStoikovStrategy stoikovStrategy;

    private BlockingQueueEventRepo<MarketData> marketDataBlockingQueueEventRepo;

    private BlockingQueueEventRepo<TradeCmd> tradeCmdBlockingQueueEventRepo;


    @BeforeEach
    void setUp() {
        logger.info("=== 初始化测试环境 ===");


        // 创建市场数据事件仓库
        marketDataBlockingQueueEventRepo = new BlockingQueueEventRepo<>();
        tradeCmdBlockingQueueEventRepo = new BlockingQueueEventRepo<>();

        // 创建 WebSocket 客户端
        mdgwActor = new BNMDGWWebSocketClient(marketDataBlockingQueueEventRepo);

        // 创建 WebSocket 客户端
        tradegwActor = new BNTradeGWWebSocketClient(marketDataBlockingQueueEventRepo, tradeCmdBlockingQueueEventRepo);


        mdgwActor.start();
        tradegwActor.start();


        stoikovStrategy.start();

        logger.info("测试环境初始化完成");
    }

}
