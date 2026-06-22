package com.kdy.indexlab.product;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private ProductStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Product(
            String name,
            String category,
            String brand,
            Long sellerId,
            int price,
            int stockQuantity,
            ProductStatus status,
            LocalDateTime createdAt
    ) {
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.sellerId = sellerId;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Product create(
            String name,
            String category,
            String brand,
            Long sellerId,
            int price,
            int stockQuantity,
            ProductStatus status,
            LocalDateTime createdAt
    ) {
        return new Product(name, category, brand, sellerId, price, stockQuantity, status, createdAt);
    }

}
