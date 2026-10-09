package com.codegym.locketclone.friendship.invite;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.friendship.Friendship;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.friendship.FriendshipStatus;
import com.codegym.locketclone.friendship.invite.dto.AcceptFriendInviteLinkResponse;
import com.codegym.locketclone.friendship.invite.dto.CreateFriendInviteLinkRequest;
import com.codegym.locketclone.friendship.invite.dto.FriendInviteLinkResponse;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendInviteLinkServiceImpl implements FriendInviteLinkService {

    private static final int DEFAULT_TTL_MINUTES = 7 * 24 * 60;
    private static final int MAX_USES = 50;

    private final FriendInviteLinkRepository friendInviteLinkRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.friend-invite.base-url:https://locket-clone.app/friend/accept}")
    private String inviteBaseUrl;

    @Override
    @Transactional
    public FriendInviteLinkResponse createOrRotate(UUID ownerUserId, CreateFriendInviteLinkRequest request) {
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        revokeActiveLinks(ownerUserId, now);

        int ttlMinutes = extractTtl(request);
        String token = generateUniqueToken();

        FriendInviteLink created = friendInviteLinkRepository.save(FriendInviteLink.builder()
                .owner(owner)
                .token(token)
                .maxUses(MAX_USES)
                .usedCount(0)
                .expiresAt(now.plusMinutes(ttlMinutes))
                .revokedAt(null)
                .build());

        return toResponse(created, now);
    }

    @Override
    @Transactional
    public FriendInviteLinkResponse getCurrent(UUID ownerUserId) {
        ensureUserExists(ownerUserId);

        LocalDateTime now = LocalDateTime.now();
        FriendInviteLink current = friendInviteLinkRepository
                .findFirstByOwner_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(ownerUserId, now)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_INVITE_LINK_NOT_FOUND));

        return toResponse(current, now);
    }

    @Override
    @Transactional
    public void revokeCurrent(UUID ownerUserId) {
        ensureUserExists(ownerUserId);

        LocalDateTime now = LocalDateTime.now();
        FriendInviteLink current = friendInviteLinkRepository
                .findFirstByOwner_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(ownerUserId, now)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_INVITE_LINK_NOT_FOUND));

        current.setRevokedAt(now);
        friendInviteLinkRepository.save(current);
    }

    @Override
    @Transactional
    public AcceptFriendInviteLinkResponse acceptByToken(UUID currentUserId, String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FRIEND_INVITE_TOKEN);
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String normalizedToken = token.trim();
        FriendInviteLink link = friendInviteLinkRepository.findByToken(normalizedToken)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_FRIEND_INVITE_TOKEN));

        validateAcceptableLink(link);

        UUID ownerUserId = link.getOwner().getId();
        if (ownerUserId.equals(currentUserId)) {
            throw new AppException(ErrorCode.CANNOT_ADD_SELF_AS_FRIEND);
        }

        // 1. Nếu đã là bạn bè, trả về ngay kết quả mà KHÔNG tăng usedCount của link (chống burn quota)
        Optional<Friendship> existingAccepted = friendshipRepository.findAcceptedBetweenUsers(ownerUserId, currentUserId);
        if (existingAccepted.isPresent()) {
            Friendship friendship = existingAccepted.get();
            return new AcceptFriendInviteLinkResponse(
                    friendship.getId(),
                    friendship.getStatus().name(),
                    userMapper.toFriendProfileResponse(link.getOwner()),
                    LocalDateTime.now()
            );
        }

        // 2. Chấp nhận hoặc tạo mới quan hệ bạn bè, bắt xung đột đồng thời (race condition)
        boolean alreadyFriends = false;
        Friendship friendship;
        try {
            friendship = acceptExistingOrCreate(ownerUserId, currentUserId, link.getOwner(), currentUser);
        } catch (DataIntegrityViolationException ex) {
            alreadyFriends = true;
            friendship = friendshipRepository.findAcceptedBetweenUsers(ownerUserId, currentUserId)
                    .or(() -> friendshipRepository.findBetweenUsers(ownerUserId, currentUserId).stream().findFirst())
                    .orElseThrow(() -> ex);
        }

        // 3. Chỉ tăng usedCount khi đây là lần kết bạn mới
        if (!alreadyFriends) {
            int currentUsedCount = link.getUsedCount() == null ? 0 : link.getUsedCount();
            link.setUsedCount(currentUsedCount + 1);
            friendInviteLinkRepository.save(link);
        }

        return new AcceptFriendInviteLinkResponse(
                friendship.getId(),
                friendship.getStatus().name(),
                userMapper.toFriendProfileResponse(link.getOwner()),
                LocalDateTime.now()
        );
    }

    private void ensureUserExists(UUID ownerUserId) {
        if (!userRepository.existsById(ownerUserId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void validateAcceptableLink(FriendInviteLink link) {
        LocalDateTime now = LocalDateTime.now();
        if (link.getRevokedAt() != null) {
            throw new AppException(ErrorCode.FRIEND_INVITE_LINK_REVOKED);
        }
        if (!link.getExpiresAt().isAfter(now)) {
            throw new AppException(ErrorCode.FRIEND_INVITE_LINK_EXPIRED);
        }
        int usedCount = link.getUsedCount() == null ? 0 : link.getUsedCount();
        int maxUses = link.getMaxUses() == null ? 0 : link.getMaxUses();
        if (usedCount >= maxUses) {
            throw new AppException(ErrorCode.FRIEND_INVITE_LINK_MAX_USES_REACHED);
        }
    }

    private Friendship acceptExistingOrCreate(UUID ownerUserId, UUID currentUserId, User ownerUser, User currentUser) {
        List<Friendship> betweenUsers = friendshipRepository.findBetweenUsers(ownerUserId, currentUserId);
        Friendship candidate = betweenUsers.stream()
                .max(Comparator.comparing(Friendship::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .orElse(null);

        if (candidate != null) {
            if (candidate.getStatus() == FriendshipStatus.ACCEPTED) {
                return candidate;
            }
            candidate.setStatus(FriendshipStatus.ACCEPTED);
            return friendshipRepository.save(candidate);
        }

        Friendship friendship = Friendship.builder()
                .user(ownerUser)
                .friend(currentUser)
                .status(FriendshipStatus.ACCEPTED)
                .build();
        return friendshipRepository.save(friendship);
    }

    private int extractTtl(CreateFriendInviteLinkRequest request) {
        if (request == null || request.ttlMinutes() == null) {
            return DEFAULT_TTL_MINUTES;
        }
        int ttlMinutes = request.ttlMinutes();
        if (ttlMinutes < 10 || ttlMinutes > 43200) {
            throw new AppException(ErrorCode.INVALID_FRIEND_INVITE_TTL);
        }
        return ttlMinutes;
    }

    private void revokeActiveLinks(UUID ownerUserId, LocalDateTime now) {
        List<FriendInviteLink> activeLinks = friendInviteLinkRepository
                .findByOwner_IdAndRevokedAtIsNullAndExpiresAtAfter(ownerUserId, now);
        if (activeLinks.isEmpty()) {
            return;
        }

        for (FriendInviteLink activeLink : activeLinks) {
            activeLink.setRevokedAt(now);
        }
        friendInviteLinkRepository.saveAll(activeLinks);
    }

    private String generateUniqueToken() {
        String token;
        do {
            byte[] bytes = new byte[24];
            secureRandom.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (friendInviteLinkRepository.existsByToken(token));

        return token;
    }

    private FriendInviteLinkResponse toResponse(FriendInviteLink link, LocalDateTime now) {
        return new FriendInviteLinkResponse(
                link.getId(),
                buildInviteUrl(link.getToken()),
                tokenPreview(link.getToken()),
                link.getMaxUses(),
                link.getUsedCount(),
                link.getExpiresAt(),
                link.getRevokedAt(),
                deriveStatus(link, now)
        );
    }

    private String buildInviteUrl(String token) {
        if (inviteBaseUrl.contains("?")) {
            return inviteBaseUrl + "&token=" + token;
        }
        return inviteBaseUrl + "?token=" + token;
    }

    private String tokenPreview(String token) {
        if (token == null || token.length() <= 10) {
            return token;
        }
        return token.substring(0, 10) + "...";
    }

    private String deriveStatus(FriendInviteLink link, LocalDateTime now) {
        if (link.getRevokedAt() != null) {
            return "REVOKED";
        }
        if (!link.getExpiresAt().isAfter(now)) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }
}





