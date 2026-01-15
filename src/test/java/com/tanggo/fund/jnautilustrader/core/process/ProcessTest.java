package com.tanggo.fund.jnautilustrader.core.process;

import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandler;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandlerRepo;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.data.OrderBookDelta;
import com.tanggo.fund.jnautilustrader.core.entity.data.TradeTick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Process 事件处理器测试
 */
@ExtendWith(MockitoExtension.class)
class ProcessTest {

    @Mock
    private EventRepo<TradeTick> eventRepo;

    @Mock
    private EventHandlerRepo<TradeTick> eventHandlerRepo;

    private Process process;


}
