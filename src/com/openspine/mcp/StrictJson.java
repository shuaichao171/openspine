package com.openspine.mcp;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/** Strict RFC 8259 syntax validation before libGDX's intentionally lenient JSON parser. */
final class StrictJson {
	private static final int MAX_DEPTH = 512;

	static Validation validate(String json) {
		if (json == null) throw new IllegalArgumentException("JSON is required.");
		Parser parser = new Parser(json);
		parser.skipWhitespace();
		parser.parseValue(0);
		parser.skipWhitespace();
		if (!parser.atEnd()) parser.fail("Unexpected trailing content");
		return new Validation(parser.requestIdNumberSupported);
	}

	record Validation(boolean requestIdNumberSupported) {}

	private static final class Parser {
		private final String input;
		private int offset;
		private boolean requestIdNumberSupported = true;

		Parser(String input) {
			this.input = input;
			if (!input.isEmpty() && input.charAt(0) == '\ufeff') offset = 1;
		}

		void parseValue(int depth) {
			if (depth > MAX_DEPTH) fail("JSON nesting exceeds " + MAX_DEPTH + " levels");
			if (atEnd()) fail("Expected a value");
			switch (input.charAt(offset)) {
			case '{' -> parseObject(depth);
			case '[' -> parseArray(depth);
			case '"' -> parseString(false);
			case 't' -> consumeLiteral("true");
			case 'f' -> consumeLiteral("false");
			case 'n' -> consumeLiteral("null");
			default -> parseNumber();
			}
		}

		private void parseObject(int depth) {
			offset++;
			skipWhitespace();
			if (consume('}')) return;
			Set<String> names = new HashSet<>();
			while (true) {
				if (atEnd() || input.charAt(offset) != '"') fail("Object names must be quoted strings");
				String name = parseString(true);
				if (!names.add(name)) fail("Duplicate object name");
				skipWhitespace();
				if (!consume(':')) fail("Expected ':' after object name");
				skipWhitespace();
				int valueStart = offset;
				parseValue(depth + 1);
				if (depth == 0 && "id".equals(name) && isNumberStart(input.charAt(valueStart)))
					requestIdNumberSupported = supportedRequestIdNumber(input.substring(valueStart, offset));
				skipWhitespace();
				if (consume('}')) return;
				if (!consume(',')) fail("Expected ',' or '}'");
				skipWhitespace();
			}
		}

		private void parseArray(int depth) {
			offset++;
			skipWhitespace();
			if (consume(']')) return;
			while (true) {
				parseValue(depth + 1);
				skipWhitespace();
				if (consume(']')) return;
				if (!consume(',')) fail("Expected ',' or ']'");
				skipWhitespace();
			}
		}

		private String parseString(boolean capture) {
			offset++;
			StringBuilder decoded = capture ? new StringBuilder() : null;
			while (!atEnd()) {
				char value = input.charAt(offset++);
				if (value == '"') return decoded == null ? "" : decoded.toString();
				if (value < 0x20) fail("Unescaped control character in string");
				if (value == '\\') {
					if (atEnd()) fail("Incomplete string escape");
					char escape = input.charAt(offset++);
					value = switch (escape) {
					case '"', '\\', '/' -> escape;
					case 'b' -> '\b';
					case 'f' -> '\f';
					case 'n' -> '\n';
					case 'r' -> '\r';
					case 't' -> '\t';
					case 'u' -> parseUnicodeEscape();
					default -> throw failure("Invalid string escape");
					};
				}
				if (Character.isHighSurrogate(value)) {
					char low = readLowSurrogate();
					if (decoded != null) decoded.append(value).append(low);
				} else {
					if (Character.isLowSurrogate(value)) fail("Unpaired low surrogate");
					if (decoded != null) decoded.append(value);
				}
			}
			fail("Unterminated string");
			return null;
		}

		private char readLowSurrogate() {
			if (offset < input.length() && Character.isLowSurrogate(input.charAt(offset)))
				return input.charAt(offset++);
			if (offset + 1 < input.length() && input.charAt(offset) == '\\' && input.charAt(offset + 1) == 'u') {
				offset += 2;
				char low = parseUnicodeEscapeDigits();
				if (Character.isLowSurrogate(low)) return low;
			}
			fail("High surrogate must be followed by a low surrogate");
			return 0;
		}

		private char parseUnicodeEscape() {
			return parseUnicodeEscapeDigits();
		}

		private char parseUnicodeEscapeDigits() {
			if (offset + 4 > input.length()) fail("Incomplete Unicode escape");
			int value = 0;
			for (int index = 0; index < 4; index++) {
				int digit = hexDigit(input.charAt(offset++));
				if (digit < 0) fail("Invalid Unicode escape");
				value = value * 16 + digit;
			}
			return (char)value;
		}

		private void parseNumber() {
			int start = offset;
			consume('-');
			if (consume('0')) {
				if (!atEnd() && isDigit(input.charAt(offset))) fail("Leading zero in number");
			} else {
				if (atEnd() || input.charAt(offset) < '1' || input.charAt(offset) > '9') fail("Invalid value");
				while (!atEnd() && isDigit(input.charAt(offset))) offset++;
			}
			if (consume('.')) {
				if (atEnd() || !isDigit(input.charAt(offset))) fail("Expected digits after decimal point");
				while (!atEnd() && isDigit(input.charAt(offset))) offset++;
			}
			if (consume('e') || consume('E')) {
				if (!atEnd() && (input.charAt(offset) == '+' || input.charAt(offset) == '-')) offset++;
				if (atEnd() || !isDigit(input.charAt(offset))) fail("Expected exponent digits");
				while (!atEnd() && isDigit(input.charAt(offset))) offset++;
			}
			if (offset == start) fail("Invalid value");
		}

		private void consumeLiteral(String literal) {
			if (!input.regionMatches(offset, literal, 0, literal.length())) fail("Invalid value");
			offset += literal.length();
		}

		void skipWhitespace() {
			while (!atEnd()) {
				char value = input.charAt(offset);
				if (value != ' ' && value != '\t' && value != '\r' && value != '\n') return;
				offset++;
			}
		}

		boolean atEnd() {
			return offset == input.length();
		}

		private boolean consume(char expected) {
			if (atEnd() || input.charAt(offset) != expected) return false;
			offset++;
			return true;
		}

		private static boolean isDigit(char value) {
			return value >= '0' && value <= '9';
		}

		private static boolean isNumberStart(char value) {
			return value == '-' || isDigit(value);
		}

		private static boolean supportedRequestIdNumber(String token) {
			if (token.length() > 64) return false;
			try {
				BigDecimal number = new BigDecimal(token);
				number.toBigIntegerExact();
				if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
					Long.parseLong(token);
					return true;
				}
				// libGDX stores decimal/exponent numbers as doubles, so keep them in the exactly portable range.
				return number.abs().compareTo(BigDecimal.valueOf(9_007_199_254_740_991L)) <= 0;
			} catch (ArithmeticException | NumberFormatException unsupported) {
				return false;
			}
		}

		private static int hexDigit(char value) {
			if (value >= '0' && value <= '9') return value - '0';
			if (value >= 'a' && value <= 'f') return value - 'a' + 10;
			if (value >= 'A' && value <= 'F') return value - 'A' + 10;
			return -1;
		}

		void fail(String message) {
			throw failure(message);
		}

		private IllegalArgumentException failure(String message) {
			return new IllegalArgumentException(message + " at character " + offset + '.');
		}
	}

	private StrictJson() {}
}
