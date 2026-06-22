package com.kdy.indexlab.product;

import com.kdy.indexlab.product.dto.ProductSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private static final int MAX_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;

    public List<ProductSearchResponse> search(
            String category,
            Integer minPrice,
            Integer maxPrice,
            int limit
    ) {
        validatePriceRange(minPrice, maxPrice);

        int safeLimit = Math.min(limit, MAX_LIMIT);

        StringBuilder sql = new StringBuilder("""
                SELECT
                    id,
                    name,
                    category,
                    brand,
                    seller_id,
                    price,
                    status,
                    created_at
                FROM products
                WHERE category = ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(category);

        if (minPrice != null && maxPrice != null) {
            sql.append("""
                    AND price BETWEEN ? AND ?
                    """);

            params.add(minPrice);
            params.add(maxPrice);
        }

        sql.append("""
                ORDER BY created_at DESC
                LIMIT ?
                """);

        params.add(safeLimit);

        return jdbcTemplate.query(
                sql.toString(),
                productRowMapper(),
                params.toArray()
        );
    }

    private void validatePriceRange(
            Integer minPrice,
            Integer maxPrice
    ) {
        if (minPrice == null && maxPrice == null) {
            return;
        }

        if (minPrice == null || maxPrice == null) {
            throw new IllegalArgumentException(
                    "minPrice와 maxPrice는 함께 입력해야 합니다."
            );
        }

        if (minPrice > maxPrice) {
            throw new IllegalArgumentException(
                    "minPrice는 maxPrice 보다 클 수 없습니다."
            );
        }
    }

    private RowMapper<ProductSearchResponse> productRowMapper() {
        return (rs, rowNum) -> new ProductSearchResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("brand"),
                rs.getLong("seller_id"),
                rs.getInt("price"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
