package com.kdy.indexlab.product.dto;

import java.time.LocalDateTime;

public record ProductSearchResponse(
        Long id,
        String name,
        String category,
        String brand,
        Long sellerId,
        int price,
        String status,
        LocalDateTime createdAt
) {
}