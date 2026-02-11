package Presentation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class ImageScanner {

    public void scan(Path root, Consumer<Path> onImageFound) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(this::looksLikeImage)
                    .forEach(onImageFound);
        } catch (IOException ignored) {
        }
    }

    private boolean looksLikeImage(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".bmp")
                || name.endsWith(".gif")
                || name.endsWith(".webp");
    }
}
