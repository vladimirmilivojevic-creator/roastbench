package dev.roastbench.imports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the text of an Artisan .alog file. These files look like a Python dictionary (single quotes, True, None).
 * This reader only builds data (maps, lists, text, numbers). It never runs any code, so a bad file cannot do harm.
 * It also stops if the data is nested too deep.
 */
final class PythonLiteralParser {

    private static final int MAX_DEPTH = 32;

    private final String text;
    private int pos;

    private PythonLiteralParser(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        PythonLiteralParser parser = new PythonLiteralParser(text);
        parser.skipWhitespace();
        Object value = parser.parseValue(0);
        parser.skipWhitespace();
        if (parser.pos < text.length()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    private Object parseValue(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("Nesting is too deep");
        }
        skipWhitespace();
        if (pos >= text.length()) {
            throw error("Unexpected end of file");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> parseDict(depth);
            case '[', '(' -> parseList(depth, c == '[' ? ']' : ')');
            case '\'', '"' -> parseString();
            default -> parseAtom();
        };
    }

    private Map<String, Object> parseDict(int depth) {
        pos++; // {
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            Object key = parseValue(depth + 1);
            skipWhitespace();
            expect(':');
            Object value = parseValue(depth + 1);
            map.put(String.valueOf(key), value);
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw error("Expected ',' or '}'");
            }
            skipWhitespace();
            if (peek() == '}') { // trailing comma
                pos++;
                return map;
            }
        }
    }

    private List<Object> parseList(int depth, char close) {
        pos++; // [ or (
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == close) {
            pos++;
            return list;
        }
        while (true) {
            list.add(parseValue(depth + 1));
            skipWhitespace();
            char c = next();
            if (c == close) {
                return list;
            }
            if (c != ',') {
                throw error("Expected ',' or '" + close + "'");
            }
            skipWhitespace();
            if (peek() == close) { // trailing comma
                pos++;
                return list;
            }
        }
    }

    private String parseString() {
        char quote = text.charAt(pos++);
        StringBuilder sb = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == quote) {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) {
                break;
            }
            char e = text.charAt(pos++);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case '0' -> sb.append('\0');
                case 'x' -> sb.append((char) hex(2));
                case 'u' -> sb.append((char) hex(4));
                default -> sb.append(e); // \\ \' \" and unknown escapes: keep the character
            }
        }
        throw error("Unterminated string");
    }

    private int hex(int digits) {
        if (pos + digits > text.length()) {
            throw error("Bad escape sequence");
        }
        try {
            int value = Integer.parseInt(text.substring(pos, pos + digits), 16);
            pos += digits;
            return value;
        } catch (NumberFormatException ex) {
            throw error("Bad escape sequence");
        }
    }

    private Object parseAtom() {
        int start = pos;
        while (pos < text.length() && isAtomChar(text.charAt(pos))) {
            pos++;
        }
        if (start == pos) {
            throw error("Unexpected character '" + text.charAt(pos) + "'");
        }
        String atom = text.substring(start, pos);
        switch (atom) {
            case "True", "true":
                return Boolean.TRUE;
            case "False", "false":
                return Boolean.FALSE;
            case "None", "null":
                return null;
            case "nan", "NaN":
                return Double.NaN;
            case "inf", "Infinity":
                return Double.POSITIVE_INFINITY;
            case "-inf", "-Infinity":
                return Double.NEGATIVE_INFINITY;
            default:
                return parseNumber(atom, start);
        }
    }

    private Object parseNumber(String atom, int start) {
        try {
            if (atom.indexOf('.') < 0 && atom.indexOf('e') < 0 && atom.indexOf('E') < 0) {
                return Long.parseLong(atom);
            }
            return Double.parseDouble(atom);
        } catch (NumberFormatException ex) {
            try {
                return Double.parseDouble(atom); // integer too large for long
            } catch (NumberFormatException ignored) {
                pos = start;
                throw error("Unrecognised value '" + atom + "'");
            }
        }
    }

    private static boolean isAtomChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '+' || c == '_';
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("Unexpected end of file");
        }
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char expected) {
        if (next() != expected) {
            pos--;
            throw error("Expected '" + expected + "'");
        }
    }

    private ImportException error(String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new ImportException("Not a valid Artisan profile: " + message + " (line " + line + ", column " + column + ")");
    }
}
