package com.github.lcybo.regex;

import com.github.lcybo.regex.jdk.utils.Interval;
import com.github.lcybo.regex.jdk.utils.IntervalNode;
import com.github.lcybo.regex.jdk.utils.IntervalTree;
import com.github.lcybo.regex.jdk.utils.RBNode;
import org.jctools.maps.NonBlockingHashSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.lcybo.regex.Label.XI;
import static com.github.lcybo.regex.NFA.CHAR_END;
import static com.github.lcybo.regex.NFA.CHAR_START;

public class NFAGraph {

	// Ordered node offer a nice graphviz chart
	private Deque<NFANode> nodes = new LinkedList<>();

	private NFANode start;
	private NFANode fin;

	NFAGraph() {}

	NFAGraph(Label label) {
		this.append(label, new NFANode());
	}

	public NFAGraph concat(NFAGraph other) {
		if (start == null) {
			start = other.start;
			fin = other.fin;
			nodes = other.nodes;
		} else if (other.start != null) {
			other.start.outgoing.forEach(o -> fin.route(other.start.label, o));
			fin = other.fin;
			other.nodes.remove(other.start);
			nodes.addAll(other.nodes);
		}
		return this;
	}

	public NFAGraph union(NFAGraph other) {
		NFANode nin = new NFANode();
		NFANode nout = new NFANode();
		nodes.addFirst(nin);
		nodes.addLast(nout);
		nin.route(XI, start);
		nin.route(XI, other.start);
		fin.route(XI, nout);
		other.fin.route(XI, nout);
		start = nin;
		fin = nout;
		nodes.addAll(other.nodes);
		return this;
	}

	public NFAGraph exists() {
		NFANode nin = new NFANode();
		NFANode nout = new NFANode();
		nodes.addFirst(nin);
		nodes.addLast(nout);
		nin.route(XI, start);
		nin.route(XI, nout);
		fin.route(XI, nout);
		start = nin;
		fin = nout;
		return this;
	}

	public NFAGraph closure() {
		NFANode nin = new NFANode();
		NFANode nout = new NFANode();
		nodes.addFirst(nin);
		nodes.addLast(nout);
		nin.route(XI, start);
		nin.route(XI, nout);
		fin.route(XI, nout);
		fin.route(XI, this.start);
		start = nin;
		fin = nout;
		return this;
	}

	public NFAGraph append(Label label, NFANode node) {
		nodes.addLast(node);
		if (start == null) {
			start = new NFANode();
			nodes.addFirst(start);
			start.route(label, node);
			fin = node;
		} else {
			fin.route(label, node);
			fin = node;
		}
		return this;
	}

	public NFAGraph copy() {
		final Map<NFANode, NFANode> map =
				nodes.stream().collect(Collectors.toMap(n -> n, NFANode::copy, (n1, n2) -> n1, IdentityHashMap::new));
		map.values().forEach(n -> n.outgoing = n.outgoing.stream().map(map::get).collect(Collectors.toList()));
		NFAGraph graph = new NFAGraph();
		graph.start = map.get(start);
		graph.fin = map.get(fin);
		graph.nodes = new LinkedList<>(map.values());
		return graph;
	}

	private static final String LF        = System.lineSeparator();
	private static final String INTENT    = "  ";

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("digraph NFA {").append(LF);
		nodes.forEach(node -> {
			sb.append(INTENT)
					.append(node.toString())
					.append(' ')
					.append("[shape=circle];")
					.append(LF);
			node.outgoing.forEach(out -> sb.append(INTENT)
					.append(node)
					.append(" -> ")
					.append(out)
					.append("[label=\"")
					.append(node.label)
					.append("\"];")
					.append(LF));
		});
		sb.append("}");
		return sb.toString();
	}

	public Simulation simulate() {
		return new Simulation();
	}

	public Conversion convert() {
		return new Conversion();
	}

	public class Conversion {

		private final NFANode start;
		private final NFANode terminal;

		// just need get(o) to retrieve effect element in set, no concurrent purpose
		private final NonBlockingHashSet<DFANode> nodes = new NonBlockingHashSet<>();
		private final NonBlockingHashSet<DFAEdge> edges = new NonBlockingHashSet<>();


		Conversion() {
			this.start = NFAGraph.this.start;
			this.terminal = NFAGraph.this.fin;
		}

		public DFAGraph execute() {
			Set<NFANode> starts = start.xi().stream().collect(Collectors.toSet());
			DFANode s = new DFANode();
			s.start = true;
			s.nfas.addAll(starts);
			nodes.add(s);
			Set<Label> outputs = outputs(starts);
			resolve(s, outputs);
			return new DFAGraph(nodes, edges, s);
		}

		private void resolve(DFANode node, Set<Label> labels) {
			node.terminal = node.nfas.stream().anyMatch(nfa -> nfa.label == null);
			nodes.add(node);
			final Set<NFANode> nfas = node.nfas;
			labels.forEach(label -> {
				DFANode out = new DFANode();
				Set<NFANode> dsts = new HashSet<>();
				nfas.forEach(nfa -> {
					if (nfa.label != null && nfa.label.match(label.first())) {
						dsts.addAll(nfa.move(label.first()).stream().flatMap(n -> n.xi().stream()).collect(Collectors.toSet()));
					}
				});
				out.nfas.addAll(dsts);
				out = nodes.contains(out) ? nodes.get(out) : out;
				DFAEdge edge = new DFAEdge(node, out, label);
				node.addEdge(edge);
				edges.add(edge);
				if (!nodes.contains(out)) {
					resolve(out, outputs(out.nfas));
				}
			});
		}

		private Set<Label> outputs(Set<NFANode> nodes) {
			Set<Label> laps = nodes.stream().map(n -> n.label).filter(l -> l != XI && l != null).collect(Collectors.toSet());
			final IntervalTree tree = new IntervalTree(Comparator.naturalOrder());
			if (laps.isEmpty()) {
				return Set.of();
			}
			laps.forEach(l -> {
				Interval i = l.interval();
				char low = (char) i.getLowEndpoint();
				char high = (char) i.getHighEndpoint();
				List overlaps = tree.findAllNodesIntersecting(i);
				Set<Interval> slices = new LinkedHashSet<>();
				if (overlaps.isEmpty()) {
					tree.insert(i, null);
				} else {
					final int size = overlaps.size();
					for (int j = 0; j < size; j++) {
						IntervalNode o = (IntervalNode) overlaps.get(j);
						tree.deleteNode(o);
						Interval ol = o.getInterval();
						char olow = (char) ol.getLowEndpoint();
						char ohigh = (char) ol.getHighEndpoint();
						char c1 = olow < low ? olow : low;
						char c2 = low == c1 ? olow : low;
						char c3 = ohigh < high ? ohigh : high;
						char c4 = high == c3 ? ohigh : high;
						if (slices.isEmpty() && c1 < c2) {
							slices.add(new Interval(c1, c2));
						}
						slices.add(new Interval(c2, c3));
						if (j + 1 == size && c3 < c4) {
							slices.add(new Interval(c3, c4));
						}
					}
					slices.forEach(in -> tree.insert(in, null));
				}
			});
			List<Interval> intervals = new ArrayList<>();
			preorder(tree.getRoot(), intervals);
			return intervals.stream().map(i ->  {
				char low = (char) i.getLowEndpoint();
				char high = (char) i.getHighEndpoint();
				if (low == high - 1) {
					return Single.single(low);
				}
				return new Range(low, high);
			}).collect(Collectors.toSet());
		}

		private void preorder(RBNode node, List<Interval> ordered) {
			if (node.getLeft() != null) {
				preorder(node.getLeft(), ordered);
			}
			ordered.add(((IntervalNode)node).getInterval());
			if (node.getRight() != null) {
				preorder(node.getRight(), ordered);
			}
		}

	}

	public class Simulation {

		private Set<NFANode> current;

		private Simulation() {
			current = Set.of(start);
		}

		public boolean test(final CharSequence cs) {
			int i = 0;
			int len = cs.length();
			Set<NFANode> starts = current.stream()
					.flatMap(n -> n.move(CHAR_START).stream()).collect(Collectors.toSet());
			current = Stream.concat(starts.stream(), current.stream()).collect(Collectors.toSet());
			while (i < len && step(cs.charAt(i))) {
				i ++;
			}
			return i == len && accept();
		}

		private boolean step(final char c) {
			Set<NFANode> last = current;
			current = last.stream()
					.flatMap(n -> n.move(c).stream()).collect(Collectors.toSet());
			return !current.isEmpty();
		}

		public Set<NFANode> current() {
			return current;
		}

		public boolean accept() {
			boolean accepted = current.stream()
					.flatMap(n -> n.xi().stream()).anyMatch(n -> n.label == null);
			if (accepted) {
				return true;
			}
			return current.stream()
					.flatMap(n -> n.move(CHAR_END).stream())
					.flatMap(n -> n.xi().stream())
					.anyMatch(n -> n.label == null);
		}

	}

}
