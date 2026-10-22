package com.kizuna.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

public final class AvatarStore {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000;
    private static final int MAX_DIMENSION = 6_000;
    private final Path root;

    public AvatarStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public String copy(Path source) throws IOException {
        if (source == null) return null;
        if (!Files.isRegularFile(source) || Files.size(source) > MAX_BYTES) throw new IllegalArgumentException("Choose a JPG or PNG image under 5 MB.");
        String ext = extension(source.getFileName().toString());
        if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png")) throw new IllegalArgumentException("Choose a JPG or PNG image under 5 MB.");
        validateImage(source);
        Files.createDirectories(root);
        String generated = UUID.randomUUID() + "." + ext;
        Files.copy(source, root.resolve(generated));
        return generated;
    }

    public Path resolve(String filename) {
        if (filename == null || filename.isBlank()) return null;
        Path child = Path.of(filename);
        if (child.isAbsolute() || child.getNameCount() != 1 || filename.contains("/") || filename.contains("\\")) return null;
        Path resolved = root.resolve(child).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) return null;
        try {
            Path realRoot = root.toRealPath();
            Path realFile = resolved.toRealPath();
            return realFile.startsWith(realRoot) ? realFile : null;
        } catch (IOException e) { return null; }
    }

    public void delete(String filename) {
        Path path = resolve(filename);
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void validateImage(Path source) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) throw new IllegalArgumentException("The selected file is not a readable image.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("The selected file is not a readable image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("Choose an image no larger than 12 megapixels or 6,000 pixels on either side.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("The selected file is not a readable image.");
            } finally {
                reader.dispose();
            }
        }
    }
}
