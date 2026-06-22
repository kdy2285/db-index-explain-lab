# DB Index / EXPLAIN 최적화 실험 프로젝트

## 1. 프로젝트 목표

MySQL 기반 상품 검색 API에서
인덱스 유무와 인덱스 설계 방식에 따라
실행 계획과 응답 시간이 어떻게 달라지는지 검증했습니다.

대량 데이터를 생성한 뒤
EXPLAIN ANALYZE를 사용해
Full Scan, Index Scan, Sort, filesort,
복합 인덱스 컬럼 순서, LIKE 검색,
범위 조건, 카디널리티와 선택도 차이를 비교했습니다.

이 프로젝트의 목표는 단순한 검색 API 구현이 아니라,
실제 검색 패턴을 기준으로
어떤 인덱스를 선택해야 하는지 판단 기준을 정리하는 것입니다.

## 2. 기술 스택

* Java 21
* Spring Boot 3.5.x
* Spring Web
* Spring JDBC
* Spring Data JPA
* MySQL 8.4
* Docker Compose
* DBeaver
* Gradle
* Lombok

## 3. 도메인 설계

상품 검색 도메인을 사용했습니다.

주요 컬럼은 다음과 같습니다.

* name: LIKE 검색 실험
* category: 카테고리 검색 조건
* brand: 브랜드 조건
* seller_id: 판매자 조건
* price: 범위 조건
* status: 낮은 카디널리티 컬럼
* created_at: 최신순 정렬 조건

대량 데이터는 JdbcTemplate batch insert를 사용해 생성했습니다.

JPA saveAll 대신 JdbcTemplate을 사용한 이유는
대량 데이터 생성 속도와 SQL 제어가
이번 프로젝트에서 더 중요했기 때문입니다.

## 4. 데이터 생성

총 100,000건의 상품 데이터를 생성했습니다.

데이터 분포는 다음과 같습니다.

* category: 20개
* brand: 1,000개
* seller_id: 10,000명
* status: ON_SALE, SOLD_OUT, HIDDEN
* price: 1,000원에서 1,000,000원
* created_at: 최근 2년 범위
* name: keyboard, mouse, monitor 등 prefix 포함

초기 상태에서는 PRIMARY KEY 외
보조 인덱스가 없는 상태에서 실험을 시작했습니다.

## 5. 인덱스 없는 검색 쿼리

처음에는 보조 인덱스가 없는 상태에서
카테고리 검색과 최신순 정렬 쿼리를 실행했습니다.

```sql
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
WHERE category = 'ELECTRONICS'
ORDER BY created_at DESC
LIMIT 20;
```

실행 결과:

* 실행 시간: 약 0.024s
* 반환 건수: 20건
* access_type: ALL
* key: NULL
* rows: 약 99,474
* Table scan 발생
* Sort created_at DESC 발생

EXPLAIN ANALYZE 결과:

* products 테이블 전체 100,000건 스캔
* category = ELECTRONICS 조건으로 약 4,876건 필터링
* created_at DESC 기준으로 정렬
* LIMIT 20 반환

인덱스가 없기 때문에 MySQL은
category 조건으로 바로 탐색하지 못하고
products 테이블 전체를 스캔했습니다.

또한 created_at DESC 정렬을 위해
별도 Sort 단계가 발생했습니다.

## 6. 단일 인덱스 실험

category 컬럼에 단일 인덱스를 생성했습니다.

```sql
CREATE INDEX idx_products_category
ON products (category);
```

동일한 검색 쿼리를 다시 실행했습니다.

실행 결과:

* type: ref
* key: idx_products_category
* rows: 약 4,876
* Extra: Using filesort
* EXPLAIN ANALYZE 기준 약 4,876건 조회
* 실행 시간: 약 5.52ms

category 인덱스를 통해
전체 테이블 스캔은 줄었습니다.

하지만 category 단일 인덱스만으로는
created_at DESC 정렬까지 처리하지 못했기 때문에
Using filesort가 남았습니다.

즉, 단일 인덱스는 WHERE 조건의 탐색 범위를
줄이는 데는 효과가 있지만,
ORDER BY까지 항상 최적화하지는 못합니다.

## 7. 복합 인덱스 실험

category 검색과 created_at 최신순 정렬을
함께 최적화하기 위해 복합 인덱스를 생성했습니다.

```sql
CREATE INDEX idx_products_category_created_at
ON products (category, created_at DESC);
```

동일한 검색 쿼리를 다시 실행했습니다.

실행 결과:

* key: idx_products_category_created_at
* Extra: NULL
* Using filesort 제거
* 별도 Sort 없음
* 실행 시간: 약 0.145ms
* LIMIT 20 즉시 반환

category 조건으로 인덱스 구간을 좁힌 뒤,
created_at DESC 순서대로 데이터를 읽을 수 있었습니다.

이를 통해 검색 조건과 정렬 조건이 함께 있는 경우
복합 인덱스 설계가 필요하다는 점을 확인했습니다.

## 8. 복합 인덱스 컬럼 순서 비교

동일한 컬럼이라도
복합 인덱스의 컬럼 순서에 따라
실행 방식이 달라졌습니다.

비교한 인덱스는 다음과 같습니다.

```sql
CREATE INDEX idx_products_category_created_at
ON products (category, created_at DESC);

CREATE INDEX idx_products_created_at_category
ON products (created_at DESC, category);
```

### 8.1 LIMIT 쿼리 비교

```sql
SELECT
    id,
    name,
    category,
    brand,
    seller_id,
    price,
    status,
    created_at
FROM products FORCE INDEX (
    idx_products_created_at_category
)
WHERE category = 'ELECTRONICS'
ORDER BY created_at DESC
LIMIT 20;
```

결과:

* key: idx_products_created_at_category
* type: index
* Extra: Using where
* created_at 순서로 인덱스 스캔
* category 조건은 필터링으로 처리
* 약 433건을 읽고 20건 반환

이 인덱스는 최신순 정렬에는 유리하지만,
category 조건으로 탐색 범위를 먼저 줄이지 못했습니다.

### 8.2 COUNT 쿼리 비교

```sql
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM products FORCE INDEX (
    idx_products_category_created_at
)
WHERE category = 'ELECTRONICS';
```

결과:

* Covering index lookup
* 약 4,876건 조회
* 실행 시간: 약 0.854ms

```sql
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM products FORCE INDEX (
    idx_products_created_at_category
)
WHERE category = 'ELECTRONICS';
```

결과:

* Covering index scan
* 전체 100,000건 조회
* category 조건으로 약 4,876건 필터링
* 실행 시간: 약 13.8ms

복합 인덱스는 필요한 컬럼을 포함하는 것보다
컬럼 순서가 더 중요했습니다.

검색 조건의 동등 비교 컬럼을 선두에 두면
탐색 범위를 먼저 줄일 수 있습니다.

## 9. LIKE 검색 실험

name 컬럼에 단일 인덱스를 생성했습니다.

```sql
CREATE INDEX idx_products_name
ON products (name);
```

### 9.1 Prefix 검색

```sql
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM products
WHERE name LIKE 'keyboard%';
```

결과:

* Covering index range scan
* idx_products_name 사용
* 약 10,003건 조회
* 실행 시간: 약 4.11ms

LIKE 'keyword%'는
문자열의 시작 값이 고정되어 있기 때문에
B-Tree 인덱스의 range scan이 가능했습니다.

### 9.2 Contains 검색

```sql
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM products
WHERE name LIKE '%keyboard%';
```

결과:

* Covering index scan
* idx_products_name 사용
* 전체 100,000건 스캔
* 약 10,003건 필터링
* 실행 시간: 약 38.4ms

LIKE '%keyword%'는
앞쪽에 와일드카드가 있기 때문에
인덱스 탐색 시작점을 잡을 수 없었습니다.

MySQL은 name 인덱스를 사용했지만,
range scan이 아니라 전체 인덱스를 스캔하면서
조건을 필터링했습니다.

따라서 일반 B-Tree 인덱스는
prefix 검색에는 효과적이지만,
중간 포함 검색에는 한계가 있습니다.

운영 환경에서 중간 포함 검색이 중요하다면
Full-Text Search 또는 검색 엔진 도입을
검토해야 합니다.

## 10. 범위 조건과 정렬 조건 실험

가격 범위 조건과 최신순 정렬 조건을 함께 실험했습니다.

먼저 다음 복합 인덱스를 생성했습니다.

```sql
CREATE INDEX idx_products_category_price_created_at
ON products (category, price, created_at DESC);
```

실행 조건:

```sql
WHERE category = 'ELECTRONICS'
  AND price BETWEEN 100000 AND 200000
ORDER BY created_at DESC
LIMIT 20;
```

결과:

* Index range scan 발생
* category 조건 사용
* price BETWEEN 조건 사용
* 약 499건 조회
* created_at DESC 별도 Sort 발생
* 실행 시간: 약 0.867ms

이 인덱스는 category와 price 범위 조건으로
후보군을 줄이는 데는 효과적이었습니다.

하지만 price가 범위 조건으로 사용되면서,
그 뒤에 있는 created_at DESC 정렬 순서는
전체 결과 정렬에 바로 활용되지 못했습니다.

그래서 별도 Sort 단계가 발생했습니다.

반대로 다음 인덱스도 비교했습니다.

```sql
CREATE INDEX idx_products_category_created_at_price
ON products (category, created_at DESC, price);
```

동일 조건으로 실행한 결과:

* category 구간 탐색
* created_at DESC 순서 활용
* price 조건은 인덱스 조건으로 검사
* 별도 Sort 없음
* 20건 반환 후 종료
* 실행 시간: 약 0.631ms

최신순 LIMIT 응답이 중요한 검색이라면
(category, created_at DESC, price)가 유리할 수 있습니다.

반면 가격 범위로 후보군을 강하게 줄이는 것이 중요하다면
(category, price, created_at DESC)가 더 적합할 수 있습니다.

즉, 범위 조건과 정렬 조건이 함께 있을 때는
필터링 우선인지, 최신순 응답 우선인지에 따라
인덱스 설계가 달라집니다.

## 11. 낮은 카디널리티 컬럼 실험

status 컬럼에 인덱스를 생성했습니다.

```sql
CREATE INDEX idx_products_status
ON products (status);
```

status 값은 다음 세 가지입니다.

* ON_SALE
* SOLD_OUT
* HIDDEN

값의 종류가 적기 때문에
status는 낮은 카디널리티 컬럼입니다.

### 11.1 ON_SALE 조회

```sql
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM products
WHERE status = 'ON_SALE';
```

결과:

* Covering index lookup
* idx_products_status 사용
* 약 80,095건 조회
* 실행 시간: 약 22.2ms

ON_SALE은 전체 데이터의 약 80%를 차지했습니다.

인덱스를 사용하더라도
읽어야 할 데이터가 많기 때문에
효율이 낮았습니다.

### 11.2 HIDDEN 조회

```sql
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM products
WHERE status = 'HIDDEN';
```

결과:

* Covering index lookup
* idx_products_status 사용
* 약 4,925건 조회
* 실행 시간: 약 0.779ms

HIDDEN은 전체 데이터의 약 5%만 차지했습니다.

같은 status 인덱스를 사용했지만,
조회 대상이 적어 훨씬 빠르게 처리됐습니다.

낮은 카디널리티 컬럼이라고 해서
인덱스가 항상 의미 없는 것은 아닙니다.

중요한 것은 선택도입니다.

결과 건수가 많은 조건은
인덱스를 사용하더라도 효율이 낮을 수 있습니다.

반면 결과 건수가 적은 조건은
낮은 카디널리티 컬럼에서도
인덱스 효과가 있을 수 있습니다.

## 12. 검색 API 구현

JdbcTemplate 기반 상품 검색 API를 구현했습니다.

API는 category 조건을 필수로 받고,
선택적으로 price 범위 조건을 받습니다.

기본 정렬은 created_at DESC 최신순이며,
limit 기반 조회로 제한했습니다.

### 12.1 검색 API

```http
GET /api/products/search
```

요청 파라미터:

* category: 필수
* minPrice: 선택
* maxPrice: 선택
* limit: 선택, 최대 100개 제한

예시:

```http
GET /api/products/search?category=ELECTRONICS
```

```http
GET /api/products/search?category=ELECTRONICS
&minPrice=100000
&maxPrice=200000
```

조건 없는 전체 검색과 무제한 조회는
DB 부하를 키울 수 있으므로 허용하지 않았습니다.

검색 API는 다음 기준으로 제한했습니다.

* category 필수
* 최신순 정렬 고정
* limit 제한
* minPrice와 maxPrice는 함께 입력
* 잘못된 파라미터는 400 응답

### 12.2 전역 예외 처리

minPrice 또는 maxPrice를 단독으로 입력하면
IllegalArgumentException이 발생합니다.

초기에는 이 예외가 처리되지 않아
500 Internal Server Error로 반환됐습니다.

이를 해결하기 위해
@RestControllerAdvice 기반
GlobalExceptionHandler를 추가했습니다.

IllegalArgumentException 발생 시
400 BAD_REQUEST와 ErrorResponse를 반환하도록 처리했습니다.

이를 통해 클라이언트 입력 오류와
서버 내부 오류를 구분했습니다.

## 13. 잘못 만든 인덱스 사례

이번 실험을 통해
인덱스를 많이 만든다고 항상 좋은 것은 아니라는 점을 확인했습니다.

### 13.1 낮은 선택도 컬럼 단독 인덱스

status는 값 종류가 적은 컬럼입니다.

특히 ON_SALE처럼 전체의 약 80%를 차지하는 값은
인덱스를 사용해도 많은 데이터를 읽어야 합니다.

따라서 status 단독 인덱스는
값 분포와 선택도를 확인한 뒤 적용해야 합니다.

### 13.2 복합 인덱스 컬럼 순서 오류

(created_at DESC, category) 인덱스는
최신순 정렬에는 유리했지만,
category 조건으로 탐색 범위를 먼저 줄이지 못했습니다.

category 검색이 핵심인 쿼리라면
(category, created_at DESC) 순서가 더 적합합니다.

### 13.3 범위 조건 뒤 정렬 컬럼 배치

(category, price, created_at DESC) 인덱스는
price 범위 조건에는 유리했습니다.

하지만 price가 범위 조건으로 사용되면서
created_at DESC 정렬에는 별도 Sort가 발생했습니다.

복합 인덱스에서 범위 조건 뒤에 있는 컬럼은
정렬 최적화에 제한이 생길 수 있습니다.

### 13.4 중복 인덱스

실험 과정에서는 비교를 위해
여러 인덱스를 생성했습니다.

하지만 실제 운영 환경에서는
복합 인덱스가 단일 인덱스 역할을
대체할 수 있는지 확인해야 합니다.

불필요한 중복 인덱스는
저장 공간, 쓰기 비용, 운영 복잡도를 증가시킵니다.

## 14. 핵심 결론

이번 프로젝트를 통해 다음을 확인했습니다.

* 인덱스가 없으면 Full Scan과 Sort가 발생할 수 있습니다.
* 단일 인덱스는 WHERE 조건 최적화에는 효과가 있습니다.
* 단일 인덱스만으로 ORDER BY까지 해결하지 못할 수 있습니다.
* 복합 인덱스는 컬럼 구성뿐 아니라 순서가 중요합니다.
* WHERE 동등 조건, 범위 조건, ORDER BY, LIMIT을 함께 봐야 합니다.
* LIKE 'keyword%'는 B-Tree 인덱스를 활용할 수 있습니다.
* LIKE '%keyword%'는 일반 B-Tree 인덱스로 최적화하기 어렵습니다.
* 낮은 카디널리티 컬럼도 선택도에 따라 효과가 달라집니다.
* 불필요한 인덱스는 저장 공간과 쓰기 비용을 증가시킵니다.
* 검색 API는 실제 사용자 검색 패턴을 기준으로 제한해야 합니다.

## 15. 기술적 의사결정 정리

이번 프로젝트에서는 검색 API의 성능을
단순 응답 시간만으로 판단하지 않고,
EXPLAIN ANALYZE 결과를 기준으로 분석했습니다.

인덱스 설계 시에는 다음 기준을 함께 고려했습니다.

- WHERE 조건의 동등 비교 컬럼
- 범위 조건 여부
- ORDER BY 정렬 조건
- LIMIT 사용 여부
- 컬럼 카디널리티
- 실제 조건의 선택도
- 중복 인덱스 여부
- 쓰기 성능과 저장 공간 비용

결론적으로 인덱스는
자주 사용하는 컬럼에 단순히 추가하는 것이 아니라,
실제 검색 패턴과 데이터 분포를 기준으로
설계해야 한다고 판단했습니다.

## 16. 정리

이 프로젝트를 통해
검색 성능 최적화는 단순히 인덱스를 추가하는 작업이 아니라,
실행 계획을 확인하고
검색 조건별 병목을 검증하는 과정이라는 점을 확인했습니다.

특히 단일 인덱스, 복합 인덱스,
컬럼 순서, LIKE 패턴, 범위 조건,
카디널리티와 선택도에 따라
실행 방식이 크게 달라졌습니다.

운영 환경에서는
무제한 검색 조건을 열기보다
사용자 검색 패턴을 기준으로 API 조건을 제한하고,
EXPLAIN ANALYZE를 통해 인덱스 효과를 검증하는 방식이
필요하다고 판단했습니다.