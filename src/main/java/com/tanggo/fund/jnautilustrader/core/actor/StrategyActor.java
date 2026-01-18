package com.tanggo.fund.jnautilustrader.core.actor;

import com.tanggo.fund.jnautilustrader.core.actor.exp.RequestMessage;

import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 方案3：策略模式 - 策略式Actor实现
 * 通过策略接口分离消息处理和错误处理逻辑
 */
public class StrategyActor<T, S> implements MessageActor<T> {

    // 状态持久化接口
    public interface StatePersister<S> {
        /**
         * 保存状态到持久化存储
         */
        void save(S state) throws Exception;

        /**
         * 从持久化存储加载状态
         */
        S load() throws Exception;

        /**
         * 删除持久化状态
         */
        void delete() throws Exception;

        /**
         * 检查是否存在持久化状态
         */
        boolean exists();
    }

    // 空实现（用于不需要持久化的情况）
    public static class NoOpPersister<S> implements StatePersister<S> {
        @Override
        public void save(S state) {}

        @Override
        public S load() {
            return null;
        }

        @Override
        public void delete() {}

        @Override
        public boolean exists() {
            return false;
        }
    }

    // 文件系统持久化实现
    public static class FilePersister<S> implements StatePersister<S> {
        private final String filePath;
        private final Class<S> stateClass;

        public FilePersister(String filePath, Class<S> stateClass) {
            this.filePath = filePath;
            this.stateClass = stateClass;
        }

        @Override
        public void save(S state) throws Exception {
            try (FileOutputStream fos = new FileOutputStream(filePath);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(state);
            }
        }

        @Override
        public S load() throws Exception {
            if (!exists()) {
                return null;
            }
            try (FileInputStream fis = new FileInputStream(filePath);
                 ObjectInputStream ois = new ObjectInputStream(fis)) {
                return stateClass.cast(ois.readObject());
            }
        }

        @Override
        public void delete() throws Exception {
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
        }

        @Override
        public boolean exists() {
            return new File(filePath).exists();
        }
    }

    // 状态管理接口
    //todo StatePersister与State接口 是不是可以合并哦？State没必要存在？
    public interface State<S> {
        /**
         * 获取当前状态
         */
        S getState();

        /**
         * 更新状态
         */
        void setState(S state);

        /**
         * 初始化状态
         */
        void initialize();

        /**
         * 重置状态
         */
        void reset();
    }

    // 默认状态实现
    public static class DefaultState<S> implements State<S> {
        private volatile S state;

        public DefaultState(S initialState) {
            this.state = initialState;
        }

        @Override
        public S getState() {
            return state;
        }

        @Override
        public void setState(S state) {
            this.state = state;
        }

        @Override
        public void initialize() {
            // 默认实现为空，可由子类重写
        }

        @Override
        public void reset() {
            this.state = null;
        }
    }

    // 消息处理策略接口（带状态访问）
    public interface MessageHandler<T, S> {
        void handle(T message, State<S> state) throws Exception;
    }

    // 错误处理策略接口
    public interface ErrorHandler {
        void handle(Exception e);
    }

    // 启动完成回调接口
    public interface StartHandler<S> {
        void handle(State<S> state);
    }

    // 停止完成回调接口
    public interface StopHandler<S> {
        void handle(State<S> state);
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ActorStatus status = ActorStatus.IDLE;

    //todo 可以被注入
    private final BlockingQueue<T> mailbox = new LinkedBlockingQueue<>();


    //todo 可以被注入
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 用于存储请求-响应的未来结果
    private final ConcurrentMap<String, CompletableFuture<Object>> responseFutures = new ConcurrentHashMap<>();

    // 策略接口实现
    private final MessageHandler<T, S> messageHandler;
    private final ErrorHandler errorHandler;
    private final StartHandler<S> startHandler;  // 新增启动回调
    private final StopHandler<S> stopHandler;    // 新增停止回调

    // 状态管理
    private final State<S> state;

    // 状态持久化器
    private final StatePersister<S> persister;

    // 是否自动持久化
    private final boolean autoPersist;

    /**
     * 构造函数（带初始状态）
     * @param messageHandler 消息处理策略
     * @param initialState 初始状态
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, S initialState) {
        this(messageHandler, initialState, new NoOpPersister<>(), true, e -> {
            System.err.println("Actor处理消息时出错: " + e.getMessage());
            e.printStackTrace();
        });
    }

    /**
     * 构造函数（带状态管理和错误处理）
     * @param messageHandler 消息处理策略
     * @param initialState 初始状态
     * @param errorHandler 错误处理策略
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, S initialState, ErrorHandler errorHandler) {
        this(messageHandler, initialState, new NoOpPersister<>(), true, errorHandler);
    }

    /**
     * 构造函数（带自定义状态管理）
     * @param messageHandler 消息处理策略
     * @param state 状态管理器
     * @param errorHandler 错误处理策略
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, State<S> state, ErrorHandler errorHandler) {
        this(messageHandler, state, new NoOpPersister<>(), true, errorHandler);
    }

    /**
     * 构造函数（带持久化支持）
     * @param messageHandler 消息处理策略
     * @param initialState 初始状态
     * @param persister 状态持久化器
     * @param errorHandler 错误处理策略
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, S initialState, StatePersister<S> persister, ErrorHandler errorHandler) {
        this(messageHandler, new DefaultState<>(initialState), persister, true, errorHandler);
    }

    /**
     * 构造函数（带持久化支持和自动持久化配置）
     * @param messageHandler 消息处理策略
     * @param initialState 初始状态
     * @param persister 状态持久化器
     * @param autoPersist 是否自动持久化
     * @param errorHandler 错误处理策略
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, S initialState, StatePersister<S> persister, boolean autoPersist, ErrorHandler errorHandler) {
        this(messageHandler, new DefaultState<>(initialState), persister, autoPersist, errorHandler);
    }

    /**
     * 构造函数（带状态管理、错误处理和回调支持）
     * @param messageHandler 消息处理策略
     * @param initialState 初始状态
     * @param errorHandler 错误处理策略
     * @param startHandler 启动完成回调
     * @param stopHandler 停止完成回调
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, S initialState, ErrorHandler errorHandler,
                         StartHandler startHandler, StopHandler stopHandler) {
        this(messageHandler, new DefaultState<>(initialState), new NoOpPersister<>(), true, errorHandler,
                startHandler, stopHandler);
    }

    /**
     * 构造函数（带自定义状态管理和持久化支持）
     * @param messageHandler 消息处理策略
     * @param state 状态管理器
     * @param persister 状态持久化器
     * @param autoPersist 是否自动持久化
     * @param errorHandler 错误处理策略
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, State<S> state, StatePersister<S> persister, boolean autoPersist, ErrorHandler errorHandler) {
        this(messageHandler, state, persister, autoPersist, errorHandler, null, null);
    }

    /**
     * 构造函数（带所有回调支持）
     * @param messageHandler 消息处理策略
     * @param state 状态管理器
     * @param persister 状态持久化器
     * @param autoPersist 是否自动持久化
     * @param errorHandler 错误处理策略
     * @param startHandler 启动完成回调
     * @param stopHandler 停止完成回调
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, State<S> state, StatePersister<S> persister, boolean autoPersist, ErrorHandler errorHandler,
                         StartHandler startHandler, StopHandler stopHandler) {
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.startHandler = startHandler;
        this.stopHandler = stopHandler;
        this.state = state;
        this.persister = persister;
        this.autoPersist = autoPersist;

        // 初始化时尝试加载持久化状态
        try {
            if (persister.exists()) {
                S loadedState = persister.load();
                if (loadedState != null) {
                    state.setState(loadedState);
                }
            }
        } catch (Exception e) {
            errorHandler.handle(new RuntimeException("状态加载失败: " + e.getMessage(), e));
        }

        state.initialize();
    }

    /**
     * 持久化当前状态
     */
    public void persistState() {
        try {
            if (!(persister instanceof NoOpPersister)) {
                persister.save(state.getState());
            }
        } catch (Exception e) {
            errorHandler.handle(e);
        }
    }



    /**
     * 检查是否支持状态持久化
     */
    public boolean supportsPersistence() {
        return !(persister instanceof NoOpPersister);
    }

    /**
     * 获取当前状态
     */
    public S getState() {
        return state.getState();
    }

    /**
     * 更新状态
     */
    public void setState(S newState) {
        state.setState(newState);
        // 如果启用自动持久化，立即保存
        if (autoPersist && !(persister instanceof NoOpPersister)) {
            try {
                persister.save(newState);
            } catch (Exception e) {
                errorHandler.handle(new RuntimeException("状态持久化失败: " + e.getMessage(), e));
            }
        }
    }



    @Override
    public void tell(T message) {
        if (!running.get()) {
            throw new IllegalStateException("Actor未启动");
        }
        mailbox.offer(message);
    }

    @Override
    public <R> R ask(T message, Class<R> responseType, long timeoutMs) throws InterruptedException {
        if (!running.get()) {
            throw new IllegalStateException("Actor未启动");
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<Object> future = new CompletableFuture<>();
        responseFutures.put(requestId, future);

        try {
            if (message instanceof RequestMessage) {
                ((RequestMessage) message).setRequestId(requestId);
            }
            mailbox.offer(message);

            Object result = future.get(timeoutMs, TimeUnit.MILLISECONDS);

            if (result == null) {
                throw new InterruptedException("响应超时");
            }

            if (!responseType.isInstance(result)) {
                throw new ClassCastException("响应类型不匹配，期望: " + responseType + ", 实际: " + result.getClass());
            }

            return responseType.cast(result);
        } catch (Exception e) {
            responseFutures.remove(requestId);
            if (e instanceof InterruptedException) {
                throw (InterruptedException) e;
            }
            throw new RuntimeException("请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送响应
     */
    public void reply(String requestId, Object response) {
        CompletableFuture<Object> future = responseFutures.remove(requestId);
        if (future != null && !future.isDone()) {
            future.complete(response);
        }
    }

    /**
     * 发送响应（通过消息对象）
     */
    public void reply(RequestMessage message, Object response) {
        if (message.getRequestId() != null) {
            reply(message.getRequestId(), response);
        }
    }

    @Override
    public void run() {
        status = ActorStatus.RUNNING;
        running.set(true);

        // 调用启动回调
        if (startHandler != null) {
            try {
                startHandler.handle(state);
            } catch (Exception e) {
                errorHandler.handle(e);
            }
        }

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                T message = mailbox.take();
                messageHandler.handle(message, state);  // 调用策略接口（带状态）

                // 自动持久化状态（如果支持）
                if (autoPersist && !(persister instanceof NoOpPersister)) {
                    try {
                        persister.save(state.getState());
                    } catch (Exception e) {
                        errorHandler.handle(new RuntimeException("状态持久化失败: " + e.getMessage(), e));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                errorHandler.handle(e);  // 调用策略接口
            }
        }

        // 调用停止回调
        if (stopHandler != null) {
            try {
                stopHandler.handle(state);
            } catch (Exception e) {
                errorHandler.handle(e);
            }
        }

        status = ActorStatus.STOPPED;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            status = ActorStatus.RUNNING;
            executor.submit(this);
        }
    }

    @Override
    public void close() {
        running.set(false);
        executor.shutdownNow();
        status = ActorStatus.STOPPED;
    }

    @Override
    public ActorStatus getStatus() {
        return status;
    }
}
