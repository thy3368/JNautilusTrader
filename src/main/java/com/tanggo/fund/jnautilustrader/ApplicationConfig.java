package com.tanggo.fund.jnautilustrader;

import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import lombok.Data;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Data
public class ApplicationConfig {

    //行情
    private List<Actor> mdClients;
    //交易
    private List<Actor> tradeClients;
    //应用服务
    private Actor service;


}
