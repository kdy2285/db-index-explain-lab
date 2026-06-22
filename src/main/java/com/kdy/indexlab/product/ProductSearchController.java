package com.kdy.indexlab.product;

import com.kdy.indexlab.product.dto.ProductSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    @GetMapping("/search")
    public List<ProductSearchResponse> searchProducts(
            @RequestParam String category,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return productSearchService.search(
                category, minPrice, maxPrice, limit
        );
    }
}
