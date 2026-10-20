package com.notificationreader;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts payer and amount from supported Bolivian payment notifications. */
public final class NotificationParser {
    private static final Pattern YAPE = Pattern.compile("(?i)QR\\s+DE\\s+(.+?)\\s+te\\s+envió\\s+(?:Bs\\.?\\s*)?([0-9][0-9.,]*)");
    private static final Pattern BCP = Pattern.compile("(?i)por\\s+(?:Bs\\.?\\s*)?([0-9][0-9.,]*)\\s+de\\s+(.+)$");
    private static final Pattern ALTOQUE = Pattern.compile("(?i)^(.+?)\\s+te\\s+abon[oó]\\s+(?:Bs\\.?\\s*)?([0-9][0-9.,]*)\\s*$");
    private NotificationParser() {}

    public static Payment parse(String bank, String title, String body) {
        String notificationTitle = title == null ? "" : title.trim();
        String notificationBody = body == null ? "" : body.trim();
        Matcher m;
        String name;
        String rawAmount;
        if ("ALTOQUE".equals(bank)) {
            // altoke uses "Recibiste un pago" as title; payer and amount are in the body.
            String candidate = notificationBody.isEmpty() ? notificationTitle : notificationBody;
            m = ALTOQUE.matcher(candidate);
            if (!m.find()) return null;
            name = m.group(1);
            rawAmount = m.group(2);
        } else {
            String text = (notificationTitle + " " + notificationBody).trim();
            m = "BCP".equals(bank) ? BCP.matcher(text) : YAPE.matcher(text);
            if (!m.find()) return null;
            name = "BCP".equals(bank) ? m.group(2) : m.group(1);
            rawAmount = "BCP".equals(bank) ? m.group(1) : m.group(2);
        }
        BigDecimal amount = parseAmount(rawAmount);
        name = name.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || amount == null || amount.signum() <= 0) return null;
        return new Payment(name, amount);
    }

    static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.replace(" ", "");
        int dot = s.lastIndexOf('.'), comma = s.lastIndexOf(',');
        if (dot >= 0 && comma >= 0) {
            char decimal = dot > comma ? '.' : ',';
            char grouping = decimal == '.' ? ',' : '.';
            s = s.replace(String.valueOf(grouping), "");
            if (decimal == ',') s = s.replace(',', '.');
        } else if (comma >= 0) {
            int digitsAfter = s.length() - comma - 1;
            s = digitsAfter == 1 || digitsAfter == 2 ? s.replace(',', '.') : s.replace(",", "");
        } else if (dot >= 0 && s.length() - dot - 1 > 2) {
            s = s.replace(".", "");
        }
        try { return new BigDecimal(s).stripTrailingZeros(); }
        catch (NumberFormatException e) { return null; }
    }

    public static final class Payment {
        public final String user;
        public final BigDecimal amount;
        Payment(String user, BigDecimal amount) { this.user = user; this.amount = amount; }
        @Override public String toString() { return user + " - " + amount.toPlainString(); }
    }
}
