package com.wifi.ap.location.estimate.dto;

import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Optional;

public record MacAddress(Optional<String> macAddress , Message message) {



}
