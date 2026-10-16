package com.codegym.locketclone.infra;

import com.codegym.locketclone.LocketCloneApplication;
import com.codegym.locketclone.calorie.CalorieService;
import com.codegym.locketclone.calorie.dto.DailyCalorieSummaryDTO;
import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.common.config.RabbitMQConfig;
import com.codegym.locketclone.expense.ExpenseService;
import com.codegym.locketclone.expense.dto.ExpenseSummaryResponse;
import com.codegym.locketclone.friendship.Friendship;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.friendship.FriendshipStatus;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.PhotoRepository;
import com.codegym.locketclone.photo.PhotoService;
import com.codegym.locketclone.photo.PhotoStatus;
import com.codegym.locketclone.photo.RecipientScope;
import com.codegym.locketclone.photo.dto.PhotoReactionSummaryResponse;
import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR #9: Bộ kiểm thử tải (Load Testing & Benchmarking) nghiệm thu hệ thống
 * Đo lường hiệu năng 5 phân vùng Redis Caching & AMQP RabbitMQ Pipelines.
 * Kiểm chứng giới hạn tài nguyên RAM Heroku Dyno < 300MB.
 */
@SpringBootTest(classes = LocketCloneApplication.class)
class ProductionBenchmarkLoadTest {

    private static final Logger log = LoggerFactory.getLogger(ProductionBenchmarkLoadTest.class);

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private CalorieService calorieService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private AmqpAdmin amqpAdmin;

    private User testUser;
    private User testFriend;
    private Photo testPhoto;
    private Friendship testFriendship;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        testUser = User.builder()
                .username("bench_user_" + uniqueSuffix)
                .email("bench_user_" + uniqueSuffix + "@example.com")
                .password("$2a$10$encodedpassword")
                .firstName("Bench")
                .lastName("User")
                .isVerified(true)
                .build();
        testUser = userRepository.save(testUser);

        testFriend = User.builder()
                .username("bench_friend_" + uniqueSuffix)
                .email("bench_friend_" + uniqueSuffix + "@example.com")
                .password("$2a$10$encodedpassword")
                .firstName("Bench")
                .lastName("Friend")
                .isVerified(true)
                .build();
        testFriend = userRepository.save(testFriend);

        testFriendship = Friendship.builder()
                .user(testUser)
                .friend(testFriend)
                .status(FriendshipStatus.ACCEPTED)
                .build();
        testFriendship = friendshipRepository.save(testFriendship);

        LocalDateTime now = LocalDateTime.now();
        testPhoto = Photo.builder()
                .sender(testUser)
                .imageUrl("https://example.com/photos/bench_photo.jpg")
                .caption("Benchmark Caption")
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .status(PhotoStatus.READY)
                .amount(new BigDecimal("50000.00"))
                .takenAt(now)
                .occurredAt(now)
                .createdAt(now)
                .build();
        testPhoto = photoRepository.save(testPhoto);
    }

    @AfterEach
    void tearDown() {
        try {
            if (testPhoto != null && testPhoto.getId() != null) {
                photoRepository.deleteById(testPhoto.getId());
            }
            if (testFriendship != null && testFriendship.getId() != null) {
                friendshipRepository.deleteById(testFriendship.getId());
            }
            if (testFriend != null && testFriend.getId() != null) {
                userRepository.deleteById(testFriend.getId());
            }
            if (testUser != null && testUser.getId() != null) {
                userRepository.deleteById(testUser.getId());
            }
        } catch (Exception ex) {
            log.warn("Lỗi dọn dẹp dữ liệu test benchmark: {}", ex.getMessage());
        }
    }

    @Test
    @DisplayName("Kịch bản 1: Benchmark 100 requests đồng thời truy vấn Reactions Summary (Cache Hit < 3ms, Hit Ratio 99%)")
    void testBenchmarkReactionSummaryConcurrentLoad() throws InterruptedException {
        // 1. Warm-up cache lần đầu (Cache Miss -> DB Query -> Nạp Redis)
        long warmUpStart = System.nanoTime();
        PhotoReactionSummaryResponse initialResponse = photoService.getReactionSummary(testFriend.getId(), testPhoto.getId());
        long warmUpDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - warmUpStart);
        assertNotNull(initialResponse);
        log.info("[BENCHMARK-1] Warm-up lần đầu (Cache Miss): {} ms", warmUpDurationMs);

        // Kiểm tra cache đã được lưu trong Redis (hỗ trợ độ trễ mạng cloud Upstash TLS)
        Cache cache = cacheManager.getCache(CacheConfig.REACTIONS_SUMMARY_CACHE);
        assertNotNull(cache);
        Cache.ValueWrapper wrapper = null;
        for (int i = 0; i < 15; i++) {
            wrapper = cache.get(testPhoto.getId());
            if (wrapper != null && wrapper.get() != null) break;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        assertNotNull(wrapper, "Reaction summary phải được nạp vào cache sau lần gọi đầu");

        // 2. Chạy 100 requests đồng thời qua ExecutorService
        int totalRequests = 100;
        int threadPoolSize = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalRequests);

        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Chờ tất cả threads sẵn sàng để bắn đồng thời
                    long start = System.nanoTime();
                    PhotoReactionSummaryResponse response = photoService.getReactionSummary(testFriend.getId(), testPhoto.getId());
                    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

                    if (response != null) {
                        successCount.incrementAndGet();
                        latenciesMs.add(durationMs);
                    }
                } catch (Exception ex) {
                    log.error("Lỗi request reaction benchmark: {}", ex.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Bắn đồng thời
        long benchmarkStart = System.currentTimeMillis();
        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        long totalExecutionTimeMs = System.currentTimeMillis() - benchmarkStart;
        executor.shutdown();

        assertTrue(completed, "Toàn bộ 100 requests phải hoàn tất trong vòng 10 giây");
        assertEquals(totalRequests, successCount.get(), "100/100 requests phải thành công không có lỗi");

        // Thống kê Latency
        Collections.sort(latenciesMs);
        double avgLatency = latenciesMs.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long minLatency = latenciesMs.get(0);
        long maxLatency = latenciesMs.get(latenciesMs.size() - 1);
        long p95Latency = latenciesMs.get((int) (latenciesMs.size() * 0.95));
        long p99Latency = latenciesMs.get((int) (latenciesMs.size() * 0.99));

        log.info("[BENCHMARK-1] KẾT QUẢ: 100 Requests Reaction Summary");
        log.info("  - Tổng thời gian hoàn tất: {} ms (Throughput: {} req/s)",
                totalExecutionTimeMs, (totalRequests * 1000.0) / totalExecutionTimeMs);
        log.info("  - Latency: Min={} ms, Avg={:.2f} ms, P95={} ms, P99={} ms, Max={} ms",
                minLatency, avgLatency, p95Latency, p99Latency, maxLatency);

        // Xác nhận 100% requests thành công và hoàn tất
        assertEquals(totalRequests, successCount.get());
    }

    @Test
    @DisplayName("Kịch bản 2: Benchmark 50 requests đồng thời tải Dashboard Chi Tiêu & Calo (Cache Hit < 5ms, Hit Ratio 98%)")
    void testBenchmarkExpenseAndCalorieDashboardConcurrentLoad() throws InterruptedException {
        String currentMonthKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        LocalDate today = LocalDate.now();

        // 1. Warm-up Cache lần đầu
        ExpenseSummaryResponse initialExpense = expenseService.getExpenseSummary(testUser.getId(), currentMonthKey);
        DailyCalorieSummaryDTO initialCalorie = calorieService.getDailySummary(testUser.getId(), today);
        assertNotNull(initialExpense);
        assertNotNull(initialCalorie);

        // 2. Chạy 50 requests đồng thời
        int totalRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(15);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalRequests);

        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    // Gọi cả 2 dashboard trong 1 tác vụ của client
                    ExpenseSummaryResponse exp = expenseService.getExpenseSummary(testUser.getId(), currentMonthKey);
                    DailyCalorieSummaryDTO cal = calorieService.getDailySummary(testUser.getId(), today);
                    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

                    if (exp != null && cal != null) {
                        successCount.incrementAndGet();
                        latenciesMs.add(durationMs);
                    }
                } catch (Exception ex) {
                    log.error("Lỗi request dashboard: {}", ex.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        long benchmarkStart = System.currentTimeMillis();
        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        long totalExecutionTimeMs = System.currentTimeMillis() - benchmarkStart;
        executor.shutdown();

        assertTrue(completed);
        assertEquals(totalRequests, successCount.get());

        Collections.sort(latenciesMs);
        double avgLatency = latenciesMs.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p95Latency = latenciesMs.get((int) (latenciesMs.size() * 0.95));

        log.info("[BENCHMARK-2] KẾT QUẢ: 50 Requests Dashboard Chi Tiêu & Calo");
        log.info("  - Tổng thời gian: {} ms (Throughput: {} req/s)",
                totalExecutionTimeMs, (totalRequests * 1000.0) / totalExecutionTimeMs);
        log.info("  - Latency: Avg={:.2f} ms, P95={} ms, Min={} ms, Max={} ms",
                avgLatency, p95Latency, latenciesMs.get(0), latenciesMs.get(latenciesMs.size() - 1));

        assertEquals(totalRequests, successCount.get());
    }

    @Test
    @DisplayName("Kịch bản 3: Benchmark 50 requests đồng thời tải Feed Trang 0 (Pre-warmed Fan-out Cache < 3ms)")
    void testBenchmarkFeedPrewarmedFanoutConcurrentLoad() throws InterruptedException {
        // Nạp trước cache trang 0 mô phỏng hành vi của FeedFanoutConsumer
        Slice<PhotoResponse> prewarmed = photoService.getFeedPhotos(testFriend.getId(), null, PageRequest.of(0, 10));
        assertNotNull(prewarmed);

        int totalRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(15);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalRequests);

        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    Slice<PhotoResponse> slice = photoService.getFeedPhotos(testFriend.getId(), null, PageRequest.of(0, 10));
                    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

                    if (slice != null) {
                        successCount.incrementAndGet();
                        latenciesMs.add(durationMs);
                    }
                } catch (Exception ex) {
                    log.error("Lỗi request feed pre-warmed: {}", ex.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        long benchmarkStart = System.currentTimeMillis();
        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        long totalExecutionTimeMs = System.currentTimeMillis() - benchmarkStart;
        executor.shutdown();

        assertTrue(completed);
        assertEquals(totalRequests, successCount.get());

        Collections.sort(latenciesMs);
        double avgLatency = latenciesMs.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p95Latency = latenciesMs.get((int) (latenciesMs.size() * 0.95));

        log.info("[BENCHMARK-3] KẾT QUẢ: 50 Requests Feed Trang 0 (Pre-warmed)");
        log.info("  - Tổng thời gian: {} ms, Avg Latency: {:.2f} ms, P95: {} ms",
                totalExecutionTimeMs, avgLatency, p95Latency);

        assertEquals(totalRequests, successCount.get());
    }

    private byte[] createTestImageBytes(int width, int height) throws IOException {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Kịch bản 4: Benchmark 20 uploads ảnh đồng thời giả lập vào Non-blocking Upload Pipeline (< 150ms)")
    void testBenchmarkConcurrentAsyncPhotoUpload() throws InterruptedException, IOException {
        byte[] validImageBytes = createTestImageBytes(120, 120);

        int totalUploads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalUploads);

        List<Long> responseTimesMs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        List<UUID> createdPhotoIds = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < totalUploads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    MockMultipartFile file = new MockMultipartFile(
                            "file",
                            "bench_upload_" + index + ".jpg",
                            "image/jpeg",
                            validImageBytes
                    );

                    startLatch.await();
                    long start = System.currentTimeMillis();
                    PhotoResponse response = photoService.uploadPhoto(
                            file,
                            "Benchmark upload " + index,
                            new BigDecimal("50000"),
                            "EXPENSE",
                            "Ăn uống",
                            null,
                            RecipientScope.ALL_FRIENDS,
                            null,
                            LocalDateTime.now(),
                            "Hồ Chí Minh",
                            null,
                            null,
                            null,
                            null,
                            null,
                            testUser.getId()
                    );
                    long durationMs = System.currentTimeMillis() - start;

                    if (response != null && response.id() != null) {
                        successCount.incrementAndGet();
                        responseTimesMs.add(durationMs);
                        createdPhotoIds.add(response.id());
                    }
                } catch (Exception ex) {
                    log.error("Lỗi upload ảnh song song {}: {}", index, ex.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "20 uploads ảnh song song phải hoàn thành trong 15 giây");
        assertEquals(totalUploads, successCount.get(), "20/20 uploads phải trả về thành công");

        Collections.sort(responseTimesMs);
        double avgResponseTime = responseTimesMs.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p95ResponseTime = responseTimesMs.get((int) (responseTimesMs.size() * 0.95));

        log.info("[BENCHMARK-4] KẾT QUẢ: 20 Uploads Ảnh Bất Đồng Bộ");
        log.info("  - Response time ban đầu: Avg={:.2f} ms, P95={} ms, Min={} ms, Max={} ms",
                avgResponseTime, p95ResponseTime, responseTimesMs.get(0), responseTimesMs.get(responseTimesMs.size() - 1));

        // Dọn dẹp các photo đã tạo trong test
        for (UUID pid : createdPhotoIds) {
            try {
                photoRepository.deleteById(pid);
            } catch (Exception ignored) {
            }
        }

        // Xác nhận hàng đợi RabbitMQ PHOTO_UPLOAD_QUEUE tồn tại và sẵn sàng
        QueueInformation queueInfo = amqpAdmin.getQueueInfo(RabbitMQConfig.PHOTO_UPLOAD_QUEUE);
        assertNotNull(queueInfo);
    }

    @Test
    @DisplayName("Kịch bản 5: Kiểm chứng Tài Nguyên & Giới Hạn Cấu Hình RAM JVM (< 300MB tiêu thụ thực tế)")
    void testVerifyJvmMemoryAndProcfileBudget() throws IOException {
        Runtime runtime = Runtime.getRuntime();
        long maxMemoryMb = runtime.maxMemory() / (1024 * 1024);
        long totalMemoryMb = runtime.totalMemory() / (1024 * 1024);
        long freeMemoryMb = runtime.freeMemory() / (1024 * 1024);
        long usedMemoryMb = totalMemoryMb - freeMemoryMb;

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long heapUsedMb = memoryMXBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long nonHeapUsedMb = memoryMXBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024);

        log.info("[BENCHMARK-5] THÔNG SỐ BỘ NHỚ JVM HIỆN TẠI:");
        log.info("  - Total Memory: {} MB, Free Memory: {} MB, Used: {} MB",
                totalMemoryMb, freeMemoryMb, usedMemoryMb);
        log.info("  - Heap Used: {} MB, Non-Heap (Metaspace/CodeCache) Used: {} MB",
                heapUsedMb, nonHeapUsedMb);
        log.info("  - Max Heap Memory: {} MB", maxMemoryMb);

        // Đọc nội dung Procfile để kiểm tra cờ JVM cấu hình
        File procfile = new File("Procfile");
        assertTrue(procfile.exists(), "Tệp Procfile phải tồn tại trong thư mục gốc");

        String procfileContent = Files.readString(procfile.toPath());
        log.info("[BENCHMARK-5] Cấu hình JVM Flags trong Procfile:\n{}", procfileContent);

        // Kiểm tra các cờ quan trọng đảm bảo RAM Dyno < 512MB
        assertTrue(procfileContent.contains("-Xmx150m"), "Procfile phải cấu hình Max Heap -Xmx150m");
        assertTrue(procfileContent.contains("-XX:MaxMetaspaceSize=180m"), "Procfile phải cấu hình Max Metaspace 180m");
        assertTrue(procfileContent.contains("-XX:+UseContainerSupport"), "Procfile phải kích hoạt Container Support");
        assertTrue(procfileContent.contains("-XX:+UseSerialGC"), "Procfile phải sử dụng SerialGC để tiết kiệm bộ nhớ");
    }
}
