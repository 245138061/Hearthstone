import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

public class split_bilibili_videoshot_sheet {

    public static void main(String[] args) throws Exception {
        String sheetPath = null;
        String outputDir = null;
        int tileWidth = 480;
        int tileHeight = 270;
        int columns = 10;
        int rows = 10;
        boolean renderTimeline = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--sheet" -> sheetPath = args[++i];
                case "--output-dir" -> outputDir = args[++i];
                case "--tile-width" -> tileWidth = Integer.parseInt(args[++i]);
                case "--tile-height" -> tileHeight = Integer.parseInt(args[++i]);
                case "--columns" -> columns = Integer.parseInt(args[++i]);
                case "--rows" -> rows = Integer.parseInt(args[++i]);
                case "--timeline" -> renderTimeline = true;
                default -> throw new IllegalArgumentException("unknown arg: " + args[i]);
            }
        }

        if (sheetPath == null || outputDir == null) {
            throw new IllegalArgumentException("required: --sheet --output-dir");
        }

        BufferedImage sheet = ImageIO.read(new File(sheetPath));
        if (sheet == null) {
            throw new IllegalArgumentException("failed to read sheet: " + sheetPath);
        }

        File output = new File(outputDir);
        output.mkdirs();

        BufferedImage timeline = renderTimeline
            ? new BufferedImage(columns * 220, rows * 150, BufferedImage.TYPE_INT_RGB)
            : null;
        Graphics2D timelineGraphics = timeline != null ? timeline.createGraphics() : null;
        if (timelineGraphics != null) {
            timelineGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            timelineGraphics.setColor(new Color(18, 23, 30));
            timelineGraphics.fillRect(0, 0, timeline.getWidth(), timeline.getHeight());
        }

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                int left = col * tileWidth;
                int top = row * tileHeight;
                if (left + tileWidth > sheet.getWidth() || top + tileHeight > sheet.getHeight()) {
                    continue;
                }
                BufferedImage tile = sheet.getSubimage(left, top, tileWidth, tileHeight);
                File target = new File(output, String.format("%03d.jpg", index));
                ImageIO.write(tile, "jpg", target);

                if (timelineGraphics != null) {
                    drawTimelineCell(timelineGraphics, tile, index, col * 220, row * 150);
                }
            }
        }

        if (timelineGraphics != null) {
            timelineGraphics.dispose();
            ImageIO.write(timeline, "jpg", new File(output.getParentFile(), "timeline_frames.jpg"));
        }
    }

    private static void drawTimelineCell(Graphics2D graphics, BufferedImage tile, int index, int left, int top) {
        int cardWidth = 220;
        int cardHeight = 150;
        graphics.setColor(new Color(28, 36, 46));
        graphics.fillRoundRect(left + 4, top + 4, cardWidth - 8, cardHeight - 8, 14, 14);
        graphics.drawImage(tile, left + 8, top + 8, 204, 114, null);
        graphics.setColor(new Color(238, 242, 247));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 18));
        graphics.drawString(String.format("%03d", index), left + 10, top + 140);
    }
}
