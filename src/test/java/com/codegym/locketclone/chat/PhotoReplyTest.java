package com.codegym.locketclone.chat;

import com.codegym.locketclone.chat.document.Conversation;
import com.codegym.locketclone.chat.document.Message;
import com.codegym.locketclone.chat.document.MessageType;
import com.codegym.locketclone.chat.dto.request.PhotoReplyRequest;
import com.codegym.locketclone.chat.dto.response.MessageResponse;
import com.codegym.locketclone.chat.repository.ConversationRepository;
import com.codegym.locketclone.chat.repository.MessageRepository;
import com.codegym.locketclone.chat.service.impl.ChatServiceImpl;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.friendship.Friendship;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.PhotoRepository;
import com.codegym.locketclone.photo.PhotoStatus;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoReplyTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatServiceImpl chatService;

    private UUID currentUserId;
    private UUID photoOwnerId;
    private UUID photoId;
    private User photoOwner;
    private Photo photo;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(
                conversationRepository,
                messageRepository,
                userRepository,
                friendshipRepository,
                photoRepository,
                mongoTemplate,
                messagingTemplate
        );

        currentUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        photoOwnerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        photoId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        photoOwner = User.builder()
                .id(photoOwnerId)
                .username("khanh_owner")
                .firstName("Khanh")
                .lastName("Nguyen")
                .avatarUrl("https://example.com/avatar.jpg")
                .build();

        photo = Photo.builder()
                .id(photoId)
                .sender(photoOwner)
                .imageUrl("https://example.com/photo.jpg")
                .thumbnailUrl("https://example.com/thumb.jpg")
                .build();
    }

    @Test
    @DisplayName("replyToPhoto - Ném ngoại lệ khi ảnh không tồn tại hoặc không thể truy cập")
    void replyToPhoto_whenNotAccessible_throwsException() {
        when(photoRepository.findAccessiblePhotoById(photoId, currentUserId, PhotoStatus.DELETED))
                .thenReturn(Optional.empty());

        PhotoReplyRequest request = PhotoReplyRequest.builder()
                .content("Đẹp quá!")
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> chatService.replyToPhoto(currentUserId, photoId, request));

        assertEquals(ErrorCode.PHOTO_NOT_ACCESSIBLE, ex.getErrorCode());
        verifyNoInteractions(conversationRepository);
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("replyToPhoto - Ném ngoại lệ khi tự phản hồi ảnh của chính mình")
    void replyToPhoto_whenOwnerIsSelf_throwsException() {
        Photo myPhoto = Photo.builder()
                .id(photoId)
                .sender(User.builder().id(currentUserId).build())
                .build();

        when(photoRepository.findAccessiblePhotoById(photoId, currentUserId, PhotoStatus.DELETED))
                .thenReturn(Optional.of(myPhoto));

        PhotoReplyRequest request = PhotoReplyRequest.builder()
                .content("Ảnh của mình")
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> chatService.replyToPhoto(currentUserId, photoId, request));

        assertEquals(ErrorCode.CANNOT_CHAT_WITH_SELF, ex.getErrorCode());
        verifyNoInteractions(conversationRepository);
    }

    @Test
    @DisplayName("replyToPhoto - Phản hồi ảnh thành công, tạo message PHOTO_REPLY và dispatch STOMP")
    void replyToPhoto_success() {
        when(photoRepository.findAccessiblePhotoById(photoId, currentUserId, PhotoStatus.DELETED))
                .thenReturn(Optional.of(photo));

        when(userRepository.findById(photoOwnerId)).thenReturn(Optional.of(photoOwner));
        when(friendshipRepository.findAcceptedBetweenUsers(currentUserId, photoOwnerId))
                .thenReturn(Optional.of(new Friendship()));

        Conversation conv = Conversation.builder()
                .id("conv-123")
                .participantIds(List.of(currentUserId, photoOwnerId))
                .readStatus(new HashMap<>())
                .build();

        when(conversationRepository.findByPairKey(any())).thenReturn(Optional.of(conv));
        when(conversationRepository.findById("conv-123")).thenReturn(Optional.of(conv));

        Message savedMsg = Message.builder()
                .id("msg-999")
                .conversationId("conv-123")
                .senderId(currentUserId)
                .content("Món này ngon quá!")
                .type(MessageType.PHOTO_REPLY)
                .readBy(new HashSet<>(List.of(currentUserId)))
                .build();

        when(messageRepository.save(any(Message.class))).thenReturn(savedMsg);

        PhotoReplyRequest request = PhotoReplyRequest.builder()
                .content("Món này ngon quá!")
                .build();

        MessageResponse response = chatService.replyToPhoto(currentUserId, photoId, request);

        assertNotNull(response);
        assertEquals("msg-999", response.getId());
        assertEquals(MessageType.PHOTO_REPLY, response.getType());

        // Kiểm tra message được lưu có context snapshot đúng
        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(msgCaptor.capture());
        Message captured = msgCaptor.getValue();
        assertNotNull(captured.getPhotoContext());
        assertEquals(photoId, captured.getPhotoContext().getPhotoId());
        assertEquals("https://example.com/thumb.jpg", captured.getPhotoContext().getImageUrl());
        assertEquals("Khanh Nguyen", captured.getPhotoContext().getSenderName());

        // Kiểm tra dispatch tới photo owner và sender
        verify(messagingTemplate).convertAndSendToUser(eq(photoOwnerId.toString()), eq("/queue/messages"), any(MessageResponse.class));
        verify(messagingTemplate).convertAndSendToUser(eq(currentUserId.toString()), eq("/queue/messages"), any(MessageResponse.class));
    }
}
