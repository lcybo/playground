package com.github.lcybo.regex;

public class DFAEdge {

	DFANode from;

	DFANode to;

	Label label;

	DFAEdge(DFANode from, DFANode to, Label label) {
		this.from = from;
		this.to = to;
		this.label = label;
	}

}
