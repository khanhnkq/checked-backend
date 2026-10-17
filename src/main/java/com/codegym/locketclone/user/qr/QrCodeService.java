package com.codegym.locketclone.user.qr;

public interface QrCodeService {
    byte[] generateProfileQrCode(String username);
}
