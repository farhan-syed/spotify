package com.example;

public class Song {
  private String name;
  private String artist;
  private String fileName;
  private String title;
  private int year;
  private String genre;
  private boolean isFavorite;
  private String comments;
  private String filePath;

  @Override
  public String toString() {
    return "{ title: " + title() + ", artist: " + artist + ", fileName: " + fileName + " }";
  }

  public String name() {
    return name;
  }

  public String title() {
    return title != null && !title.isBlank() ? title : name;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String artist() {
    return artist;
  }

  public void setArtist(String artist) {
    this.artist = artist;
  }

  public String fileName() {
    return fileName;
  }

  public int year() {
    return year;
  }

  public void setYear(int year) {
    this.year = year;
  }

  public String genre() {
    return genre;
  }

  public void setGenre(String genre) {
    this.genre = genre;
  }

  public boolean isFavorite() {
    return isFavorite;
  }

  public void setFavorite(boolean favorite) {
    isFavorite = favorite;
  }

  public String comments() {
    return comments;
  }

  public void setComments(String comments) {
    this.comments = comments;
  }

  public String filePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }
}
