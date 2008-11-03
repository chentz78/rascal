package org.meta_environment.rascal.ast;

import org.eclipse.imp.pdb.facts.ITree;

public abstract class CharacterLiteral extends AbstractAST {
	static public class Ambiguity extends CharacterLiteral {
		private final java.util.List<org.meta_environment.rascal.ast.CharacterLiteral> alternatives;

		public Ambiguity(
				java.util.List<org.meta_environment.rascal.ast.CharacterLiteral> alternatives) {
			this.alternatives = java.util.Collections
					.unmodifiableList(alternatives);
		}

		public java.util.List<org.meta_environment.rascal.ast.CharacterLiteral> getAlternatives() {
			return alternatives;
		}
	}

	static public class Lexical extends CharacterLiteral {
		/* "'" SingleCharacter "'" -> CharacterLiteral */
		private String string;

		/* package */Lexical(ITree tree, String string) {
			this.tree = tree;
			this.string = string;
		}

		public String getString() {
			return string;
		}
	}
}
