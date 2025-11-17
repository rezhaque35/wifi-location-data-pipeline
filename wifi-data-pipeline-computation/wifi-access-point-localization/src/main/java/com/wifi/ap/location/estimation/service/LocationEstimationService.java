// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/MessageProcessor.java
package com.wifi.ap.location.estimation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.estimation.MessageMacAddress;
import com.wifi.ap.location.estimation.MessageProcessingResult;
import com.wifi.ap.location.measurements.WifiMeasurements;
import com.wifi.ap.location.repository.APLocationPublisher;
import com.wifi.ap.location.estimation.algorithm.AlgorithmSelections;
import com.wifi.ap.location.estimation.algorithm.AlgorithmSelector;
import com.wifi.ap.location.dynamic.WiFiHotspotResult;
import com.wifi.ap.location.repository.APLocationRepository;
import com.wifi.ap.location.measurements.service.MeasurementFilteringService;
import com.wifi.ap.location.measurements.service.MeasurementRetrievalService;
import com.wifi.ap.location.dynamic.WiFiHotspotDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LocationEstimationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationEstimationService.class);
    public static final String WI_FI_HOTSPOT = "Ignored As mac %s deemed to be WiFi Hotspot";

    private final ObjectMapper objectMapper;
    private final APLocationRepository repository;
    private final MeasurementRetrievalService measurementsRepo;
    private final WiFiHotspotDetectionService hotspotIdentifier;
    private final AlgorithmSelector algorithmSelector;
    private final MeasurementFilteringService measurementsFilter;
    private final APLocationPublisher wifiAPLocationPublisher;

    public LocationEstimationService(APLocationRepository apLocationRepository, ObjectMapper objectMapper, MeasurementRetrievalService measurementsRepo, WiFiHotspotDetectionService hotspotIdentifier, MeasurementFilteringService measurementsFilter, APLocationPublisher wifiAPLocationPublisher, AlgorithmSelector algorithmSelector) {

        this.objectMapper = objectMapper;
        this.repository = apLocationRepository;
        this.measurementsRepo = measurementsRepo;
        this.hotspotIdentifier = hotspotIdentifier;
        this.wifiAPLocationPublisher = wifiAPLocationPublisher;
        this.algorithmSelector = algorithmSelector;
        this.measurementsFilter = measurementsFilter;

        logger.info("Message Processor initialized with S3 event extractor and feed processor factory");

    }

    public Stream<MessageProcessingResult> estimateLocation(List<Message> messages) {


        try {
            UnaryOperator<List<Message>> givenMessages = UnaryOperator.identity();
            return givenMessages.andThen(this::parseMacAddresses)
                                .andThen(this::retrieveExistingLocationEstimates)
                                .andThen(this::estimateLocation)
                                .andThen(this::storeCalculatedLocations)
                                .andThen(this::buildResults)
                                .apply(messages);

        } catch (Exception e) {
            logger.error("Error processing message: {}", messages.stream()
                                                                 .map(Message::messageId)
                                                                 .collect(Collectors.joining()), e);
            return messages.stream()
                           .map(M -> new MessageProcessingResult(false, M.receiptHandle(), e.getMessage(), M.messageId()));
        }
    }


    private Stream<APLocationEstimation> storeCalculatedLocations(Stream<APLocationEstimation> macAddressesLocations) {

        List<APLocationEstimation> APLocationEstimations = macAddressesLocations.toList();


        APLocationEstimations.stream()
                             .filter(APLocationEstimation::success)
                             .map(EL -> EL.location()
                                          .location()
                                          .orElseThrow())
                             .forEach(this.wifiAPLocationPublisher::publish);
        return APLocationEstimations.stream();

    }

    private Stream<MessageProcessingResult> buildResults(Stream<APLocationEstimation> macAddressesLocations) {


        return macAddressesLocations.map(this::buildResult);

    }

    private MessageProcessingResult buildResult(APLocationEstimation estimatedLocation) {

        if (estimatedLocation.success()) {
            return this.buildResult(estimatedLocation.location());
        } else {
            return this.handleFailure(estimatedLocation);
        }


    }

    private MessageProcessingResult buildResult(MacLocation L) {
        return new MessageProcessingResult(true, L.messageMacAddress()
                                                  .message()
                                                  .receiptHandle(), "", L.messageMacAddress()
                                                                         .message()
                                                                         .messageId());
    }

    private Stream<MacLocation> retrieveExistingLocationEstimates(Stream<MessageMacAddress> macAddressesStream) {

        Set<MessageMacAddress> macAddresses = macAddressesStream.collect(Collectors.toSet());
        return this.repository.findByMacAddresses(macAddresses)
                              .entrySet()
                              .stream()
                              .map(E -> new MacLocation(E.getKey(), E.getValue()));

    }

    private MessageProcessingResult handleFailure(APLocationEstimation estimatedLocation) {

        Message message = estimatedLocation.location()
                                           .messageMacAddress()
                                           .message();
        logger.error(
                "Failed to estimate currentEstimation for AP {} in - messageId: {}, messageBody: {} due to {}",
                estimatedLocation.location()
                                 .messageMacAddress()
                                 .macAddress()
                                 .orElse(""),
                message.messageId(),
                message.body(),
                estimatedLocation.failureReason());

        return new MessageProcessingResult(false, message.receiptHandle(), estimatedLocation.failureReason(), message.messageId());
    }


    public Stream<MessageMacAddress> parseMacAddresses(List<Message> messages) {
        return messages.stream()
                       .map(this::parseToMacAddress);

    }


    private Stream<APLocationEstimation> estimateLocation(Stream<MacLocation> apLocations) {

        return apLocations.map(this::retrieveMeasurements)
                          .flatMap(Optional::stream)
                          .map(this::estimateLocation);

    }

    private APLocationEstimation estimateLocation(APLocationData apLocationData) {

        var wiFiHotspotDetectionResult = detectHotspot(apLocationData);

        if (wiFiHotspotDetectionResult.hotspotDetected()) {
            return handleHotspotDetection(apLocationData.currentEstimation());
        } else {
            return this.executeLocalizationAlgorithm(apLocationData);

        }


    }

    private WiFiHotspotResult detectHotspot(APLocationData apLocationData) {
        var measurements = apLocationData.measurements();
        MacLocation currentEstimation = apLocationData.currentEstimation();

        return currentEstimation
                .location()
                .map(l -> this.hotspotIdentifier.detect(measurements, l))
                .orElseGet(() -> this.hotspotIdentifier.detect(measurements));
    }

    private APLocationEstimation handleHotspotDetection(MacLocation currentEstimation) {
        if (isAlreadyMarkedAsHotSpot(currentEstimation)) {

            return new APLocationEstimation(currentEstimation, false, String.format(WI_FI_HOTSPOT, currentEstimation.messageMacAddress()
                                                                                                                    .macAddress()
                                                                                                                    .orElseThrow()));
        } else {
            // create new estimation to update repository with wifi-hotspot detection.
            return new APLocationEstimation(copyAsHotspot(currentEstimation), true, "");
        }
    }

    private static Boolean isAlreadyMarkedAsHotSpot(MacLocation currentEstimation) {
        return currentEstimation.location()
                                .map(com.wifi.ap.location.APLocation::isHotspot)
                                .orElse(false);
    }

    private MacLocation copyAsHotspot(MacLocation currentEstimation) {
        //TODO return AP location with creating new WifiAccessPointLocation with status as wifi hotspot.
        return null;
    }

    private APLocationEstimation executeLocalizationAlgorithm(APLocationData apLocationData) {
        try {
            WifiMeasurements measurements = apLocationData.measurements();
            MacLocation knownAP = apLocationData.currentEstimation();

            var existingEstimation = knownAP.location();
            var filteredMeasurements = this.measurementsFilter.filter(measurements, existingEstimation);

            var selectedAlgo = selectAlgorithmWith(filteredMeasurements, existingEstimation);
            var calculatedLocation = selectedAlgo.execute();

            return new APLocationEstimation(new MacLocation(knownAP.messageMacAddress(), Optional.of(calculatedLocation)), true, null);

        } catch (Exception e) {
            return new APLocationEstimation(new MacLocation(apLocationData.currentEstimation().messageMacAddress(), Optional.empty()), false, e.getMessage());
        }
    }

    private AlgorithmSelections selectAlgorithmWith(WifiMeasurements measurements, Optional<APLocation> existingEstimation) {
        Optional<AlgorithmSelections> selection = existingEstimation
                .map(currentEstimation -> this.algorithmSelector.select(measurements, currentEstimation))
                .orElseGet(() -> this.algorithmSelector.select(measurements));

        return selection.orElseThrow(() -> new IllegalStateException("No algorithm could be selected for the given measurements"));
    }


    private Optional<APLocationData> retrieveMeasurements(MacLocation macLocation) {

        return macLocation.messageMacAddress()
                          .macAddress()
                          .flatMap(this.measurementsRepo::lookup)
                          .map(MS -> new APLocationData(macLocation, MS));

    }


    public MessageMacAddress parseToMacAddress(Message message) {

        String messageBody = message.body();
        if (messageBody == null || messageBody.trim()
                                              .isEmpty()) {
            logger.warn("Message body is null or empty");
            return new MessageMacAddress(Optional.empty(), message);
        }
        String macAddress = null;
        try {
            macAddress = this.objectMapper.readValue(messageBody, String.class);
            return new MessageMacAddress(Optional.of(macAddress), message);

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse mac addressed from message id {} with body {} due to {}", message.messageId(), messageBody, e.getMessage());
            logger.error("Error details", e);
            return new MessageMacAddress(Optional.empty(), message);

        }

    }
}


record MacLocation(MessageMacAddress messageMacAddress, Optional<APLocation> location) {

}

record APLocationEstimation(MacLocation location, boolean success, String failureReason) {

}

record APLocationData(MacLocation currentEstimation, WifiMeasurements measurements) {

}