package com.wifi.scan.consume.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for MessageTransformationService.
 *
 * <p>Tests message compression and encoding operations, including GZIP compression and Base64
 * encoding.
 */
@ExtendWith(MockitoExtension.class)
class MessageTransformationServiceTest {

  private MessageTransformationService transformationService;

  @BeforeEach
  void setUp() {
    transformationService = new MessageTransformationService();
  }

  @Test
  @DisplayName("Should transform single message successfully")
  void transform_SingleMessage_ShouldTransformSuccessfully() {
    // Given
    String originalMessage = "{\"test\":\"data\",\"timestamp\":1234567890}";
    List<String> messages = List.of(originalMessage);

    // When
    List<String> transformed = transformationService.transform(messages);

    // Then
    assertNotNull(transformed);
    assertEquals(1, transformed.size());
    assertFalse(transformed.get(0).isEmpty());

    // Verify the message can be decoded and decompressed back to original
    String decompressed = decompressAndDecode(transformed.get(0));
    assertEquals(originalMessage, decompressed);
  }

  @Test
  @DisplayName("Should transform multiple messages successfully")
  void transform_MultipleMessages_ShouldTransformSuccessfully() {
    // Given
    List<String> messages = List.of(
        "{\"test\":\"data1\",\"timestamp\":1234567890}",
        "{\"test\":\"data2\",\"timestamp\":1234567891}",
        "{\"test\":\"data3\",\"timestamp\":1234567892}"
    );

    // When
    List<String> transformed = transformationService.transform(messages);

    // Then
    assertNotNull(transformed);
    assertEquals(3, transformed.size());

    // Verify all messages can be decoded and decompressed
    for (int i = 0; i < messages.size(); i++) {
      String decompressed = decompressAndDecode(transformed.get(i));
      assertEquals(messages.get(i), decompressed);
    }
  }

  @Test
  @DisplayName("Should handle empty message list")
  void transform_EmptyList_ShouldReturnEmptyList() {
    // Given
    List<String> messages = List.of();

    // When
    List<String> transformed = transformationService.transform(messages);

    // Then
    assertNotNull(transformed);
    assertTrue(transformed.isEmpty());
  }



  @Test
  @DisplayName("Should handle empty string message")
  void transform_EmptyStringMessage_ShouldTransformEmptyString() {
    // Given
    List<String> messages = List.of("", "{\"test\":\"data\"}");

    // When
    List<String> transformed = transformationService.transform(messages);

    // Then
    assertNotNull(transformed);
    assertEquals(2, transformed.size());

    // Verify empty string is transformed
    assertEquals("", decompressAndDecode(transformed.get(0)));
    assertEquals("{\"test\":\"data\"}", decompressAndDecode(transformed.get(1)));
  }

  @Test
  @DisplayName("Should compress large JSON messages effectively")
  void transform_LargeMessage_ShouldCompressEffectively() {
    // Given
    StringBuilder largeJson = new StringBuilder("{\"data\":[");
    for (int i = 0; i < 100; i++) {
      if (i > 0) largeJson.append(",");
      largeJson.append("{\"id\":").append(i).append(",\"value\":\"test_data_").append(i).append("\"}");
    }
    largeJson.append("]}");
    
    String originalMessage = largeJson.toString();
    List<String> messages = List.of(originalMessage);

    // When
    List<String> transformed = transformationService.transform(messages);

    // Then
    assertNotNull(transformed);
    assertEquals(1, transformed.size());

    // Verify compression achieved some reduction
    String compressedEncoded = transformed.get(0);
    byte[] compressedBytes = Base64.getDecoder().decode(compressedEncoded);
    
    // Compressed size should be significantly smaller than original
    assertTrue(compressedBytes.length < originalMessage.getBytes().length);
    
    // Verify message integrity
    String decompressed = decompressAndDecode(compressedEncoded);
    assertEquals(originalMessage, decompressed);
  }

  @Test
  @DisplayName("Should handle special characters and Unicode")
  void transform_SpecialCharacters_ShouldHandleCorrectly() {
    // Given
    String messageWithSpecialChars = "{\"unicode\":\"测试数据\",\"symbols\":\"!@#$%^&*()_+=\",\"emoji\":\"🚀📡\"}";
    List<String> messages = List.of(messageWithSpecialChars);

    // When
    List<String> transformed = transformationService.transform(messages);

    // Then
    assertNotNull(transformed);
    assertEquals(1, transformed.size());

    // Verify special characters are preserved
    String decompressed = decompressAndDecode(transformed.get(0));
    assertEquals(messageWithSpecialChars, decompressed);
  }

  @Test
  @DisplayName("Should handle very small messages")
  void transform_VerySmallMessage_ShouldTransformCorrectly() {
    // Given
    List<String> messages = List.of("a", "1", "{}", "[]");

    // When
    List<String> transformed = transformationService.transform(messages);

    // Then
    assertNotNull(transformed);
    assertEquals(4, transformed.size());

    // Verify all small messages are transformed correctly
    assertEquals("a", decompressAndDecode(transformed.get(0)));
    assertEquals("1", decompressAndDecode(transformed.get(1)));
    assertEquals("{}", decompressAndDecode(transformed.get(2)));
    assertEquals("[]", decompressAndDecode(transformed.get(3)));
  }

  @Test
  @DisplayName("Should produce valid Base64 encoded output")
  void transform_AnyMessage_ShouldProduceValidBase64() {
    // Given
    List<String> messages = List.of(
        "{\"test\":\"data\"}",
        "simple text",
        "123456789"
    );

    // When
    List<String> transformed = transformationService.transform(messages);

    // Then
    assertNotNull(transformed);
    assertEquals(3, transformed.size());

    // Verify all outputs are valid Base64
    for (String encoded : transformed) {
      assertNotNull(encoded);
      assertFalse(encoded.isEmpty());
      
      // Should be able to decode without exception
      byte[] decoded = Base64.getDecoder().decode(encoded);
      assertTrue(decoded.length > 0);
    }
  }

  @Test
  @DisplayName("Should handle parallel processing correctly")
  void transform_ManyMessages_ShouldHandleParallelProcessing() {
    // Given - Create many messages to trigger parallel processing
    List<String> messages = List.of(
        "{\"msg\":1}", "{\"msg\":2}", "{\"msg\":3}", "{\"msg\":4}", "{\"msg\":5}",
        "{\"msg\":6}", "{\"msg\":7}", "{\"msg\":8}", "{\"msg\":9}", "{\"msg\":10}",
        "{\"msg\":11}", "{\"msg\":12}", "{\"msg\":13}", "{\"msg\":14}", "{\"msg\":15}"
    );

    // When
    List<String> transformed = transformationService.transform(messages);

    // Then
    assertNotNull(transformed);
    assertEquals(15, transformed.size());

    // Verify all messages are transformed correctly (order might be different due to parallel processing)
    for (int i = 0; i < messages.size(); i++) {
      String decompressed = decompressAndDecode(transformed.get(i));
      assertTrue(messages.contains(decompressed));
    }
  }

  /**
   * Helper method to decompress and decode a Base64-encoded GZIP-compressed string.
   */
  private String decompressAndDecode(String compressedEncoded) {
    try {
      byte[] compressedData = Base64.getDecoder().decode(compressedEncoded);
      try (GZIPInputStream gzipInput = new GZIPInputStream(new ByteArrayInputStream(compressedData));
           InputStreamReader reader = new InputStreamReader(gzipInput, "UTF-8");
           BufferedReader bufferedReader = new BufferedReader(reader)) {
        return bufferedReader.lines().collect(Collectors.joining("\n"));
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to decompress and decode message", e);
    }
  }
}

