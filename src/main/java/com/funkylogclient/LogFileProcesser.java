package com.funkylogclient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

public class LogFileProcesser {

	private static File file;

	public static void selectFile(Stage stage) {
		try {
			FileChooser fileChooser = new FileChooser();
			FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("ChimpCheck File", "*.log846");
			fileChooser.getExtensionFilters().add(extFilter);
			fileChooser.setTitle("Open Log File");

			fileChooser.setInitialDirectory(new File(FunkyLogSorter.getLogFileDirectory()));

			file = fileChooser.showOpenDialog(stage);
			readFile(file, stage);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

	}

	public void setFile(String pathname) {
		file = new File(pathname);
	}

	public File getFile() {
		if (file != null)
			System.out.println(file.getAbsolutePath());
		return file;
	}

	public static void readFile(File file, Stage primaryStage) {
		Task<List<Message>> readTask = new Task<List<Message>>() {
			@Override
			protected List<Message> call() throws Exception {
				List<Message> messages = new ArrayList<>();
				try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
					String line;
					while ((line = reader.readLine()) != null) {
						Message log = new Message(line);
						if (log.getValid()) {
							messages.add(log);
						}
					}
				} catch (IOException ex) {
					System.err.println("Error reading file: " + ex.getMessage());
				}
				return messages;
			}
		};
		
		readTask.setOnSucceeded(e -> {
			List<Message> messages = readTask.getValue();
			LinkedList<Message> messageList = new LinkedList<>(messages);
			Platform.runLater(() -> {
				SavedFunkyLogs.displaySavedLogs(messageList, primaryStage, file.getName());
			});
		});
		
		readTask.setOnFailed(e -> {
			System.err.println("Failed to read file: " + readTask.getException().getMessage());
		});
		
		Thread readThread = new Thread(readTask);
		readThread.setDaemon(true);
		readThread.start();
	}

}
