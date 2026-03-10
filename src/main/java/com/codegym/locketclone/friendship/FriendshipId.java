package com.codegym.locketclone.friendship;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipId implements Serializable {
    private UUID userId1;
    private UUID userId2;
}
