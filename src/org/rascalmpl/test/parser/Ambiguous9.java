/*******************************************************************************
 * Copyright (c) 2009-2011 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
*******************************************************************************/
package org.rascalmpl.test.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.io.StandardTextReader;
import org.rascalmpl.parser.gtd.SGTDBF;
import org.rascalmpl.parser.gtd.stack.AbstractStackNode;
import org.rascalmpl.parser.gtd.stack.LiteralStackNode;
import org.rascalmpl.parser.gtd.stack.NonTerminalStackNode;
import org.rascalmpl.parser.uptr.NodeToUPTR;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.uptr.Factory;

/*
* S ::= E
* E ::= E + E | E * E | 1
* 
* NOTE: This test, tests prefix sharing.
*/
public class Ambiguous9 extends SGTDBF implements IParserTest{
	private final static IConstructor SYMBOL_START_S = VF.constructor(Factory.Symbol_Sort, VF.string("S"));
	private final static IConstructor SYMBOL_E = VF.constructor(Factory.Symbol_Sort, VF.string("E"));
	private final static IConstructor SYMBOL_plus = VF.constructor(Factory.Symbol_Lit, VF.string("+"));
	private final static IConstructor SYMBOL_star = VF.constructor(Factory.Symbol_Lit, VF.string("*"));
	private final static IConstructor SYMBOL_1 = VF.constructor(Factory.Symbol_Lit, VF.string("1"));
	private final static IConstructor SYMBOL_char_plus = VF.constructor(Factory.Symbol_CharClass, VF.list(VF.constructor(Factory.CharRange_Single, VF.integer(43))));
	private final static IConstructor SYMBOL_char_star = VF.constructor(Factory.Symbol_CharClass, VF.list(VF.constructor(Factory.CharRange_Single, VF.integer(42))));
	private final static IConstructor SYMBOL_char_1 = VF.constructor(Factory.Symbol_CharClass, VF.list(VF.constructor(Factory.CharRange_Single, VF.integer(49))));
	
	private final static IConstructor PROD_S_E = VF.constructor(Factory.Production_Default,  SYMBOL_START_S, VF.list(SYMBOL_E), VF.set());
	private final static IConstructor PROD_E_EplusE = VF.constructor(Factory.Production_Default,  SYMBOL_E, VF.list(SYMBOL_E, SYMBOL_plus, SYMBOL_E), VF.set());
	private final static IConstructor PROD_E_EstarE = VF.constructor(Factory.Production_Default,  SYMBOL_E, VF.list(SYMBOL_E, SYMBOL_star, SYMBOL_E), VF.set());
	private final static IConstructor PROD_E_1 = VF.constructor(Factory.Production_Default,  SYMBOL_E, VF.list(SYMBOL_1), VF.set());
	private final static IConstructor PROD_plus_plus = VF.constructor(Factory.Production_Default,  SYMBOL_plus, VF.list(SYMBOL_char_plus), VF.set());
	private final static IConstructor PROD_star_star = VF.constructor(Factory.Production_Default,  SYMBOL_star, VF.list(SYMBOL_char_star), VF.set());
	private final static IConstructor PROD_1_1 = VF.constructor(Factory.Production_Default,  SYMBOL_1, VF.list(SYMBOL_char_1), VF.set());
	
	private final static AbstractStackNode NONTERMINAL_START_S = new NonTerminalStackNode(AbstractStackNode.START_SYMBOL_ID, 0, "S");
	private final static AbstractStackNode[] S_EXPECT_1 = new AbstractStackNode[1];
	static{
		S_EXPECT_1[0] = new NonTerminalStackNode(0, 0, "E");
		S_EXPECT_1[0].setProduction(S_EXPECT_1);
		S_EXPECT_1[0].setParentProduction(PROD_S_E);
	}
	
	private final static AbstractStackNode[] E_EXPECT_1 = new AbstractStackNode[3];
	static{
		E_EXPECT_1[0] = new NonTerminalStackNode(1, 0, "E");
		E_EXPECT_1[0].setProduction(E_EXPECT_1);
		E_EXPECT_1[1] = new LiteralStackNode(4, 1, PROD_plus_plus, "+".toCharArray());
		E_EXPECT_1[1].setProduction(E_EXPECT_1);
		E_EXPECT_1[2] = new NonTerminalStackNode(2, 2, "E");
		E_EXPECT_1[2].setProduction(E_EXPECT_1);
		E_EXPECT_1[2].setParentProduction(PROD_E_EplusE);
	}
	private final static AbstractStackNode[] E_EXPECT_2 = new AbstractStackNode[3];
	static{
		E_EXPECT_2[0] = new NonTerminalStackNode(1, 0, "E");
		E_EXPECT_2[0].setProduction(E_EXPECT_2);
		E_EXPECT_2[1] = new LiteralStackNode(5, 1, PROD_star_star, "*".toCharArray());
		E_EXPECT_2[1].setProduction(E_EXPECT_2);
		E_EXPECT_2[2] = new NonTerminalStackNode(3, 2, "E");
		E_EXPECT_2[2].setProduction(E_EXPECT_2);
		E_EXPECT_2[2].setParentProduction(PROD_E_EstarE);
	}
	private final static AbstractStackNode[] E_EXPECT_3 = new AbstractStackNode[1];
	static{
		E_EXPECT_3[0] = new LiteralStackNode(6, 0, PROD_1_1, "1".toCharArray());
		E_EXPECT_3[0].setProduction(E_EXPECT_3);
		E_EXPECT_3[0].setParentProduction(PROD_E_1);
	}
	
	public Ambiguous9(){
		super();
	}
	
	public void S(){
		expect(S_EXPECT_1[0]);
	}
	
	public void E(){
		expect(E_EXPECT_1[0]);
		expect(E_EXPECT_2[0]);
		
		expect(E_EXPECT_3[0]);
	}
	
	public IConstructor executeParser(){
		return (IConstructor) parse(NONTERMINAL_START_S, null, "1+1+1".toCharArray(), new NodeToUPTR());
	}
	
	public IValue getExpectedResult() throws IOException{
		String expectedInput = "appl(prod(sort(\"S\"),[sort(\"E\")],{}),[amb({appl(prod(sort(\"E\"),[sort(\"E\"),lit(\"+\"),sort(\"E\")],{}),[appl(prod(sort(\"E\"),[lit(\"1\")],{}),[appl(prod(lit(\"1\"),[\\char-class([single(49)])],{}),[char(49)])]),appl(prod(lit(\"+\"),[\\char-class([single(43)])],{}),[char(43)]),appl(prod(sort(\"E\"),[sort(\"E\"),lit(\"+\"),sort(\"E\")],{}),[appl(prod(sort(\"E\"),[lit(\"1\")],{}),[appl(prod(lit(\"1\"),[\\char-class([single(49)])],{}),[char(49)])]),appl(prod(lit(\"+\"),[\\char-class([single(43)])],{}),[char(43)]),appl(prod(sort(\"E\"),[lit(\"1\")],{}),[appl(prod(lit(\"1\"),[\\char-class([single(49)])],{}),[char(49)])])])]),appl(prod(sort(\"E\"),[sort(\"E\"),lit(\"+\"),sort(\"E\")],{}),[appl(prod(sort(\"E\"),[sort(\"E\"),lit(\"+\"),sort(\"E\")],{}),[appl(prod(sort(\"E\"),[lit(\"1\")],{}),[appl(prod(lit(\"1\"),[\\char-class([single(49)])],{}),[char(49)])]),appl(prod(lit(\"+\"),[\\char-class([single(43)])],{}),[char(43)]),appl(prod(sort(\"E\"),[lit(\"1\")],{}),[appl(prod(lit(\"1\"),[\\char-class([single(49)])],{}),[char(49)])])]),appl(prod(lit(\"+\"),[\\char-class([single(43)])],{}),[char(43)]),appl(prod(sort(\"E\"),[lit(\"1\")],{}),[appl(prod(lit(\"1\"),[\\char-class([single(49)])],{}),[char(49)])])])})])";
		return new StandardTextReader().read(ValueFactoryFactory.getValueFactory(), Factory.uptr, Factory.Tree, new ByteArrayInputStream(expectedInput.getBytes()));
	}
	
	public static void main(String[] args){
		Ambiguous9 a9 = new Ambiguous9();
		IConstructor result = a9.executeParser();
		System.out.println(result);
		
		System.out.println("S([E(E(1),+,E(E(1),+,E(1))),E(E(E(1),+,E(1)),+,E(1))]) <- good");
	}
}
