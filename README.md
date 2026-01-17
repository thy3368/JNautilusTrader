# 基于事件驱动的微内核插件化量化交易系统的原型

## 1. 系统概述

JNautilusTrader是一个基于Spring Boot的量化交易系统原型，采用事件驱动架构和微内核插件化设计理念。系统支持多交易所连接、实时行情监控、策略执行和交易管理，具备高可扩展性的特点。

### 核心特性
- **事件驱动架构**: 所有组件通过事件总线通信，实现松耦合
- **微内核插件化**: 核心功能模块化，支持策略和交易所网关的热插拔
- **多交易所支持**: 已实现币安(Binance)和Bitget交易所的行情和交易接口
- **实时行情处理**: 支持订单簿深度、交易Tick、报价Tick等多种市场数据类型
- **策略框架**: 提供跨交易所套利和做市商策略的实现框架
- **低延迟设计**: 使用单线程事件循环和内存队列，确保高吞吐量和低延迟
- **优雅启停**: 完整的生命周期管理，支持优雅启动和关闭

## 2. 系统架构

### 2.1 分层架构

```
JNautilusTrader/
├── core/                    # 核心领域层（内核）
│   ├── entity/             # 领域实体和接口
│   │   ├── Actor.java      # Actor接口（启动/停止生命周期）
│   │   ├── Event.java      # 事件包装类（支持Avro序列化）
│   │   ├── EventHandler.java # 事件处理器接口
│   │   ├── EventRepo.java  # 事件仓库接口
│   │   ├── EventHandlerRepo.java # 事件处理器仓库
│   │   ├── ApplicationConfig.java # 应用配置（行情、交易、策略）
│   │   ├── TradeCmd.java   # 交易指令
│   │   ├── MarketData.java # 市场数据基类
│   │   ├── UseCase.java    # 策略接口（继承自Actor）
│   │   ├── data/           # 市场数据类型
│   │   │   ├── TradeTick.java
│   │   │   ├── OrderBookDepth10.java
│   │   │   ├── OrderBookDeltas.java
│   │   │   ├── OrderBookDelta.java
│   │   │   ├── QuoteTick.java
│   │   │   └── ...
│   │   └── trade/          # 交易指令类型
│   │       ├── PlaceOrder.java
│   │       └── ...
│   └── util/               # 工具类
│       └── ThreadLogger.java # 线程日志工具
├── stragety/               # 策略层（插件）
│   ├── cross/              # 跨市场套利策略
│   │   ├── CrossAppService.java
│   │   ├── CrossArbitrageState.java
│   │   └── CrossArbitrageParams.java
│   └── stoikov/            # Stoikov做市策略
├── adapter/                # 接口适配层（插件）
│   ├── mdgw/              # 行情网关
│   │   ├── bn/            # 币安行情网关
│   │   └── bitget/        # Bitget行情网关
│   ├── tradegw/           # 交易网关
│   │   ├── bn/            # 币安交易网关
│   │   └── bitget/        # Bitget交易网关
│   ├── event_repo/        # 事件仓库实现
│   └── config/            # 配置类
└── Process.java           # 系统启动/停止协调器
```

### 2.2 核心微内核设计

#### 微内核架构理念

**设计原则**:
- **插件化架构**: 核心模块定义接口，功能模块通过插件实现
- **热插拔支持**: 策略和交易所网关可动态加载和卸载
- **低耦合**: 组件间通过事件总线通信，无需直接依赖
- **高内聚**: 每个组件职责单一，可独立测试和维护

#### 核心扩展点设计

##### 1. Actor接口 - 组件生命周期管理
```java
// 所有组件统一生命周期接口
public interface Actor {
    void start_link();  // 启动组件，初始化资源
    void stop();        // 停止组件，清理资源
}
```

**扩展方式**: 行情网关、策略、交易客户端都实现此接口

##### 2. UseCase接口 - 策略扩展点
```java
// 策略接口，继承自Actor
public interface UseCase extends Actor {
    // 策略实现接口，核心交易逻辑
}
```

**扩展方式**: 实现此接口开发新策略（如跨交易所套利、做市商策略）

##### 3. EventHandler接口 - 事件处理扩展点
```java
// 事件处理器接口
public interface EventHandler<T> {
    void handle(Event<T> event);  // 处理特定类型的事件
}
```

**扩展方式**: 为新事件类型实现此接口，如处理新交易所的市场数据

##### 4. EventRepo接口 - 事件存储扩展点
```java
// 事件仓库接口，负责事件存储和分发
public interface EventRepo<T> {
    Event<T> receive();       // 接收事件
    boolean send(Event<T> event);  // 发送事件
}
```

**扩展方式**: 提供不同的实现类：
- `BlockingQueueEventRepo`: 内存队列实现（默认）
- `DisruptorEventRepo`: LMAX Disruptor高性能实现
- `AdvancedDisruptorEventRepo`: 高级Disruptor优化版本
- `CacheAlignedRingBufferEventRepo`: 缓存对齐环形缓冲区
- `ParquetFileQueueEventRepo`: Parquet文件持久化实现

##### 5. EventHandlerRepo接口 - 事件处理器管理扩展点
```java
// 事件处理器仓库接口
public interface EventHandlerRepo<T> {
    EventHandler<T> queryBy(String type);  // 根据事件类型查询处理器
    void addHandler(String type, EventHandler<T> handler);  // 添加事件处理器
}
```

**扩展方式**: 提供不同的实现类，如内存存储、数据库存储等

#### 微内核设计优势

1. **高度可扩展**: 新增策略或交易所支持只需实现对应接口
2. **低风险变更**: 插件化设计隔离了核心代码和功能代码
3. **易于测试**: 每个组件可独立测试，无需依赖整个系统
4. **灵活配置**: 支持通过Spring配置文件动态加载组件
5. **性能优化**: 支持多种事件存储实现，可根据需求选择

### 2.3 事件驱动架构

**事件总线设计**:
- 所有组件通过事件总线通信，实现松耦合
- 事件格式统一: `Event<T>`，支持Avro序列化
- 事件类型可扩展，新类型无需修改核心代码

**事件流程**:
```
行情网关 → 事件仓库 → 策略组件 → 交易指令仓库 → 交易网关
```

#### Actor模型
所有组件（行情客户端、策略、交易客户端）实现`Actor`接口，具有统一的`start_link()`和`stop()`生命周期管理：

```java
public interface Actor {
    void start_link();
    void stop();
}
```

#### 事件驱动
系统通过事件总线传递市场数据和交易指令，事件格式为`Event<T>`，支持Avro序列化：

```java
@Data
public class Event<T> {
    private String type;
    private T payload;
}
```

#### 仓库模式
- `EventRepo`: 负责事件存储和分发
- `EventHandlerRepo`: 管理事件处理器的注册和查找

## 3. 系统启动流程

### 3.1 启动器(Starter.java)

```java
// 1. 加载Spring配置文件
context = new ClassPathXmlApplicationContext(configLocation);

// 2. 获取Process实例
process = context.getBean(Process.class);

// 3. 注册JVM关闭钩子
registerShutdownHook(process, context);

// 4. 启动交易系统
process.start();

// 5. 保持主线程运行
keepAlive();
```

### 3.2 流程协调器(Process.java)

```java
// 按照依赖关系启动组件
1. 启动市场数据客户端 → startMarketDataClients()
2. 启动策略应用服务 → startAppService()
3. 启动交易客户端 → startTradeClients()

// 按照逆序停止组件
1. 停止交易客户端 → stopTradeClients()
2. 停止策略 → stopStrategy()
3. 停止市场数据客户端 → stopMarketDataClients()
```

## 4. 策略框架

### 4.1 跨交易所套利策略(CrossAppService.java)

#### 策略概述
该策略通过同时监控币安和Bitget交易所的实时市场数据，当发现价格差异超过设定阈值时，执行低买高卖的套利操作。

#### 核心功能
- 实时监控币安和Bitget的BTC/USDT现货价格
- 计算价格差异和潜在利润
- 当价差超过阈值时执行套利订单
- 风险管理和持仓控制
- 策略状态监控和统计

#### 事件驱动执行流程

```java
// 单线程事件驱动模式
mainTaskFuture = singleThreadExecutor.submit(() -> {
    registerEventHandlers();
    state.start();

    while (state.isRunning() && state.getCurrentTime() < params.getRunTime()) {
        // 1. 接收市场数据事件
        Event<MarketData> event = marketDataRepo.receive();

        if (event != null) {
            // 2. 处理事件并更新状态
            EventHandler<MarketData> handler = eventHandlerRepo.queryBy(event.type);
            if (handler != null) {
                handler.handle(event);

                // 3. 事件处理后执行策略检查
                if (shouldExecuteStrategy()) {
                    state.updateState();
                    executeStrategy();
                }
            }
        }
    }
});
```

#### 事件处理器注册

```java
private void registerEventHandlers() {
    eventHandlerRepo = new HashMapEventHandlerRepo<MarketData>();
    eventHandlerRepo.addHandler("BINANCE_TRADE_TICK", new BinanceTradeTickEventHandler());
    eventHandlerRepo.addHandler("BINANCE_ORDER_BOOK_DEPTH", new BinanceOrderBookDepthEventHandler());
    eventHandlerRepo.addHandler("BINANCE_ORDER_BOOK_DELTA", new BinanceOrderBookDeltaEventHandler());
    eventHandlerRepo.addHandler("BINANCE_QUOTE_TICK", new BinanceQuoteTickEventHandler());
    eventHandlerRepo.addHandler("BITGET_TRADE_TICK", new BitgetTradeTickEventHandler());
    eventHandlerRepo.addHandler("BITGET_ORDER_BOOK_DEPTH", new BitgetOrderBookDepthEventHandler());
    eventHandlerRepo.addHandler("BITGET_ORDER_BOOK_DELTA", new BitgetOrderBookDeltaEventHandler());
}
```

#### 策略执行逻辑

```java
private void executeStrategy() {
    // 检查套利条件
    if (!state.canArbitrage(1000)) {
        return;
    }

    double spreadPercentage = state.calculateSpreadPercentage();

    // 检查是否满足套利阈值
    if (!params.shouldArbitrage(state.getBinanceMidPrice(), state.getBitgetMidPrice())) {
        return;
    }

    // 确定套利方向
    if (state.getBinanceMidPrice() < state.getBitgetMidPrice()) {
        // 币安买入，Bitget卖出
        executeArbitrage("BINANCE", "BITGET",
            state.getBinanceAskPrice(), state.getBitgetBidPrice());
    } else {
        // Bitget买入，币安卖出
        executeArbitrage("BITGET", "BINANCE",
            state.getBitgetAskPrice(), state.getBinanceBidPrice());
    }
}

```

### 4.2 策略参数配置

```java
@Data
public class CrossArbitrageParams {
    // 套利阈值（百分比）
    private double arbitrageThreshold = 0.1;

    // 最小利润（USDT）
    private double minProfit = 0.1;

    // 订单数量（BTC）
    private double orderQuantity = 0.001;

    // 最大持仓限制（BTC）
    private double maxPositionLimit = 0.01;

    // 检查间隔（毫秒）
    private long checkInterval = 100;

    // 运行时间（秒）
    private double runTime = 3600;

    // 调试模式
    private boolean debugMode = false;
}
```

### 4.3 策略状态管理

```java
@Data
public class CrossArbitrageState {
    // 币安市场数据
    private double binanceBidPrice;
    private double binanceAskPrice;
    private double binanceMidPrice;

    // Bitget市场数据
    private double bitgetBidPrice;
    private double bitgetAskPrice;
    private double bitgetMidPrice;

    // 策略状态
    private boolean isRunning;
    private double currentTime;
    private double lastStrategyExecutionTime;

    // 统计信息
    private int arbitrageCount;
    private int successfulArbitrageCount;
    private int failedArbitrageCount;
    private double totalProfit;
    private double totalVolume;
    private double currentPosition;
    private double maxPosition;
    private double minPosition;
    private double avgPosition;
    private double maxSpreadPercentage;
    private double avgSpreadPercentage;
}
```

## 5. 事件处理机制

### 5.1 事件类型

#### 市场数据事件
- `BINANCE_TRADE_TICK`: 币安交易Tick
- `BINANCE_ORDER_BOOK_DEPTH`: 币安订单簿深度
- `BINANCE_ORDER_BOOK_DELTA`: 币安订单簿增量更新
- `BINANCE_QUOTE_TICK`: 币安报价Tick（bookTicker）
- `BITGET_TRADE_TICK`: Bitget交易Tick
- `BITGET_ORDER_BOOK_DEPTH`: Bitget订单簿深度
- `BITGET_ORDER_BOOK_DELTA`: Bitget订单簿增量更新

#### 交易指令事件
- `PLACE_ORDER_BINANCE`: 币安下单指令
- `PLACE_ORDER_BITGET`: Bitget下单指令
- `CANCEL_ORDER_BINANCE`: 币安撤单指令
- `CANCEL_ORDER_BITGET`: Bitget撤单指令

### 5.2 事件处理器示例

```java
private class BinanceTradeTickEventHandler implements EventHandler<MarketData> {
    @Override
    public void handle(Event<MarketData> event) {
        MarketData marketData = event.payload;
        if (marketData.getMessage() instanceof TradeTick tradeTick) {
            state.setBinanceMidPrice(tradeTick.price);
            if (params.isDebugMode()) {
                logger.debug("币安最新成交价: {}", String.format("%.2f", tradeTick.price));
            }
        }
    }
}

private class BinanceOrderBookDepthEventHandler implements EventHandler<MarketData> {
    @Override
    public void handle(Event<MarketData> event) {
        MarketData marketData = event.payload;
        if (marketData.getMessage() instanceof OrderBookDepth10 orderBook) {
            // 提取最佳买卖价
            if (orderBook.getBids() != null && !orderBook.getBids().isEmpty()) {
                String bidPriceStr = orderBook.getBids().get(0).get(0);
                if (bidPriceStr != null && !bidPriceStr.isEmpty()) {
                    double bidPrice = Double.parseDouble(bidPriceStr);
                    state.setBinanceBidPrice(bidPrice);
                }
            }

            if (orderBook.getAsks() != null && !orderBook.getAsks().isEmpty()) {
                String askPriceStr = orderBook.getAsks().get(0).get(0);
                if (askPriceStr != null && !askPriceStr.isEmpty()) {
                    double askPrice = Double.parseDouble(askPriceStr);
                    state.setBinanceAskPrice(askPrice);
                }
            }

            // 更新中间价
            if (state.getBinanceBidPrice() > 0 && state.getBinanceAskPrice() > 0) {
                state.setBinanceMidPrice((state.getBinanceBidPrice() + state.getBinanceAskPrice()) / 2);
            }
        }
    }
}
```

## 6. 技术栈与依赖

### 6.1 核心依赖

```xml
<!-- Spring Boot 4.0.1 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- Jackson JSON处理 -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.2</version>
</dependency>

<!-- LMAX Disruptor高性能事件处理 -->
<dependency>
    <groupId>com.lmax</groupId>
    <artifactId>disruptor</artifactId>
    <version>3.4.4</version>
</dependency>

<!-- Parquet文件存储 -->
<dependency>
    <groupId>org.apache.parquet</groupId>
    <artifactId>parquet-avro</artifactId>
    <version>1.13.1</version>
</dependency>

<!-- Lombok代码简化 -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>

<!-- JMH基准测试 -->
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.35</version>
</dependency>
```

### 6.2 构建与运行

```bash
# 使用Maven Wrapper构建项目
./mvnw clean compile

# 运行应用程序
./mvnw spring-boot:run

# 打包成JAR
./mvnw clean package

# 运行特定测试类
./mvnw test -Dtest=ProcessTest
```

## 7. 系统配置

### 7.1 应用配置文件(applicationContext.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
        http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
        http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context.xsd">

    <!-- 启用组件扫描 - 会自动扫描@Component注解的类,但排除WebSocket客户端(手动注册) -->
    <context:component-scan base-package="com.tanggo.fund.jnautilustrader">
        <context:exclude-filter type="regex" expression="com\.tanggo\.fund\.jnautilustrader\.adapter\.(mdgw|tradegw)\..*WebSocketClient"/>
    </context:component-scan>

    <!-- ==================== StrategyConfig - 策略配置 ==================== -->

    <!-- StrategyConfig配置 - 配置市场数据客户端、交易客户端和策略 -->
    <bean id="serviceConfig" class="com.tanggo.fund.jnautilustrader.core.entity.ApplicationConfig">
        <!-- 市场数据客户端列表 -->
        <property name="mdClients">
            <list>
                <ref bean="bnMDGWWebSocketClient"/>
                <ref bean="btMDGWWebSocketClient"/>
            </list>
        </property>
        <!-- 交易客户端列表 -->
        <property name="tradeClients">
            <list>
                <ref bean="bnTradeGWWebSocketClient"/>
                <ref bean="btTradeGWWebSocketClient"/>
            </list>
        </property>
        <!-- 策略 -->
        <property name="service" ref="crossAppService"/>
    </bean>

    <!-- ==================== CrossStrategyConfig - 跨交易所策略配置 ==================== -->

    <!-- 市场数据事件仓库 -->
    <bean id="marketDataEventRepo" class="com.tanggo.fund.jnautilustrader.adapter.event_repo.event.BlockingQueueEventRepo">
        <!-- 类型参数通过构造函数或setter注入，这里使用无参构造 -->
    </bean>

    <!-- 交易指令事件仓库 -->
    <bean id="tradeCmdEventRepo" class="com.tanggo.fund.jnautilustrader.adapter.event_repo.event.BlockingQueueEventRepo">
        <!-- 类型参数通过构造函数或setter注入，这里使用无参构造 -->
    </bean>

    <!-- 事件处理器仓库 -->
    <bean id="eventHandlerRepo" class="com.tanggo.fund.jnautilustrader.adapter.event_repo.handler.HashMapEventHandlerRepo"/>

    <!-- 跨交易所套利参数 -->
    <bean id="crossArbitrageParams" class="com.tanggo.fund.jnautilustrader.stragety.cross.CrossArbitrageParams"
          factory-method="defaultParams"/>

    <!-- 跨交易所套利策略 -->
    <bean id="crossAppService" class="com.tanggo.fund.jnautilustrader.stragety.cross.CrossAppService">
        <property name="params" ref="crossArbitrageParams"/>
        <property name="marketDataRepo" ref="marketDataEventRepo"/>
        <property name="tradeCmdRepo" ref="tradeCmdEventRepo"/>
        <property name="singleThreadExecutor" ref="singleThreadExecutorService"/>
    </bean>

    <!-- 币安市场数据网关WebSocket客户端 -->
    <bean id="bnMDGWWebSocketClient" class="com.tanggo.fund.jnautilustrader.adapter.mdgw.bn.BNMDGWWebSocketClient">
        <constructor-arg ref="marketDataEventRepo"/>
        <constructor-arg ref="timerExecutorService"/>
        <constructor-arg ref="marketDataExecutorService"/>
    </bean>

    <!-- 币安交易网关WebSocket客户端 -->
    <bean id="bnTradeGWWebSocketClient" class="com.tanggo.fund.jnautilustrader.adapter.tradegw.bn.BNTradeGWWebSocketClient">
        <constructor-arg ref="marketDataEventRepo"/>
        <constructor-arg ref="tradeCmdEventRepo"/>
        <constructor-arg ref="timerExecutorService"/>
    </bean>

    <!-- Bitget市场数据网关WebSocket客户端 -->
    <bean id="btMDGWWebSocketClient" class="com.tanggo.fund.jnautilustrader.adapter.mdgw.bitget.BTMDGWWebSocketClient">
        <constructor-arg ref="marketDataEventRepo"/>
        <constructor-arg ref="timerExecutorService"/>
    </bean>

    <!-- Bitget交易网关WebSocket客户端 -->
    <bean id="btTradeGWWebSocketClient" class="com.tanggo.fund.jnautilustrader.adapter.tradegw.bitget.BTTradeGWWebSocketClient">
        <constructor-arg ref="marketDataEventRepo"/>
        <constructor-arg ref="tradeCmdEventRepo"/>
        <constructor-arg ref="timerExecutorService"/>
    </bean>

    <!-- ==================== ThreadConfig - 高性能线程模型配置 ==================== -->

    <!-- 线程池默认参数 -->
    <bean id="threadPoolDefaults" class="org.springframework.beans.factory.config.PropertiesFactoryBean">
        <property name="properties">
            <props>
                <prop key="corePoolSize">#{T(java.lang.Runtime).getRuntime().availableProcessors()}</prop>
                <prop key="maxPoolSize">#{T(java.lang.Runtime).getRuntime().availableProcessors() * 2}</prop>
                <prop key="queueCapacity">1024</prop>
                <prop key="keepAliveTime">60</prop>
            </props>
        </property>
    </bean>

    <!-- ==================== 市场数据路径线程池 ==================== -->

    <!-- 市场数据接收线程工厂 -->
    <bean id="marketDataThreadFactory" class="org.springframework.scheduling.concurrent.CustomizableThreadFactory">
        <constructor-arg value="md-receiver-"/>
    </bean>

    <!-- 市场数据处理线程工厂 -->
    <bean id="marketDataProcessorThreadFactory" class="org.springframework.scheduling.concurrent.CustomizableThreadFactory">
        <constructor-arg value="md-processor-"/>
    </bean>

    <!-- 市场数据接收线程池 -->
    <bean id="marketDataExecutorService" class="java.util.concurrent.ThreadPoolExecutor">
        <constructor-arg value="4"/> <!-- corePoolSize -->
        <constructor-arg value="4"/> <!-- maxPoolSize -->
        <constructor-arg value="0"/> <!-- keepAliveTime -->
        <constructor-arg value="MILLISECONDS"/> <!-- timeUnit -->
        <constructor-arg>
            <bean class="java.util.concurrent.LinkedBlockingQueue">
                <constructor-arg value="#{2 * 1024}"/> <!-- capacity -->
            </bean>
        </constructor-arg>
        <constructor-arg ref="marketDataThreadFactory"/>
        <constructor-arg>
            <bean class="java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy"/>
        </constructor-arg>
    </bean>

    <!-- 市场数据处理线程池 -->
    <bean id="marketDataProcessorExecutorService" class="java.util.concurrent.ThreadPoolExecutor">
        <constructor-arg value="#{T(java.lang.Runtime).getRuntime().availableProcessors()}"/> <!-- corePoolSize -->
        <constructor-arg value="#{T(java.lang.Runtime).getRuntime().availableProcessors() * 2}"/> <!-- maxPoolSize -->
        <constructor-arg value="60"/> <!-- keepAliveTime -->
        <constructor-arg value="SECONDS"/> <!-- timeUnit -->
        <constructor-arg>
            <bean class="java.util.concurrent.LinkedBlockingQueue">
                <constructor-arg value="#{2 * 1024}"/> <!-- capacity -->
            </bean>
        </constructor-arg>
        <constructor-arg ref="marketDataProcessorThreadFactory"/>
        <constructor-arg>
            <bean class="java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy"/>
        </constructor-arg>
    </bean>

    <!-- ==================== 交易执行路径线程池 ==================== -->

    <!-- 交易指令执行线程工厂 -->
    <bean id="tradingThreadFactory" class="org.springframework.scheduling.concurrent.CustomizableThreadFactory">
        <constructor-arg value="trading-executor-"/>
    </bean>

    <!-- 交易指令执行线程池 -->
    <bean id="tradingExecutorService" class="java.util.concurrent.ThreadPoolExecutor">
        <constructor-arg value="4"/> <!-- corePoolSize -->
        <constructor-arg value="4"/> <!-- maxPoolSize -->
        <constructor-arg value="0"/> <!-- keepAliveTime -->
        <constructor-arg value="MILLISECONDS"/> <!-- timeUnit -->
        <constructor-arg>
            <bean class="java.util.concurrent.LinkedBlockingQueue">
                <constructor-arg value="256"/> <!-- capacity -->
            </bean>
        </constructor-arg>
        <constructor-arg ref="tradingThreadFactory"/>
        <constructor-arg>
            <bean class="java.util.concurrent.ThreadPoolExecutor.AbortPolicy"/>
        </constructor-arg>
    </bean>

    <!-- ==================== 策略计算路径线程池 ==================== -->

    <!-- 策略计算线程工厂 -->
    <bean id="strategyThreadFactory" class="org.springframework.scheduling.concurrent.CustomizableThreadFactory">
        <constructor-arg value="strategy-calc-"/>
    </bean>

    <!-- 策略计算线程池 -->
    <bean id="strategyExecutorService" class="java.util.concurrent.ThreadPoolExecutor">
        <constructor-arg value="#{T(java.lang.Runtime).getRuntime().availableProcessors()}"/> <!-- corePoolSize -->
        <constructor-arg value="#{T(java.lang.Runtime).getRuntime().availableProcessors() * 2}"/> <!-- maxPoolSize -->
        <constructor-arg value="60"/> <!-- keepAliveTime -->
        <constructor-arg value="SECONDS"/> <!-- timeUnit -->
        <constructor-arg>
            <bean class="java.util.concurrent.LinkedBlockingQueue">
                <constructor-arg value="1024"/> <!-- capacity -->
            </bean>
        </constructor-arg>
        <constructor-arg ref="strategyThreadFactory"/>
        <constructor-arg>
            <bean class="java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy"/>
        </constructor-arg>
    </bean>

    <!-- ==================== 辅助路径线程池 ==================== -->

    <!-- IO操作线程工厂 -->
    <bean id="ioThreadFactory" class="org.springframework.scheduling.concurrent.CustomizableThreadFactory">
        <constructor-arg value="io-worker-"/>
    </bean>

    <!-- 定时器线程工厂 -->
    <bean id="timerThreadFactory" class="org.springframework.scheduling.concurrent.CustomizableThreadFactory">
        <constructor-arg value="timer-task-"/>
    </bean>

    <!-- IO操作线程池 -->
    <bean id="ioExecutorService" class="java.util.concurrent.ThreadPoolExecutor">
        <constructor-arg value="#{T(java.lang.Runtime).getRuntime().availableProcessors()}"/> <!-- corePoolSize -->
        <constructor-arg value="2147483647"/> <!-- maxPoolSize (Integer.MAX_VALUE) -->
        <constructor-arg value="60"/> <!-- keepAliveTime -->
        <constructor-arg value="SECONDS"/> <!-- timeUnit -->
        <constructor-arg>
            <bean class="java.util.concurrent.SynchronousQueue"/>
        </constructor-arg>
        <constructor-arg ref="ioThreadFactory"/>
        <constructor-arg>
            <bean class="java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy"/>
        </constructor-arg>
    </bean>

    <!-- 定时器线程池 -->
    <bean id="timerExecutorService" class="java.util.concurrent.Executors" factory-method="newScheduledThreadPool">
        <constructor-arg value="4"/>
        <constructor-arg ref="timerThreadFactory"/>
    </bean>

    <!-- 单线程执行器 -->
    <bean id="singleThreadExecutorService" class="java.util.concurrent.Executors" factory-method="newSingleThreadExecutor">
        <constructor-arg ref="ioThreadFactory"/>
    </bean>

    <!-- ==================== 事件处理线程池 ==================== -->
    <!-- 注意：CrossStrategy中引用了eventExecutorService，但ThreadConfig中没有定义，这里补充一个 -->
    <bean id="eventExecutorService" class="java.util.concurrent.ThreadPoolExecutor">
        <constructor-arg value="#{T(java.lang.Runtime).getRuntime().availableProcessors()}"/> <!-- corePoolSize -->
        <constructor-arg value="#{T(java.lang.Runtime).getRuntime().availableProcessors() * 2}"/> <!-- maxPoolSize -->
        <constructor-arg value="60"/> <!-- keepAliveTime -->
        <constructor-arg value="SECONDS"/> <!-- timeUnit -->
        <constructor-arg>
            <bean class="java.util.concurrent.LinkedBlockingQueue">
                <constructor-arg value="1024"/> <!-- capacity -->
            </bean>
        </constructor-arg>
        <constructor-arg ref="ioThreadFactory"/>
        <constructor-arg>
            <bean class="java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy"/>
        </constructor-arg>
    </bean>

    <!-- ==================== Process - 系统进程管理器 ==================== -->
    <bean id="process" class="com.tanggo.fund.jnautilustrader.Process">
        <property name="applicationConfig" ref="serviceConfig"/>
    </bean>

</beans>
```

### 7.2 日志配置(logback.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="DEBUG">
        <appender-ref ref="CONSOLE"/>
    </root>

    <logger name="org.springframework" level="INFO"/>
</configuration>
```

## 8. 性能优化与架构设计原则

### 8.1 低延迟设计

#### 单线程事件循环
```java
// 使用单线程执行器处理事件和策略
private ExecutorService singleThreadExecutor;

// 事件处理和策略执行在同一线程
mainTaskFuture = singleThreadExecutor.submit(() -> {
    while (state.isRunning()) {
        Event<MarketData> event = marketDataRepo.receive();
        if (event != null) {
            handler.handle(event);
            executeStrategy();
        }
    }
});
```

#### 内存队列优化
```java
// 使用阻塞队列实现事件存储和分发
public class InMemoryEventRepo<T> implements EventRepo<T> {
    private final BlockingQueue<Event<T>> queue = new LinkedBlockingQueue<>();

    @Override
    public Event<T> receive() {
        try {
            return queue.poll(10, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public boolean send(Event<T> event) {
        return queue.offer(event);
    }
}
```

#### 纳秒级计时
```java
// 使用纳秒级精度控制策略执行频率
long currentTimeNanos = System.nanoTime();
long intervalNanos = params.getCheckInterval() * 1_000_000L; // 转换为纳秒
long timeSinceLastExecution = currentTimeNanos - state.getLastStrategyExecutionTime();

if (timeSinceLastExecution >= intervalNanos) {
    state.updateState();
    executeStrategy();
    state.setLastStrategyExecutionTime(currentTimeNanos);
}
```

### 8.2 架构设计原则

#### 开闭原则(OCP)
策略和交易所网关通过接口实现，支持扩展新策略和交易所而无需修改核心代码。

#### 单一职责原则(SRP)
- 行情网关只负责数据采集
- 策略只负责交易决策
- 交易客户端只负责订单执行

#### 依赖倒置原则(DIP)
核心组件依赖于抽象接口而非具体实现，通过Spring DI容器实现依赖注入。

## 9. 扩展性设计

### 9.1 添加新策略

1. 实现策略类并继承`UseCase`接口
2. 在策略中实现事件处理逻辑
3. 配置策略到`ApplicationConfig`
4. 在`Process`类中添加启动/停止逻辑

### 9.2 添加新交易所支持

1. 在`mdgw/`包中创建行情网关实现
2. 在`tradegw/`包中创建交易网关实现
3. 实现相应的事件处理器
4. 配置到`ApplicationConfig`

### 9.3 插件化部署

系统支持通过配置文件动态加载策略和交易所网关，实现插件化部署和更新。

## 10. 总结

JNautilusTrader实现了一个高性能、可扩展的事件驱动量化交易系统原型。其架构设计遵循了微内核插件化原则，通过事件总线实现组件间的松耦合，支持多交易所连接和多种策略类型。系统具备低延迟处理能力、完整的生命周期管理和丰富的统计功能，为量化交易策略的开发和回测提供了坚实的基础框架。

该原型系统展示了如何将事件驱动架构与量化交易业务结合，实现了高吞吐量、低延迟的交易执行，同时保持了代码的可维护性和可扩展性。
