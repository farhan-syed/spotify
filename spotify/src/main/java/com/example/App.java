

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
  private static final int MAX_RECENT_SONGS = 5;
  private static final List<Song> recentSongs = new ArrayList<>();

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
      handleMenu(userInput, library, input);
    }

    // close the scanner
    input.close();
  }

  /*
   * displays the menu for the app
   */
  public static void menu() {
    System.out.println();
    System.out.println("---- SpotifyLikeApp ----");
    System.out.println("[H]ome");
    System.out.println("[S]earch by title");
    System.out.println("[L]ibrary");
    System.out.println("[P]lay");
    System.out.println("S[t]op");
    System.out.println("[Q]uit");
    System.out.println();
    System.out.print("Choose a menu option: ");
  }

  /*
   * handles the user input for the app
   */
  public static void handleMenu(String userInput, Song[] library, Scanner input) {
    switch (userInput) {
      case "h":
        showHome(library);
        break;
      case "s":
        System.out.println("-->Search by title<--");
        searchByTitle(library, input);
        break;
      case "l":
        System.out.println("-->Library<--");
        libraryMenu(library, input);
        break;
      case "p":
        System.out.println("-->Play<--");
        play(library, 0);
        break;
      case "t":
        System.out.println("-->Stop<--");
        stop();
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
  public static void play(Song[] library, int songIndex) {
    if (library == null || library.length == 0) {
      System.out.println("No songs were loaded from the library.");
      return;
    }

    if (songIndex < 0 || songIndex >= library.length) {
      System.out.println("Please choose a valid song number.");
      return;
    }

    final Song selectedSong = library[songIndex];
    final String filename = selectedSong.fileName();
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
      addRecentSong(selectedSong);
      System.out.printf("Now playing: %s - %s\n", selectedSong.name(), selectedSong.artist());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void addRecentSong(Song song) {
    recentSongs.removeIf(
      recentSong ->
        recentSong.name().equals(song.name()) && recentSong.artist().equals(song.artist())
    );
    recentSongs.add(0, song);

    if (recentSongs.size() > MAX_RECENT_SONGS) {
      recentSongs.remove(recentSongs.size() - 1);
    }
  }

  public static void libraryMenu(Song[] library, Scanner input) {
    if (library == null || library.length == 0) {
      System.out.println("No songs were loaded from the library.");
      return;
    }

    printLibrary(library);
    System.out.println();
    System.out.print("Enter a song number to play: ");

    String choice = input.nextLine().trim();

    try {
      int songNumber = Integer.parseInt(choice);
      play(library, songNumber - 1);
    } catch (NumberFormatException e) {
      System.out.println("Please enter a valid number.");
    }
  }

  public static void searchByTitle(Song[] library, Scanner input) {
    if (library == null || library.length == 0) {
      System.out.println("No songs were loaded from the library.");
      return;
    }

    System.out.print("Enter the song title or part of the title: ");
    String searchText = input.nextLine().trim().toLowerCase();

    if (searchText.isEmpty()) {
      System.out.println("Please enter a song title.");
      return;
    }

    for (int i = 0; i < library.length; i++) {
      Song song = library[i];
      if (song.name().toLowerCase().contains(searchText)) {
        System.out.printf("Found: %s - %s\n", song.name(), song.artist());
        play(library, i);
        return;
      }
    }

    System.out.println("No matching song was found.");
  }

  public static void stop() {
    if (audioClip != null && audioClip.isRunning()) {
      audioClip.stop();
      audioClip.close();
      System.out.println("Playback stopped.");
      return;
    }

    System.out.println("No song is currently playing.");
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

  public static void showHome(Song[] library) {
    System.out.println("-->Home<--");
    System.out.println("Welcome to SpotifyLikeApp");
    System.out.println();
    System.out.println("Recently played songs:");

    if (recentSongs.isEmpty()) {
      System.out.println("No songs played yet.");
    } else {
      for (int i = 0; i < recentSongs.size(); i++) {
        Song song = recentSongs.get(i);
        System.out.printf("%d. %s - %s\n", i + 1, song.name(), song.artist());
      }
    }

    System.out.println();
    System.out.println("Instructions: Press L to view the library and P to play a song.");
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
