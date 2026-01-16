package com.tanggo.fund.jnautilustrader.core.entity;

import lombok.Data;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;

import java.io.Serializable;

@Data
public class Event<T> implements Serializable {
    public String type;
    public T payload;

    public Event() {
        // 必须提供无参构造函数以便 Avro 反序列化
    }

    public Event(String type, T payload) {
        this.type = type;
        this.payload = payload;
    }

    // Avro 序列化需要的方法
    public Schema getSchema() {
        return Schema.createRecord("Event", "An event with type and payload",
                "com.tanggo.fund.jnautilustrader.core.entity", false);
    }

    public Object get(int fieldPos) {
        switch (fieldPos) {
            case 0:
                return type;
            case 1:
                return payload;
            default:
                throw new IndexOutOfBoundsException("Invalid field position: " + fieldPos);
        }
    }

    public void put(int fieldPos, Object value) {
        switch (fieldPos) {
            case 0:
                this.type = (String) value;
                break;
            case 1:
                this.payload = (T) value;
                break;
            default:
                throw new IndexOutOfBoundsException("Invalid field position: " + fieldPos);
        }
    }
}
