CREATE TABLE IF NOT EXISTS friend_invite_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(128) NOT NULL UNIQUE,
    max_uses INTEGER NOT NULL DEFAULT 50,
    used_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_friend_invite_links_max_uses_positive CHECK (max_uses > 0),
    CONSTRAINT ck_friend_invite_links_used_count_non_negative CHECK (used_count >= 0),
    CONSTRAINT ck_friend_invite_links_usage_valid CHECK (used_count <= max_uses)
);

CREATE INDEX IF NOT EXISTS idx_friend_invite_links_owner_active
    ON friend_invite_links(owner_user_id, revoked_at, expires_at DESC);

CREATE INDEX IF NOT EXISTS idx_friend_invite_links_token
    ON friend_invite_links(token);

