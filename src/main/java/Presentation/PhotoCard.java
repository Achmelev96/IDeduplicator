package Presentation;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.nio.file.Path;
import java.util.function.BiConsumer;

public final class PhotoCard extends StackPane {

    private static final double CARD_W = 180;
    private static final double CARD_H = 140;

    private final Path path;
    private final Circle selectCircle;
    private boolean selected;

    public PhotoCard(Path path, BiConsumer<Path, Boolean> onToggle) {
        this.path = path;

        setPrefSize(CARD_W, CARD_H);
        setMaxSize(CARD_W, CARD_H);
        setCache(true);

        setStyle("""
            -fx-background-color: #3a3a3a;
            -fx-background-radius: 10;
            -fx-border-radius: 10;
        """);

        String uri = path.toUri().toString();
        Image img = new Image(uri, CARD_W, CARD_H, true, true, true);

        ImageView imageView = new ImageView(img);
        imageView.setFitWidth(CARD_W);
        imageView.setFitHeight(CARD_H);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        selectCircle = new Circle(8);
        selectCircle.setStroke(Color.WHITE);
        selectCircle.setFill(Color.TRANSPARENT);

        StackPane.setAlignment(selectCircle, Pos.TOP_RIGHT);
        StackPane.setMargin(selectCircle, new Insets(6));

        getChildren().addAll(imageView, selectCircle);

        Runnable toggle = () -> {
            setSelected(!selected);
            onToggle.accept(this.path, this.selected);
        };

        selectCircle.setOnMouseClicked(e -> { e.consume(); toggle.run(); });
        setOnMouseClicked(e -> toggle.run());
    }

    public Path getPath() {
        return path;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean value) {
        if (this.selected == value) return;
        this.selected = value;

        if (selected) {
            selectCircle.setFill(Color.web("#4f9cff"));
            setStyle("""
                -fx-background-color: #3a3a3a;
                -fx-background-radius: 10;
                -fx-border-color: #4f9cff;
                -fx-border-width: 2;
                -fx-border-radius: 10;
            """);
        } else {
            selectCircle.setFill(Color.TRANSPARENT);
            setStyle("""
                -fx-background-color: #3a3a3a;
                -fx-background-radius: 10;
                -fx-border-radius: 10;
            """);
        }
    }
}
