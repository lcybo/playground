package com.github.lcybo.regex;

import com.github.lcybo.regex.jdk.utils.Interval;

import java.util.Objects;

import static com.github.lcybo.regex.NFA.isWord;

public class Single implements Label {

	static final Single[] SINGLES = new Single[128];

	static {
		for (char c = 0; c < 128; c++) {
			SINGLES[c] = new Single(c);
		}
	}

	private final char ch;

	private Single(char c) {
		this.ch = c;
	}

	static Single single(char c) {
		if (c < 128) {
			return SINGLES[c];
		}
		throw new IllegalArgumentException("Only ASCII supported.");
	}

	@Override
	public boolean match(char c) {
		return ch == c;
	}

	@Override
	public Interval interval() {
		return new Interval(ch, (char) (ch + 1));
	}

	@Override
	public char first() {
		return ch;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Single single = (Single) o;
		return ch == single.ch;
	}

	@Override
	public int hashCode() {
		return Objects.hash(ch);
	}

	public String toString() {
		if (isWord(ch)) {
			return String.valueOf(ch);
		}
		return "0x" + Integer.toHexString(ch);
	}
}
