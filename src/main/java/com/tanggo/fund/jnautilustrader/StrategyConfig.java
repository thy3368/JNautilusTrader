package com.tanggo.fund.jnautilustrader;

import com.tanggo.fund.jnautilustrader.adapter.event_repo.BlockingQueueEventRepo;
import com.tanggo.fund.jnautilustrader.adapter.event_repo.HashMapEventHandlerRepo;
import com.tanggo.fund.jnautilustrader.adapter.mdgw.bitget.BTMDGWWebSocketClient;
import com.tanggo.fund.jnautilustrader.adapter.mdgw.bn.BNMDGWWebSocketClient;
import com.tanggo.fund.jnautilustrader.adapter.tradegw.bitget.BTTradeGWWebSocketClient;
import com.tanggo.fund.jnautilustrader.adapter.tradegw.bn.BNTradeGWWebSocketClient;
import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandlerRepo;
import com.tanggo.fund.jnautilustrader.core.entity.MarketData;
import com.tanggo.fund.jnautilustrader.core.entity.TradeCmd;
import com.tanggo.fund.jnautilustrader.core.process.cross.CrossArbitrageParams;
import com.tanggo.fund.jnautilustrader.core.process.cross.CrossStrategy;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Configuration
@Data
public class StrategyConfig {

    private List<Actor> mdClients;
    private List<Actor> tradeClients;
    private Actor strategy;


//    @Bean
//    public BlockingQueueEventRepo<MarketData> marketDataEventRepo() {
//        return new BlockingQueueEventRepo<>();
//    }
//
//    @Bean
//    public BlockingQueueEventRepo<TradeCmd> tradeCmdEventRepo() {
//        return new BlockingQueueEventRepo<>();
//    }
//
//    @Bean
//    public EventHandlerRepo<MarketData> eventHandlerRepo() {
//        return new HashMapEventHandlerRepo<>();
//    }
//
//    @Bean
//    public CrossArbitrageParams crossArbitrageParams() {
//        return CrossArbitrageParams.defaultParams();
//    }
//
//    @Bean
//    public CrossStrategy crossStrategy(BlockingQueueEventRepo<MarketData> marketDataEventRepo, BlockingQueueEventRepo<TradeCmd> tradeCmdEventRepo, EventHandlerRepo<MarketData> eventHandlerRepo, CrossArbitrageParams crossArbitrageParams, @Qualifier("strategyExecutorService") ExecutorService strategyExecutorService, @Qualifier("eventExecutorService") ExecutorService eventExecutorService) {
//        return new CrossStrategy(marketDataEventRepo, tradeCmdEventRepo, eventHandlerRepo, crossArbitrageParams, strategyExecutorService, eventExecutorService);
//    }
//
//    @Bean
//    public BNMDGWWebSocketClient bnMDGWWebSocketClient(BlockingQueueEventRepo<MarketData> marketDataEventRepo) {
//        return new BNMDGWWebSocketClient(marketDataEventRepo);
//    }
//
//    @Bean
//    public BNTradeGWWebSocketClient bnTradeGWWebSocketClient(BlockingQueueEventRepo<MarketData> marketDataEventRepo, BlockingQueueEventRepo<TradeCmd> tradeCmdEventRepo) {
//        return new BNTradeGWWebSocketClient(marketDataEventRepo, tradeCmdEventRepo);
//    }
//
//    @Bean
//    public BTMDGWWebSocketClient btMDGWWebSocketClient(BlockingQueueEventRepo<MarketData> marketDataEventRepo) {
//        return new BTMDGWWebSocketClient(marketDataEventRepo);
//    }
//
//    @Bean
//    public BTTradeGWWebSocketClient btTradeGWWebSocketClient(BlockingQueueEventRepo<MarketData> marketDataEventRepo, BlockingQueueEventRepo<TradeCmd> tradeCmdEventRepo) {
//        return new BTTradeGWWebSocketClient(marketDataEventRepo, tradeCmdEventRepo);
//    }
}
