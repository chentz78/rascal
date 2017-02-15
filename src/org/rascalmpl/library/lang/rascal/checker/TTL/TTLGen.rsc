module lang::rascal::checker::TTL::TTLGen

//extend lang::rascal::\syntax::Rascal;
extend lang::rascal::checker::TTL::TTLsyntax;
import IO;
import String;
import List;
import lang::rascal::checker::TTL::Library;
import lang::rascal::checker::TTL::PatternGenerator;
import lang::rascal::checker::TTL::ExpressionGenerator;
import ParseTree;

private int tcnt = 0;

private int genSym() { tcnt += 1; return tcnt; }

list[TestItem] Infix = [];
list[TestItem] Prefix = [];
list[TestItem] Postfix = [];
 
// Main: compile all TTL specifications to Rascal tests

void main() { 
  tcnt = 0;
  Infix = Prefix = Postfix = [];
  for(loc ttl <- (TTLRoot + "specs").ls, ttl.extension == TTL)
      generate(ttl); 
  generateSignatures(Infix, Prefix, Postfix);
}

str basename(loc l) = l.file[ .. findFirst(l.file, ".")];

str basename(TestItem item) = basename(item@\loc);

str addModulePrefix(Module m){
  if(/\s*module\s+<mname:[a-zA-Z0-9]+><rest:.*$>/ := "<m>"){
    return "module <modulePrefix>::<mname>\n<rest>";
  }
  return "Malformed Module";
}
// Generate tests for one TTL file
void generate(loc src){
   println("generate: <src>");
   spec = parse(#TTL, src);
   map[Name, Declaration] decls = ();
   map[Name, Module] modules = ();
   str tests = "";
   for(TestItem item <- spec.items){
       if(defMod(Name nm, Module moduleText) := item){// Was: item is defMod){
       	     if(decls[nm]?) throw "Ambiguous name <nm> at <item@\loc>";
       	     if(modules[nm]?) throw "Redeclared module name <nm> at <item@\loc>";
             modules[nm] = item.moduleText;
             writeFile(TTLRoot + "generated/<item.name>.rsc", addModulePrefix(item.moduleText)); // TODO: Imports from different ttl files could conflict
       } else if(defDecl(Name nm2, Declaration declaration) := item){
       		if(modules[nm2]?) throw "Ambiguous name <nm2> at <item@\loc>";
       	     if(decls[nm2]?) throw "Redeclared declaration name <nm2> at <item@\loc>";
             decls[nm2] = declaration;
       } else if(item is GeneralTest){
          tests += genGeneralTest(item, decls, modules);
       } else if(item is InfixTest){
          tests += genInfixTest(item);
          Infix += item;
       } else if(item is PrefixTest){
         tests += genUnaryTest(item, true);
         Prefix += item;
       } else if(item is PostfixTest){
         tests += genUnaryTest(item, false);
         Postfix += item;
       } else if(item is PatternTest){
         tests += genPatternTest(item);
       } else {
         println("Skipped: <item>");
       }
   }
   generatedTests = "TypeCheckTests";
   typechecker = "Typechecker";
   code = "module lang::rascal::checker::TTL::generated::<basename(src)>
          'import lang::rascal::checker::TTL::Library;
          'import Type;
          'import IO;
          'import List;
          'import Set;
          'import Message;
          'import util::Eval;
          'import lang::rascal::types::AbstractName;
          'import lang::rascal::types::TestChecker;
          'import lang::rascal::checker::TTL::PatternGenerator;
          'public bool verbose = true;
          '<tests>
          '";
   writeFile(TTLRoot + "generated/<basename(src)>.rsc", code);
}

// Generate code for a single TTL test
str genGeneralTest(TestItem item,  map[Name, Declaration] declarations,  map[Name, Module] modules){
  tname = "<basename(item)><genSym()>";
  nargs = toInt("<item.nargs>");
  args = intercalate(", ", [ "&T<i> arg<i>" | i <- [0 .. nargs]]);
  vtypes = "[" + intercalate(", ", [ "type(typeOf(arg<i>), ())" | i <- [0 .. nargs]]) + "]";
  
  <imports, decls> = expandUsedNames(getUsedNames(item.use), declarations, modules);
  <inferred_type, messages, expected_exception> = getExpectations([e | e <- item.expectations]);
 
  escapedChars =  ("\"" : "\\\"", "\\" : "\\\\");
  decls = [escape(d, escapedChars) | d <- decls];
  code = escape("<item.statements>", escapedChars);

  if(!isEmpty(expected_exception))
  	expected_exception = "@expect{<expected_exception>}";
  	
  inferredChecks = "";
  for(<var, tp> <- inferred_type){
    str v = "<var>";
    if(v[0] == "_") v = v[1..];
    if(nargs > 0 && "<tp>"[1] == "T"){  // _T<i>
       i = toInt("<tp>"[2]);
       inferredChecks += "if(!subtype(getTypeForName(checkResult.conf, \"<v>\"), typeOf(arg<i>))) return false;";    
    } else {
      btp = buildType("<tp>");
      inferredChecks += "if(!subtype(getTypeForName(checkResult.conf, \"<v>\"), normalize(<btp>, vsenv))) return false;";
    }
  }
  
  messageCheckConditions = intercalate(" || ", ["<msg> := m.msg" | msg <- messages]);
  messageChecks = "validatedMessages = {};\n";
  if(!isEmpty(messageCheckConditions)){
     messageChecks += "for(m \<-  getFailureMessages(checkResult)){
		 			  '      if(<messageCheckConditions>)
		 			  '	        validatedMessages += m;
					  '}
				 	  'for(m \<-  getWarningMessages(checkResult)){
		 			  '      if(<messageCheckConditions>)
		 			  '	        validatedMessages += m;
					  '}
					  '";
  }
  messageChecks +=  "unvalidatedMessages = getAllMessages(checkResult) - validatedMessages;
                    'if(size(unvalidatedMessages) \> 0) { println(\"[<tname>] *** Unexpected messages: \<unvalidatedMessages\>\"); return false;}";
 
  checks = (isEmpty(inferredChecks) ? "" : inferredChecks) +
  		   (isEmpty(messageChecks) ? "" : "\n" + messageChecks);
 
  
  return "
  		 '/* <item> */ <expected_exception>
  		 'test bool <tname>(<args>){
  		 '  vtypes = <vtypes>; 
  		 '  venv = ( );
  		 '  <for(i <- [0 .. nargs]){>venv[\"<i>\"] = \< vtypes[<i>], arg<i> \>; <}>
  		 '  vsenv = (\"T\<id\>\" : vtypes[id].symbol | id \<- index(vtypes) );
  		 '  stats = buildStatements(\"<code>\", venv);
  		 '  if(verbose) println(\"[<tname>] stats: \" + stats);
         '  checkResult = checkStatementsString(stats, importedModules=<imports>, initialDecls = <decls>);
         '  <checks>
         '  return true;
         '}
         '";
}

list[Name] getUsedNames(Use u){
    return (u is use) ? [name | Name name <- u.names] : [];
}

tuple[list[str],list[str]] expandUsedNames(list[Name] names, map[Name, Declaration] declarations,  map[Name, Module] modules){
    imports = [];
    decls = [];
    for(Name name <- names){
      if(modules[name]?)
         imports += "<modulePrefix>::<name>";
      else if(declarations[name]?)
         decls += "<declarations[name]>";
      else
          throw "Undefined name <name> at <name@\loc>";
    }
    return <imports, decls>;
}

tuple[lrel[Name,Type],list[RegExpLiteral],str] getExpectations(list[Expect] expect){
  lrel[Name, Type] inferred_type = [];
  list[RegExpLiteral] message = [];
  expected_exception = "";
  for(Expect e <- expect){
      if(e is inferred){
         throw "JV: this next line did not compile: ";
         //inferred_type += <e.name, toSymbol(e.expectedType)>;
      } else if (e is message){
      	 message += e.regexp;
      } else {
        expected_exception = "<e.name>";
      }
   }
   return <inferred_type, message, expected_exception>;
}

str genCondition(Condition cond){
    typeCondition = "\n";
    if(nonempty(Name name, typeName) := cond){
       str tname = "<typeName>";
       tname = toUpperCase(tname[0]) + tname[1..];
	   typeCondition = "if(is<tname>Type(bindings[\"<name>\"])) return true;\n";
    }
    return typeCondition;
}

str buildType(str txt){   // TODO remove this limitation to 5 variables

   for(int id <- [0 .. 5]){
      txt = replaceAll(txt, "_T<id>", "#&T<id>.symbol");
   }
   txt = replaceAll(txt, "LUB", "lub");
   return startsWith(txt, "lub") || (txt[0] == "#") ? txt : "#<txt>.symbol";          
}

// Generete code for TTL test for Infix operator
str genInfixTest(TestItem item){
  tests = "";
  for(operator <- item.operators){
	  operatorName = "<operator>"[1..-1]; 
	  for(sig <- item.bin_signatures){
	     typeCondition = "";
	     if(sig.condition is condition){
	        tname = "<sig.condition.typeName>";
	        tname = toUpperCase(tname[0]) + tname[1..];
	        typeCondition = "if(is<tname>Type(bindings[\"<sig.condition.name>\"])) return true;";
	     }
	    
	     tname = "<basename(item)><genSym()>";
	     tests += "// Testing Infix <item.name> <operatorName> for <sig>
	     		  'test bool <tname>(<sig.left> arg1, <sig.right> arg2){ 
	              '  ltype = typeOf(arg1);
	              '  rtype = typeOf(arg2);
	              '  if(isDateTimeType(ltype) || isDateTimeType(rtype))
	              '		return true;
	     		  '  <genArgument(sig.left, "l")>
	       		  '  <genArgument(sig.right, "r")>
				  '  if(lmatches && rmatches){
	              '     bindings = merge(lbindings, rbindings); 
	              '     <genCondition(sig.condition)>
	              '     expression = \"(\<escape(arg1)\>) <operatorName> (\<escape(arg2)\>);\";
	              '     if(verbose) println(\"[<tname>] exp: \<expression\>\");
	              '     checkResult = checkStatementsString(expression, importedModules=[], initialDecls = []); // apply the operator to its arguments
	              '     actualType = checkResult.res; 
	              '     expectedType = normalize(<toSymbolAsStr(sig.result)>, bindings);
	              '     return validate(\"<tname>\", expression, actualType, expectedType, arg1, arg2, <escape("signature <sig>")>);
	              '  }
	              '  return false;
	              '}\n";
	  }
  }
  return tests;
}

// Generete code for TTL test for unary (Prefix or Postfix) operator
str genUnaryTest(TestItem item, bool Prefix){
  tests = "";
  for(operator <- item.operators){
	  operatorName = "<operator>"[1..-1]; 
	  for(sig <- item.un_signatures){
	     expr = Prefix ? "\"<operatorName> (\<escape(arg1)\>);\"" : "\"(\<escape(arg1)\>) <operatorName>;\"";
	     tname = "<basename(item)><genSym()>";
	     tests += "// Testing <Prefix ? "Prefix" : "Postfix"> <item.name> <operatorName> for <sig>
	     		  'test bool <tname>(<sig.left> arg1){ 
	              '  ltype = typeOf(arg1);
	              '  if(isDateTimeType(ltype))
	              '		return true;
	     		  '  <genArgument(sig.left, "l")>
				  '  if(lmatches){
				  '     <genCondition(sig.condition)>
				  '     if(verbose) println(\"[<tname>] exp: \" + <expr>);
				  '	    checkResult = checkStatementsString(<expr>, importedModules=[], initialDecls = []); // apply the operator to its arguments
	              '     actualType = checkResult.res; 
	              '     expectedType = normalize(<toSymbolAsStr(sig.result)>, lbindings);
	              '     return validate(\"<tname>\", <expr>, actualType, expectedType, arg1, <escape("signature <sig>")>);
	              '  }
	              '  return false;
	              '}\n";
	  }
  }
  return tests;
}

str genArgument(ExtendedType a, str side) = "\<<side>matches, <side>bindings\> = bind(<toSymbolAsStr(a)>, <side>type);\n";


str genPatternTest(TestItem item){
  nargs = toInt("<item.nargs>");
  args = intercalate(", ", [ "&T<i> arg<i>" | i <- [0 .. nargs]]);
  ptypes = "[" + intercalate(", ", [ "type(typeOf(arg<i>), ())" | i <- [0 .. nargs]]) + "]";
  exp = escape("<item.expression>;");
  tname = "<basename(item)><genSym()>";
  return   "// Testing <item.expression>
   	 	   'test bool <tname>(<args>){
           '     ptypes = <ptypes>; 
           '     \<penv, expected_pvars\> = generatePatterns(ptypes); 
           '     exp = buildExpr(<exp>, penv);
           '     if(verbose) println(\"[<tname>] exp = \<exp\>\");
           '     checkResult = checkStatementsString(exp);
           '
           '     pvars = getPatternVariables(checkResult.conf);
           '
    	   '     for(v \<- expected_pvars){
           '         try {
           '             expectedType = expected_pvars[v].symbol;
           '             actualType = pvars[RSimpleName(v)];
           '             if(inferred(_) := actualType){
           '                 println(\"[<tname>] *** No type found for variable \<v\>; expected type \<expectedType\>; exp = \<exp\>\");
           '             } else {
           '             if(!validate(\"<tname>\", exp, actualType, expectedType, \"variable \<v\>\"))
           '                return false;
           '             }
           '         } catch: {
           '           println(\"[<tname>] *** Variable \<v\> unbound; expected type \<expected_pvars[v].symbol\>; exp = \<exp\>\");
           '           return false;
           '         }
           '     }
           '     return true;
           '}\n";
}
  