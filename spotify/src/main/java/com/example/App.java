package com.example;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javazoom.jl.player.Player;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

// declares a class for the app
public class App {
  private static final long SEEK_STEP_MICROSECONDS = 15_000_000L;

  // the current audio clip
  private static Clip audioClip;
  private static AudioInputStream currentAudioStream;
  private static Player mp3Player;
  private static Thread mp3Thread;
  private static Song currentSong;
  private static boolean isPaused;
  private static long pausedPositionMicroseconds;
  private static final int MAX_RECENT_SONGS = 5;
  private static final List<Song> recentSongs = new ArrayList<>();

  private static Song[] library = new Song[0];
  private static final List<Song> displayedSongs = new ArrayList<>();
  private static JFrame frame;
  private static JList<String> songList;
  private static JList<String> recentList;
  private static DefaultListModel<String> songListModel;
  private static DefaultListModel<String> recentListModel;
  private static JTextField searchField;
  private static JButton pauseButton;
  private static JLabel statusLabel;
  private static JLabel homeLabel;

  // "main" makes this class a java app that can be executed
  public static void main(final String[] args) {
    Song[] loadedLibrary = readAudioLibrary();

    if (loadedLibrary != null) {
      library = loadedLibrary;
    }

    SwingUtilities.invokeLater(App::createAndShowUi);
  }

  /*
   * creates the main window for the app
   */
  public static void createAndShowUi() {
    frame = new JFrame("MyMusicApp");
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        quitApp();
      }
    });

    frame.setLayout(new BorderLayout(10, 10));
    frame.add(createHeaderPanel(), BorderLayout.NORTH);
    frame.add(createContentPanel(), BorderLayout.CENTER);
    frame.add(createSidePanel(), BorderLayout.EAST);
    frame.add(createMediaControlsPanel(), BorderLayout.SOUTH);

    frame.getRootPane().setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    frame.setSize(900, 500);
    frame.setLocationRelativeTo(null);

    refreshSongList(Arrays.asList(library));
    refreshRecentSongs();
    showHome();

    if (library.length == 0) {
      updateStatus("No songs were loaded from the audio library.");
    }

    frame.setVisible(true);
  }

  public static JPanel createHeaderPanel() {
    JPanel headerPanel = new JPanel(new BorderLayout(0, 10));

    JLabel titleLabel = new JLabel("MyMusicApp", SwingConstants.CENTER);
    titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
    headerPanel.add(titleLabel, BorderLayout.NORTH);
    headerPanel.add(createButtonPanel(), BorderLayout.SOUTH);

    return headerPanel;
  }

  public static JPanel createButtonPanel() {
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

    JButton homeButton = new JButton("Home");
    JButton searchButton = new JButton("Search");
    JButton libraryButton = new JButton("Library");
    JButton quitButton = new JButton("Quit");

    homeButton.addActionListener(event -> showHome());
    searchButton.addActionListener(event -> searchSongs());
    libraryButton.addActionListener(event -> showLibrary());
    quitButton.addActionListener(event -> quitApp());

    buttonPanel.add(homeButton);
    buttonPanel.add(searchButton);
    buttonPanel.add(libraryButton);
    buttonPanel.add(quitButton);

    return buttonPanel;
  }

  public static JPanel createMediaControlsPanel() {
    JPanel mediaControlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
    mediaControlsPanel.setBorder(BorderFactory.createTitledBorder("Media Controls"));

    JButton backButton = new JButton("Back 15s");
    JButton playButton = new JButton("Play");
    pauseButton = new JButton("Pause");
    JButton forwardButton = new JButton("Forward 15s");
    JButton stopButton = new JButton("Stop");

    backButton.addActionListener(event -> skipBackward());
    playButton.addActionListener(event -> playSelectedSong());
    pauseButton.addActionListener(event -> pauseOrResume());
    forwardButton.addActionListener(event -> skipForward());
    stopButton.addActionListener(event -> stop());

    mediaControlsPanel.add(backButton);
    mediaControlsPanel.add(playButton);
    mediaControlsPanel.add(pauseButton);
    mediaControlsPanel.add(forwardButton);
    mediaControlsPanel.add(stopButton);

    return mediaControlsPanel;
  }

  public static JPanel createContentPanel() {
    JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
    contentPanel.setBorder(BorderFactory.createTitledBorder("Songs"));

    JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
    JLabel searchLabel = new JLabel("Search by title:");
    searchField = new JTextField();

    searchField.addActionListener(event -> searchSongs());

    searchPanel.add(searchLabel, BorderLayout.WEST);
    searchPanel.add(searchField, BorderLayout.CENTER);

    songListModel = new DefaultListModel<>();
    songList = new JList<>(songListModel);
    songList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    contentPanel.add(searchPanel, BorderLayout.NORTH);
    contentPanel.add(new JScrollPane(songList), BorderLayout.CENTER);

    return contentPanel;
  }

  public static JPanel createSidePanel() {
    JPanel sidePanel = new JPanel(new BorderLayout(0, 10));
    sidePanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

    homeLabel = new JLabel();
    homeLabel.setVerticalAlignment(SwingConstants.TOP);
    homeLabel.setBorder(BorderFactory.createTitledBorder("Home"));

    recentListModel = new DefaultListModel<>();
    recentList = new JList<>(recentListModel);
    recentList.setFocusable(false);
    JScrollPane recentScrollPane = new JScrollPane(recentList);
    recentScrollPane.setBorder(BorderFactory.createTitledBorder("Recently Played"));

    statusLabel = new JLabel("Ready");
    statusLabel.setBorder(BorderFactory.createTitledBorder("Status"));

    sidePanel.add(homeLabel, BorderLayout.NORTH);
    sidePanel.add(recentScrollPane, BorderLayout.CENTER);
    sidePanel.add(statusLabel, BorderLayout.SOUTH);

    return sidePanel;
  }

  public static void showHome() {
    homeLabel.setText(
      "<html><b>Welcome to MyMusicApp</b><br><br>"
        + "Use Library to show all songs.<br>"
        + "Use Search to find a song by title.<br>"
        + "Select a song, then press Play.<br>"
        + "Press Stop to end playback.</html>");
    refreshRecentSongs();
    updateStatus("Home view ready.");
  }

  public static void showLibrary() {
    refreshSongList(Arrays.asList(library));
    homeLabel.setText(
      "<html><b>Library</b><br><br>Select a song from the list and press Play.</html>");
    updateStatus("Showing all songs.");
  }

  public static void searchSongs() {
    if (library.length == 0) {
      updateStatus("No songs were loaded from the library.");
      return;
    }

    String searchText = searchField.getText().trim().toLowerCase(Locale.ROOT);

    if (searchText.isEmpty()) {
      updateStatus("Enter a song title to search.");
      return;
    }

    List<Song> matches = new ArrayList<>();

    for (Song song : library) {
      if (song.name().toLowerCase(Locale.ROOT).contains(searchText)) {
        matches.add(song);
      }
    }

    if (matches.isEmpty()) {
      updateStatus("No matching song was found.");
      return;
    }

    refreshSongList(matches);
    songList.setSelectedIndex(0);
    homeLabel.setText(
      "<html><b>Search Results</b><br><br>Found "
        + matches.size()
        + " matching song(s).</html>");
    updateStatus("Search results updated.");
  }

  public static void refreshSongList(List<Song> songs) {
    displayedSongs.clear();
    displayedSongs.addAll(songs);
    songListModel.clear();

    for (Song song : displayedSongs) {
      songListModel.addElement(formatSong(song));
    }
  }

  public static void refreshRecentSongs() {
    if (recentListModel == null) {
      return;
    }

    recentListModel.clear();

    if (recentSongs.isEmpty()) {
      recentListModel.addElement("No songs played yet.");
      return;
    }

    for (Song song : recentSongs) {
      recentListModel.addElement(formatSong(song));
    }
  }

  public static void playSelectedSong() {
    if (displayedSongs.isEmpty()) {
      updateStatus("No songs are available to play.");
      return;
    }

    int selectedIndex = songList.getSelectedIndex();

    if (selectedIndex < 0 || selectedIndex >= displayedSongs.size()) {
      updateStatus("Select a song from the list first.");
      return;
    }

    play(displayedSongs.get(selectedIndex));
  }

  /*
   * plays an audio file
   */
  public static void play(Song selectedSong) {
    if (selectedSong == null) {
      updateStatus("No song selected.");
      return;
    }

    final String filename = selectedSong.fileName();
    final URL audioResource = App.class.getResource("/com/example/wav/" + filename);

    if (audioResource == null) {
      updateStatus("Unable to find the audio file " + filename + ".");
      return;
    }

    stopCurrentPlayback();
    currentSong = selectedSong;
    isPaused = false;
    pausedPositionMicroseconds = 0;
    updatePauseButtonLabel();

    try {
      if (filename.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
        playMp3(audioResource, selectedSong);
        return;
      }

      audioClip = AudioSystem.getClip();
      currentAudioStream = createPlayableAudioStream(audioResource);
      audioClip.open(currentAudioStream);
      audioClip.setMicrosecondPosition(0);
      audioClip.start();
      addRecentSong(selectedSong);
      refreshRecentSongs();
      updateStatus("Now playing: " + formatSong(selectedSong));
    } catch (Exception e) {
      currentSong = null;
      updatePauseButtonLabel();
      updateStatus("Unable to play the selected audio file.");
      e.printStackTrace();
    }
  }

  public static void playMp3(URL audioResource, Song selectedSong) {
    mp3Thread = new Thread(() -> {
      try (InputStream audioStream = audioResource.openStream()) {
        mp3Player = new Player(audioStream);
        addRecentSong(selectedSong);
        SwingUtilities.invokeLater(() -> {
          refreshRecentSongs();
          updateStatus("Now playing: " + formatSong(selectedSong));
        });
        mp3Player.play();
      } catch (Exception e) {
        currentSong = null;
        updatePauseButtonLabel();
        SwingUtilities.invokeLater(() ->
          updateStatus("Unable to play the selected audio file."));
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

    if (currentAudioStream != null) {
      try {
        currentAudioStream.close();
      } catch (Exception e) {
        // ignore cleanup errors
      }
      currentAudioStream = null;
    }

    if (mp3Player != null) {
      mp3Player.close();
      mp3Player = null;
    }

    if (mp3Thread != null) {
      mp3Thread.interrupt();
      mp3Thread = null;
    }

    currentSong = null;
    isPaused = false;
    pausedPositionMicroseconds = 0;
    updatePauseButtonLabel();
  }

  public static AudioInputStream createPlayableAudioStream(URL audioResource) throws Exception {
    final AudioInputStream sourceStream = AudioSystem.getAudioInputStream(audioResource);
    final AudioFormat sourceFormat = sourceStream.getFormat();

    AudioFormat targetFormat = new AudioFormat(
      AudioFormat.Encoding.PCM_SIGNED,
      sourceFormat.getSampleRate(),
      16,
      sourceFormat.getChannels(),
      sourceFormat.getChannels() * 2,
      sourceFormat.getSampleRate(),
      false);

    if (AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
      return AudioSystem.getAudioInputStream(targetFormat, sourceStream);
    }

    return sourceStream;
  }

  public static void addRecentSong(Song song) {
    recentSongs.removeIf(recentSong ->
      recentSong.name().equals(song.name()) && recentSong.artist().equals(song.artist()));
    recentSongs.add(0, song);

    if (recentSongs.size() > MAX_RECENT_SONGS) {
      recentSongs.remove(recentSongs.size() - 1);
    }
  }

  public static void stop() {
    if (audioClip != null || mp3Player != null || isPaused) {
      stopCurrentPlayback();
      updateStatus("Playback stopped.");
      return;
    }

    updateStatus("No song is currently playing.");
  }

  public static void pauseOrResume() {
    if (currentSong == null) {
      updateStatus("No song is currently playing.");
      return;
    }

    if (currentSong.fileName().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
      updateStatus("Pause is available for WAV playback only.");
      return;
    }

    if (audioClip == null) {
      updateStatus("No song is currently playing.");
      return;
    }

    if (isPaused) {
      audioClip.setMicrosecondPosition(pausedPositionMicroseconds);
      audioClip.start();
      isPaused = false;
      updatePauseButtonLabel();
      updateStatus("Resumed: " + formatSong(currentSong));
      return;
    }

    pausedPositionMicroseconds = audioClip.getMicrosecondPosition();
    audioClip.stop();
    isPaused = true;
    updatePauseButtonLabel();
    updateStatus("Paused: " + formatSong(currentSong));
  }

  public static void skipBackward() {
    skipClipPosition(-SEEK_STEP_MICROSECONDS);
  }

  public static void skipForward() {
    skipClipPosition(SEEK_STEP_MICROSECONDS);
  }

  public static void skipClipPosition(long offsetMicroseconds) {
    if (currentSong == null) {
      updateStatus("No song is currently playing.");
      return;
    }

    if (currentSong.fileName().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
      updateStatus("Skipping is available for WAV playback only.");
      return;
    }

    if (audioClip == null) {
      updateStatus("No song is currently playing.");
      return;
    }

    long clipLength = audioClip.getMicrosecondLength();
    long currentPosition = isPaused
      ? pausedPositionMicroseconds
      : audioClip.getMicrosecondPosition();
    long targetPosition = Math.max(0, Math.min(currentPosition + offsetMicroseconds, clipLength));

    boolean wasRunning = audioClip.isRunning();
    audioClip.stop();
    audioClip.setMicrosecondPosition(targetPosition);

    if (isPaused) {
      pausedPositionMicroseconds = targetPosition;
      updateStatus("Moved to " + formatTime(targetPosition) + " in " + formatSong(currentSong));
      return;
    }

    if (wasRunning) {
      audioClip.start();
    }

    updateStatus("Moved to " + formatTime(targetPosition) + " in " + formatSong(currentSong));
  }

  public static void quitApp() {
    stopCurrentPlayback();

    if (frame != null) {
      frame.dispose();
    }

    System.exit(0);
  }

  public static void updateStatus(String message) {
    if (statusLabel == null) {
      return;
    }

    if (SwingUtilities.isEventDispatchThread()) {
      statusLabel.setText(message);
      return;
    }

    SwingUtilities.invokeLater(() -> statusLabel.setText(message));
  }

  public static String formatSong(Song song) {
    return song.name() + " - " + song.artist();
  }

  public static void updatePauseButtonLabel() {
    if (pauseButton == null) {
      return;
    }

    String label = isPaused ? "Resume" : "Pause";

    if (SwingUtilities.isEventDispatchThread()) {
      pauseButton.setText(label);
      return;
    }

    SwingUtilities.invokeLater(() -> pauseButton.setText(label));
  }

  public static String formatTime(long microseconds) {
    long totalSeconds = microseconds / 1_000_000L;
    long minutes = totalSeconds / 60;
    long seconds = totalSeconds % 60;
    return String.format("%d:%02d", minutes, seconds);
  }

  // read the audio library of music
  public static Song[] readAudioLibrary() {
    final InputStream jsonStream = App.class.getResourceAsStream("/com/example/audio-library.json");

    if (jsonStream == null) {
      return null;
    }

    try (InputStreamReader fileReader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8);
         JsonReader reader = new JsonReader(fileReader)) {
      return new Gson().fromJson(reader, Song[].class);
    } catch (Exception e) {
      return null;
    }
  }
}
