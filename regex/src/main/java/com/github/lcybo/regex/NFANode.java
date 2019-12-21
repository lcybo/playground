package com.github.lcybo.regex;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.lcybo.regex.Label.XI;

public class NFANode {

	// label is either ε or a word in the given language
	Label label;

	List<NFANode> outgoing = new ArrayList<>(2);

	NFANode() {}

	public List<NFANode> move(final char c) {
		if (label.match(c)) {
			return List.of(outgoing.get(0));
		}
		return xi().stream().flatMap(x -> {
			if (x.label == XI || x.label == null || !x.label.match(c)) {
				return Stream.empty();
			}
			return Stream.of(x.outgoing.get(0));
		}).collect(Collectors.toList());
	}

	public List<NFANode> xi() {
		if (label != XI) {
			return List.of(this);
		}
		List<NFANode> xi = new ArrayList<>();
		xi.add(this);
		outgoing.forEach(x -> xi.addAll(x.xi()));
		return xi;
	}

	void route(Label label, NFANode node) {
		if (this.label == null) {
			this.label = label;
		} else if (!this.label.equals(label)) {
			throw new IllegalStateException("More than 1 outgoing label");
		}
		if ((label != XI && !outgoing.isEmpty()) || (label == XI && outgoing.size() > 1)) {
			throw new IllegalStateException("A nfa node could have either one non-ε outgoing node or two ε outgoing node.");
		}
		outgoing.add(node);
	}

	public NFANode copy() {
		NFANode node = new NFANode();
		node.label = this.label;
		node.outgoing = new ArrayList<>(2);
		node.outgoing.addAll(outgoing);
		return node;
	}

	public boolean terminate() {
		return this.label == null;
	}

	public String toString() {
		return String.valueOf(hashCode());
	}

}
