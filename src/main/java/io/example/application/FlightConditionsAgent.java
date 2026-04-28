package io.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

/*
 * The flight conditions agent evaluates whether a time slot is safe for flight training.
 * It calls the getWeatherForecast tool and assesses conditions against VFR minimums:
 * visibility ≥ 5 miles, wind ≤ 25 knots, no precipitation.
 */
@Component(id = "flight-conditions-agent")
public class FlightConditionsAgent extends Agent {

    public record ConditionsReport(String timeSlotId, Boolean meetsRequirements) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are a flight conditions evaluation agent for a flight training scheduler.

            Your job is to determine whether a given time slot is safe for student flight training.

            Instructions:
            1. Call the getWeatherForecast tool with the provided timeSlotId to retrieve forecast conditions.
            2. Evaluate the conditions against these VFR minimums:
               - Visibility: at least 5 miles
               - Wind speed: no more than 25 knots
               - Precipitation: none acceptable
            3. Return a ConditionsReport JSON object with:
               - "timeSlotId": the input slot ID
               - "meetsRequirements": true if all criteria pass, false otherwise

            Respond ONLY with a valid JSON object matching the ConditionsReport schema.
            Do not include any explanation outside of the JSON.
            """.stripIndent();

    public Effect<ConditionsReport> query(String timeSlotId) {
        return effects()
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage("Evaluate flight conditions for time slot ID: " + timeSlotId
                        + ". Call getWeatherForecast first, then return a ConditionsReport JSON.")
                .responseAs(ConditionsReport.class)
                .thenReply();
    }

    @FunctionTool(description = "Queries the weather conditions as they are forecasted based on the time slot ID of the training session booking")
    private String getWeatherForecast(String timeSlotId) {
        if (timeSlotId != null && (timeSlotId.contains("storm") || timeSlotId.contains("bad"))) {
            return "Forecast for slot " + timeSlotId + ": Thunderstorm activity, visibility 1 mile, "
                    + "wind gusts 45 knots, heavy precipitation. Conditions are below VFR minimums.";
        }
        return "Forecast for slot " + timeSlotId + ": Clear skies, visibility 10 miles, "
                + "wind 8 knots from the west, no precipitation. Conditions are excellent for VFR flight.";
    }
}
