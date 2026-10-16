package de.kalenderwerker.app;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Small, loss-preserving iCalendar editor for VEVENT records. */
public final class IcsCalendar {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT);
    public final List<Event> events = new ArrayList<>();
    public final List<List<String>> timezones = new ArrayList<>();
    public String name = "";

    public static IcsCalendar parse(String source) {
        IcsCalendar calendar = new IcsCalendar();
        List<String> lines = unfold(source);
        List<String> outer = new ArrayList<>();
        List<String> eventLines = null;
        int depth = 0;
        for (String line : lines) {
            if (line.equalsIgnoreCase("BEGIN:VEVENT")) {
                eventLines = new ArrayList<>();
                eventLines.add(line);
                depth = 1;
                continue;
            }
            if (eventLines != null) {
                eventLines.add(line);
                if (line.regionMatches(true, 0, "BEGIN:", 0, 6)) depth++;
                if (line.regionMatches(true, 0, "END:", 0, 4) && --depth == 0) {
                    Event event = Event.fromLines(eventLines);
                    if (event != null) calendar.events.add(event);
                    eventLines = null;
                }
            } else {
                outer.add(line);
            }
        }
        List<String> zone = null;
        depth = 0;
        for (String line : outer) {
            if (line.equalsIgnoreCase("BEGIN:VTIMEZONE")) {
                zone = new ArrayList<>();
                zone.add(line);
                depth = 1;
            } else if (zone != null) {
                zone.add(line);
                if (line.regionMatches(true, 0, "BEGIN:", 0, 6)) depth++;
                if (line.regionMatches(true, 0, "END:", 0, 4) && --depth == 0) {
                    calendar.timezones.add(zone);
                    zone = null;
                }
            } else {
                Property property = Property.parse(line);
                if (property != null && property.name.equals("X-WR-CALNAME")) calendar.name = unescape(property.value);
            }
        }
        return calendar;
    }

    public void addAll(IcsCalendar other) {
        events.addAll(other.events);
        Map<String, List<String>> unique = new LinkedHashMap<>();
        for (List<String> zone : timezones) unique.put(zoneKey(zone), zone);
        for (List<String> zone : other.timezones) unique.putIfAbsent(zoneKey(zone), zone);
        timezones.clear();
        timezones.addAll(unique.values());
        if (name.trim().isEmpty()) name = other.name;
    }

    private static String zoneKey(List<String> zone) {
        for (String line : zone) {
            Property p = Property.parse(line);
            if (p != null && p.name.equals("TZID")) return p.value;
        }
        return String.join("\n", zone);
    }

    public String serialize() {
        StringBuilder out = new StringBuilder();
        out.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Kalenderwerker//Android//DE\r\nCALSCALE:GREGORIAN\r\nMETHOD:PUBLISH\r\n");
        if (!name.trim().isEmpty()) out.append("X-WR-CALNAME:").append(escape(name)).append("\r\n");
        for (List<String> zone : timezones) for (String line : zone) out.append(fold(line)).append("\r\n");
        for (Event event : events) for (String line : event.lines) out.append(fold(line)).append("\r\n");
        return out.append("END:VCALENDAR\r\n").toString();
    }

    private static List<String> unfold(String input) {
        String normalized = input.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        List<String> result = new ArrayList<>();
        for (String line : normalized.split("\n", -1)) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && !result.isEmpty()) {
                int last = result.size() - 1;
                result.set(last, result.get(last) + line.substring(1));
            } else if (!line.isEmpty()) result.add(line);
        }
        return result;
    }

    static String fold(String line) {
        StringBuilder result = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < line.length();) {
            int cp = line.codePointAt(i);
            String chars = new String(Character.toChars(cp));
            int size = chars.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 73) { result.append("\r\n "); bytes = 1; }
            result.append(chars);
            bytes += size;
            i += Character.charCount(cp);
        }
        return result.toString();
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r\n", "\n").replace('\r', '\n')
                .replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;");
    }

    static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                if (c == 'n' || c == 'N') result.append('\n'); else result.append(c);
                escaped = false;
            } else if (c == '\\') escaped = true; else result.append(c);
        }
        if (escaped) result.append('\\');
        return result.toString();
    }

    public static final class Event {
        public final List<String> lines;
        public String title;
        public String location;
        public String description;
        public String startValue;
        public String startParams;
        public String endValue;
        public String endParams;
        public String duration;
        public boolean allDay;

        private Event(List<String> lines) { this.lines = new ArrayList<>(lines); }

        static Event fromLines(List<String> lines) {
            Event event = new Event(lines);
            Property start = event.get("DTSTART");
            if (start == null) return null;
            event.startValue = start.value;
            event.startParams = start.params;
            event.allDay = start.value.length() == 8 || start.params.toUpperCase(Locale.ROOT).contains("VALUE=DATE");
            Property end = event.get("DTEND");
            event.endValue = end == null ? "" : end.value;
            event.endParams = end == null ? "" : end.params;
            Property summary = event.get("SUMMARY");
            Property location = event.get("LOCATION");
            Property description = event.get("DESCRIPTION");
            Property duration = event.get("DURATION");
            event.title = summary == null ? "(Ohne Titel)" : unescape(summary.value);
            event.location = location == null ? "" : unescape(location.value);
            event.description = description == null ? "" : unescape(description.value);
            event.duration = duration == null ? "" : duration.value;
            return event;
        }

        Property get(String name) {
            for (String line : lines) { Property p = Property.parse(line); if (p != null && p.name.equals(name)) return p; }
            return null;
        }

        void set(String name, String value, String fallbackParams) {
            int index = -1;
            Property old = null;
            for (int i = 0; i < lines.size(); i++) {
                Property p = Property.parse(lines.get(i));
                if (p != null && p.name.equals(name)) { index = i; old = p; break; }
            }
            String params = old == null ? fallbackParams : old.params;
            String updated = name + params + ":" + value;
            if (index >= 0) lines.set(index, updated);
            else lines.add(Math.max(1, lines.size() - 1), updated);
        }

        void remove(String name) { lines.removeIf(line -> { Property p = Property.parse(line); return p != null && p.name.equals(name); }); }

        public LocalDateTime startDateTime() { return parseDateTime(startValue, startParams); }
        public LocalDateTime endDateTime() {
            if (!endValue.trim().isEmpty()) return parseDateTime(endValue, endParams);
            if (!duration.trim().isEmpty()) return startDateTime().plusSeconds(durationSeconds(duration));
            return allDay ? startDateTime().plusDays(1) : startDateTime().plusHours(1);
        }

        public String displayTime() {
            if (allDay) return "Ganztägig";
            return startDateTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm", Locale.GERMANY));
        }

        public void update(String newTitle, LocalDateTime start, LocalDateTime end, boolean newAllDay, String newLocation, String newDescription) {
            boolean wasUtc = startValue.endsWith("Z");
            String st = formatDateTime(start, newAllDay, wasUtc);
            String en = formatDateTime(newAllDay ? end.plusDays(1) : end, newAllDay, wasUtc);
            String pStart = newAllDay ? ";VALUE=DATE" : startParams.replaceAll("(?i);VALUE=DATE", "");
            String pEnd = newAllDay ? ";VALUE=DATE" : (endParams.trim().isEmpty() ? startParams : endParams).replaceAll("(?i);VALUE=DATE", "");
            set("SUMMARY", escape(newTitle), "");
            set("DTSTART", st, pStart);
            set("DTEND", en, pEnd);
            remove("DURATION");
            if (newLocation.trim().isEmpty()) remove("LOCATION"); else set("LOCATION", escape(newLocation), "");
            if (newDescription.trim().isEmpty()) remove("DESCRIPTION"); else set("DESCRIPTION", escape(newDescription), "");
            if (get("UID") == null) set("UID", UUID.randomUUID() + "@kalenderwerker.app", "");
            if (get("DTSTAMP") == null) set("DTSTAMP", DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).format(LocalDateTime.now(ZoneOffset.UTC)), "");
            startValue = st; startParams = pStart; endValue = en; endParams = pEnd; duration = ""; allDay = newAllDay;
        }

        private static String formatDateTime(LocalDateTime value, boolean dateOnly, boolean utc) {
            if (dateOnly) return DATE.format(value.toLocalDate());
            if (utc) return LOCAL.format(value.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)) + "Z";
            return LOCAL.format(value);
        }

        private static LocalDateTime parseDateTime(String value, String params) {
            if (value == null || value.trim().isEmpty()) return LocalDateTime.now();
            try {
                if (value.length() == 8) return LocalDate.parse(value, DATE).atStartOfDay();
                boolean utc = value.endsWith("Z");
                String raw = utc ? value.substring(0, value.length() - 1) : value;
                LocalDateTime local = LocalDateTime.parse(raw.length() == 13 ? raw + "00" : raw, LOCAL);
                return utc ? local.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() : local;
            } catch (Exception ignored) { return LocalDateTime.now(); }
        }

        private static long durationSeconds(String value) {
            try {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?").matcher(value);
                if (!m.matches()) return 0;
                return Long.parseLong(m.group(1) == null ? "0" : m.group(1)) * 86400
                        + Long.parseLong(m.group(2) == null ? "0" : m.group(2)) * 3600
                        + Long.parseLong(m.group(3) == null ? "0" : m.group(3)) * 60
                        + Long.parseLong(m.group(4) == null ? "0" : m.group(4));
            } catch (Exception ignored) { return 0; }
        }
    }

    static final class Property {
        final String name, params, value;
        Property(String name, String params, String value) { this.name = name; this.params = params; this.value = value; }
        static Property parse(String line) {
            boolean quoted = false;
            int colon = -1;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') quoted = !quoted;
                else if (c == ':' && !quoted) { colon = i; break; }
            }
            if (colon < 0) return null;
            String left = line.substring(0, colon);
            int semi = left.indexOf(';');
            return new Property((semi < 0 ? left : left.substring(0, semi)).toUpperCase(Locale.ROOT),
                    semi < 0 ? "" : left.substring(semi), line.substring(colon + 1));
        }
    }
}
