package com.wifi.ap.location.estimation.algorithm;

import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.estimation.algorithm.impl.LocalizationAlgorithmType;
import lombok.Getter;

import java.util.function.Supplier;

/**
 * Represents the result of algorithm selection with reasoning and execution capability.
 * 
 * <p>Contains the selected localization algorithm execution strategy, reasoning for the selection,
 * and metadata about the data maturity assessment that led to the selection.
 * 
 * <p>The algorithm and measurements are captured at selection time, ensuring that execution
 * uses the exact same context that was used for algorithm selection, preventing measurement
 * mismatches and temporal coupling issues.
 */
public class AlgorithmSelections {

    private final Supplier<APLocation> algorithmExecution;

    @Getter
    private final LocalizationAlgorithmType algorithmType;
    

    @Getter
    private final String selectionReasoning;
    


    private AlgorithmSelections(
            Supplier<APLocation> algorithmExecution,
            LocalizationAlgorithmType algorithmType,
            String selectionReasoning) {
        this.algorithmExecution = algorithmExecution;
        this.algorithmType = algorithmType;
        this.selectionReasoning = selectionReasoning;
    }

    /**
     * Creates a selection with a single algorithm execution strategy.
     * 
     * <p>The algorithm execution is captured as a Supplier, which encapsulates both
     * the algorithm logic and the measurements that were used for selection. This
     * ensures that execution uses the exact same context as selection.
     * 
     * @param algorithmExecution The algorithm execution supplier that captures measurements and algorithm
     * @param algorithmType The type of algorithm selected
     * @param reasoning The reasoning for this selection
     * @return AlgorithmSelections instance
     */
    public static AlgorithmSelections single(
            Supplier<APLocation> algorithmExecution,
            LocalizationAlgorithmType algorithmType,
            String reasoning) {
        return new AlgorithmSelections(
            algorithmExecution,
            algorithmType,
            reasoning
        );
    }


    /**
     * Executes the selected algorithm using the measurements captured at selection time.
     * 
     * <p>This method is safe from measurement mismatches because the algorithm execution
     * was captured with the exact measurements that were used for algorithm selection.
     * 
     * @return Estimated location
     */
    public APLocation execute() {
        return algorithmExecution.get();
    }


}
