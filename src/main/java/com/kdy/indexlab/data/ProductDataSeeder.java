package com.kdy.indexlab.data;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class ProductDataSeeder {

    private static final int BATCH_SIZE = 1_000;

    private static final String[] CATEGORIES = {
            "ELECTRONICS", "FASHION", "BEAUTY", "FOOD", "BOOK",
            "SPORTS", "TOY", "LIFE", "PET", "CAR",
            "DIGITAL", "FURNITURE", "KITCHEN", "HEALTH", "BABY",
            "GAME", "OFFICE", "GARDEN", "MUSIC", "TRAVEL"
    };

    private static final String[] NAME_PREFIXES = {
            "keyboard", "mouse", "monitor", "laptop", "chair",
            "desk", "phone", "tablet", "camera", "speaker"
    };

    private static final String[] STATUSES = {
            "ON_SALE", "SOLD_OUT", "HIDDEN"
    };

    private final JdbcTemplate jdbcTemplate;

    public int seed(int totalCount) {
        int insertedCount = 0;

        while (insertedCount < totalCount) {
            int batchCount = Math.min(
                    BATCH_SIZE,
                    totalCount - insertedCount
            );

            insertBatch(insertedCount, batchCount);
            insertedCount += batchCount;
        }

        return insertedCount;
    }

    private void insertBatch(
            int startIndex,
            int batchCount
    ) {
        String sql = """
            INSERT INTO products
            (
                name,
                category,
                brand,
                seller_id,
                price,
                stock_quantity,
                status,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.batchUpdate(
                sql,
                new BatchPreparedStatementSetter() {

                    @Override
                    public void setValues(
                            PreparedStatement ps,
                            int i
                    ) throws java.sql.SQLException {
                        int sequence = startIndex + i + 1;
                        setProductParams(ps, sequence);
                    }

                    @Override
                    public int getBatchSize() {
                        return batchCount;
                    }
                }
        );
    }

    private void setProductParams(
            PreparedStatement ps,
            int sequence
    ) throws java.sql.SQLException {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String prefix = NAME_PREFIXES[
                random.nextInt(NAME_PREFIXES.length)
                ];

        String category = CATEGORIES[
                random.nextInt(CATEGORIES.length)
                ];

        String brand = "brand-" + random.nextInt(1, 1_001);
        long sellerId = random.nextLong(1, 10_001);
        int price = random.nextInt(1_000, 1_000_001);
        int stockQuantity = random.nextInt(0, 1_001);
        String status = randomStatus(random);

        LocalDateTime createdAt = LocalDateTime.now()
                .minusDays(random.nextInt(0, 730))
                .minusSeconds(random.nextInt(0, 86_400));

        String name = prefix + "-product-" + sequence;

        ps.setString(1, name);
        ps.setString(2, category);
        ps.setString(3, brand);
        ps.setLong(4, sellerId);
        ps.setInt(5, price);
        ps.setInt(6, stockQuantity);
        ps.setString(7, status);
        ps.setTimestamp(8, Timestamp.valueOf(createdAt));
    }

    private String randomStatus(ThreadLocalRandom random) {
        int value = random.nextInt(100);

        if (value < 80) {
            return "ON_SALE";
        }

        if (value < 95) {
            return "SOLD_OUT";
        }

        return "HIDDEN";
    }
}