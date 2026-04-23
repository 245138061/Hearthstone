import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class extract_video_frame {

    public static void main(String[] args) throws Exception {
        String inputPath = null;
        String outputPath = null;
        double seconds = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input" -> inputPath = args[++i];
                case "--seconds" -> seconds = Double.parseDouble(args[++i]);
                case "--output" -> outputPath = args[++i];
                default -> throw new IllegalArgumentException("unknown arg: " + args[i]);
            }
        }

        if (inputPath == null || outputPath == null) {
            throw new IllegalArgumentException("required: --input --seconds --output");
        }

        File input = new File(inputPath);
        File output = new File(outputPath);
        output.getParentFile().mkdirs();

        try (SeekableByteChannel channel = NIOUtils.readableChannel(input)) {
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            grab.seekToSecondPrecise(seconds);
            Picture picture = grab.getNativeFrame();
            if (picture == null) {
                throw new IllegalStateException("no frame at " + seconds);
            }
            BufferedImage image = AWTUtil.toBufferedImage(picture);
            ImageIO.write(image, "jpg", output);
        }
    }
}
