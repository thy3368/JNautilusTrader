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

        /**
         * 持久化当前状态
         */
        void persist() throws Exception;

        /**
         * 从持久化存储加载状态
         */
        void load() throws Exception;

        /**
         * 检查是否支持持久化
         */
        boolean supportsPersistence();
    }

    // 可持久化状态包装器
    public static class PersistentStateWrapper<S> implements State<S> {
        private final State<S> delegate;
        private final StatePersister<S> persister;
        private final boolean autoPersist;

        public PersistentStateWrapper(State<S> delegate, StatePersister<S> persister) {
            this(delegate, persister, true);
        }

        public PersistentStateWrapper(State<S> delegate, StatePersister<S> persister, boolean autoPersist) {
            this.delegate = delegate;
            this.persister = persister;
            this.autoPersist = autoPersist;
        }

        @Override
        public S getState() {
            return delegate.getState();
        }

        @Override
        public void setState(S state) {
            delegate.setState(state);
            if (autoPersist) {
                try {
                    persister.save(state);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void initialize() {
            try {
                if (persister.exists()) {
                    S loadedState = persister.load();
                    if (loadedState != null) {
                        delegate.setState(loadedState);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            delegate.initialize();
        }

        @Override
        public void reset() {
            delegate.reset();
            try {
                persister.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void persist() throws Exception {
            persister.save(delegate.getState());
        }

        @Override
        public void load() throws Exception {
            S loadedState = persister.load();
            if (loadedState != null) {
                delegate.setState(loadedState);
            }
        }

        @Override
        public boolean supportsPersistence() {
            return !(persister instanceof NoOpPersister);
        }
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

        @Override
        public void persist() throws Exception {
            // 默认不支持持久化
            throw new UnsupportedOperationException("持久化操作未实现");
        }

        @Override
        public void load() throws Exception {
            // 默认不支持加载
            throw new UnsupportedOperationException("加载操作未实现");
        }

        @Override
        public boolean supportsPersistence() {
            return false;
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

    private final BlockingQueue<T> mailbox = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile ActorStatus status = ActorStatus.IDLE;

    // 用于存储请求-响应的未来结果
    private final ConcurrentMap<String, CompletableFuture<Object>> responseFutures = new ConcurrentHashMap<>();

    // 策略接口实现
    private final MessageHandler<T, S> messageHandler;
    private final ErrorHandler errorHandler;

    // 状态管理
    private final State<S> state;


    /**
     * 构造函数（带初始状态）
     * @param messageHandler 消息处理策略
     * @param initialState 初始状态
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, S initialState) {
        this(messageHandler, initialState, e -> {
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
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.state = new DefaultState<>(initialState);
        this.state.initialize();
    }

    /**
     * 构造函数（带自定义状态管理）
     * @param messageHandler 消息处理策略
     * @param state 状态管理器
     * @param errorHandler 错误处理策略
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, State<S> state, ErrorHandler errorHandler) {
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.state = state;
        this.state.initialize();
    }

    /**
     * 构造函数（带持久化支持）
     * @param messageHandler 消息处理策略
     * @param initialState 初始状态
     * @param persister 状态持久化器
     * @param errorHandler 错误处理策略
     */
    public StrategyActor(MessageHandler<T, S> messageHandler, S initialState, StatePersister<S> persister, ErrorHandler errorHandler) {
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.state = new PersistentStateWrapper<>(new DefaultState<>(initialState), persister);
        this.state.initialize();
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
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.state = new PersistentStateWrapper<>(new DefaultState<>(initialState), persister, autoPersist);
        this.state.initialize();
    }

    /**
     * 持久化当前状态
     */
    public void persistState() {
        try {
            if (state.supportsPersistence()) {
                state.persist();
            }
        } catch (Exception e) {
            errorHandler.handle(e);
        }
    }

    /**
     * 从持久化存储加载状态
     */
    public void loadState() {
        try {
            if (state.supportsPersistence()) {
                state.load();
            }
        } catch (Exception e) {
            errorHandler.handle(e);
        }
    }

    /**
     * 检查是否支持状态持久化
     */
    public boolean supportsPersistence() {
        return state.supportsPersistence();
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
    }

    /**
     * 重置状态
     */
    public void resetState() {
        state.reset();
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

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                T message = mailbox.take();
                messageHandler.handle(message, state);  // 调用策略接口（带状态）

                //直接用 StatePersister.save方法重构 去掉 PersistentStateWrapper

                //todo 直接用 StatePersister.save方法重构 去掉 PersistentStateWrapper
                // 自动持久化状态（如果支持）
                if (state.supportsPersistence()) {
                    try {
                        state.persist();
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

        status = ActorStatus.STOPPED;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
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
