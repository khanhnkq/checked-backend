package com.codegym.locketclone.user.share;

import com.codegym.locketclone.friendship.invite.FriendInviteLink;
import com.codegym.locketclone.friendship.invite.FriendInviteLinkRepository;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Tag(name = "Social Share", description = "Public OpenGraph share page and deep-linking for user profiles and invite links")
public class ShareController {

    private final UserRepository userRepository;
    private final FriendInviteLinkRepository friendInviteLinkRepository;

    @Value("${app.profile-base-url:https://heroku-7usd-5a73e86b5b14.herokuapp.com/u/}")
    private String profileBaseUrl;

    @Value("${app.friend-invite.base-url:https://heroku-7usd-5a73e86b5b14.herokuapp.com/invite}")
    private String inviteBaseUrl;

    // MARK: - 1. Trang Web Hồ Sơ Cá Nhân (GET /u/{username})
    @Operation(summary = "Get OpenGraph social share page with deep link for user profile")
    @GetMapping(value = "/u/{username}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSocialSharePage(@PathVariable String username) {
        Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            String escapedUsername = HtmlUtils.htmlEscape(username);
            String notFoundHtml = buildNotFoundPage("Không tìm thấy người dùng @" + escapedUsername + " trên Checked.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundHtml);
        }

        User user = userOpt.get();
        String escapedDisplayName = HtmlUtils.htmlEscape(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
        String escapedUsername = HtmlUtils.htmlEscape(user.getUsername());
        String avatarUrl = resolveAvatarUrl(user.getAvatarUrl());

        String cleanProfileBase = profileBaseUrl.endsWith("/") ? profileBaseUrl : profileBaseUrl + "/";
        String profileUrl = cleanProfileBase + escapedUsername;
        String deepLinkUrl = "checked://u/" + escapedUsername;

        String html = """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <title>%s (@%s) trên Checked</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <!-- OpenGraph Meta Tags -->
                <meta property="og:type" content="profile" />
                <meta property="og:site_name" content="Checked" />
                <meta property="og:title" content="Kết bạn với %s trên Checked" />
                <meta property="og:description" content="Nhấn vào để kết bạn với @%s và cùng chia sẻ ảnh khoảnh khắc trực tiếp lên widget màn hình chính." />
                <meta property="og:image" content="%s" />
                <meta property="og:url" content="%s" />
                <!-- Twitter Card -->
                <meta name="twitter:card" content="summary_large_image" />
                <meta name="twitter:title" content="Kết bạn với %s trên Checked" />
                <meta name="twitter:description" content="Nhấn vào để kết bạn với @%s và cùng chia sẻ ảnh khoảnh khắc trực tiếp lên widget màn hình chính." />
                <meta name="twitter:image" content="%s" />
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Roboto, sans-serif;
                        background-color: #0F0F0F;
                        color: #FFFFFF;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        padding: 24px 16px;
                    }
                    .card {
                        max-width: 380px;
                        width: 100%%;
                        background: #1A1A1A;
                        border: 1px solid #282828;
                        border-radius: 28px;
                        padding: 36px 24px;
                        text-align: center;
                        box-shadow: 0 16px 40px rgba(0,0,0,0.6);
                    }
                    .avatar {
                        width: 96px;
                        height: 96px;
                        border-radius: 50%%;
                        object-fit: cover;
                        margin: 0 auto 16px auto;
                        border: 3px solid #C8FF00;
                        box-shadow: 0 4px 16px rgba(200, 255, 0, 0.25);
                    }
                    .name { font-size: 22px; font-weight: 700; margin-bottom: 4px; }
                    .username { color: #8E8E93; font-size: 15px; font-weight: 500; margin-bottom: 20px; }
                    .desc { color: #E5E5EA; font-size: 14px; line-height: 1.5; margin-bottom: 28px; }
                    .btn-primary {
                        display: block;
                        width: 100%%;
                        padding: 15px;
                        background-color: #C8FF00;
                        color: #000000;
                        text-decoration: none;
                        border-radius: 20px;
                        font-weight: 700;
                        font-size: 16px;
                        margin-bottom: 12px;
                        transition: transform 0.1s ease;
                    }
                    .btn-primary:active { transform: scale(0.98); }
                    .badge {
                        display: inline-block;
                        padding: 4px 12px;
                        background: rgba(200, 255, 0, 0.12);
                        color: #C8FF00;
                        border-radius: 12px;
                        font-size: 12px;
                        font-weight: 600;
                        margin-bottom: 16px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <span class="badge">Hồ sơ cá nhân</span>
                    <img class="avatar" src="%s" alt="%s" />
                    <h1 class="name">%s</h1>
                    <p class="username">@%s</p>
                    <p class="desc">Chia sẻ khoảnh khắc ảnh chụp trực tiếp từ widget màn hình chính với bạn bè thân thiết.</p>
                    <a class="btn-primary" href="%s">Mở trong ứng dụng Checked</a>
                </div>
                <script>
                    setTimeout(function() {
                        window.location.href = "%s";
                    }, 300);
                </script>
            </body>
            </html>
            """.formatted(
                escapedDisplayName, escapedUsername,
                escapedDisplayName,
                escapedUsername,
                avatarUrl,
                profileUrl,
                escapedDisplayName,
                escapedUsername,
                avatarUrl,
                avatarUrl, escapedDisplayName,
                escapedDisplayName,
                escapedUsername,
                deepLinkUrl,
                deepLinkUrl
        );

        return ResponseEntity.ok(html);
    }

    // MARK: - 2. Trang Web Lời Mời Kết Bạn (GET /invite & GET /friend/accept)
    @Operation(summary = "Get OpenGraph social share page with deep link for friend invite token")
    @GetMapping(value = {"/invite", "/friend/accept"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getInviteSharePage(@RequestParam(value = "token", required = false) String token) {
        if (token == null || token.trim().isEmpty()) {
            String emptyHtml = buildNotFoundPage("Vui lòng cung cấp mã mời (token) hợp lệ.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(emptyHtml);
        }

        String normalizedToken = token.trim();
        Optional<FriendInviteLink> linkOpt = friendInviteLinkRepository.findByTokenWithOwner(normalizedToken);
        if (linkOpt.isEmpty()) {
            String notFoundHtml = buildNotFoundPage("Liên kết mời kết bạn không tồn tại hoặc đã bị xóa.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundHtml);
        }

        FriendInviteLink link = linkOpt.get();
        LocalDateTime now = LocalDateTime.now();

        if (link.getRevokedAt() != null) {
            String revokedHtml = buildNotFoundPage("Liên kết mời kết bạn này đã bị chủ sở hữu hủy.");
            return ResponseEntity.status(HttpStatus.GONE).body(revokedHtml);
        }

        if (link.getExpiresAt().isBefore(now)) {
            String expiredHtml = buildNotFoundPage("Liên kết mời kết bạn này đã hết hạn.");
            return ResponseEntity.status(HttpStatus.GONE).body(expiredHtml);
        }

        User owner = link.getOwner();
        String escapedDisplayName = HtmlUtils.htmlEscape(owner.getDisplayName() != null ? owner.getDisplayName() : owner.getUsername());
        String escapedUsername = HtmlUtils.htmlEscape(owner.getUsername());
        String avatarUrl = resolveAvatarUrl(owner.getAvatarUrl());

        String deepLinkUrl = "checked://invite?token=" + HtmlUtils.htmlEscape(normalizedToken);
        String inviteFullUrl = inviteBaseUrl.contains("?")
                ? inviteBaseUrl + "&token=" + HtmlUtils.htmlEscape(normalizedToken)
                : inviteBaseUrl + "?token=" + HtmlUtils.htmlEscape(normalizedToken);

        String html = """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <title>Lời mời kết bạn từ %s (@%s) trên Checked</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <!-- OpenGraph Meta Tags -->
                <meta property="og:type" content="website" />
                <meta property="og:site_name" content="Checked" />
                <meta property="og:title" content="Lời mời kết bạn từ %s trên Checked" />
                <meta property="og:description" content="%s (@%s) đã gửi cho bạn lời mời kết bạn. Nhấn vào để kết bạn ngay trên Checked!" />
                <meta property="og:image" content="%s" />
                <meta property="og:url" content="%s" />
                <!-- Twitter Card -->
                <meta name="twitter:card" content="summary_large_image" />
                <meta name="twitter:title" content="Lời mời kết bạn từ %s trên Checked" />
                <meta name="twitter:description" content="%s (@%s) đã gửi cho bạn lời mời kết bạn. Nhấn vào để kết bạn ngay trên Checked!" />
                <meta name="twitter:image" content="%s" />
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Roboto, sans-serif;
                        background-color: #0F0F0F;
                        color: #FFFFFF;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        padding: 24px 16px;
                    }
                    .card {
                        max-width: 380px;
                        width: 100%%;
                        background: #1A1A1A;
                        border: 1px solid #282828;
                        border-radius: 28px;
                        padding: 36px 24px;
                        text-align: center;
                        box-shadow: 0 16px 40px rgba(0,0,0,0.6);
                    }
                    .avatar {
                        width: 96px;
                        height: 96px;
                        border-radius: 50%%;
                        object-fit: cover;
                        margin: 0 auto 16px auto;
                        border: 3px solid #C8FF00;
                        box-shadow: 0 4px 16px rgba(200, 255, 0, 0.25);
                    }
                    .name { font-size: 22px; font-weight: 700; margin-bottom: 4px; }
                    .username { color: #8E8E93; font-size: 15px; font-weight: 500; margin-bottom: 20px; }
                    .desc { color: #E5E5EA; font-size: 14px; line-height: 1.5; margin-bottom: 24px; }
                    .btn-primary {
                        display: block;
                        width: 100%%;
                        padding: 15px;
                        background-color: #C8FF00;
                        color: #000000;
                        text-decoration: none;
                        border-radius: 20px;
                        font-weight: 700;
                        font-size: 16px;
                        margin-bottom: 14px;
                        transition: transform 0.1s ease;
                    }
                    .btn-primary:active { transform: scale(0.98); }
                    .token-box {
                        background: #242424;
                        border: 1px solid #333333;
                        border-radius: 14px;
                        padding: 10px 14px;
                        font-family: monospace;
                        font-size: 13px;
                        color: #A0A0A0;
                        word-break: break-all;
                        margin-bottom: 12px;
                    }
                    .badge {
                        display: inline-block;
                        padding: 4px 12px;
                        background: rgba(200, 255, 0, 0.12);
                        color: #C8FF00;
                        border-radius: 12px;
                        font-size: 12px;
                        font-weight: 600;
                        margin-bottom: 16px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <span class="badge">Lời mời kết bạn</span>
                    <img class="avatar" src="%s" alt="%s" />
                    <h1 class="name">%s</h1>
                    <p class="username">@%s</p>
                    <p class="desc">Bạn đã nhận được lời mời kết bạn. Hãy mở ứng dụng Checked để kết nối ngay!</p>
                    <a class="btn-primary" href="%s">Chấp nhận kết bạn trong Checked</a>
                    <div class="token-box">Mã mời: %s</div>
                </div>
                <script>
                    setTimeout(function() {
                        window.location.href = "%s";
                    }, 300);
                </script>
            </body>
            </html>
            """.formatted(
                escapedDisplayName, escapedUsername,
                escapedDisplayName,
                escapedDisplayName, escapedUsername,
                avatarUrl,
                inviteFullUrl,
                escapedDisplayName,
                escapedDisplayName, escapedUsername,
                avatarUrl,
                avatarUrl, escapedDisplayName,
                escapedDisplayName,
                escapedUsername,
                deepLinkUrl,
                HtmlUtils.htmlEscape(normalizedToken),
                deepLinkUrl
        );

        return ResponseEntity.ok(html);
    }

    private String resolveAvatarUrl(String rawAvatar) {
        if (rawAvatar != null && (rawAvatar.startsWith("https://") || rawAvatar.startsWith("http://"))) {
            return HtmlUtils.htmlEscape(rawAvatar);
        }
        return "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop&q=80";
    }

    private String buildNotFoundPage(String message) {
        String escapedMsg = HtmlUtils.htmlEscape(message);
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <title>Checked</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', sans-serif;
                        background: #0F0F0F; color: #FFFFFF;
                        display: flex; align-items: center; justify-content: center;
                        min-height: 100vh; text-align: center; padding: 24px;
                    }
                    .card {
                        max-width: 380px; width: 100%%; background: #1A1A1A;
                        border: 1px solid #282828; border-radius: 28px; padding: 40px 24px;
                    }
                    h2 { font-size: 20px; font-weight: 700; margin-bottom: 12px; }
                    p { color: #8E8E93; font-size: 14px; line-height: 1.5; margin-bottom: 24px; }
                    a {
                        display: inline-block; padding: 12px 24px; background: #C8FF00;
                        color: #000; text-decoration: none; border-radius: 20px; font-weight: 700;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>Checked</h2>
                    <p>%s</p>
                    <a href="checked://home">Mở ứng dụng Checked</a>
                </div>
            </body>
            </html>
            """.formatted(escapedMsg);
    }
}
