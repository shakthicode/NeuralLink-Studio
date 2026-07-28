package com.simulink.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.Bloom;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.InputStream;

/** Animated startup splash packaged entirely with the application. */
public final class AnimatedSplash {
    private static final double WIDTH = 720;
    private static final double HEIGHT = 460;
    private static final Duration LOAD_DURATION = Duration.seconds(2.2);

    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label progressLabel = new Label("Initialising workspace… 0%");

    public AnimatedSplash() {
        StackPane root = new StackPane();
        root.setPrefSize(WIDTH, HEIGHT);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #070b18, #111936 48%, #071421);"
                + "-fx-background-radius: 20; -fx-border-radius: 20;"
                + "-fx-border-color: rgba(63,219,255,0.35); -fx-border-width: 1;");
        root.setEffect(new DropShadow(36, Color.rgb(0, 207, 255, 0.28)));

        ImageView brain = imageView("/splash/brain_glow.png", 310);
        brain.setOpacity(0.92);
        brain.setEffect(new Bloom(0.18));

        ImageView core = imageView("/splash/brain_transparent.png", 265);
        core.setOpacity(0.96);

        StackPane visual = new StackPane(brain, core);
        visual.setPrefHeight(285);

        Label title = new Label("NeuralLink Studio");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 38));
        title.setTextFill(Color.WHITE);
        title.setEffect(new DropShadow(12, Color.rgb(0, 205, 255, 0.65)));

        Label subtitle = new Label("Graphical Block-Diagram Simulator");
        subtitle.setFont(Font.font("Segoe UI", 15));
        subtitle.setTextFill(Color.web("#94dff5"));

        progressBar.setPrefWidth(410);
        progressBar.setPrefHeight(12);
        progressBar.setStyle("-fx-accent: #20d7ff; -fx-control-inner-background: rgba(255,255,255,0.08);");
        progressLabel.setFont(Font.font("Segoe UI", 12));
        progressLabel.setTextFill(Color.web("#b8eaff"));

        Region spacer = new Region();
        spacer.setPrefHeight(3);

        VBox content = new VBox(8, visual, title, subtitle, spacer, progressBar, progressLabel);
        content.setAlignment(Pos.CENTER);
        root.getChildren().add(content);

        Scene scene = new Scene(root, WIDTH, HEIGHT, Color.TRANSPARENT);
        InputStream css = AnimatedSplash.class.getResourceAsStream("/splash/splash-style.css");
        if (css != null) {
            scene.getStylesheets().add(AnimatedSplash.class.getResource("/splash/splash-style.css").toExternalForm());
        }
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.centerOnScreen();

        RotateTransition rotate = new RotateTransition(Duration.seconds(8), brain);
        rotate.setByAngle(360);
        rotate.setCycleCount(RotateTransition.INDEFINITE);
        rotate.setInterpolator(Interpolator.LINEAR);
        rotate.play();

        ScaleTransition pulse = new ScaleTransition(Duration.seconds(1.2), core);
        pulse.setFromX(0.96);
        pulse.setFromY(0.96);
        pulse.setToX(1.04);
        pulse.setToY(1.04);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(ScaleTransition.INDEFINITE);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        pulse.play();
    }

    public void show(Runnable onFinished) {
        stage.show();
        Timeline loading = new Timeline(
                new KeyFrame(Duration.ZERO, e -> updateProgress(0.0, "Initialising workspace…")),
                new KeyFrame(Duration.seconds(0.45), e -> updateProgress(0.22, "Loading interface assets…")),
                new KeyFrame(Duration.seconds(0.95), e -> updateProgress(0.48, "Preparing simulation engine…")),
                new KeyFrame(Duration.seconds(1.45), e -> updateProgress(0.72, "Restoring workspace panels…")),
                new KeyFrame(Duration.seconds(1.9), e -> updateProgress(0.92, "Final checks…")),
                new KeyFrame(LOAD_DURATION, e -> updateProgress(1.0, "Ready"))
        );
        loading.setOnFinished(e -> {
            FadeTransition fade = new FadeTransition(Duration.millis(320), stage.getScene().getRoot());
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(done -> {
                stage.close();
                onFinished.run();
            });
            fade.play();
        });
        loading.play();
    }

    private void updateProgress(double value, String text) {
        progressBar.setProgress(value);
        progressLabel.setText(text + " " + Math.round(value * 100) + "%");
    }

    private static ImageView imageView(String resource, double fitWidth) {
        InputStream stream = AnimatedSplash.class.getResourceAsStream(resource);
        if (stream == null) {
            return new ImageView();
        }
        ImageView view = new ImageView(new Image(stream));
        view.setPreserveRatio(true);
        view.setFitWidth(fitWidth);
        view.setSmooth(true);
        return view;
    }
}
