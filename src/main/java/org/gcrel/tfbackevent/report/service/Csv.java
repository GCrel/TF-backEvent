package org.gcrel.tfbackevent.report.service;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Csv {
    public static final String BOM = "\uFEFF";
    private static final String LINE_END = "\r\n";
    private static final String FORMULA_STARTS = "=+-@\t\r";

    private Csv() {
    }

    public static String row(String... cells) {
        return Stream.of(cells).map(Csv::cell).collect(Collectors.joining(",")) + LINE_END;
    }

    // Una celda que empieza con = + - @ (o tab / retorno de carro) se ejecutaría como fórmula en Excel;
    // los nombres los escribe el usuario, así que se antepone un apóstrofo.
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = !value.isEmpty() && FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean needsQuotes = safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r");
        return needsQuotes ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
