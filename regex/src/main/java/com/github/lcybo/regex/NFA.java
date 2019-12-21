package com.github.lcybo.regex;

import com.github.lcybo.regex.jdk.utils.Interval;
import com.github.lcybo.regex.jdk.utils.IntervalNode;
import com.github.lcybo.regex.jdk.utils.IntervalTree;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.github.lcybo.regex.NFA.ClassState.NONE;
import static com.github.lcybo.regex.NFA.ClassState.RANGE;
import static com.github.lcybo.regex.NFA.ClassState.SINGLE;
import static com.github.lcybo.regex.Range.RANGE_BQ_C_LOWER;
import static com.github.lcybo.regex.Range.RANGE_CNTL_SPACE;
import static com.github.lcybo.regex.Range.RANGE_CNTL_SPACE_C_BLANK;
import static com.github.lcybo.regex.Range.RANGE_C_CNTL_SPACE;
import static com.github.lcybo.regex.Range.RANGE_C_DIGIT;
import static com.github.lcybo.regex.Range.RANGE_DIGIT_C;
import static com.github.lcybo.regex.Range.RANGE_DIGIT_C_UPPER;
import static com.github.lcybo.regex.Range.RANGE_DIGIT;
import static com.github.lcybo.regex.Range.RANGE_DOT;
import static com.github.lcybo.regex.Range.RANGE_EXCLAIMATION_C;
import static com.github.lcybo.regex.Range.RANGE_LOWER_C;
import static com.github.lcybo.regex.Range.RANGE_LOWER;
import static com.github.lcybo.regex.Range.RANGE_UPPER_C_LOWER;
import static com.github.lcybo.regex.Range.RANGE_UPPER_C_UNDER;
import static com.github.lcybo.regex.Range.RANGE_UPPER;
import static com.github.lcybo.regex.Single.single;

public class NFA {

	static final char    CHAR_START            = 0x81;
	static final char    CHAR_END              = 0x82;
	static final char    CHAR_DIGITS           = 0x83;
	static final char    CHAR_NON_DIGITS       = 0x84;
	static final char    CHAR_SPACES           = 0x85;
	static final char    CHAR_NON_SPACES       = 0x86;
	static final char    CHAR_WORDS            = 0x87;
	static final char    CHAR_NON_WORDS        = 0x88;
	static final char    CHAR_ASCII_START      = 0x00;
	static final char    CHAR_ASCII_END        = 0x80;
	static final char    CHAR_NUM_START        = '0';
	static final char    CHAR_NUM_END          = '9' + 1;
	static final char    CHAR_UPPER_START      = 'A';
	static final char    CHAR_UPPER_END        = 'Z' + 1;
	static final char    CHAR_LOWER_START      = 'a';
	static final char    CHAR_LOWER_END        = 'z' + 1;
	static final char    CHAR_UNDERSCORE       = '_';
	static final char    CHAR_BACKQUOTE        = '`';
	static final char    CHAR_CNTL_SPACE_START = '\11';
	static final char    CHAR_CNTL_SPACE_END   = '\16';
	static final char    CHAR_BLANK            = ' ';
	static final char    CHAR_EXCLAIMATION     = '!';
	static final Label[] RANGES_EMPTY          = new Label[]{};
	static final Label[] RANGES_DIGIT          = new Label[]{RANGE_DIGIT};
	static final Label[] RANGES_NON_DIGIT      = new Label[]{RANGE_C_DIGIT, RANGE_DIGIT_C};
	static final Label[] RANGES_ALNUM          = new Label[]{RANGE_DIGIT, RANGE_UPPER, RANGE_LOWER};
	static final Label[] RANGES_NON_ALNUM      = new Label[]{RANGE_C_DIGIT, RANGE_DIGIT_C_UPPER, RANGE_UPPER_C_LOWER, RANGE_LOWER_C};
	static final Label[] RANGES_WORD           = new Label[]{RANGE_DIGIT, RANGE_UPPER, single(CHAR_UNDERSCORE), RANGE_LOWER};
	static final Label[] RANGES_NON_WORD       = new Label[]{RANGE_C_DIGIT, RANGE_DIGIT_C_UPPER, RANGE_UPPER_C_UNDER, RANGE_BQ_C_LOWER, RANGE_LOWER_C};
	static final Label[] RANGES_SPACE          = new Label[]{RANGE_CNTL_SPACE, single(CHAR_BLANK)};
	static final Label[] RANGES_NON_SPACE      = new Label[]{RANGE_C_CNTL_SPACE, RANGE_CNTL_SPACE_C_BLANK, RANGE_EXCLAIMATION_C};

	static final Label BEGIN = new Begin();
	static final Label END   = new End();

	final String regex;

	private NFAGraph   graph;
	private CharBuffer buffer;

	public NFA(String regex) {
		Objects.requireNonNull(regex);
		this.regex = regex;
		buffer = CharBuffer.wrap(regex);
		graph = compile();
		sanityCheck();
	}

	private void sanityCheck() {
		if (remaining()) {
			char c = peek();
			if (c == ')') {
				throw new IllegalStateException("Unpaired character '" + c + "'");
			}
			throw new IllegalStateException("Unexpected error, regex not fully compiled");
		}
	}

	private boolean remaining() {
		return buffer.hasRemaining();
	}

	private char fetch() {
		return buffer.get();
	}

	private char peek() {
		return buffer.get(buffer.position());
	}

	private NFAGraph oneOrMore(NFAGraph graph) {
		NFAGraph copy = graph.copy();
		graph.concat(copy.closure());
		return graph;
	}

	private NFAGraph zeroOrMore(NFAGraph graph) {
		return graph.closure();
	}

	private NFAGraph zeroOrOne(NFAGraph graph) {
		return graph.exists();
	}

	private NFAGraph compile() {
		NFAGraph graph = new NFAGraph();
		while (remaining()) {
			char c = peek();
			switch (c) {
				case ')':
					return graph;
				case '{':
				case '*':
				case '+':
				case '?':
					throw new IllegalStateException("Detached repeat meta character " + c);
				case '^':
					// no multi-line mode support
					fetch();
					if (!remaining() || peek() != '^')
						attach(graph, BEGIN);
					break;
				case '$':
					fetch();
					if (!remaining() || peek() != '$')
						attach(graph, END);
					break;
				case '.':
					fetch();
					attach(graph, RANGE_DOT);
					break;
				case '(':
					fetch();
					NFAGraph group = compile();
					consume(')', "unclosed group");
					attach(graph, group);
					break;
				case '[':
					fetch();
					Label[] clazz = clazz();
					consume(']', "unclosed character class");
					attach(graph, clazz);
					break;
				case '|':
					fetch();
					graph.union(compile());
					break;
				case '\\':
					fetch();
					char escaped = escape();
					if (escaped < 0x80) {
						attach(graph, single(escaped));
					} else {
						attach(graph, toLabels(escaped));
					}
					break;
				default:
					fetch();
					if (c > 127) {
						throw new IllegalStateException("Only ASCII supported");
					}
					attach(graph, single(c));
					break;
			}
		}
		return graph;
	}

	private char escape() {
		if (remaining()) {
			char c = peek();
			switch (c) {
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7':
					return octal();
				case 'x':
					fetch();
					return hex();
				case 'r':
					fetch();
					return '\r';
				case 'n':
					fetch();
					return '\n';
				case 't':
					fetch();
					return '\t';
				case 'a':
					fetch();
					return '\7';
				case 'b':
					fetch();
					return '\10';
				case 'd':
					fetch();
					return CHAR_DIGITS;
				case 'D':
					fetch();
					return CHAR_NON_DIGITS;
				case 'e':
					fetch();
					return '\33';
				case 'f':
					fetch();
					return '\f';
				case 's':
					fetch();
					return CHAR_SPACES;
				case 'S':
					fetch();
					return CHAR_NON_SPACES;
				case 'w':
					fetch();
					return CHAR_WORDS;
				case 'W':
					fetch();
					return CHAR_NON_WORDS;
				default:
					if (!isAlnum(c)) {
						return fetch();
					}
			}
		}
		throw new IllegalStateException("Unsupported escape sequence");
	}

	private static final int[] HEX = new int[128];

	static {
		Arrays.fill(HEX, -1);
		HEX['0'] = 0x0;
		HEX['1'] = 0x1;
		HEX['2'] = 0x2;
		HEX['3'] = 0x3;
		HEX['4'] = 0x4;
		HEX['5'] = 0x5;
		HEX['6'] = 0x6;
		HEX['7'] = 0x7;
		HEX['8'] = 0x8;
		HEX['9'] = 0x9;
		HEX['a'] = 0xa;
		HEX['b'] = 0xb;
		HEX['c'] = 0xc;
		HEX['d'] = 0xd;
		HEX['e'] = 0xe;
		HEX['f'] = 0xf;
		HEX['A'] = 0xa;
		HEX['B'] = 0xb;
		HEX['C'] = 0xc;
		HEX['D'] = 0xd;
		HEX['E'] = 0xe;
		HEX['F'] = 0xf;
	}

	// 0 ~ 127
	private char hex() {
		if (!remaining()) {
			throw new IllegalStateException("Unexpected end of hex sequence");
		}
		char high = fetch();
		if (!remaining()) {
			throw new IllegalStateException("Unexpected end of hex sequence");
		}
		char low = fetch();
		if (HEX[high] < 0 || HEX[low] < 0) {
			throw new IllegalStateException("Invalid hex sequence: " + high + low);
		}
		int t = (HEX[high] << 4) + HEX[low];
		if (t >= 128) {
			throw new IllegalStateException("Only ASCII character is accepted.");
		}
		return (char) t;
	}

	// 0 ~ 127
	private char octal() {
		int t = fetch() - '0';
		while ((t << 3) < 128) {
			char c = peek();
			if (c >= '0' && c < '8') {
				t = (t << 3) + (fetch() - '0');
				continue;
			}
			break;
		}
		return (char) t;
	}

	private Label[] toLabels(char c) {
		switch (c) {
			case CHAR_DIGITS:
				return RANGES_DIGIT;
			case CHAR_NON_DIGITS:
				return RANGES_NON_DIGIT;
			case CHAR_SPACES:
				return RANGES_SPACE;
			case CHAR_NON_SPACES:
				return RANGES_NON_SPACE;
			case CHAR_WORDS:
				return RANGES_WORD;
			case CHAR_NON_WORDS:
				return RANGES_NON_WORD;
			default:
				throw new IllegalStateException("Should not reach here, c = " + c);
		}
	}

	private void attach(NFAGraph graph, Label label) {
		if (remaining()) {
			char c = peek();
			switch (c) {
				case '{':
				case '*':
				case '+':
				case '?':
					attach(graph, new NFAGraph(label));
					return;
			}
		}
		graph.append(label, new NFANode());
	}

	private void attach(NFAGraph graph, Label[] labels) {
		attach(graph, union(labels));
	}

	private void attach(NFAGraph graph, NFAGraph sub) {
		if (remaining()) {
			NFAGraph enhanced;
			char c = peek();
			switch (c) {
				case '{':
					fetch();
					enhanced = repeat(sub);
					consume('}', "Unclosed repeation");
					break;
				case '*':
					fetch();
					enhanced = zeroOrMore(sub);
					break;
				case '+':
					fetch();
					enhanced = oneOrMore(sub);
					break;
				case '?':
					fetch();
					enhanced = zeroOrOne(sub);
					break;
				default:
					enhanced = sub;
					break;
			}
			graph.concat(enhanced);
		} else {
			graph.concat(sub);
		}
	}

	private NFAGraph repeat(NFAGraph graph) {
		int[] r = new int[2];
		int idx = 0;
		while (remaining()) {
			char c = peek();
			if (!isDigit(c) && c != ',' && c != '}') {
				break;
			}
			if (isDigit(c)) {
				fetch();
				if (idx == 1 && r[idx] == 0 && c == '0') {
					break;
				}
				r[idx] *= 10;
				r[idx] += c - '0';
			} else if (c == ',') {
				fetch();
				if (++idx > 1) {
					break;
				}
			} else {
				List<NFAGraph> temp = new ArrayList<>();
				int min = r[0];
				int max = r[idx];
				if (idx == 1 && r[1] < r[0]) {
					break;
				}
				for (int i = 1; i < min; i++) {
					temp.add(graph.copy());
				}
				for (int i = min; i < max; i++) {
					temp.add(zeroOrOne(graph.copy()));
				}
				if (min == 0) {
					graph = new NFAGraph();
				}
				for (NFAGraph g : temp) {
					graph.concat(g);
				}
				return graph;
			}
		}
		throw new IllegalStateException("Illegal repeat.");
	}

	private NFAGraph union(Label[] labels) {
		NFAGraph graph = new NFAGraph(labels[0]);
		for (int i = 1; i < labels.length; i++) {
			graph.union(new NFAGraph(labels[i]));
		}
		return graph;
	}

	enum ClassState {
		NONE, SINGLE, RANGE
	}

	private Label[] clazz() {
		boolean reversed = false;
		char c = peek();
		IntervalTree tree = new IntervalTree(Comparator.naturalOrder());
		if (c == '^') {
			reversed = true;
			fetch();
			c = peek();
		}
		if (c == ']' || c == '-') {
			insert(tree, fetch());
		}
		char last = 0;
		ClassState state = NONE;
		while (remaining() && (c = peek()) != ']') {
			if (c >= 128) {
				throw new IllegalStateException("Only ASCII supported.");
			}
			// process '-'
			if (c == '-') {
				switch (state) {
					case NONE:
						fetch();
						if (peek() != ']') {
							throw new IllegalStateException("Dangling '-' in character class");
						}
						last = '-';
						state = SINGLE;
						break;
					case SINGLE:
						fetch();
						if (peek() != ']') {
							state = RANGE;
						} else {
							insert(tree, last);
							last = '-';
						}
						break;
					case RANGE:
						if (last > '-') {
							throw new IllegalStateException("Invalid range, from " + last + " to " + '-');
						}
						fetch();
						insert(tree, last, '-');
						state = NONE;
						break;
					default:
						throw new IllegalStateException("Should not reach here.");
				}
			} else {
				if (c == '\\') {
					fetch();
					if (!remaining()) {
						throw new IllegalStateException("Incomplete escape sequence");
					}
					c = escapeInClazz();
				} else {
					fetch();
				}
				switch (state) {
					case NONE:
						last = c;
						state = SINGLE;
						break;
					case SINGLE:
						insert(tree, last);
						last = c;
						break;
					case RANGE:
						if (c < last) {
							throw new IllegalStateException("Invalid range, from " + last + " to " + c);
						}
						insert(tree, last, c);
						state = NONE;
				}
			}
		}
		if (state == SINGLE) {
			insert(tree, last);
		}
		IntervalNode root = (IntervalNode) tree.getRoot();
		List<Range> ranges = new ArrayList<>();
		preorder(ranges, root);
		ranges = merge(ranges);
		if (reversed) {
			return reverse(ranges);
		}
		Label[] labels = ranges.toArray(new Label[]{});
		normalize(labels);
		return labels;
	}

	// nested character class is not allowed
	private char escapeInClazz() {
		if (remaining()) {
			char c = peek();
			switch (c) {
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7':
					return octal();
				case 'x':
					fetch();
					return hex();
				case 'r':
					fetch();
					return '\r';
				case 'n':
					fetch();
					return '\n';
				case 't':
					fetch();
					return '\t';
				case 'a':
					fetch();
					return '\7';
				case 'b':
					fetch();
					return '\10';
				case 'e':
					fetch();
					return '\33';
				case 'f':
					fetch();
					return '\f';
				default:
					if (!isAlnum(c)) {
						return fetch();
					}
			}
		}
		throw new IllegalStateException("Unsupported escape sequence in character class");
	}

	private Label[] reverse(List<Range> ranges) {
		List<Range> reverses = new ArrayList<>();
		Range prev = ranges.get(0);
		if (prev.from > 0) {
			reverses.add(new Range((char) 0, prev.from));
		}
		for (int i = 1; i < ranges.size(); i++) {
			Range cur = ranges.get(i);
			reverses.add(new Range(prev.to, cur.from));
			prev = cur;
		}
		if (prev.to < CHAR_ASCII_END) {
			reverses.add(new Range(prev.to, CHAR_ASCII_END));
		}
		Label[] labels = reverses.toArray(new Label[]{});
		normalize(labels);
		return labels;
	}

	private void normalize(Label[] labels) {
		for (int i = 0; i < labels.length; i++) {
			Range l = (Range) labels[i];
			if (l.from + 1 == l.to) {
				labels[i] = Single.single(l.from);
			}
		}
	}

	private List<Range> merge(List<Range> ranges) {
		if (ranges.size() == 1) {
			return ranges;
		}
		List<Range> normalized = new ArrayList<>();
		Range last = ranges.get(0);
		for (int i = 1; i < ranges.size(); i++) {
			Range cur = ranges.get(i);
			if (cur.from == last.to) {
				last = last.concat(cur);
			} else {
				normalized.add(last);
				last = cur;
			}
		}
		normalized.add(last);
		return normalized;
	}

	private void preorder(List<Range> ranges, IntervalNode node) {
		if (node.getLeft() != null) {
			preorder(ranges, (IntervalNode) node.getLeft());
		}
		Interval interval = node.getInterval();
		ranges.add(new Range((Character) interval.getLowEndpoint(), (Character) interval.getHighEndpoint()));
		if (node.getRight() != null) {
			preorder(ranges, (IntervalNode) node.getRight());
		}
	}

	@SuppressWarnings("unchecked")
	private void insert(IntervalTree tree, char last, char end) {
		Interval interval = new Interval(last, (char) (end + 1));
		List<IntervalNode> intersected = tree.findAllNodesIntersecting(interval);
		Character min = (Character) interval.getLowEndpoint();
		Character max = (Character) interval.getHighEndpoint();
		if (!intersected.isEmpty()) {
			for (IntervalNode node : intersected) {
				Character nMin = (Character) node.getInterval().getLowEndpoint();
				Character nMax = (Character) node.getInterval().getHighEndpoint();
				min = min.compareTo(nMin) > 0 ? nMin : min;
				max = max.compareTo(nMax) < 0 ? nMax : max;
				tree.deleteNode(node);
			}
		}
		Interval actual = new Interval(min, max);
		tree.insert(actual, null);
	}

	private void insert(IntervalTree tree, char c) {
		Interval interval = new Interval(c, (char) (c + 1));
		if (tree.findAllNodesIntersecting(interval).isEmpty()) {
			tree.insert(interval, null);
		}
	}

	private void consume(char c, String msg) {
		if (fetch() != c) {
			throw new IllegalStateException(msg);
		}
	}

	public static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	public static boolean isLower(char c) {
		return c >= 'a' && c <= 'z';
	}

	public static boolean isUpper(char c) {
		return c >= 'A' && c <= 'Z';
	}

	public static boolean isAlnum(char c) {
		return isDigit(c) || isLower(c) || isUpper(c);
	}

	public static boolean isWord(char c) {
		return isAlnum(c) || c == '_';
	}

	public NFAGraph getGraph() {
		return graph;
	}

	public static void main(String[] args) {
		String test = "abc|[a-k]*c";
		NFA nfa = new NFA(test);
		NFAGraph graph = nfa.graph;
		System.out.println(graph);
	}

	/*
	TODO support '^'/'$' in DFA
	 */
	public static class Begin implements Label {
		private Begin() {}

		@Override
		public boolean match(char c) {
			return c == CHAR_START;
		}

		@Override
		public Interval interval() {
			throw new UnsupportedOperationException("DFA not support non-placeholder yet");
		}

		@Override
		public String toString() {
			return "begin";
		}

		@Override
		public char first() {
			throw new UnsupportedOperationException();
		}

	}

	public static class End implements Label {
		private End() {}

		@Override
		public boolean match(char c) {
			return c == CHAR_END;
		}

		@Override
		public Interval interval() {
			throw new UnsupportedOperationException("DFA not support non-placeholder yet");
		}

		@Override
		public String toString() {
			return "end";
		}

		@Override
		public char first() {
			throw new UnsupportedOperationException();
		}

	}

}
