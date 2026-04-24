package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;

public class App {
  private static final long SEEK_STEP_MICROSECONDS = 15_000_000L;
  private static final String TRACK_SETTINGS_RELATIVE_PATH =
    "src/main/java/com/example/track-settings.json";
  private static final String AUDIO_RELATIVE_DIRECTORY = "src/main/java/com/example/wav/";
  private static final int MAX_RECENT_SONGS = 5;
  private static final int DEFAULT_YEAR = 2020;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  // Playback state
  private static Clip audioClip;
  private static AudioInputStream currentAudioStream;
  private static Player mp3Player;
  private static Thread mp3Thread;
  private static Song currentSong;
  private static boolean isPaused;
  private static long pausedPositionMicroseconds;
  private static final List<Song> recentSongs = new ArrayList<>();

  // UI state
  private static Song[] library = new Song[0];
  private static final List<Song> displayedSongs = new ArrayList<>();
  private static JFrame frame;
  private static JPanel contentPanel;
  private static CardLayout contentCardLayout;
  private static JList<String> songList;
  private static JList<String> homeRecentList;
  private static JTextField searchField;
  private static JButton pauseButton;
  private static JToggleButton favoriteButton;
  private static JLabel metadataTitleLabel;
  private static JLabel metadataArtistLabel;
  private static JLabel metadataYearLabel;
  private static JLabel metadataGenreLabel;
  private static JLabel metadataFilePathLabel;
  private static JTextArea commentsArea;

  public static void main(final String[] args) {
    Song[] loadedLibrary = readAudioLibrary();

    if (loadedLibrary != null) {
      library = loadedLibrary;
    }

    initializeTrackSettings();
    SwingUtilities.invokeLater(App::createAndShowUi);
  }

  // Build the main Swing window and preload the default Home view.
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
    frame.add(createMainPanel(), BorderLayout.CENTER);
    frame.add(createMediaControlsPanel(), BorderLayout.SOUTH);

    frame.getRootPane().setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    frame.setSize(980, 700);
    frame.setLocationRelativeTo(null);

    showHome();

    if (library.length == 0) {
      updateStatus("No songs were loaded from the audio library.", null);
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
    JButton favoritesViewButton = new JButton("Favorites");
    JButton quitButton = new JButton("Quit");

    homeButton.addActionListener(event -> showHome());
    searchButton.addActionListener(event -> searchSongs());
    libraryButton.addActionListener(event -> showLibrary());
    favoritesViewButton.addActionListener(event -> showFavorites());
    quitButton.addActionListener(event -> quitApp());

    buttonPanel.add(homeButton);
    buttonPanel.add(searchButton);
    buttonPanel.add(libraryButton);
    buttonPanel.add(favoritesViewButton);
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
    favoriteButton = new JToggleButton("Favorite");
    favoriteButton.addActionListener(event -> toggleFavoriteForSelectedSong());

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
    mediaControlsPanel.add(favoriteButton);

    return mediaControlsPanel;
  }

  public static JPanel createContentPanel() {
    contentCardLayout = new CardLayout();
    contentPanel = new JPanel(contentCardLayout);
    contentPanel.add(createHomeContentPanel(), "HOME");
    contentPanel.add(createLibraryContentPanel(), "LIBRARY");
    return contentPanel;
  }

  public static JPanel createMainPanel() {
    JPanel mainPanel = new JPanel(new GridLayout(2, 1, 0, 10));
    JPanel mainContentPanel = createContentPanel();
    JPanel metadataPanel = createMetadataPanel();

    mainContentPanel.setPreferredSize(new Dimension(0, 280));
    metadataPanel.setPreferredSize(new Dimension(0, 280));

    mainPanel.add(mainContentPanel);
    mainPanel.add(metadataPanel);

    return mainPanel;
  }

  public static JPanel createHomeContentPanel() {
    JPanel homeContentPanel = new JPanel(new BorderLayout(0, 10));
    homeContentPanel.setBorder(BorderFactory.createTitledBorder("Recently Played Songs"));

    homeRecentList = new JList<>();
    homeRecentList.setFocusable(false);
    homeContentPanel.add(new JScrollPane(homeRecentList), BorderLayout.CENTER);

    return homeContentPanel;
  }

  public static JPanel createLibraryContentPanel() {
    JPanel libraryContentPanel = new JPanel(new BorderLayout(0, 10));
    libraryContentPanel.setBorder(BorderFactory.createTitledBorder("Songs"));

    JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
    JLabel searchLabel = new JLabel("Search by title:");
    searchField = new JTextField();
    searchField.addActionListener(event -> searchSongs());

    searchPanel.add(searchLabel, BorderLayout.WEST);
    searchPanel.add(searchField, BorderLayout.CENTER);

    songList = new JList<>();
    songList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    songList.addListSelectionListener(App::handleSongSelection);

    libraryContentPanel.add(searchPanel, BorderLayout.NORTH);
    libraryContentPanel.add(new JScrollPane(songList), BorderLayout.CENTER);

    return libraryContentPanel;
  }

  public static JPanel createMetadataPanel() {
    JPanel metadataPanel = new JPanel(new BorderLayout(0, 10));
    metadataPanel.setBorder(BorderFactory.createTitledBorder("Now Playing"));

    JPanel infoPanel = new JPanel(new GridLayout(0, 1, 0, 4));
    metadataTitleLabel = new JLabel("Title: No song selected");
    metadataArtistLabel = new JLabel("Artist: ");
    infoPanel.add(metadataTitleLabel);
    infoPanel.add(metadataArtistLabel);

    JPanel fieldsPanel = new JPanel(new GridLayout(0, 1, 0, 2));
    metadataYearLabel = new JLabel("Year: ");
    metadataGenreLabel = new JLabel("Genre: ");
    metadataFilePathLabel = new JLabel("File Path: ");
    fieldsPanel.add(metadataYearLabel);
    fieldsPanel.add(metadataGenreLabel);
    fieldsPanel.add(metadataFilePathLabel);

    commentsArea = new JTextArea(5, 20);
    commentsArea.setLineWrap(true);
    commentsArea.setWrapStyleWord(true);
    JScrollPane commentsScrollPane = new JScrollPane(commentsArea);
    commentsScrollPane.setBorder(BorderFactory.createTitledBorder("Comments"));
    commentsScrollPane.setPreferredSize(new Dimension(340, 220));

    JButton saveMetadataButton = new JButton("Save");
    saveMetadataButton.addActionListener(event -> saveSelectedSongSettings());

    JPanel formPanel = new JPanel(new BorderLayout(0, 4));
    formPanel.add(infoPanel, BorderLayout.NORTH);
    formPanel.add(fieldsPanel, BorderLayout.CENTER);

    metadataPanel.add(formPanel, BorderLayout.NORTH);
    metadataPanel.add(commentsScrollPane, BorderLayout.CENTER);

    JPanel wrapperPanel = new JPanel(new BorderLayout(0, 8));
    wrapperPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
    wrapperPanel.add(metadataPanel, BorderLayout.CENTER);
    wrapperPanel.add(saveMetadataButton, BorderLayout.SOUTH);

    return wrapperPanel;
  }

  public static void showHome() {
    contentCardLayout.show(contentPanel, "HOME");
    refreshHomeRecentSongs();
    updateStatus("Home view ready.", null);
  }

  public static void showLibrary() {
    contentCardLayout.show(contentPanel, "LIBRARY");
    refreshSongList(Arrays.asList(library));
    updateStatus("Showing all songs.", null);
  }

  public static void showFavorites() {
    List<Song> favoriteSongs = new ArrayList<>();

    for (Song song : library) {
      if (song.isFavorite()) {
        favoriteSongs.add(song);
      }
    }

    contentCardLayout.show(contentPanel, "LIBRARY");
    refreshSongList(favoriteSongs);

    if (favoriteSongs.isEmpty()) {
      updateStatus("No favorited songs found.", null);
      return;
    }

    updateStatus("Showing favorited songs.", null);
  }

  public static void searchSongs() {
    if (library.length == 0) {
      updateStatus("No songs were loaded from the library.", null);
      return;
    }

    String searchText = searchField.getText().trim().toLowerCase(Locale.ROOT);

    if (searchText.isEmpty()) {
      updateStatus("Enter a song title to search.", null);
      return;
    }

    List<Song> matches = new ArrayList<>();

    for (Song song : library) {
      if (song.title().toLowerCase(Locale.ROOT).contains(searchText)) {
        matches.add(song);
      }
    }

    if (matches.isEmpty()) {
      updateStatus("No matching song was found.", null);
      return;
    }

    contentCardLayout.show(contentPanel, "LIBRARY");
    refreshSongList(matches);
    songList.setSelectedIndex(0);
    updateStatus("Search results updated.", null);
  }

  public static void refreshSongList(List<Song> songs) {
    displayedSongs.clear();
    displayedSongs.addAll(songs);

    String[] songLabels = new String[displayedSongs.size()];
    for (int i = 0; i < displayedSongs.size(); i++) {
      songLabels[i] = formatSong(displayedSongs.get(i));
    }

    songList.setListData(songLabels);

    if (!displayedSongs.isEmpty()) {
      songList.setSelectedIndex(0);
    } else {
      clearMetadataFields();
    }
  }

  public static void refreshHomeRecentSongs() {
    if (homeRecentList == null) {
      return;
    }

    String[] recentLabels;

    if (recentSongs.isEmpty()) {
      recentLabels = new String[] { "No songs recently played." };
    } else {
      recentLabels = new String[recentSongs.size()];
      for (int i = 0; i < recentSongs.size(); i++) {
        recentLabels[i] = formatSong(recentSongs.get(i));
      }
    }

    homeRecentList.setListData(recentLabels);
  }

  public static void handleSongSelection(ListSelectionEvent event) {
    if (event.getValueIsAdjusting()) {
      return;
    }

    Song selectedSong = getSelectedSong();

    if (selectedSong == null) {
      clearMetadataFields();
      return;
    }

    populateMetadataFields(selectedSong);
  }

  public static Song getSelectedSong() {
    int selectedIndex = songList.getSelectedIndex();

    if (selectedIndex < 0 || selectedIndex >= displayedSongs.size()) {
      return null;
    }

    return displayedSongs.get(selectedIndex);
  }

  public static void populateMetadataFields(Song song) {
    metadataTitleLabel.setText("Title: " + song.title());
    metadataArtistLabel.setText("Artist: " + song.artist());
    metadataYearLabel.setText("Year: " + song.year());
    metadataGenreLabel.setText("Genre: " + song.genre());
    metadataFilePathLabel.setText(
      "<html>File Path: " + escapeHtml(song.filePath()) + "</html>");
    if (favoriteButton != null) {
      favoriteButton.setSelected(song.isFavorite());
      updateFavoriteButtonLabel();
    }
    commentsArea.setText(song.comments());
  }

  public static void clearMetadataFields() {
    metadataTitleLabel.setText("Title: No song selected");
    metadataArtistLabel.setText("Artist: ");
    metadataYearLabel.setText("Year: ");
    metadataGenreLabel.setText("Genre: ");
    metadataFilePathLabel.setText("File Path: ");
    if (favoriteButton != null) {
      favoriteButton.setSelected(false);
      updateFavoriteButtonLabel();
    }
    commentsArea.setText("");
  }

  public static void selectSongInDisplayedList(Song songToSelect) {
    if (songToSelect == null) {
      return;
    }

    for (int i = 0; i < displayedSongs.size(); i++) {
      Song song = displayedSongs.get(i);
      if (song.fileName().equals(songToSelect.fileName())) {
        songList.setSelectedIndex(i);
        return;
      }
    }
  }

  public static void saveSelectedSongSettings() {
    Song selectedSong = getSelectedSong();

    if (selectedSong == null) {
      updateStatus("Select a song before saving settings.", null);
      return;
    }

    selectedSong.setFavorite(favoriteButton != null && favoriteButton.isSelected());
    selectedSong.setComments(normalizeComments(commentsArea.getText()));

    if (saveTrackSettings()) {
      List<Song> currentView = new ArrayList<>(displayedSongs);
      refreshSongList(currentView);
      selectSongInDisplayedList(selectedSong);
      populateMetadataFields(selectedSong);
      updateStatus("Track settings saved.", selectedSong);
      return;
    }

    updateStatus("Unable to save track settings.", selectedSong);
  }

  public static void toggleFavoriteForSelectedSong() {
    Song selectedSong = getSelectedSong();

    if (selectedSong == null) {
      if (favoriteButton != null) {
        favoriteButton.setSelected(false);
        updateFavoriteButtonLabel();
      }
      updateStatus("Select a song before changing favorite.", null);
      return;
    }

    selectedSong.setFavorite(favoriteButton != null && favoriteButton.isSelected());
    updateFavoriteButtonLabel();

    if (saveTrackSettings()) {
      List<Song> currentView = new ArrayList<>(displayedSongs);
      refreshSongList(currentView);
      selectSongInDisplayedList(selectedSong);
      populateMetadataFields(selectedSong);
      updateStatus("Favorite setting saved.", selectedSong);
      return;
    }

    updateStatus("Unable to save favorite setting.", selectedSong);
  }

  public static void playSelectedSong() {
    Song selectedSong = getSelectedSong();

    if (selectedSong == null) {
      updateStatus("Select a song from the list first.", null);
      return;
    }

    play(selectedSong);
  }

  // Start playback for the selected song and update the UI state around it.
  public static void play(Song selectedSong) {
    if (selectedSong == null) {
      updateStatus("No song selected.", null);
      return;
    }

    final String filename = selectedSong.fileName();
    final URL audioResource = App.class.getResource("/com/example/wav/" + filename);

    if (audioResource == null) {
      updateStatus("Unable to find the audio file " + filename + ".", selectedSong);
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
      refreshHomeRecentSongs();
      updateStatus("Now playing: " + formatSong(selectedSong), selectedSong);
    } catch (Exception e) {
      currentSong = null;
      updatePauseButtonLabel();
      updateStatus("Unable to play the selected audio file.", selectedSong);
      e.printStackTrace();
    }
  }

  public static void playMp3(URL audioResource, Song selectedSong) {
    mp3Thread = new Thread(() -> {
      try (InputStream audioStream = audioResource.openStream()) {
        mp3Player = new Player(audioStream);
        addRecentSong(selectedSong);
        SwingUtilities.invokeLater(() -> {
          refreshHomeRecentSongs();
          updateStatus("Now playing: " + formatSong(selectedSong), selectedSong);
        });
        mp3Player.play();
      } catch (Exception e) {
        currentSong = null;
        updatePauseButtonLabel();
        SwingUtilities.invokeLater(() ->
          updateStatus("Unable to play the selected audio file.", selectedSong));
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
        // Cleanup should not block stopping playback.
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
      recentSong.title().equals(song.title()) && recentSong.artist().equals(song.artist()));
    recentSongs.add(0, song);

    if (recentSongs.size() > MAX_RECENT_SONGS) {
      recentSongs.remove(recentSongs.size() - 1);
    }
  }

  public static void stop() {
    if (audioClip != null || mp3Player != null || isPaused) {
      Song stoppedSong = currentSong;
      stopCurrentPlayback();
      updateStatus("Playback stopped.", stoppedSong);
      return;
    }

    updateStatus("No song is currently playing.", null);
  }

  public static void pauseOrResume() {
    if (currentSong == null) {
      updateStatus("No song is currently playing.", null);
      return;
    }

    if (currentSong.fileName().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
      updateStatus("Pause is available for WAV playback only.", currentSong);
      return;
    }

    if (audioClip == null) {
      updateStatus("No song is currently playing.", null);
      return;
    }

    if (isPaused) {
      audioClip.setMicrosecondPosition(pausedPositionMicroseconds);
      audioClip.start();
      isPaused = false;
      updatePauseButtonLabel();
      updateStatus("Resumed: " + formatSong(currentSong), currentSong);
      return;
    }

    pausedPositionMicroseconds = audioClip.getMicrosecondPosition();
    audioClip.stop();
    isPaused = true;
    updatePauseButtonLabel();
    updateStatus("Paused: " + formatSong(currentSong), currentSong);
  }

  public static void skipBackward() {
    skipClipPosition(-SEEK_STEP_MICROSECONDS);
  }

  public static void skipForward() {
    skipClipPosition(SEEK_STEP_MICROSECONDS);
  }

  public static void skipClipPosition(long offsetMicroseconds) {
    if (currentSong == null) {
      updateStatus("No song is currently playing.", null);
      return;
    }

    if (currentSong.fileName().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
      updateStatus("Skipping is available for WAV playback only.", currentSong);
      return;
    }

    if (audioClip == null) {
      updateStatus("No song is currently playing.", null);
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
      updateStatus("Moved to " + formatTime(targetPosition) + ".", currentSong);
      return;
    }

    if (wasRunning) {
      audioClip.start();
    }

    updateStatus("Moved to " + formatTime(targetPosition) + ".", currentSong);
  }

  public static void quitApp() {
    stopCurrentPlayback();

    if (frame != null) {
      frame.dispose();
    }

    System.exit(0);
  }

  public static void updateStatus(String message, Song song) {
    // No status panel is shown in the current UI layout.
  }

  public static String formatSong(Song song) {
    String favoritePrefix = song.isFavorite() ? "* " : "";
    return favoritePrefix + song.title() + " - " + song.artist();
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

  public static void updateFavoriteButtonLabel() {
    if (favoriteButton == null) {
      return;
    }

    String label = favoriteButton.isSelected() ? "Unfavorite" : "Favorite";

    if (SwingUtilities.isEventDispatchThread()) {
      favoriteButton.setText(label);
      return;
    }

    SwingUtilities.invokeLater(() -> favoriteButton.setText(label));
  }

  public static String formatTime(long microseconds) {
    long totalSeconds = microseconds / 1_000_000L;
    long minutes = totalSeconds / 60;
    long seconds = totalSeconds % 60;
    return String.format("%d:%02d", minutes, seconds);
  }

  public static String normalizeGenre(String genreText) {
    if (genreText == null || genreText.isBlank()) {
      return "Unknown";
    }

    return genreText.trim();
  }

  public static String normalizeComments(String commentsText) {
    if (commentsText == null) {
      return "";
    }

    return commentsText.trim();
  }

  public static int parseYear(String yearText) {
    if (yearText == null || yearText.isBlank()) {
      return DEFAULT_YEAR;
    }

    try {
      return Integer.parseInt(yearText.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_YEAR;
    }
  }

  public static void initializeTrackSettings() {
    for (Song song : library) {
      applyDefaultSettings(song);
    }

    SongSettingsRecord[] savedSettings = loadTrackSettings();

    if (savedSettings != null) {
      mergeTrackSettings(savedSettings);
    }

    saveTrackSettings();
  }

  public static void applyDefaultSettings(Song song) {
    song.setTitle(song.name());
    song.setYear(DEFAULT_YEAR);
    song.setGenre("Unknown");
    song.setFavorite(false);
    song.setComments("");
    song.setFilePath(AUDIO_RELATIVE_DIRECTORY + song.fileName());
  }

  public static SongSettingsRecord[] loadTrackSettings() {
    Path settingsPath = getTrackSettingsPath();

    if (!Files.exists(settingsPath)) {
      return null;
    }

    try (Reader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
      return GSON.fromJson(reader, SongSettingsRecord[].class);
    } catch (Exception e) {
      return null;
    }
  }

  public static void mergeTrackSettings(SongSettingsRecord[] savedSettings) {
    for (SongSettingsRecord settings : savedSettings) {
      Song matchingSong = findSongBySettings(settings);

      if (matchingSong == null) {
        continue;
      }

      if (settings.title != null && !settings.title.isBlank()) {
        matchingSong.setTitle(settings.title);
      }

      if (settings.artist != null && !settings.artist.isBlank()) {
        matchingSong.setArtist(settings.artist);
      }

      if (settings.year > 0) {
        matchingSong.setYear(settings.year);
      }

      matchingSong.setGenre(normalizeGenre(settings.genre));
      matchingSong.setFavorite(settings.isFavorite);
      matchingSong.setComments(normalizeComments(settings.comments));

      if (settings.filePath != null && !settings.filePath.isBlank()) {
        matchingSong.setFilePath(settings.filePath);
      }
    }
  }

  public static Song findSongBySettings(SongSettingsRecord settings) {
    String settingsPath = settings.filePath == null ? "" : settings.filePath;

    for (Song song : library) {
      if (song.title().equals(settings.title)
        && song.artist().equals(settings.artist)
        && song.filePath().equals(settingsPath)) {
        return song;
      }

      if (song.filePath().equals(settingsPath)) {
        return song;
      }
    }

    return null;
  }

  public static boolean saveTrackSettings() {
    Path settingsPath = getTrackSettingsPath();
    SongSettingsRecord[] settingsRecords = new SongSettingsRecord[library.length];

    for (int i = 0; i < library.length; i++) {
      settingsRecords[i] = SongSettingsRecord.fromSong(library[i]);
    }

    try {
      Files.createDirectories(settingsPath.getParent());
      try (Writer writer = Files.newBufferedWriter(settingsPath, StandardCharsets.UTF_8)) {
        GSON.toJson(settingsRecords, writer);
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static Path getTrackSettingsPath() {
    return Paths.get(TRACK_SETTINGS_RELATIVE_PATH);
  }

  public static String escapeHtml(String text) {
    return text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\n", "<br>");
  }

  // Load the base audio library bundled with the app resources.
  public static Song[] readAudioLibrary() {
    final InputStream jsonStream = App.class.getResourceAsStream("/com/example/audio-library.json");

    if (jsonStream == null) {
      return null;
    }

    try (InputStreamReader fileReader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8);
         JsonReader reader = new JsonReader(fileReader)) {
      return GSON.fromJson(reader, Song[].class);
    } catch (Exception e) {
      return null;
    }
  }

  public static class SongSettingsRecord {
    String title;
    String artist;
    int year;
    String genre;
    boolean isFavorite;
    String comments;
    String filePath;

    public static SongSettingsRecord fromSong(Song song) {
      SongSettingsRecord record = new SongSettingsRecord();
      record.title = song.title();
      record.artist = song.artist();
      record.year = song.year();
      record.genre = song.genre();
      record.isFavorite = song.isFavorite();
      record.comments = song.comments();
      record.filePath = song.filePath();
      return record;
    }
  }
}
