package com.example.stt;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static String formatText(String text) {
        return text;
    }

    public static String processPunctuation(String text) {
        String newline = "\n";

        text = text.replaceAll("(?i)παπάκι", "@@@@@@@@")
                .replaceAll("(?i)ανοίγω\\s+παρένθεση", "(")
                .replaceAll("(?i)ανοιγω\\s+παρενθεση", "(")
                .replaceAll("(?i)ανοίγω\\s+παρενθεση", "(")
                .replaceAll("(?i)ανοιγω\\s+παρένθεση", "(")
                .replaceAll("(?i)κλείνω\\s+παρένθεση", ")")
                .replaceAll("(?i)κλεινω\\s+παρενθεση", ")")
                .replaceAll("(?i)κλείνω\\s+παρενθεση", ")")
                .replaceAll("(?i)κλεινω\\s+παρένθεση", ")")
                .replaceAll("(?i)\\bκόμμα\\b", ",")
                .replaceAll("(?i)\\bκομμα\\b", ",")
                .replaceAll("(?i)\\bτελεία\\b", ".")
                .replaceAll("(?i)\\bτελεια\\b", ".")
                .replaceAll("(?i)νέα\\s*σειρά\\s*", newline)
                .replaceAll("(?i)νεα\\s*σειρα\\s*", newline)
                .replaceAll("(?i)νέα\\s*σειρα\\s*", newline)
                .replaceAll("(?i)νεα\\s*σειρά\\s*", newline)
                .replaceAll("(?i)παράγραφος\\s*", newline + newline)
                .replaceAll("(?i)Παράγραφος\\s*", newline + newline)
                .replaceAll("(?i)Παραγραφος\\s*", newline + newline)
                .replaceAll("(?i)παραγραφος\\s*", newline + newline)
                .replaceAll("(?i)\\b(άνω|ανω)\\s+(κάτω|κατω)\\s+(τελεία|τελεια)\\b", ":");

        text = text.replaceAll("\\s+([,.])", "$1");
        text = text.replaceAll("([.,])(?!\\s|\\n|$)", "$1 ");

        // Capitalize after periods and paragraphs
        text = capitalizeAfterPeriodOrParagraph(text);

        return text;
    }

    private static String capitalizeAfterPeriodOrParagraph(String text) {
        StringBuffer sb = new StringBuffer();
        Pattern pattern = Pattern.compile("([\\.\\n]\\s*)(\\p{L})");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1) + Objects.requireNonNull(matcher.group(2)).toUpperCase());
        }
        matcher.appendTail(sb);

        if (sb.length() > 0) {
            sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        }

        return sb.toString();
    }

}
