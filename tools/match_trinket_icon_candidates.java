import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class match_trinket_icon_candidates {

    private record Candidate(String cardId, String spellSchool, String name) {}
    private record ScoredCandidate(String cardId, String spellSchool, String name, double score) {}

    private static final int[][] LESSER_CENTERS = {
        {188, 112},
        {235, 112},
        {282, 112},
    };
    private static final int[][] GREATER_CENTERS = {
        {339, 112},
        {386, 112},
        {433, 112},
    };

    public static void main(String[] args) throws Exception {
        String tilePath = null;
        String catalogPath = null;
        String imageDir = null;
        String spellSchool = null;
        String outputPath = null;
        int slot = 1;
        int limit = 8;
        int offsetX = 0;
        int offsetY = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--tile" -> tilePath = args[++i];
                case "--catalog" -> catalogPath = args[++i];
                case "--image-dir" -> imageDir = args[++i];
                case "--school" -> spellSchool = args[++i];
                case "--slot" -> slot = Integer.parseInt(args[++i]);
                case "--limit" -> limit = Integer.parseInt(args[++i]);
                case "--output" -> outputPath = args[++i];
                case "--offset-x" -> offsetX = Integer.parseInt(args[++i]);
                case "--offset-y" -> offsetY = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("unknown arg: " + args[i]);
            }
        }

        if (tilePath == null || catalogPath == null || imageDir == null || spellSchool == null) {
            throw new IllegalArgumentException("required: --tile --catalog --image-dir --school --slot");
        }

        int[] center = "LESSER_TRINKET".equals(spellSchool)
            ? LESSER_CENTERS[slot - 1]
            : GREATER_CENTERS[slot - 1];
        center = new int[] {center[0] + offsetX, center[1] + offsetY};

        BufferedImage tile = ImageIO.read(new File(tilePath));
        BufferedImage reviewTarget = cropRect(tile, center[0] - 12, center[1] - 18, 42, 92);
        BufferedImage target = cropSquare(tile, center[0], center[1], 28);
        BufferedImage normalizedTarget = normalize(target);

        List<Candidate> candidates = loadCandidates(catalogPath, spellSchool);
        List<ScoredCandidate> scored = new ArrayList<>();
        for (Candidate candidate : candidates) {
            File imageFile = new File(imageDir, candidate.cardId + ".jpg");
            if (!imageFile.exists() || imageFile.length() <= 0L) {
                continue;
            }
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                continue;
            }
            BufferedImage normalizedCandidate = normalize(candidateFocusCrop(image));
            double score = mse(normalizedTarget, normalizedCandidate);
            scored.add(new ScoredCandidate(candidate.cardId, candidate.spellSchool, candidate.name, score));
        }

        List<ScoredCandidate> topCandidates = scored.stream()
            .sorted(Comparator.comparingDouble(ScoredCandidate::score))
            .limit(limit)
            .toList();

        topCandidates.forEach(candidate -> System.out.printf(
                "%s\t%s\t%s\t%.2f%n",
                candidate.cardId,
                candidate.spellSchool,
                candidate.name,
                candidate.score
            ));

        if (outputPath != null && !outputPath.isBlank()) {
            renderReviewSheet(reviewTarget, topCandidates, imageDir, outputPath);
        }
    }

    private static List<Candidate> loadCandidates(String catalogPath, String spellSchool) throws Exception {
        List<Candidate> candidates = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(catalogPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) {
                    continue;
                }
                if (!spellSchool.equals(parts[1])) {
                    continue;
                }
                candidates.add(new Candidate(parts[0], parts[1], parts[2]));
            }
        }
        return candidates;
    }

    private static BufferedImage cropSquare(BufferedImage image, int centerX, int centerY, int size) {
        int half = size / 2;
        int left = Math.max(0, centerX - half);
        int top = Math.max(0, centerY - half);
        int right = Math.min(image.getWidth(), left + size);
        int bottom = Math.min(image.getHeight(), top + size);
        return image.getSubimage(left, top, right - left, bottom - top);
    }

    private static BufferedImage cropRect(BufferedImage image, int left, int top, int width, int height) {
        int safeLeft = Math.max(0, left);
        int safeTop = Math.max(0, top);
        int safeRight = Math.min(image.getWidth(), safeLeft + width);
        int safeBottom = Math.min(image.getHeight(), safeTop + height);
        return image.getSubimage(safeLeft, safeTop, safeRight - safeLeft, safeBottom - safeTop);
    }

    private static BufferedImage normalize(BufferedImage source) {
        BufferedImage output = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, 32, 32, null);
        graphics.dispose();
        return output;
    }

    private static BufferedImage candidateFocusCrop(BufferedImage image) {
        int left = (int) (image.getWidth() * 0.22);
        int top = (int) (image.getHeight() * 0.06);
        int width = (int) (image.getWidth() * 0.56);
        int height = (int) (image.getHeight() * 0.42);
        return cropRect(image, left, top, width, height);
    }

    private static double mse(BufferedImage left, BufferedImage right) {
        long total = 0L;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int rgbA = left.getRGB(x, y);
                int rgbB = right.getRGB(x, y);
                int dr = ((rgbA >> 16) & 0xFF) - ((rgbB >> 16) & 0xFF);
                int dg = ((rgbA >> 8) & 0xFF) - ((rgbB >> 8) & 0xFF);
                int db = (rgbA & 0xFF) - (rgbB & 0xFF);
                total += (long) dr * dr + (long) dg * dg + (long) db * db;
            }
        }
        return total / (32.0 * 32.0 * 3.0);
    }

    private static void renderReviewSheet(
        BufferedImage target,
        List<ScoredCandidate> candidates,
        String imageDir,
        String outputPath
    ) throws Exception {
        int columns = 3;
        int tileWidth = 220;
        int tileHeight = 140;
        int rows = 1 + Math.max(1, (int) Math.ceil(candidates.size() / (double) columns));
        BufferedImage canvas = new BufferedImage(
            columns * tileWidth,
            rows * tileHeight,
            BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setColor(new Color(18, 23, 30));
        graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        drawCell(
            graphics,
            target,
            "目标槽位",
            "",
            0,
            0,
            tileWidth,
            tileHeight
        );

        for (int index = 0; index < candidates.size(); index++) {
            ScoredCandidate candidate = candidates.get(index);
            File imageFile = new File(imageDir, candidate.cardId + ".jpg");
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                continue;
            }
            int cell = index + 1;
            int col = cell % columns;
            int row = cell / columns;
            drawCell(
                graphics,
                image,
                candidate.cardId,
                candidate.name + "  " + String.format("%.0f", candidate.score),
                col * tileWidth,
                row * tileHeight,
                tileWidth,
                tileHeight
            );
        }

        graphics.dispose();
        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();
        ImageIO.write(canvas, "jpg", outputFile);
    }

    private static void drawCell(
        Graphics2D graphics,
        BufferedImage image,
        String title,
        String subtitle,
        int left,
        int top,
        int width,
        int height
    ) {
        graphics.setColor(new Color(28, 36, 46));
        graphics.fillRoundRect(left + 8, top + 8, width - 16, height - 16, 16, 16);

        int previewWidth = Math.min(92, width - 32);
        int previewHeight = Math.min(116, height - 32);
        graphics.drawImage(
            image,
            left + 16,
            top + 12,
            previewWidth,
            previewHeight,
            null
        );

        graphics.setColor(new Color(238, 242, 247));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 15));
        graphics.drawString(ellipsize(title, 18), left + 118, top + 40);

        if (!subtitle.isBlank()) {
            graphics.setColor(new Color(176, 190, 204));
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 13));
            graphics.drawString(ellipsize(subtitle, 22), left + 118, top + 64);
        }
    }

    private static String ellipsize(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
