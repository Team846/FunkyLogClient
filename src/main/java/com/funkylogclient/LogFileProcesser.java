package com.funkylogclient;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.Scanner;

import javafx.stage.Stage;
import javafx.stage.FileChooser;

public class LogFileProcesser {

	private static Scanner input;
	private static File file;

	public static void selectFile(Stage stage) {
		try {
			FileChooser fileChooser = new FileChooser();
			FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("FunkyLogs File", "*.log846");
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
		try {
			input = new Scanner(file);
		} catch (FileNotFoundException ex) {
			System.out.println("File not found");
			System.exit(1);
		}
		LinkedList<Message> messages = new LinkedList<Message>();
		while (input.hasNextLine()) {
			Message log = new Message(input.nextLine());
			if (log.getValid()) {
				messages.add(log);
			}
		}
		SavedFunkyLogs.displaySavedLogs(messages, primaryStage, file.getName());
	}

}
