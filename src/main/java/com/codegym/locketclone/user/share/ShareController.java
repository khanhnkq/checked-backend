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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Tag(name = "Social Share", description = "Public OpenGraph share page and deep-linking for user profiles and invite links")
public class ShareController {

    private final UserRepository userRepository;
    private final FriendInviteLinkRepository friendInviteLinkRepository;

    public static final String DEFAULT_AVATAR_URL = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop&q=80";

    @Value("${app.profile-base-url:https://heroku-7usd-5a73e86b5b14.herokuapp.com/u/}")
    private String profileBaseUrl;

    @Value("${app.friend-invite.base-url:https://heroku-7usd-5a73e86b5b14.herokuapp.com/invite}")
    private String inviteBaseUrl;

    // MARK: - Flat, Minimalist Design System (No Emoji, No Blur, No Shadow)
    private static final String COMMON_CSS = """
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', -apple-system, system-ui, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            background-color: #EDE9DF;
            color: #141613;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            padding: 32px 16px;
            -webkit-font-smoothing: antialiased;
        }
        .brand-header {
            display: flex;
            align-items: center;
            gap: 10px;
            margin-bottom: 24px;
            text-decoration: none;
        }
        .brand-logo {
            width: 36px;
            height: 36px;
            background: #141613;
            border-radius: 11px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .brand-logo svg {
            width: 20px;
            height: 20px;
        }
        .brand-title {
            font-size: 22px;
            font-weight: 800;
            letter-spacing: -0.03em;
            color: #141613;
        }
        .card {
            max-width: 400px;
            width: 100%;
            background: #FFFFFF;
            border: 1px solid #DFDACD;
            border-radius: 32px;
            padding: 36px 28px;
            text-align: center;
        }
        .badge-pill {
            display: inline-flex;
            align-items: center;
            padding: 6px 14px;
            background: #E6F5A3;
            color: #26380A;
            border-radius: 9999px;
            font-size: 13px;
            font-weight: 700;
            margin-bottom: 20px;
            letter-spacing: -0.01em;
        }
        .badge-pill.error {
            background: #FFE8E5;
            color: #C0392B;
        }
        .avatar-container {
            position: relative;
            width: 104px;
            height: 104px;
            margin: 0 auto 18px;
        }
        .avatar {
            width: 104px;
            height: 104px;
            border-radius: 50%;
            object-fit: cover;
            border: 3px solid #DFDACD;
            display: block;
        }
        .gold-crown {
            position: absolute;
            bottom: -2px;
            right: -2px;
            background: #FFFFFF;
            border: 1px solid #DFDACD;
            border-radius: 50%;
            width: 26px;
            height: 26px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .name {
            font-size: 24px;
            font-weight: 800;
            letter-spacing: -0.02em;
            color: #141613;
            margin-bottom: 4px;
        }
        .username {
            color: #63685F;
            font-size: 15px;
            font-weight: 600;
            margin-bottom: 14px;
        }
        .desc {
            color: #63685F;
            font-size: 14.5px;
            line-height: 1.55;
            margin-bottom: 24px;
        }
        .btn-primary {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            width: 100%;
            padding: 16px 20px;
            background-color: #D4EB70;
            color: #141613;
            text-decoration: none;
            border-radius: 9999px;
            font-weight: 700;
            font-size: 16px;
            letter-spacing: -0.01em;
            transition: background-color 0.15s ease;
        }
        .btn-primary:hover {
            background-color: #C8E05A;
        }
        .btn-primary:active {
            opacity: 0.9;
        }
        .token-card {
            background: #F7F5EE;
            border: 1px solid #DFDACD;
            border-radius: 18px;
            padding: 12px 14px;
            margin-top: 14px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            text-align: left;
        }
        .token-content {
            flex: 1;
            min-width: 0;
        }
        .token-label {
            font-size: 11px;
            font-weight: 700;
            color: #8A8F84;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            margin-bottom: 2px;
        }
        .token-code {
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
            font-size: 13px;
            font-weight: 600;
            color: #141613;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .btn-copy {
            background: #FFFFFF;
            border: 1px solid #DFDACD;
            border-radius: 12px;
            padding: 7px 13px;
            font-size: 12px;
            font-weight: 700;
            color: #141613;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            white-space: nowrap;
            transition: background-color 0.15s ease;
        }
        .btn-copy:hover {
            background: #EDE9DF;
        }
        .footer-text {
            margin-top: 24px;
            font-size: 13px;
            color: #8A8F84;
            font-weight: 500;
        }
        """;

    private static final String BRAND_HEADER_HTML = """
        <div class="brand-header">
            <div class="brand-logo">
                <svg viewBox="0 0 24 24" fill="none" stroke="#D4EB70" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="20 6 9 17 4 12"></polyline>
                </svg>
            </div>
            <span class="brand-title">Checked</span>
        </div>
        """;

    private static final String COPY_SCRIPT_HTML = """
        <script>
            function copyToken(token, btn) {
                navigator.clipboard.writeText(token).then(function() {
                    var orig = btn.innerText;
                    btn.innerText = 'Đã sao chép';
                    btn.style.color = '#3A6B1A';
                    setTimeout(function() {
                        btn.innerText = orig;
                        btn.style.color = '#141613';
                    }, 2000);
                });
            }
        </script>
        """;

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
        boolean isGold = Boolean.TRUE.equals(user.getIsGoldMember());
        String crownBadgeHtml = isGold ? "<span class=\"gold-crown\"><svg viewBox=\"0 0 24 24\" width=\"14\" height=\"14\" fill=\"#E5A93B\"><path d=\"M5 16L3 5l5.5 5L12 4l3.5 6L21 5l-2 11H5zm14 3c0 .6-.4 1-1 1H6c-.6 0-1-.4-1-1v-1h14v1z\"/></svg></span>" : "";

        String cleanProfileBase = getEffectiveProfileBaseUrl();
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
                <meta property="og:description" content="Nhấn vào để kết bạn với @%s và cùng chia sẻ khoảnh khắc ảnh chụp trực tiếp từ widget màn hình chính." />
                <meta property="og:image" content="%s" />
                <meta property="og:url" content="%s" />
                <!-- Twitter Card -->
                <meta name="twitter:card" content="summary_large_image" />
                <meta name="twitter:title" content="Kết bạn với %s trên Checked" />
                <meta name="twitter:description" content="Nhấn vào để kết bạn với @%s và cùng chia sẻ khoảnh khắc ảnh chụp trực tiếp từ widget màn hình chính." />
                <meta name="twitter:image" content="%s" />
                <style>
                    %s
                </style>
            </head>
            <body>
                %s
                <div class="card">
                    <span class="badge-pill">Hồ sơ cá nhân</span>
                    <div class="avatar-container">
                        <img class="avatar" src="%s" alt="%s" />
                        %s
                    </div>
                    <h1 class="name">%s</h1>
                    <p class="username">@%s</p>
                    <p class="desc">Chia sẻ khoảnh khắc ảnh chụp trực tiếp từ widget màn hình chính với bạn bè thân thiết.</p>
                    <a class="btn-primary" href="%s">
                        Mở trong ứng dụng Checked
                    </a>
                </div>
                <p class="footer-text">Checked • Kết nối bạn bè thân thiết</p>
                <script>
                    setTimeout(function() {
                        window.location.href = "%s";
                    }, 400);
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
                COMMON_CSS,
                BRAND_HEADER_HTML,
                avatarUrl, escapedDisplayName,
                crownBadgeHtml,
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
        boolean isGold = Boolean.TRUE.equals(owner.getIsGoldMember());
        String crownBadgeHtml = isGold ? "<span class=\"gold-crown\"><svg viewBox=\"0 0 24 24\" width=\"14\" height=\"14\" fill=\"#E5A93B\"><path d=\"M5 16L3 5l5.5 5L12 4l3.5 6L21 5l-2 11H5zm14 3c0 .6-.4 1-1 1H6c-.6 0-1-.4-1-1v-1h14v1z\"/></svg></span>" : "";

        String deepLinkUrl = "checked://invite?token=" + HtmlUtils.htmlEscape(normalizedToken);
        String baseInvite = getEffectiveInviteBaseUrl();
        String inviteFullUrl = baseInvite.contains("?")
                ? baseInvite + "&token=" + HtmlUtils.htmlEscape(normalizedToken)
                : baseInvite + "?token=" + HtmlUtils.htmlEscape(normalizedToken);

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
                    %s
                </style>
            </head>
            <body>
                %s
                <div class="card">
                    <span class="badge-pill">Lời mời kết bạn</span>
                    <div class="avatar-container">
                        <img class="avatar" src="%s" alt="%s" />
                        %s
                    </div>
                    <h1 class="name">%s</h1>
                    <p class="username">@%s</p>
                    <p class="desc">Bạn đã nhận được lời mời kết bạn. Hãy mở ứng dụng Checked để cùng chia sẻ khoảnh khắc đời thường!</p>
                    
                    <a class="btn-primary" href="%s">
                        Chấp nhận kết bạn trong Checked
                    </a>
                    
                    <div class="token-card">
                        <div class="token-content">
                            <div class="token-label">MÃ MỜI</div>
                            <div class="token-code" title="%s">%s</div>
                        </div>
                        <button class="btn-copy" onclick="copyToken('%s', this)">Sao chép</button>
                    </div>
                </div>
                <p class="footer-text">Checked • Chia sẻ khoảnh khắc cùng bạn thân</p>
                %s
                <script>
                    setTimeout(function() {
                        window.location.href = "%s";
                    }, 400);
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
                COMMON_CSS,
                BRAND_HEADER_HTML,
                avatarUrl, escapedDisplayName,
                crownBadgeHtml,
                escapedDisplayName,
                escapedUsername,
                deepLinkUrl,
                HtmlUtils.htmlEscape(normalizedToken),
                HtmlUtils.htmlEscape(normalizedToken),
                HtmlUtils.htmlEscape(normalizedToken),
                COPY_SCRIPT_HTML,
                deepLinkUrl
        );

        return ResponseEntity.ok(html);
    }

    private String resolveAvatarUrl(String rawAvatar) {
        if (rawAvatar != null && (rawAvatar.startsWith("https://") || rawAvatar.startsWith("http://"))) {
            return HtmlUtils.htmlEscape(rawAvatar);
        }
        return DEFAULT_AVATAR_URL;
    }

    private String getEffectiveProfileBaseUrl() {
        String base = (profileBaseUrl != null && !profileBaseUrl.trim().isEmpty())
                ? profileBaseUrl.trim()
                : "https://checked.app/u/";
        return base.endsWith("/") ? base : base + "/";
    }

    private String getEffectiveInviteBaseUrl() {
        return (inviteBaseUrl != null && !inviteBaseUrl.trim().isEmpty())
                ? inviteBaseUrl.trim()
                : "https://checked.app/invite";
    }

    private String buildNotFoundPage(String message) {
        String escapedMsg = HtmlUtils.htmlEscape(message);
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <title>Checked</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    %s
                </style>
            </head>
            <body>
                %s
                <div class="card">
                    <span class="badge-pill error">Thông báo</span>
                    <h2 class="name" style="font-size: 20px; margin-bottom: 12px;">Liên kết không khả dụng</h2>
                    <p class="desc">%s</p>
                    <a class="btn-primary" href="checked://home">Mở ứng dụng Checked</a>
                </div>
                <p class="footer-text">Checked</p>
            </body>
            </html>
            """.formatted(COMMON_CSS, BRAND_HEADER_HTML, escapedMsg);
    }
}
