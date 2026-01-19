package com.tanggo.fund.jnautilustrader.core.entity.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单簿价格档位
 * 表示订单簿中的单个价格档位（价格和数量）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class PriceLevel {
    /**
     * 价格
     */
    private String price;

    /**
     * 数量
     */
    private String quantity;

    /**
     * 获取价格的BigDecimal表示（用于精确计算）
     */
    public BigDecimal getPriceAsBigDecimal() {
        return new BigDecimal(price);
    }

    /**
     * 获取数量的BigDecimal表示（用于精确计算）
     */
    public BigDecimal getQuantityAsBigDecimal() {
        return new BigDecimal(quantity);
    }

    /**
     * 获取价格的double表示（用于快速计算，可能损失精度）
     */
    public double getPriceAsDouble() {
        return Double.parseDouble(price);
    }

    /**
     * 获取数量的double表示（用于快速计算，可能损失精度）
     */
    public double getQuantityAsDouble() {
        return Double.parseDouble(quantity);
    }

    @Override
    public String toString() {
        return "[" + price + ", " + quantity + "]";
    }
}