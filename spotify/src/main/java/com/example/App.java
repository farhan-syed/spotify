package com.example;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import javazoom.jl.player.Player;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

// declares a class for the app
public class App {

  // the current audio clip
  private static Clip audioClip;
  private static Player mp3Player;
  private static Thread mp3Thread;
  private static final int MAX_RECENT_SONGS = 5;
  private static final List<Song> recentSongs = new ArrayList<>();

  // "main" makes this class a java app that can be executed
  public static void main(final String[] args) {
    Song[] library = readAudioLibrary();
    Scanner input = new Scanner(System.in);
    String userInput = "";

    while (!userInput.equals("q")) {
      menu();
      userInput = input.nextLine();
      userInput = userInput.trim().toLowerCase(Locale.ROOT);
      handleMenu(userInput, library, input);
    }

    input.close();
  }

  /*
   * displays the menu for the app
   */
  public static void menu() {
    System.out.println();
    System.out.println("---- MyMusicApp ----");
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
        System.out.println("Please choose a valid menu option.");
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
      System.out.printf("Unable to find the audio file %s.%n", filename);
      return;
    }

    stopCurrentPlayback();

    try {
      if (filename.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
        playMp3(audioResource, selectedSong);
        return;
      }

      audioClip = AudioSystem.getClip();
      final AudioInputStream in = AudioSystem.getAudioInputStream(audioResource);
      audioClip.open(in);
      audioClip.setMicrosecondPosition(0);
      audioClip.start();
      addRecentSong(selectedSong);
      System.out.printf("Now playing: %s - %s%n", selectedSong.name(), selectedSong.artist());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void playMp3(URL audioResource, Song selectedSong) {
    mp3Thread = new Thread(() -> {
      try (InputStream audioStream = audioResource.openStream()) {
        mp3Player = new Player(audioStream);
        addRecentSong(selectedSong);
        System.out.printf("Now playing: %s - %s%n", selectedSong.name(), selectedSong.artist());
        mp3Player.play();
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        mp3Player = null;
        mp3Thread = null;
      }
    });

    mp3Thread.start();
  }

  public static void stopCurrentPlayback() {
    if (audioClip != null) {
      audioClip.stop();
      audioClip.close();
      audioClip = null;
    }

    if (mp3Player != null) {
      mp3Player.close();
      mp3Player = null;
    }

    if (mp3Thread != null) {
      mp3Thread.interrupt();
      mp3Thread = null;
    }
  }

  public static void addRecentSong(Song song) {
    recentSongs.removeIf(recentSong ->
      recentSong.name().equals(song.name()) && recentSong.artist().equals(song.artist()));
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
      final int songNumber = Integer.parseInt(choice);
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
      final Song song = library[i];
      if (song.name().toLowerCase().contains(searchText)) {
        System.out.printf("Found: %s - %s%n", song.name(), song.artist());
        play(library, i);
        return;
      }
    }

    System.out.println("No matching song was found.");
  }

  public static void stop() {
    if ((audioClip != null && audioClip.isRunning()) || mp3Player != null) {
      stopCurrentPlayback();
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
      final Song song = library[i];
      System.out.printf("%d. %s - %s%n", i + 1, song.name(), song.artist());
    }
  }

  public static void showHome(Song[] library) {
    System.out.println("-->Home<--");
    System.out.println("Welcome to MyMusicApp");
    System.out.println();
    System.out.println("Recently played songs:");

    if (recentSongs.isEmpty()) {
      System.out.println("No songs played yet.");
    } else {
      for (int i = 0; i < recentSongs.size(); i++) {
        final Song song = recentSongs.get(i);
        System.out.printf("%d. %s - %s%n", i + 1, song.name(), song.artist());
      }
    }

    System.out.println();
    System.out.println("Instructions: Press L to view the library and P to play a song.");
  }

  // read the audio library of music
  public static Song[] readAudioLibrary() {
    final InputStream jsonStream = App.class.getResourceAsStream("/com/example/audio-library.json");

    if (jsonStream == null) {
      System.out.println("Unable to find the audio library.");
      return null;
    }

    try (InputStreamReader fileReader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8);
         JsonReader reader = new JsonReader(fileReader)) {
      return new Gson().fromJson(reader, Song[].class);
    } catch (Exception e) {
      System.out.println("Unable to read the audio library.");
      return null;
    }
  }
}
