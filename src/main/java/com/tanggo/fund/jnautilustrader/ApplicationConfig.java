package com.tanggo.fund.jnautilustrader;

import com.tanggo.fund.jnautilustrader.core.entity.Actor;
import lombok.Data;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Data
public class ApplicationConfig {

    private List<Actor> mdClients;
    private List<Actor> tradeClients;
    private Actor service;



}
