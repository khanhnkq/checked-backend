package com.codegym.locketclone.user.qr;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QrCodeServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private QrCodeServiceImpl qrCodeService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(qrCodeService, "profileBaseUrl", "https://checked.app/u/");
    }

    @Test
    void generateProfileQrCode_validUsername_returnsPngByteArray() {
        when(userRepository.existsByUsernameIgnoreCase("khanhnkq")).thenReturn(true);

        byte[] qrBytes = qrCodeService.generateProfileQrCode("khanhnkq");

        assertNotNull(qrBytes);
        assertTrue(qrBytes.length > 0);
        // Verify PNG magic header: 0x89, 0x50 ('P'), 0x4E ('N'), 0x47 ('G')
        assertEquals((byte) 0x89, qrBytes[0]);
        assertEquals((byte) 'P', qrBytes[1]);
        assertEquals((byte) 'N', qrBytes[2]);
        assertEquals((byte) 'G', qrBytes[3]);

        verify(userRepository).existsByUsernameIgnoreCase("khanhnkq");
    }

    @Test
    void generateProfileQrCode_userNotFound_throwsUserNotFound() {
        when(userRepository.existsByUsernameIgnoreCase("nonexistent")).thenReturn(false);

        AppException exception = assertThrows(AppException.class,
                () -> qrCodeService.generateProfileQrCode("nonexistent"));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(userRepository).existsByUsernameIgnoreCase("nonexistent");
    }

    @Test
    void generateProfileQrCode_emptyUsername_throwsInvalidRequest() {
        AppException exception1 = assertThrows(AppException.class,
                () -> qrCodeService.generateProfileQrCode(""));
        assertEquals(ErrorCode.INVALID_REQUEST, exception1.getErrorCode());

        AppException exception2 = assertThrows(AppException.class,
                () -> qrCodeService.generateProfileQrCode(null));
        assertEquals(ErrorCode.INVALID_REQUEST, exception2.getErrorCode());

        verifyNoInteractions(userRepository);
    }
}
