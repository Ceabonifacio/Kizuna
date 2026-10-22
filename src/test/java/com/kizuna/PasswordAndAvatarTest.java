package com.kizuna;

import com.kizuna.util.AvatarStore;
import com.kizuna.util.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class PasswordAndAvatarTest {
    @TempDir Path temp;

    @Test
    void passwordHashesUseUniqueSaltAndVerifyWithoutPlaintextStorage() {
        PasswordHasher hasher = new PasswordHasher();
        char[] one = "a long passphrase".toCharArray();
        char[] two = "a long passphrase".toCharArray();
        String first = hasher.hash(one);
        String second = hasher.hash(two);
        assertTrue(first.startsWith("pbkdf2_sha256$600000$"));
        assertNotEquals(first, second);
        assertTrue(hasher.verify("a long passphrase".toCharArray(), first));
        assertFalse(hasher.verify("wrong passphrase".toCharArray(), first));
        assertFalse(hasher.verify("anything".toCharArray(), "plain-sha256"));
        assertEquals("a long passphrase", new String(one));
        assertEquals(16, java.util.Base64.getDecoder().decode(first.split("\\$")[2]).length);
        assertEquals(32, java.util.Base64.getDecoder().decode(first.split("\\$")[3]).length);
    }

    @Test
    void avatarCopyValidatesDecodesAndRejectsTraversal() throws Exception {
        Path source = temp.resolve("image.png");
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", source.toFile());
        AvatarStore store = new AvatarStore(temp.resolve("owned-avatars"));
        String filename = store.copy(source);
        assertNotNull(filename);
        assertFalse(filename.contains("/"));
        assertTrue(store.resolve(filename).startsWith(temp.resolve("owned-avatars").toRealPath()));
        assertNull(store.resolve("../../outside.png"));
        Path fake = temp.resolve("fake.jpg"); Files.writeString(fake, "not an image");
        assertThrows(IllegalArgumentException.class, () -> store.copy(fake));
        assertThrows(IllegalArgumentException.class, () -> store.copy(temp.resolve("missing.png")));

        Path oversized = temp.resolve("oversized.png");
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", oversized.toFile());
        byte[] png = Files.readAllBytes(oversized);
        writeInt(png, 16, 100_000); writeInt(png, 20, 100_000);
        CRC32 crc = new CRC32(); crc.update(png, 12, 17); writeInt(png, 29, (int) crc.getValue());
        Files.write(oversized, png);
        IllegalArgumentException sizeError = assertThrows(IllegalArgumentException.class, () -> store.copy(oversized));
        assertTrue(sizeError.getMessage().contains("12 megapixels"));

        store.delete(filename);
        assertNull(store.resolve(filename));
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
