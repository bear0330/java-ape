package com.nuwainfo.javaape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A deliberately small JSON parser for trusted Host Services protocol envelopes. */
final class HostJson {
  private HostJson() {
  }

  static Object parse(String json) {
    Parser parser = new Parser(json);
    Object value = parser.parseValue();
    parser.skipWhitespace();

    if (!parser.atEnd()) {
      throw new HostServices.HostProtocolException("trailing JSON data");
    }

    return value;
  }

  private static final class Parser {
    private final String text;
    private int position;

    private Parser(String text) {
      this.text = text;
    }

    private Object parseValue() {
      skipWhitespace();
      char token = take();

      switch (token) {
        case '{':
          return parseObject();
        case '[':
          return parseArray();
        case '"':
          return parseString();
        case 't':
          consumeLiteral("rue");
          return Boolean.TRUE;
        case 'f':
          consumeLiteral("alse");
          return Boolean.FALSE;
        case 'n':
          consumeLiteral("ull");
          return null;
        default:
          --position;
          return parseNumber();
      }
    }

    private Map<String, Object> parseObject() {
      Map<String, Object> result = new LinkedHashMap<>();
      skipWhitespace();

      if (nextIs('}')) {
        return result;
      }

      while (true) {
        skipWhitespace();
        expect('"', "object key must be a string");

        String key = parseString();
        skipWhitespace();
        expect(':', "missing JSON colon");

        result.put(key, parseValue());
        skipWhitespace();

        if (nextIs('}')) {
          return result;
        }

        expect(',', "missing JSON comma");
      }
    }

    private List<Object> parseArray() {
      List<Object> result = new ArrayList<>();
      skipWhitespace();

      if (nextIs(']')) {
        return result;
      }

      while (true) {
        result.add(parseValue());
        skipWhitespace();

        if (nextIs(']')) {
          return result;
        }

        expect(',', "missing JSON comma");
      }
    }

    private String parseString() {
      StringBuilder result = new StringBuilder();

      while (true) {
        char value = take();
        if (value == '"') {
          return result.toString();
        }

        if (value != '\\') {
          result.append(value);
          continue;
        }

        appendEscape(result);
      }
    }

    private void appendEscape(StringBuilder result) {
      char escape = take();

      switch (escape) {
        case '"':
          result.append('"');
          return;
        case '\\':
          result.append('\\');
          return;
        case '/':
          result.append('/');
          return;
        case 'b':
          result.append('\b');
          return;
        case 'f':
          result.append('\f');
          return;
        case 'n':
          result.append('\n');
          return;
        case 'r':
          result.append('\r');
          return;
        case 't':
          result.append('\t');
          return;
        case 'u':
          result.append(parseUnicodeEscape());
          return;
        default:
          throw new HostServices.HostProtocolException("invalid JSON escape");
      }
    }

    private char parseUnicodeEscape() {
      int end = position + 4;

      try {
        char result = (char) Integer.parseInt(text.substring(position, end), 16);
        position = end;
        return result;
      } catch (RuntimeException error) {
        throw new HostServices.HostProtocolException("invalid JSON unicode escape", error);
      }
    }

    private Number parseNumber() {
      int start = position;
      boolean fraction = false;

      if (!atEnd() && text.charAt(position) == '-') {
        ++position;
      }

      consumeDigits();

      if (!atEnd() && text.charAt(position) == '.') {
        fraction = true;
        ++position;
        consumeDigits();
      }

      if (!atEnd() && isExponent(text.charAt(position))) {
        fraction = true;
        ++position;

        if (!atEnd() && isSign(text.charAt(position))) {
          ++position;
        }

        consumeDigits();
      }

      String number = text.substring(start, position);

      try {
        if (fraction) {
          return Double.valueOf(number);
        }

        return Long.valueOf(number);
      } catch (RuntimeException error) {
        throw new HostServices.HostProtocolException("invalid JSON number", error);
      }
    }

    private void consumeDigits() {
      while (!atEnd() && Character.isDigit(text.charAt(position))) {
        ++position;
      }
    }

    private boolean atEnd() {
      return position == text.length();
    }

    private void skipWhitespace() {
      while (!atEnd() && Character.isWhitespace(text.charAt(position))) {
        ++position;
      }
    }

    private char take() {
      if (atEnd()) {
        throw new HostServices.HostProtocolException("unexpected end of JSON");
      }

      return text.charAt(position++);
    }

    private void consumeLiteral(String expected) {
      for (int index = 0; index < expected.length(); ++index) {
        if (take() != expected.charAt(index)) {
          throw new HostServices.HostProtocolException("invalid JSON literal");
        }
      }
    }

    private boolean nextIs(char expected) {
      if (atEnd() || text.charAt(position) != expected) {
        return false;
      }

      ++position;
      return true;
    }

    private void expect(char expected, String message) {
      if (take() != expected) {
        throw new HostServices.HostProtocolException(message);
      }
    }

    private boolean isExponent(char value) {
      return value == 'e' || value == 'E';
    }

    private boolean isSign(char value) {
      return value == '+' || value == '-';
    }
  }
}
