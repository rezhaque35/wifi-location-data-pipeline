package com.wifi.positioning.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PositionTest {

  @Test
  void isValid_WithValidCoordinates_ReturnsTrue() {
    Position position = new Position(37.7749, -122.4194, 10.0, 25.0, 0.5);
    assertTrue(position.isValid());
  }

  @Test
  void isValid_WithNullLatitude_ReturnsFalse() {
    Position position = new Position(null, -122.4194, 10.0, 25.0, 0.5);
    assertFalse(position.isValid());
  }

  @Test
  void isValid_WithNullLongitude_ReturnsFalse() {
    Position position = new Position(37.7749, null, 10.0, 25.0, 0.5);
    assertFalse(position.isValid());
  }

  @Test
  void isValid_WithNaNLatitude_ReturnsFalse() {
    Position position = new Position(Double.NaN, -122.4194, 10.0, 25.0, 0.5);
    assertFalse(position.isValid());
  }

  @Test
  void isValid_WithNaNLongitude_ReturnsFalse() {
    Position position = new Position(37.7749, Double.NaN, 10.0, 25.0, 0.5);
    assertFalse(position.isValid());
  }

  @Test
  void isValid_WithValidCoordinatesAndNullAltitude_ReturnsTrue() {
    Position position = new Position(37.7749, -122.4194, null, 25.0, 0.5);
    assertTrue(position.isValid());
  }

  @Test
  void of_WithValidCoordinates_CreatesValidPosition() {
    Position position = Position.of(37.7749, -122.4194);
    assertTrue(position.isValid());
    assertEquals(37.7749, position.latitude());
    assertEquals(-122.4194, position.longitude());
    assertNull(position.altitude());
    assertEquals(1.0, position.accuracy());
    assertEquals(1.0, position.confidence());
  }
}
