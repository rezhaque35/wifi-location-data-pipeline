package com.wifi.ap.location.estimation;

import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Optional;

public record MessageMacAddress(Optional<String> macAddress , Message message) {



}
