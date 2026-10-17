package com.codegym.locketclone.user.share;

import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.Optional;

@RestController
@RequestMapping("/u")
@RequiredArgsConstructor
@Tag(name = "Social Share", description = "Public OpenGraph share page and deep-linking for user profiles")
public class ShareController {

    private final UserRepository userRepository;

    @Operation(summary = "Get OpenGraph social share page with deep link for user profile")
    @GetMapping(value = "/{username}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSocialSharePage(@PathVariable String username) {
        Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            String escapedUsername = HtmlUtils.htmlEscape(username);
            String notFoundHtml = """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <title>Không tìm thấy người dùng - Checked</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; text-align: center; padding: 60px 20px; background-color: #121212; color: #FFFFFF;">
                    <h2 style="font-size: 24px; margin-bottom: 12px;">Không tìm thấy người dùng</h2>
                    <p style="color: #A0A0A0; font-size: 16px; margin-bottom: 24px;">Người dùng @%s không tồn tại trên Checked.</p>
                    <a href="https://checked.app" style="display: inline-block; padding: 12px 24px; background-color: #FFB800; color: #000000; text-decoration: none; border-radius: 24px; font-weight: bold;">Về trang chủ Checked</a>
                </body>
                </html>
                """.formatted(escapedUsername);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundHtml);
        }

        User user = userOpt.get();
        String escapedDisplayName = HtmlUtils.htmlEscape(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
        String escapedUsername = HtmlUtils.htmlEscape(user.getUsername());
        String rawAvatar = user.getAvatarUrl();
        String avatarUrl;
        if (rawAvatar != null && (rawAvatar.startsWith("https://") || rawAvatar.startsWith("http://"))) {
            avatarUrl = HtmlUtils.htmlEscape(rawAvatar);
        } else {
            avatarUrl = "https://storage.checked.app/defaults/default-avatar.png";
        }
        String profileUrl = "https://checked.app/u/" + escapedUsername;
        String deepLinkUrl = "checked://u/" + escapedUsername;
        String storeUrl = "https://checked.app/download";

        String html = """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <title>%s (@%s) trên Checked</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <!-- OpenGraph Meta Tags -->
                <meta property="og:type" content="profile" />
                <meta property="og:title" content="Kết bạn với %s trên Checked" />
                <meta property="og:description" content="Quét mã QR hoặc nhấn vào liên kết để kết bạn với @%s và cùng chia sẻ khoảnh khắc lên màn hình chính." />
                <meta property="og:image" content="%s" />
                <meta property="og:url" content="%s" />
                <!-- Twitter Card -->
                <meta name="twitter:card" content="summary_large_image" />
                <meta name="twitter:title" content="Kết bạn với %s trên Checked" />
                <meta name="twitter:description" content="Quét mã QR hoặc nhấn vào liên kết để kết bạn với @%s và cùng chia sẻ khoảnh khắc lên màn hình chính." />
                <meta name="twitter:image" content="%s" />
                <!-- Smart App Deep Linking -->
                <meta http-equiv="refresh" content="0; url=%s" />
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; text-align: center; padding: 60px 20px; background-color: #121212; color: #FFFFFF;">
                <div style="max-width: 400px; margin: 0 auto; background-color: #1E1E1E; border-radius: 24px; padding: 32px 24px; box-shadow: 0 8px 24px rgba(0,0,0,0.5);">
                    <img src="%s" alt="%s" style="width: 100px; height: 100px; border-radius: 50%%; object-fit: cover; margin-bottom: 16px; border: 3px solid #FFB800;" />
                    <h2 style="font-size: 22px; margin: 0 0 4px 0;">%s</h2>
                    <p style="color: #A0A0A0; margin: 0 0 20px 0;">@%s</p>
                    <p style="color: #E0E0E0; font-size: 15px; line-height: 1.5; margin-bottom: 28px;">Đang mở liên kết kết bạn trên ứng dụng Checked...</p>
                    <a id="deepLinkBtn" href="%s" style="display: block; width: 100%%; box-sizing: border-box; padding: 14px; background-color: #FFB800; color: #000000; text-decoration: none; border-radius: 30px; font-weight: bold; font-size: 16px; margin-bottom: 12px;">Mở ứng dụng Checked</a>
                    <a href="%s" style="display: block; width: 100%%; box-sizing: border-box; padding: 12px; background-color: transparent; color: #A0A0A0; text-decoration: none; border-radius: 30px; font-size: 14px;">Chưa có ứng dụng? Tải ngay</a>
                </div>
                <script>
                    window.location.href = "%s";
                    setTimeout(function() {
                        // If still on the page after 2 seconds, the deep link probably failed (app not installed)
                    }, 2000);
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
                deepLinkUrl,
                avatarUrl, escapedDisplayName,
                escapedDisplayName,
                escapedUsername,
                deepLinkUrl,
                storeUrl,
                deepLinkUrl
        );

        return ResponseEntity.ok(html);
    }
}
