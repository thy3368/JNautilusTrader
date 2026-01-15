package com.tanggo.fund.jnautilustrader.core.entity;

import lombok.Data;

@Data
public class Event<T> {
    public String type;
    public T payload;
}
