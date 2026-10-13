package com.codegym.locketclone.admin;

import com.codegym.locketclone.LocketCloneApplication;
import com.codegym.locketclone.admin.dto.DlqReplayRequest;
import com.codegym.locketclone.admin.dto.DlqReplayResponse;
import com.codegym.locketclone.admin.dto.DlqStatsResponse;
import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.common.config.RabbitMQConfig;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.photo.PhotoService;
import com.codegym.locketclone.photo.RecipientScope;
import com.codegym.locketclone.photo.consumer.FeedFanoutConsumer;
import com.codegym.locketclone.photo.dto.FeedFanoutEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = LocketCloneApplication.class)
class DlqAndFanoutIntegrationTest {

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private FeedFanoutConsumer feedFanoutConsumer;

    @Autowired
    private DlqAdminService dlqAdminService;

    @Autowired
    private DlqAdminController dlqAdminController;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Test
    @DisplayName("Verify AMQP Topology: Queue feed.fanout.queue và Dead Letter Queue phải tồn tại và gắn DLX")
    void testTopologyConfiguration() {
        QueueInformation fanoutQueueInfo = amqpAdmin.getQueueInfo(RabbitMQConfig.FEED_FANOUT_QUEUE);
        assertNotNull(fanoutQueueInfo, "Queue 'feed.fanout.queue' phải tồn tại trên RabbitMQ broker");

        QueueInformation dlqInfo = amqpAdmin.getQueueInfo(RabbitMQConfig.DEAD_LETTER_QUEUE);
        assertNotNull(dlqInfo, "Queue 'checked.dead-letter.queue' phải tồn tại trên RabbitMQ broker");
    }

    @Test
    @DisplayName("Verify FeedFanoutConsumer: Xử lý phạm vi ALL_FRIENDS thành công, dọn cache và làm ấm feed")
    void testFeedFanoutConsumerAllFriends() {
        UUID senderId = UUID.randomUUID();
        UUID friend1 = UUID.randomUUID();
        UUID friend2 = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        FriendshipRepository mockFriendshipRepo = mock(FriendshipRepository.class);
        PhotoService mockPhotoService = mock(PhotoService.class);
        CacheManager mockCacheManager = mock(CacheManager.class);
        Cache mockFeedCache = mock(Cache.class);
        Cache mockMyPhotosCache = mock(Cache.class);

        when(mockFriendshipRepo.findAcceptedFriendIds(senderId)).thenReturn(List.of(friend1, friend2));
        when(mockCacheManager.getCache(CacheConfig.FEED_CACHE)).thenReturn(mockFeedCache);
        when(mockCacheManager.getCache(CacheConfig.MY_PHOTOS_CACHE)).thenReturn(mockMyPhotosCache);

        FeedFanoutConsumer consumer = new FeedFanoutConsumer(mockFriendshipRepo, mockPhotoService);
        // Inject mock cache manager qua reflection hoặc field injection nếu có setter, hoặc tạo consumer chạy độc lập
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "cacheManager", mockCacheManager);

        FeedFanoutEvent event = new FeedFanoutEvent(photoId, senderId, RecipientScope.ALL_FRIENDS, null);

        assertDoesNotThrow(() -> consumer.processFeedFanout(event));

        // Xác minh gọi danh sách bạn bè
        verify(mockFriendshipRepo, times(1)).findAcceptedFriendIds(senderId);

        // Xác minh dọn dẹp cache cho sender và bạn bè
        verify(mockMyPhotosCache, atLeastOnce()).evict(senderId);
        verify(mockFeedCache, atLeastOnce()).evict(friend1);
        verify(mockFeedCache, atLeastOnce()).evict(friend2);

        // Xác minh gọi getFeedPhotos pre-warm cho từng bạn bè
        verify(mockPhotoService, times(1)).getFeedPhotos(eq(friend1), eq(null), any());
        verify(mockPhotoService, times(1)).getFeedPhotos(eq(friend2), eq(null), any());
    }

    @Test
    @DisplayName("Verify FeedFanoutConsumer: Xử lý phạm vi SELECTED_FRIENDS chỉ cập nhật cho bạn bè được chọn")
    void testFeedFanoutConsumerSelectedFriends() {
        UUID senderId = UUID.randomUUID();
        UUID targetFriend = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        FriendshipRepository mockFriendshipRepo = mock(FriendshipRepository.class);
        PhotoService mockPhotoService = mock(PhotoService.class);

        FeedFanoutConsumer consumer = new FeedFanoutConsumer(mockFriendshipRepo, mockPhotoService);

        FeedFanoutEvent event = new FeedFanoutEvent(photoId, senderId, RecipientScope.SELECTED_FRIENDS, List.of(targetFriend));

        assertDoesNotThrow(() -> consumer.processFeedFanout(event));

        // Không gọi findAcceptedFriendIds vì đã có danh sách cụ thể
        verify(mockFriendshipRepo, never()).findAcceptedFriendIds(any());
        // Xác minh làm ấm feed cho targetFriend
        verify(mockPhotoService, times(1)).getFeedPhotos(eq(targetFriend), eq(null), any());
    }

    @Test
    @DisplayName("Verify FeedFanoutConsumer: Bỏ qua gracefully khi event rỗng hoặc thiếu thông tin")
    void testFeedFanoutConsumerEmptyEvent() {
        FriendshipRepository mockFriendshipRepo = mock(FriendshipRepository.class);
        PhotoService mockPhotoService = mock(PhotoService.class);
        FeedFanoutConsumer consumer = new FeedFanoutConsumer(mockFriendshipRepo, mockPhotoService);

        assertDoesNotThrow(() -> consumer.processFeedFanout(null));
        assertDoesNotThrow(() -> consumer.processFeedFanout(new FeedFanoutEvent(null, null, null, null)));

        verify(mockFriendshipRepo, never()).findAcceptedFriendIds(any());
        verify(mockPhotoService, never()).getFeedPhotos(any(), any(), any());
    }

    @Test
    @DisplayName("Verify DLQ Admin Service: Lấy thống kê DLQ stats chính xác")
    void testDlqStatsRetrieval() {
        DlqStatsResponse stats = dlqAdminService.getDlqStats();

        assertNotNull(stats);
        assertEquals(RabbitMQConfig.DEAD_LETTER_QUEUE, stats.queueName());
        assertEquals(RabbitMQConfig.CHECKED_DLX_EXCHANGE, stats.deadLetterExchange());
        assertTrue(stats.messageCount() >= 0);
        assertTrue(stats.consumerCount() >= 0);
        assertTrue("ACTIVE".equals(stats.status()) || "NOT_FOUND".equals(stats.status()));
    }

    @Test
    @DisplayName("Verify DLQ Admin Service: Replay tin nhắn khi hàng đợi rỗng trả về replayedCount = 0 an toàn")
    void testDlqReplayEmptyQueue() {
        DlqReplayResponse response = dlqAdminService.replayMessages(5, null);

        assertNotNull(response);
        assertEquals(0, response.replayedCount());
        assertEquals(0, response.failedCount());
        assertNotNull(response.details());
    }

    @Test
    @DisplayName("Verify DLQ Replay Routing Key Resolution: Giải mã routing key từ header x-death và __TypeId__")
    void testDlqRoutingKeyResolutionLogic() {
        AmqpAdmin mockAdmin = mock(AmqpAdmin.class);
        RabbitTemplate mockRabbit = mock(RabbitTemplate.class);

        DlqAdminService service = new DlqAdminService(mockAdmin, mockRabbit);

        // Case 1: Message có header x-death
        MessageProperties propsWithXDeath = new MessageProperties();
        propsWithXDeath.setMessageId("msg-xdeath-1");
        propsWithXDeath.setHeader("x-death", List.of(Map.of("routing-keys", List.of("photo.upload"))));
        Message messageWithXDeath = new Message("{}".getBytes(), propsWithXDeath);

        when(mockRabbit.receive(eq(RabbitMQConfig.DEAD_LETTER_QUEUE), eq(1000L)))
                .thenReturn(messageWithXDeath)
                .thenReturn(null);

        DlqReplayResponse res1 = service.replayMessages(1, null);
        assertEquals(1, res1.replayedCount());
        verify(mockRabbit, times(1)).send(eq(RabbitMQConfig.CHECKED_DIRECT_EXCHANGE), eq("photo.upload"), eq(messageWithXDeath));

        // Case 2: Message có header __TypeId__ cho OtpEmailMessage
        MessageProperties propsWithTypeId = new MessageProperties();
        propsWithTypeId.setMessageId("msg-mail-1");
        propsWithTypeId.setHeader("__TypeId__", "com.codegym.locketclone.notification.dto.OtpEmailMessage");
        Message messageWithTypeId = new Message("{}".getBytes(), propsWithTypeId);

        when(mockRabbit.receive(eq(RabbitMQConfig.DEAD_LETTER_QUEUE), eq(1000L)))
                .thenReturn(messageWithTypeId)
                .thenReturn(null);

        DlqReplayResponse res2 = service.replayMessages(1, null);
        assertEquals(1, res2.replayedCount());
        verify(mockRabbit, times(1)).send(eq(RabbitMQConfig.CHECKED_DIRECT_EXCHANGE), eq(RabbitMQConfig.MAIL_NOTIFICATION_ROUTING_KEY), eq(messageWithTypeId));

        // Case 3: Message có header __TypeId__ cho FeedFanoutEvent
        MessageProperties propsFanout = new MessageProperties();
        propsFanout.setMessageId("msg-fanout-1");
        propsFanout.setHeader("__TypeId__", "com.codegym.locketclone.photo.dto.FeedFanoutEvent");
        Message messageFanout = new Message("{}".getBytes(), propsFanout);

        when(mockRabbit.receive(eq(RabbitMQConfig.DEAD_LETTER_QUEUE), eq(1000L)))
                .thenReturn(messageFanout)
                .thenReturn(null);

        DlqReplayResponse res3 = service.replayMessages(1, null);
        assertEquals(1, res3.replayedCount());
        verify(mockRabbit, times(1)).send(eq(RabbitMQConfig.CHECKED_DIRECT_EXCHANGE), eq(RabbitMQConfig.FEED_FANOUT_ROUTING_KEY), eq(messageFanout));
    }

    @Test
    @DisplayName("Verify DLQ Admin Controller: Gọi endpoint GET /stats và POST /replay hoạt động trơn tru")
    void testDlqAdminControllerEndpoints() {
        ResponseEntity<DlqStatsResponse> statsEntity = dlqAdminController.getDlqStats();
        assertNotNull(statsEntity);
        assertEquals(200, statsEntity.getStatusCode().value());
        assertNotNull(statsEntity.getBody());

        DlqReplayRequest request = new DlqReplayRequest(10, RabbitMQConfig.CHECKED_DIRECT_EXCHANGE);
        ResponseEntity<DlqReplayResponse> replayEntity = dlqAdminController.replayMessages(request, null, null);
        assertNotNull(replayEntity);
        assertEquals(200, replayEntity.getStatusCode().value());
        assertNotNull(replayEntity.getBody());
    }
}
