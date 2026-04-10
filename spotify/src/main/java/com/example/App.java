

package com.example;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import java.io.*;
import java.net.URL;
import java.util.*;
import javax.sound.sampled.*;

// declares a class for the app
public class App {

  // the current audio clip
  private static Clip audioClip;

  // "main" makes this class a java app that can be executed
  public static void main(final String[] args) {
    // reading audio library from json file
    Song[] library = readAudioLibrary();

    // create a scanner for user input
    Scanner input = new Scanner(System.in);

    String userInput = "";
    while (!userInput.equals("q")) {
      menu();

      // get input
      userInput = input.nextLine();

      // accept upper or lower case commands
      userInput = userInput.toLowerCase();

      // do something
      handleMenu(userInput, library);
    }

    // close the scanner
    input.close();
  }

  /*
   * displays the menu for the app
   */
  public static void menu() {
    System.out.println("---- SpotifyLikeApp ----");
    System.out.println("[H]ome");
    System.out.println("[S]earch by title");
    System.out.println("[L]ibrary");
    System.out.println("[P]lay");
    System.out.println("[Q]uit");

    System.out.println("");
    System.out.print("Enter q to Quit:");
  }

  /*
   * handles the user input for the app
   */
  public static void handleMenu(String userInput, Song[] library) {
    switch (userInput) {
      case "h":
        System.out.println("-->Home<--");
        break;
      case "s":
        System.out.println("-->Search by title<--");
        break;
      case "l":
        System.out.println("-->Library<--");
        printLibrary(library);
        break;
      case "p":
        System.out.println("-->Play<--");
        play(library);
        break;
      case "q":
        System.out.println("-->Quit<--");
        break;
      default:
        break;
    }
  }

  /*
   * plays an audio file
   */
  public static void play(Song[] library) {
    if (library == null || library.length == 0) {
      System.out.println("No songs were loaded from the library.");
      return;
    }

    // get the filePath and open a audio file
    final Integer i = 3;
    final String filename = library[i].fileName();
    final URL audioResource = App.class.getResource("/com/example/wav/" + filename);

    if (audioResource == null) {
      System.out.printf("ERROR: unable to find the audio file %s\n", filename);
      return;
    }

    // stop the current song from playing, before playing the next one
    if (audioClip != null) {
      audioClip.close();
    }

    try {
      // create clip
      audioClip = AudioSystem.getClip();

      // get input stream
      final AudioInputStream in = AudioSystem.getAudioInputStream(audioResource);

      audioClip.open(in);
      audioClip.setMicrosecondPosition(0);
      audioClip.start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void printLibrary(Song[] library) {
    if (library == null || library.length == 0) {
      System.out.println("No songs were loaded from the library.");
      return;
    }

    for (int i = 0; i < library.length; i++) {
      Song song = library[i];
      System.out.printf("%d. %s - %s\n", i + 1, song.name(), song.artist());
    }
  }

  // read the audio library of music
  public static Song[] readAudioLibrary() {
    Song[] library = null;
    final InputStream jsonStream =
      App.class.getResourceAsStream("/com/example/audio-library.json");

    if (jsonStream == null) {
      System.out.println("ERROR: unable to find the audio-library.json resource");
      System.out.println();
      return null;
    }

    try (
      InputStreamReader fileReader = new InputStreamReader(jsonStream);
      JsonReader reader = new JsonReader(fileReader)
    ) {
      System.out.println("Reading the audio-library.json resource");
      library = new Gson().fromJson(reader, Song[].class);
    } catch (Exception e) {
      System.out.println("ERROR: unable to read the audio-library.json resource");
      System.out.println();
    }

    return library;
  }
}
