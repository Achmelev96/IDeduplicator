package Presentation;

import Model.DuplicateScanner;
import Model.HashCache;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MainFX extends Application {

    private final TilePane tilePane = new TilePane();
    private final Button deleteBtn = new Button("Del");
    private final Button setPathBtn = new Button("Path");
    private final Button scanBtn = new Button("Scan");

    private final Set<Path> selected = ConcurrentHashMap.newKeySet();
    private final Map<Path, PhotoCard> pathToCard = new ConcurrentHashMap<>();
    private final List<Path> allImages = Collections.synchronizedList(new ArrayList<>());

    private static final int UI_BATCH_SIZE = 100;
    private static final double DUP_THRESHOLD = 0.20;

    private final HashCache hashCache = new HashCache("./idedup_db");
    private final DuplicateScanner duplicateScanner = new DuplicateScanner(hashCache);

    private static final String DARK_CSS = """
.root {
  -fx-base: #2b2b2b;
  -fx-background: #2b2b2b;
  -fx-control-inner-background: #333333;
  -fx-accent: #4f9cff;
  -fx-focus-color: -fx-accent;
  -fx-faint-focus-color: rgba(79,156,255,0.25);
}
.label { -fx-text-fill: #e6e6e6; }
.button { -fx-background-color: #3a3a3a; -fx-text-fill: #e6e6e6; }
.button:hover { -fx-background-color: #444444; }
""";

    @Override
    public void start(final Stage stage) {

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar toolbar = new ToolBar(scanBtn, spacer, setPathBtn, deleteBtn);
        toolbar.setPrefWidth(Double.MAX_VALUE);

        deleteBtn.setDisable(true);

        tilePane.setHgap(10);
        tilePane.setVgap(10);
        tilePane.setPadding(new Insets(10));
        tilePane.setPrefColumns(5);
        tilePane.setCache(true);

        ScrollPane scrollPane = new ScrollPane(tilePane);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(scrollPane);

        setPathBtn.setOnAction(e -> chooseAndScanFolder(stage));
        deleteBtn.setOnAction(e -> deleteSelected());
        scanBtn.setOnAction(e -> runDuplicateScan());

        stage.setTitle("IDeduplicator");
        Scene scene = new Scene(root, 1000, 600);
        scene.getStylesheets().add("data:text/css," + DARK_CSS.replace(" ", "%20"));
        stage.setScene(scene);
        stage.show();
    }

    private void chooseAndScanFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose folder with images");
        File dir = chooser.showDialog(stage);
        if (dir == null) return;

        tilePane.getChildren().clear();
        selected.clear();
        pathToCard.clear();
        allImages.clear();
        updateDeleteButton();

        Path root = dir.toPath();

        Thread t = new Thread(() -> {
            ImageScanner scanner = new ImageScanner();
            List<PhotoCard> batch = new ArrayList<>(UI_BATCH_SIZE);

            scanner.scan(root, imagePath -> {
                PhotoCard card = PhotoCardFactory.create(imagePath, this::onToggleSelection);

                pathToCard.put(imagePath, card);
                allImages.add(imagePath);

                batch.add(card);

                if (batch.size() >= UI_BATCH_SIZE) {
                    List<PhotoCard> toAdd = new ArrayList<>(batch);
                    batch.clear();
                    Platform.runLater(() -> tilePane.getChildren().addAll(toAdd));
                }
            });

            if (!batch.isEmpty()) {
                List<PhotoCard> toAdd = new ArrayList<>(batch);
                Platform.runLater(() -> tilePane.getChildren().addAll(toAdd));
            }
        }, "image-scan-thread");

        t.setDaemon(true);
        t.start();
    }

    private void runDuplicateScan() {
        if (allImages.isEmpty()) return;

        scanBtn.setDisable(true);

        Platform.runLater(() -> {
            for (PhotoCard c : pathToCard.values()) c.setSelected(false);
            selected.clear();
            updateDeleteButton();
        });

        List<Path> snapshot = new ArrayList<>(allImages);

        duplicateScanner.scanAsync(snapshot, DUP_THRESHOLD).thenAccept(result -> {
            List<Path> list = new ArrayList<>(result.toSelect());
            final int batchSize = 200;

            for (int i = 0; i < list.size(); i += batchSize) {
                int from = i;
                int to = Math.min(i + batchSize, list.size());
                List<Path> part = list.subList(from, to);

                Platform.runLater(() -> {
                    for (Path p : part) {
                        PhotoCard card = pathToCard.get(p);
                        if (card != null) {
                            card.setSelected(true);
                            selected.add(p);
                        }
                    }
                    updateDeleteButton();
                });
            }

        }).whenComplete((ok, ex) -> {
            if (ex != null) ex.printStackTrace();
            Platform.runLater(() -> scanBtn.setDisable(false));
        });
    }

    private void onToggleSelection(Path path, boolean isSelectedNow) {
        if (isSelectedNow) selected.add(path);
        else selected.remove(path);
        updateDeleteButton();
    }

    private void updateDeleteButton() {
        Platform.runLater(() -> deleteBtn.setDisable(selected.isEmpty()));
    }

    private void deleteSelected() {
        Set<Path> toDelete = new HashSet<>(selected);

        Thread t = new Thread(() -> {
            for (Path p : toDelete) {
                try {
                    Files.deleteIfExists(p);
                    hashCache.remove(p);

                    PhotoCard card = pathToCard.remove(p);
                    if (card != null) {
                        Platform.runLater(() -> tilePane.getChildren().remove(card));
                    }

                    allImages.remove(p);
                } catch (Exception ignored) { }
            }

            selected.removeAll(toDelete);
            updateDeleteButton();
        }, "delete-thread");

        t.setDaemon(true);
        t.start();
    }

    @Override
    public void stop() {
        duplicateScanner.shutdown();
        hashCache.close();
    }
}
