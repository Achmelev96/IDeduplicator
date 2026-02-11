package Presentation;

import java.nio.file.Path;
import java.util.function.BiConsumer;

public final class PhotoCardFactory {

    private PhotoCardFactory() {}

    public static PhotoCard create(Path path, BiConsumer<Path, Boolean> onToggle) {
        return new PhotoCard(path, onToggle);
    }
}
