package com.github.lcybo.regex;

import com.github.lcybo.regex.jdk.utils.Interval;

import java.util.Objects;

import static com.github.lcybo.regex.NFA.CHAR_ASCII_END;
import static com.github.lcybo.regex.NFA.CHAR_ASCII_START;
import static com.github.lcybo.regex.NFA.CHAR_BACKQUOTE;
import static com.github.lcybo.regex.NFA.CHAR_BLANK;
import static com.github.lcybo.regex.NFA.CHAR_CNTL_SPACE_END;
import static com.github.lcybo.regex.NFA.CHAR_CNTL_SPACE_START;
import static com.github.lcybo.regex.NFA.CHAR_EXCLAIMATION;
import static com.github.lcybo.regex.NFA.CHAR_LOWER_END;
import static com.github.lcybo.regex.NFA.CHAR_LOWER_START;
import static com.github.lcybo.regex.NFA.CHAR_NUM_END;
import static com.github.lcybo.regex.NFA.CHAR_NUM_START;
import static com.github.lcybo.regex.NFA.CHAR_UNDERSCORE;
import static com.github.lcybo.regex.NFA.CHAR_UPPER_END;
import static com.github.lcybo.regex.NFA.CHAR_UPPER_START;
import static com.github.lcybo.regex.NFA.RANGES_EMPTY;
import static com.github.lcybo.regex.NFA.isWord;

public class Range implements Label {

	static final Range RANGE_DOT                = new Range(CHAR_ASCII_START, CHAR_ASCII_END);
	static final Range RANGE_DIGIT              = new Range(CHAR_NUM_START, CHAR_NUM_END);
	static final Range RANGE_UPPER              = new Range(CHAR_UPPER_START, CHAR_UPPER_END);
	static final Range RANGE_LOWER              = new Range(CHAR_LOWER_START, CHAR_LOWER_END);
	static final Range RANGE_C_DIGIT            = new Range(CHAR_ASCII_START, CHAR_NUM_START);
	static final Range RANGE_DIGIT_C            = new Range(CHAR_NUM_END, CHAR_ASCII_END);
	static final Range RANGE_DIGIT_C_UPPER      = new Range(CHAR_NUM_END, CHAR_UPPER_START);
	static final Range RANGE_UPPER_C_UNDER      = new Range(CHAR_UPPER_END, CHAR_UNDERSCORE);
	static final Range RANGE_BQ_C_LOWER         = new Range(CHAR_BACKQUOTE, CHAR_LOWER_START);
	static final Range RANGE_UPPER_C_LOWER      = new Range(CHAR_UPPER_END, CHAR_LOWER_START);
	static final Range RANGE_LOWER_C            = new Range(CHAR_LOWER_END, CHAR_ASCII_END);
	static final Range RANGE_CNTL_SPACE         = new Range(CHAR_CNTL_SPACE_START, CHAR_CNTL_SPACE_END);
	static final Range RANGE_C_CNTL_SPACE       = new Range(CHAR_ASCII_START, CHAR_CNTL_SPACE_START);
	static final Range RANGE_CNTL_SPACE_C_BLANK = new Range(CHAR_CNTL_SPACE_END, CHAR_BLANK);
	static final Range RANGE_EXCLAIMATION_C     = new Range(CHAR_EXCLAIMATION, CHAR_ASCII_END);


	// inclusive
	final char from;
	// exclusive
	final char to;

	public Range(char from, char to) {
		this.from = from;
		this.to = to;
	}

	@Override
	public boolean match(char c) {
		return c >= from && c < to;
	}

	@Override
	public Interval interval() {
		return new Interval(from, to);
	}

	@Override
	public char first() {
		return from;
	}

	public Label[] split(char d) {
		return new Range[]{new Range(from, d), new Range(d, to)};
	}

	public Label[] remove(Range range) {
		if (range.from >= to || range.to <= from) {
			return new Range[]{this};
		}
		if (range.from <= from && range.to >= to) {
			return RANGES_EMPTY;
		}
		if (range.from <= from) {
			return new Range[]{new Range(range.to, to)};
		}
		if (range.to >= to) {
			return new Range[]{new Range(from, range.from)};
		}
		return new Range[]{new Range(from, range.from), new Range(range.to, to)};
	}

	public Range intersect(Range range) {
		if (range.from >= to || range.to <= from) {
			throw new IllegalArgumentException();
		}
		if (range.from <= from && range.to >= to) {
			return this;
		}
		if (range.from <= from) {
			return new Range(from, range.to);
		}
		if (range.to >= to) {
			return new Range(range.from, to);
		}
		return range;
	}

	public Range concat(Range range) {
		if (to == range.from) {
			return new Range(from, range.to);
		}
		if (range.to == from) {
			return new Range(range.from, to);
		}
		throw new IllegalArgumentException();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Range range = (Range) o;
		return from == range.from &&
				to == range.to;
	}

	@Override
	public int hashCode() {
		return Objects.hash(from, to);
	}

	@Override
	public String toString() {
		boolean literal = isWord(from) && isWord((char) (to - 1));
		if (literal) {
			return "[" + from + ", " + (char) (to - 1) + "]";
		}
		return "[0x" + Integer.toHexString(from) + ", " + "0x" + Integer.toHexString(to - 1) + "]";
	}

}
