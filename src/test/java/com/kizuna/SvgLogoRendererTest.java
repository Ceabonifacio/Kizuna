package com.kizuna;

import com.kizuna.util.SvgLogoRenderer;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SvgLogoRendererTest {
    @Test
    void rendersClasspathLogoAtHighResolutionWithTransparency() {
        WritableImage logo = SvgLogoRenderer.logo();
        assertEquals(1024, (int) logo.getWidth());
        assertEquals(1024, (int) logo.getHeight());

        PixelReader pixels = logo.getPixelReader();
        assertEquals(0, pixels.getColor(0, 0).getOpacity(), "logo corners should remain transparent");
        boolean hasVisibleArtwork = false;
        for (int y = 0; y < (int) logo.getHeight() && !hasVisibleArtwork; y += 16) {
            for (int x = 0; x < (int) logo.getWidth(); x += 16) {
                if (pixels.getColor(x, y).getOpacity() > 0) { hasVisibleArtwork = true; break; }
            }
        }
        assertTrue(hasVisibleArtwork, "logo artwork should render from the classpath SVG");
    }
}
