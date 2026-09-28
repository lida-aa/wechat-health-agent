package com.Myself.demo.service;

import com.Myself.demo.entity.WeatherResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts weather data into health-only advice for the daily briefing. */
public final class SmartWeatherService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private SmartWeatherService() {
        throw new AssertionError("Utility class must not be instantiated");
    }

    public static String getHealthAdvice(WeatherResponse weather) {
        if (weather == null) {
            throw new IllegalArgumentException("weather must not be null");
        }

        OptionalDouble temperature = effectiveTemperature(weather);
        OptionalDouble feelsLike = number(weather.getFeelsLike());
        OptionalDouble humidity = number(weather.getHumidity());
        String condition = normalized(weather.getWeather());
        List<String> advice = new ArrayList<>();
        OptionalDouble perceived = feelsLike.isPresent() ? feelsLike : temperature;

        if (atLeast(perceived, 32)) {
            advice.add("及时补水，避免长时间暴晒和高强度运动");
        } else if (atMost(perceived, 5)) {
            advice.add("注意头颈和手脚保暖，谨防受凉");
        }
        if (atLeast(humidity, 85)) {
            advice.add("湿度较高，室内注意通风除湿");
        } else if (humidity.isPresent() && humidity.getAsDouble() < 30) {
            advice.add("空气干燥，注意补水和皮肤保湿");
        }
        if (containsAny(condition, "霾", "沙尘", "haze", "dust")) {
            advice.add("敏感人群应减少户外停留，外出可佩戴口罩");
        }
        if (containsAny(condition, "雷", "暴雨", "暴雪", "台风", "thunder", "storm", "typhoon")) {
            advice.add("恶劣天气应减少户外活动，并留意当地预警");
        }
        if (advice.isEmpty()) {
            advice.add("体感条件总体正常，保持规律补水即可");
        }
        return String.join("；", advice) + "。";
    }

    private static OptionalDouble effectiveTemperature(WeatherResponse weather) {
        OptionalDouble current = number(weather.getTemperature());
        if (current.isPresent()) {
            return current;
        }

        OptionalDouble high = number(weather.getHigh());
        OptionalDouble low = number(weather.getLow());
        if (high.isPresent() && low.isPresent()) {
            return OptionalDouble.of((high.getAsDouble() + low.getAsDouble()) / 2.0);
        }
        return high.isPresent() ? high : low;
    }

    private static OptionalDouble number(String value) {
        if (value == null || value.isBlank()) {
            return OptionalDouble.empty();
        }
        Matcher matcher = NUMBER_PATTERN.matcher(value.replace(',', '.'));
        if (!matcher.find()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(matcher.group()));
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
    }

    private static boolean atLeast(OptionalDouble value, double threshold) {
        return value.isPresent() && value.getAsDouble() >= threshold;
    }

    private static boolean atMost(OptionalDouble value, double threshold) {
        return value.isPresent() && value.getAsDouble() <= threshold;
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
