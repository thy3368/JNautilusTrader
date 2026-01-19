package com.tanggo.fund.jnautilustrader.core.stragety;

import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import com.tanggo.fund.jnautilustrader.core.entity.EventHandlerRepo;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import com.tanggo.fund.jnautilustrader.core.entity.event.data.TradeTick;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    void abc() {
        Actor actor = null;

        actor.start_link();
        actor.stop();


    }

}
