package com.kdy.indexlab.data;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/data/products")
public class ProductDataController {

    private final ProductDataSeeder productDataSeeder;

    @PostMapping("/seed")
    public String seedProducts(
            @RequestParam(defaultValue = "100000") int count
    ) {
        long startTime = System.currentTimeMillis();

        int insertedCount = productDataSeeder.seed(count);

        long elapsedTime = System.currentTimeMillis() - startTime;

        return "insertedCount=" + insertedCount
                + ", elapsedTimeMs=" + elapsedTime;
    }
}