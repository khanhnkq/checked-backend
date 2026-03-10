package com.codegym.locketclone.friendship;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;


@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "friendships")
@IdClass(FriendshipId.class)
@Builder
public class Friendship {
    @Id
    @Column(name = "user_id_1")
    private UUID userId1;
    @Id
    @Column(name = "user_id_2")
    private UUID userId2;

    private String status;

}

