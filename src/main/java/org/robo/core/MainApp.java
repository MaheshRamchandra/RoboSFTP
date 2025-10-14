package org.robo.core;


import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // Load FXML file
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();

        // Create scene and attach stylesheet
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        // Stage setup
        stage.setTitle("SFTP File Generator");
        stage.setScene(scene);
        stage.setMinWidth(920);
        stage.setMinHeight(820);
        stage.show();
    }
    public static void main(String[] args) {
        launch();
    }
}
