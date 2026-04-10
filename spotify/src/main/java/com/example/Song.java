package com.example;

public class Song {
  private String name;
  private String artist;
  private String fileName;

  // serializes attributes into a string
  @Override
  public String toString() {
    return "{ name: " + name + ", artist: " + artist + ", fileName: " + fileName + " }";
  }

  // getters
  public String name() {
    return name;
  }

  public String artist() {
    return artist;
  }

  public String fileName() {
    return fileName;
  }
}
