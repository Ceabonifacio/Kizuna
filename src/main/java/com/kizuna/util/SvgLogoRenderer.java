package com.kizuna.util;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.net.URL;

/** Loads the bundled SVG logo and rasterizes it at high resolution for JavaFX. */
public final class SvgLogoRenderer {
    private static final int RENDER_SIZE = 1024;
    private static final WritableImage LOGO = loadLogo();

    private SvgLogoRenderer() { }

    public static WritableImage logo() { return LOGO; }

    private static WritableImage loadLogo() {
        URL resource = SvgLogoRenderer.class.getResource("/images/KizunaLogo.svg");
        if (resource == null) throw new IllegalStateException("Bundled Kizuna logo is missing.");

        SVGDocument document = new SVGLoader().load(resource);
        if (document == null) throw new IllegalStateException("Bundled Kizuna logo could not be read.");

        BufferedImage raster = new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = raster.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            document.render(null, graphics, new ViewBox(0, 0, RENDER_SIZE, RENDER_SIZE));
        } finally {
            graphics.dispose();
        }

        WritableImage image = new WritableImage(RENDER_SIZE, RENDER_SIZE);
        int[] pixels = ((DataBufferInt) raster.getRaster().getDataBuffer()).getData();
        image.getPixelWriter().setPixels(0, 0, RENDER_SIZE, RENDER_SIZE,
                PixelFormat.getIntArgbInstance(), pixels, 0, RENDER_SIZE);
        return image;
    }
}
