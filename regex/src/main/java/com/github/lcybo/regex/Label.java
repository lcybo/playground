package com.github.lcybo.regex;

import com.github.lcybo.regex.jdk.utils.Interval;

public interface Label {

	Label XI = new Label() {
		@Override
		public boolean match(char c) {
			return false;
		}

		@Override
		public Interval interval() {
			throw new UnsupportedOperationException("calling interval() upon XI.");
		}

		@Override
		public char first() {
			return 'ε';
		}

		@Override
		public String toString() {
			return "ε";
		}
	};

	boolean match(char c);

	Interval interval();

	char first();

}
