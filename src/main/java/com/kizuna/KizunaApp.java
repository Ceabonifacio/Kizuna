package com.kizuna;

import com.kizuna.ui.MainView;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.stage.Stage;

public final class KizunaApp extends Application {
    private AppContext context;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Kizuna | Campus Clubs");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        StackPane loading = new StackPane(); loading.getStyleClass().add("app-root");
        VBox body = new VBox(16, new Label("Kizuna"), new ProgressIndicator(), new Label("Preparing your local club network…"));
        body.setAlignment(Pos.CENTER); body.setPadding(new Insets(32));
        loading.getChildren().add(body);
        Scene loadingScene = new Scene(loading, 1100, 720);
        loadingScene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setScene(loadingScene);
        stage.show();
        loadContext(stage, loadingScene, body);
    }

    private void loadContext(Stage stage, Scene loadingScene, VBox body) {
        Task<AppContext> initialization = new Task<>() {
            @Override protected AppContext call() throws Exception { return AppContext.create(); }
        };
        initialization.setOnSucceeded(event -> {
            context = initialization.getValue();
            MainView mainView = new MainView(context, stage);
            Scene scene = new Scene(mainView.root(), 1100, 720);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
        });
        initialization.setOnFailed(event -> {
            Label problem = new Label("Kizuna could not prepare its local database. Check that your home folder is writable and restart the app.");
            problem.setWrapText(true); problem.getStyleClass().add("message-error");
            Button retry = new Button("Try again"); retry.getStyleClass().add("button-primary"); retry.setMinHeight(42);
            retry.setOnAction(action -> {
                body.getChildren().setAll(new Label("Kizuna"), new ProgressIndicator(), new Label("Preparing your local club network…"));
                stage.setScene(loadingScene); loadContext(stage, loadingScene, body);
            });
            body.getChildren().setAll(new Label("Kizuna"), problem, retry);
        });
        Thread thread = new Thread(initialization, "kizuna-initialize"); thread.setDaemon(true); thread.start();
    }

    @Override
    public void stop() {
        if (context != null) context.auth.logout();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
