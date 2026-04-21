package com.codegym.locketclone.friendship.invite;

import com.codegym.locketclone.friendship.invite.dto.CreateFriendInviteLinkRequest;
import com.codegym.locketclone.friendship.invite.dto.AcceptFriendInviteLinkResponse;
import com.codegym.locketclone.friendship.invite.dto.FriendInviteLinkResponse;

import java.util.UUID;

public interface FriendInviteLinkService {

    FriendInviteLinkResponse createOrRotate(UUID ownerUserId, CreateFriendInviteLinkRequest request);

    FriendInviteLinkResponse getCurrent(UUID ownerUserId);

    void revokeCurrent(UUID ownerUserId);

    AcceptFriendInviteLinkResponse acceptByToken(UUID currentUserId, String token);
}


