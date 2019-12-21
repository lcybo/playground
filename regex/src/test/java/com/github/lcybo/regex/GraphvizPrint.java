package com.github.lcybo.regex;


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class GraphvizPrint {

	@Test
	public void printSequence() {
		NFA nfa = new NFA("a|bc");
		System.out.println(nfa.getGraph());
	}

	@Test
	public void printClass() {
		NFA nfa = new NFA("[]a-k0-6-]");
		System.out.println(nfa.getGraph());
	}

	@Test
	public void printReverseClass() {
		NFA nfa = new NFA("[^]a-k0-6-]");
		System.out.println(nfa.getGraph());
	}

	@Test
	public void printEscape() {
		NFA nfa = new NFA("\\072\\77\\x35\\r\\n\\t\\a\\b\\e\\f");
		System.out.println(nfa.getGraph());
	}

	@Test
	public void printBeginEnd() {
		NFA nfa = new NFA("^^^^^$$$$$");
		System.out.println(nfa.getGraph());
	}

	@Test
	public void printClosure() {
		NFA nfa = new NFA("(ab)*z");
		System.out.println(nfa.getGraph());
	}

	@Test
	public void printOneOrMore() {
		NFA nfa = new NFA("zz[d-gx]+");
		System.out.println(nfa.getGraph());
	}

	@Test
	public void printRepeat() {
		NFA nfa = new NFA("zz[d-gx]{2,5}");
		System.out.println(nfa.getGraph());
	}

	@Test
	public void printGroup() {
		NFA nfa = new NFA("b([adk]1b)*5");
		System.out.println(nfa.getGraph());
	}

	@Test
	public void test() throws IOException {
//		NFA nfa = new NFA("[a-dk]*b*bz");
		NFA nfa = new NFA("[a-z0-9]*");
		String str = nfa.getGraph().toString();
		System.out.println(str);
		NFAGraph graph = nfa.getGraph();
		NFAGraph.Simulation sim = graph.simulate();
		System.out.println(sim.test("abz"));
		NFAGraph.Conversion con = graph.convert();
		DFAGraph dg = con.execute();
		System.out.println(dg);
	}

}
