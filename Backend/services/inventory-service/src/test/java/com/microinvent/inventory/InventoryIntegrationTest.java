package com.microinvent.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.inventory.domain.ProductWarehouseSetting;
import com.microinvent.inventory.domain.StockBalance;
import com.microinvent.inventory.domain.StockHold;
import com.microinvent.inventory.domain.StockMovement;
import com.microinvent.inventory.domain.Warehouse;
import com.microinvent.inventory.repository.ProductWarehouseSettingRepository;
import com.microinvent.inventory.repository.StockBalanceRepository;
import com.microinvent.inventory.repository.StockMovementRepository;
import com.microinvent.inventory.repository.WarehouseRepository;
import com.microinvent.inventory.service.InternalStockService;
import com.microinvent.inventory.web.dto.StockReceiveRequest;
import com.microinvent.inventory.web.dto.StockReserveRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test trên MySQL 8.4 + Redis thật (Testcontainers): kịch bản đồng thời SRS 8.5#5 và các query
 * native / JPQL của repository. Máy không có Docker thì cả lớp được bỏ qua.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class InventoryIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withUsername("root").withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        // tenant-starter tạo database inventory_<tenant> và chạy Flyway trên đó
        r.add("microinvent.tenant.url-template", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/inventory_%s?createDatabaseIfNotExist=true");
        r.add("microinvent.tenant.username", () -> "root");
        r.add("microinvent.tenant.password", () -> "test");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("microinvent.internal.api-key", () -> "test-internal-key");
        r.add("eureka.client.enabled", () -> "false");
    }

    /** Mỗi test dùng productId riêng để không ảnh hưởng nhau. */
    private static final AtomicInteger PRODUCT = new AtomicInteger(1000);
    private static final AtomicInteger REF = new AtomicInteger(1);

    @Autowired
    InternalStockService stock;
    @Autowired
    WarehouseRepository warehouses;
    @Autowired
    StockBalanceRepository balances;
    @Autowired
    StockMovementRepository movements;
    @Autowired
    ProductWarehouseSettingRepository settings;

    private Integer warehouseId;

    @BeforeEach
    void setUp() {
        warehouseId = warehouse("IT" + REF.getAndIncrement(), Warehouse.Status.ACTIVE).getId();
    }

    @Test
    void scenario85_5_twoReservesOfSameSerialOnlyOneWins() throws Exception {
        int product = PRODUCT.getAndIncrement();
        receive(product, 1, List.of("SN-" + product));

        List<Object> results = runConcurrently(2, i -> () -> stock.reserve(reserve(product, 1, List.of("SN-" + product))));

        assertThat(results).filteredOn(r -> !(r instanceof BusinessException)).hasSize(1);
        // Bên thua chờ khóa dòng tồn rồi thấy hết hàng (STOCK_NOT_ENOUGH), hoặc thấy serial đã HOLD (CONFLICT) – đều 409.
        assertThat(results).filteredOn(r -> r instanceof BusinessException).singleElement()
                .extracting(r -> ((BusinessException) r).getCode())
                .isIn(ErrorCode.STOCK_NOT_ENOUGH, ErrorCode.CONFLICT);
        assertThat(balance(product).getQtyHold()).isEqualTo(1);
    }

    @Test
    void twentyReservesForFiveUnitsExactlyFiveWin() throws Exception {
        int product = PRODUCT.getAndIncrement();
        receive(product, 5, null);

        List<Object> results = runConcurrently(20, i -> () -> stock.reserve(reserve(product, 1, null)));

        assertThat(results).filteredOn(r -> !(r instanceof BusinessException)).hasSize(5);
        assertThat(results).filteredOn(r -> r instanceof BusinessException)
                .extracting(r -> ((BusinessException) r).getCode()).containsOnly(ErrorCode.STOCK_NOT_ENOUGH);
        StockBalance b = balance(product);
        assertThat(List.of(b.getQtyOnHand(), b.getQtyHold(), b.available())).containsExactly(5, 5, 0);
    }

    @Test
    void concurrentReceivesOfNewProductDoNotDeadlockOrLoseUpdates() throws Exception {
        int product = PRODUCT.getAndIncrement();

        List<Object> results = runConcurrently(20, i -> () -> stock.receive(new StockReceiveRequest(warehouseId,
                StockMovement.RefType.RECEIPT, REF.getAndIncrement(), 1,
                List.of(line(product, 1, null, BigDecimal.valueOf(1000L * (i + 1)))))));

        assertThat(results).noneMatch(r -> r instanceof Throwable);
        StockBalance b = balance(product);
        assertThat(b.getQtyOnHand()).isEqualTo(20);
        // Giá vốn bình quân làm tròn 2 chữ số sau mỗi lần nhập → lệch vài xu tùy thứ tự đến (DECIMAL(18,2)).
        assertThat(b.getAvgCost()).isCloseTo(new BigDecimal("10500.00"), org.assertj.core.data.Offset.offset(new BigDecimal("0.10")));
        assertThat(movements.search(false, Set.of(warehouseId), product, null, null, null, PageRequest.of(0, 50))
                .getTotalElements()).isEqualTo(20);
    }

    @Test
    void closeCheckAndLowStockQueriesRunOnRealSchema() {
        int product = PRODUCT.getAndIncrement();
        receive(product, 3, null);
        stock.reserve(reserve(product, 1, null));
        settings.save(new ProductWarehouseSetting(product, warehouseId, 10));

        assertThat(warehouses.sumOnHand(warehouseId)).isEqualTo(3);
        assertThat(warehouses.countActiveHolds(warehouseId)).isEqualTo(1);
        assertThat(warehouses.countOpenDocuments(warehouseId)).isZero();
        assertThat(warehouses.sumStock(List.of(warehouseId))).singleElement()
                .satisfies(t -> assertThat(t.getTotalQty()).isEqualTo(3));
        assertThat(balances.findBelowMin(false, Set.of(warehouseId))).singleElement()
                .satisfies(r -> assertThat(List.of(r.getProductId(), r.getAvailable(), r.getMinStock()))
                        .containsExactly(product, 2, 10));
        assertThat(warehouses.search(null, Warehouse.Status.ACTIVE, "it", false, Set.of(warehouseId),
                PageRequest.of(0, 10)).getContent()).extracting(Warehouse::getId).containsExactly(warehouseId);
    }

    /** Chạy n tác vụ cùng lúc (cùng chờ 1 hiệu lệnh); trả kết quả hoặc exception của từng tác vụ. */
    private static List<Object> runConcurrently(int n, IntFunction<Callable<Object>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<Object> c = task.apply(i);
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return c.call();
                } catch (Exception ex) {
                    return ex;
                }
            }));
        }
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    private Warehouse warehouse(String code, Warehouse.Status status) {
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName("Kho " + code);
        w.setType(Warehouse.Type.BRANCH);
        w.setAddress("Test");
        w.setStatus(status);
        return warehouses.save(w);
    }

    private void receive(int product, int qty, List<String> serials) {
        stock.receive(new StockReceiveRequest(warehouseId, StockMovement.RefType.RECEIPT, REF.getAndIncrement(), 1,
                List.of(line(product, qty, serials, BigDecimal.TEN))));
    }

    private StockReserveRequest reserve(int product, int qty, List<String> serials) {
        return new StockReserveRequest(warehouseId, StockHold.RefType.EXPORT_SLIP, REF.getAndIncrement(), 1, null,
                null, List.of(new StockReserveRequest.Line(product, qty, serials)));
    }

    private static StockReceiveRequest.Line line(int product, int qty, List<String> serials, BigDecimal cost) {
        return new StockReceiveRequest.Line(product, qty, serials, cost, null, null);
    }

    private StockBalance balance(int product) {
        return balances.findByProductIdAndWarehouseId(product, warehouseId).orElseThrow();
    }
}
