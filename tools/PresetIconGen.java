// 一次性脚本:把 icons/icon{1..4}.{jpg,png} 缩放为 16 张 PNG 写入 static/img/presets/
// 跑法:bash deploy/gen-presets.sh
// 输入路径硬编码,4 个源文件 × 4 尺寸(96/180/192/512)= 16 输出。
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class PresetIconGen {
    private static final int[] SIZES = { 96, 180, 192, 512 };
    private static final String[] SOURCE_NAMES = { "icon1.jpg", "icon2.jpg", "icon3.png", "icon4.png" };
    private static final String SRC_DIR = "/home/finance/financial-management/icons";
    private static final String OUT_DIR = "/home/finance/financial-management/src/main/resources/static/img/presets";

    public static void main(String[] args) throws IOException {
        File outDir = new File(OUT_DIR);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("can't create " + OUT_DIR);
        }
        for (int i = 0; i < SOURCE_NAMES.length; i++) {
            File src = new File(SRC_DIR, SOURCE_NAMES[i]);
            BufferedImage source = ImageIO.read(src);
            if (source == null) {
                throw new IOException("can't read " + src);
            }
            String stem = "icon" + (i + 1);
            for (int size : SIZES) {
                BufferedImage scaled = scale(source, size);
                File out = new File(outDir, stem + "-" + size + ".png");
                ImageIO.write(scaled, "PNG", out);
                System.out.println("✓ " + out.getName() + " (" + size + "x" + size + ")");
            }
        }
        System.out.println("Done. " + (SOURCE_NAMES.length * SIZES.length) + " PNGs in " + OUT_DIR);
    }

    private static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
}
