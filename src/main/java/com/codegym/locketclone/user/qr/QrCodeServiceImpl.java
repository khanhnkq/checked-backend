package com.codegym.locketclone.user.qr;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.user.UserRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeServiceImpl implements QrCodeService {

    private static final int QR_SIZE = 512;

    private final UserRepository userRepository;

    @Value("${app.profile-base-url:https://heroku-7usd-5a73e86b5b14.herokuapp.com/u/}")
    private String profileBaseUrl;

    @Override
    public byte[] generateProfileQrCode(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        String normalizedUsername = username.trim();
        if (!userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        String targetUrl = profileBaseUrl.endsWith("/")
                ? profileBaseUrl + normalizedUsername
                : profileBaseUrl + "/" + normalizedUsername;

        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(targetUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            log.error("Failed to generate QR code for user {}: {}", normalizedUsername, ex.getMessage(), ex);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }
}
