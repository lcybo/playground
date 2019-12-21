package com.github.lcybo.regex;

import java.util.Set;

public class DFAGraph {

	final Set<DFANode> nodes;

	final Set<DFAEdge> edges;

	final DFANode start;

	public DFAGraph(Set<DFANode> nodes, Set<DFAEdge> edges, DFANode start) {
		this.nodes = nodes;
		this.edges = edges;
		this.start = start;
	}

	private static final String LF        = System.lineSeparator();
	private static final String INTENT    = "  ";

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("digraph DFA {").append(LF);
		nodes.forEach(node -> {
			sb.append(INTENT)
					.append(node.toString())
					.append(' ');
			if (node.start)
				sb.append("[shape=ellipse];");
			else if (node.terminal)
				sb.append("[shape=doublecircle]");
			else
				sb.append("[shape=circle]");
			sb.append(LF);
		});
		edges.forEach(edge -> {
			sb.append(INTENT)
					.append(edge.from)
					.append(" -> ")
					.append(edge.to)
					.append("[label=\"")
					.append(edge.label)
					.append("\"];")
					.append(LF);
		});
		sb.append("}");
		return sb.toString();
	}

}
