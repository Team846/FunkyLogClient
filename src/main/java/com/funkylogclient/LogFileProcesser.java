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


	public static void selectFile(Stage stage)
	{
		try {
			FileChooser fil_chooser = new FileChooser();
			// get the file selected
			file = fil_chooser.showOpenDialog(stage);
			readFile(file);
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
		}

	}

	public void setFile(String pathname) {
		file = new File(pathname);
	}

	public File getFile() {
		if (file != null) System.out.println(file.getAbsolutePath());
		return file;
	}

	public static LinkedList<Message> readFile(File file) {
		try {
			input = new Scanner(file);
		}
		catch ( FileNotFoundException ex) {
			System.out.println("could not find file"); //testing
			System.exit(1);
		}
		LinkedList<Message> messages = new LinkedList<Message>();
		while (input.hasNextLine()) {
			Message log = new Message(input.nextLine(), true);
			if (log.getValid()) {
				messages.add(log);
			}
		}
		for (Message m : messages) { //testing
			System.out.println(m);
		}
		return messages; //change to popup method call
	}

}

