package com.github.lcybo.regex;

import com.github.lcybo.regex.jdk.utils.Interval;
import com.github.lcybo.regex.jdk.utils.IntervalTree;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class DFANode {

	boolean start = false;

	boolean terminal = false;

	Set<NFANode> nfas = new HashSet<>();

	IntervalTree edges = new IntervalTree(Comparator.naturalOrder());

	public void addEdge(DFAEdge edge) {
		Interval interval = edge.label.interval();
		if (!edges.findAllNodesIntersecting(interval).isEmpty()) {
			throw new IllegalStateException();
		}
		edges.insert(interval, edge);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		DFANode dfaNode = (DFANode) o;
		return Objects.equals(nfas, dfaNode.nfas);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nfas);
	}

	@Override
	public String toString() {
		return String.valueOf(hashCode());
	}

}
