package com.amz.spyglass.scraper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ImageDownloader 的 MD5 计算单元测试（不做网络请求）
 * 这里只测试静态的 md5Hex(byte[]) 方法，确保二进制 MD5 计算正确。
 */
public class ImageDownloaderTest {

    @Test
    public void md5Hex_knownBytes_shouldMatch() {
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04};
        String md5 = ImageDownloader.md5Hex(data);
        // 预计算的 0x01 0x02 0x03 0x04 的 MD5 小写十六进制
        assertEquals("08d6c05a21512a79a1dfeb9d2a8f262f", md5);
    }
}
