package Presentation;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PhotoCardFactory {

    private PhotoCardFactory() {}

    public static PhotoCard create(
            Path path,
            BiConsumer<Path, Boolean> onToggle,
            Consumer<Path> onPreview
    ) {
        return new PhotoCard(path, onToggle, onPreview);
    }
}
