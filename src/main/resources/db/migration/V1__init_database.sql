-- 1. Kích hoạt extension tự sinh UUID cho khóa chính
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. Bảng Users
CREATE TABLE users (
                       id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
                       phone_number VARCHAR(20) UNIQUE NOT NULL,
                       username VARCHAR(50) UNIQUE NOT NULL,
                       display_name VARCHAR(100) NOT NULL,
                       fcm_token VARCHAR(255),
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Bảng Friendships
CREATE TABLE friendships (
                             user_id_1 UUID REFERENCES users(id) ON DELETE CASCADE,
                             user_id_2 UUID REFERENCES users(id) ON DELETE CASCADE,
                             status VARCHAR(20) DEFAULT 'PENDING',
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             PRIMARY KEY (user_id_1, user_id_2),
                             CHECK (user_id_1 < user_id_2)
);

-- 4. Bảng Photos
CREATE TABLE photos (
                        id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
                        sender_id UUID REFERENCES users(id) ON DELETE CASCADE,
                        image_url VARCHAR(500) NOT NULL,
                        caption VARCHAR(50),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Bảng Photo Receivers
CREATE TABLE photo_receivers (
                                 photo_id UUID REFERENCES photos(id) ON DELETE CASCADE,
                                 receiver_id UUID REFERENCES users(id) ON DELETE CASCADE,
                                 is_viewed BOOLEAN DEFAULT FALSE,
                                 viewed_at TIMESTAMP,
                                 PRIMARY KEY (photo_id, receiver_id)
);