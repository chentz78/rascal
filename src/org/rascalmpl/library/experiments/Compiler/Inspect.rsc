module experiments::Compiler::Inspect

import IO;
import ValueIO;
import String;
import List;
import Map;
import Set;
import Relation;
import Node;
import Type;
import Message;
import util::FileSystem;
import util::Reflective;
import experiments::Compiler::RVM::AST;

import experiments::Compiler::Compile;

import lang::rascal::types::CheckerConfig;
import lang::rascal::types::CheckTypes;
import lang::rascal::types::AbstractName;

import experiments::vis2::sandbox::Figure;
import experiments::vis2::sandbox::FigureServer;

/*
 * A mini-query language to query .rvm and .tc files
 */

data Query = none()
           | q(str select)
           | and(str select1, str select2)
           | and(list[str] selects)
           | and(Query query1, Query query2)
           | and(list[Query] queries)
           | or(str select1, str select2)
           | or(list[str] selects)
           | or(list[Query] queries)
           | not(str select)
           | not(Query query)
           ;

Query and(Query query1, Query query2) = and([query1, query2]);
Query and(str select1, str select2) = and([q(select1), q(select2)]);
Query and(list[str] selects) = and([q(select)| select <- selects]);

Query or(Query query1, Query query2) = or([query1, query2]);
Query or(str select1, str select2) = or([q(select1), q(select2)]);
Query or(list[str] selects) = or([q(select)| select <- selects]);

Query not(str select) = not(q(select));

bool evalQuery(value subject, none()) = true;

bool evalQuery(value subject, q(str select)) = containsSelected(subject, select);

bool evalQuery(value subject, and(list[Query] queries)) = all(query <- queries, evalQuery(subject, query));

bool evalQuery(value subject, or(list[Query] queries)) = any(query <- queries, evalQuery(subject, query));

bool evalQuery(value subject, not(Query query)) = !evalQuery(subject, query);

bool containsSelected(value v, str select){
	visit(v){
		case str s:		if(startsWith(s, select)) return true;
		case node n: 	if(startsWith(getName(n), select)) return true;
	};
	return false;
}

bool containsSelected(value v, Query query) = evalQuery(v, query);

void printSelected(value v, Query query){
	if(evalQuery(v, query)){
		println("\t<v>");
	}
}
void printSelected(value v1, value v2, Query query){
	if(evalQuery(v1, query) || evalQuery(v2, query)){
		println("\t<v1>: <v2>");
	}
}

str getSelected(value v, Query query){
    if(evalQuery(v, query)){
        return "\t<v>\n";
    }
    return "";
}

str getSelected(value v1, value v2, Query query){
    if(evalQuery(v1, query) || evalQuery(v2, query)){
        return "\t<v1>: <v2>\n";
    }
    return "";
}


bool hasMatches(set[&T] s, Query query) = !isEmpty(s) && evalQuery(s, query);
bool hasMatches(map[&K,&KV] m, Query query) = !isEmpty(m) && evalQuery(m, query);


void inspect(str qualifiedModuleName,   // nameof Rascal source module
          PathConfig pcfg = pathConfig(),// path configuration where binaries are stored
          Query select = none(),        // select function names to be shown
          int line = -1,                // select line of function to be shown
          bool listing = false,         // show instruction listing
          bool linked = false           // inspect the fully linked version of the program
          ){
           <e, rvmLoc> = linked ? RVMExecutableCompressedReadLoc(qualifiedModuleName, pcfg) : RVMModuleReadLoc(qualifiedModuleName, pcfg);
    inspect(rvmLoc, select=select, line=line, listing=listing);
}
           
/*
 *  Viewer for compiled RVM programs
 *
 * TODO: zillions of options could be added
 * - show a foldable vizialization.
 */
 void inspect(
          loc rvmLoc,   // location of rvm Module
          Query select = none(),        // select function names to be shown
          int line = -1,                // select line of function to be shown
          bool listing = false         // show instruction listing
          ){

    println("rvmLoc = <rvmLoc>");
    RVMModule p;
    listing = listing || line >= 0;
    try {
    	if(contains(rvmLoc.path, "experiments/Compiler/muRascal2RVM/MuLibrary")){
    		decls = readBinaryValueFile(#list[RVMDeclaration], rvmLoc);
    		p = rvmModule("Library",        // name
    		  (),                           // module_tags
		      {},                           // messages
			  [],                           // imports
			  [],                           // extends
              (),                           // types 
              (),                           // symbol_definitions
              //(d.qname : d | d <- decls),   // declarations
              [d | d <- decls],
              [],                           // initialization
              (),                           // resolver
              [],                           // overloaded_functions
              {},                           // importGraph
              rvmLoc);                      // src
    	} else {
        	p = readBinaryValueFile(#RVMModule, rvmLoc);
        }	
        
        noSelection = line < 0 && select == none();
        
        if(noSelection){
	        println("RVM PROGRAM: <p.name>");
	        
	        if(p.module_tags != ()){
	        	println("TAGS: <p.module_tags>");
	        }
	        
	        if(p.importGraph != {}){
	        	println("IMPORTGRAPH: <p.importGraph>");
	        }
        }
         
        if(line >= 0){
         	listDecls(p, select, line, listing);
         	return;
         }	
         
        if(select != none()){
            listDecls(p, select, line, listing);
            printOverloaded(p.overloaded_functions, select, line);
            printResolver(p.resolver, select, line);
            return;
        }
       
        printMessages(p.messages);
       
        printImports(p.imports);
        
        printExtends(p.extends);
       
        printSymbolDefinitions(p.symbol_definitions);
       
        println("DECLARATIONS:");
        for(decl <- p.declarations){
            printDecl(decl);
        }
        
        init = p.initialization;
        if(size(init) > 0){
            println("INITIALIZATION:");
            iprintln(init);
        }
        
        printResolver(p.resolver, select, line);
        
        printOverloaded(p.overloaded_functions, select, line);
        
        return;
    } catch e: {
        println("Reading: <rvmLoc>: <e>");
    }
}

void printSymbolDefinitions(map[Symbol, Production] sym_defs){
	if(size(sym_defs) > 0){
    	println("SYMBOL DEFINITIONS:");
		for(sym <- sym_defs){
        	if(choice(s, choices) := sym_defs[sym]){
            	println("\t<s>:");
                for(c <- choices){
                	println("\t\t<c>");
                }
            } else {
            	println("\t<sym>: <sym_defs[sym]>");
            }
		}
	}
}

void printMessages(set[Message] messages){
	if(size(messages) > 0){
    	println("MESSAGES:");
        for(msg <- messages){
        	println("\t<msg>");
        }
    }
}

void printImports(list[str] imports){
	if(size(imports)> 0){
    	println("IMPORTS:");
       	for(imp <- imports){
        	println("\t<imp>");
        }
    }
}
void printExtends(list[str] extends){
	if(size(extends)> 0){
    	println("EXTENDS:");
       	for(ext <- extends){
        	println("\t<ext>");
        }
    }
}

void printResolver(map[str, int] resolver, Query select, int line){
	if(size(resolver) > 0){
		println("RESOLVER:");
		for(f <- resolver){
			if(matchesSelection(f, select, atStart=false)){
					println("\t<f>: <resolver[f]>");
			}
		}
    }
}

void printOverloaded(lrel[str name, Symbol funType, str scope, list[str] ofunctions, list[str] oconstructors] overloaded, Query select, int line){
	if(size(overloaded) > 0){
    	println("OVERLOADED FUNCTIONS:");
        for(int i <- index(overloaded)){
        	t = overloaded[i];
        	if(select == none() || any(/str s :=  t, matchesSelection(s, select, atStart=false))){
            	println("\t<right("<i>", 6)>: <t>");
            }
        }
	}
}

void printDecl(RVMDeclaration d){
    if(d is FUNCTION){
        println("\tFUNCTION <d.uqname>, <d.qname>, <d.ftype>");
        print("\t\tisPublic=<d.isPublic>, isDefault=<d.isDefault>, ");
    } else {
        println("\tCOROUTINE <d.uqname>, <d.qname>");
        print("\t\t");
    }
    println("nformals=<d.nformals>, nlocals=<d.nlocals>, maxStack=<d.maxStack>, instructions=<size(d.instructions)>, scopeIn=<d.scopeIn>");
    println("\t\tsrc=<d.src>");
    println("\t\tusedOverLoadedFunctions=<d.usedOverloadedFunctions>");
    println("\t\tusedFunctions=<d.usedFunctions>");
    if(size(d.exceptions) > 0){
    	for(<str from, str to, Symbol \type, str target, int fromSP> <- d.exceptions){
    		println("\t\ttry: from=<from>, to=<to>, type=<\type>, target=<target>, fromSP=<fromSP>");
    	}
    }
}

bool matchesSelection(str info, Query select, bool atStart = false){
     return evalQuery(info, select);
	//return any(sel <- select, int i := findFirst(toLowerCase(info), sel), atStart ? i == 0 : i >= 0);
}

bool containsLine(loc src, int line) =
	line >= 0 && line >= src.begin.line && line <= src.end.line;

void listDecls(RVMModule p, Query select, int line, bool listing){
    for(decl <- p.declarations){
        dname = decl.uqname;
        uqname = decl.uqname;
        if(matchesSelection(uqname, select, atStart = true) || containsLine(decl.src, line)){
        	printDecl(decl);
            if(listing){
 				for(ins <- decl.instructions){
					println("\t\t<ins>");                
				}
            }
        }
    }
}

void statistics(loc root = |std:///|,
                PathConfig pcfg = pathConfig(),
                bool printMessages = false
                ){
    allFiles = find(root, "gz");
    println(allFiles);
    
    nfunctions = 0;
    ncoroutines = 0;
    ninstructions = 0;
    locals = ();
  
    messages = [];
    missing = {};
    nsuccess = 0;
    
    if(printMessages){
    	println("Messages:\n");
    }
    
    for(f <- allFiles){
        rvmLoc = f;
        try {
            p = readBinaryValueFile(#RVMModule, rvmLoc);
            if(size(p.messages) == 0 || all(msg <- p.messages, msg is warning)){
                nsuccess += 1;
            }
            messages += toList(p.messages);
            
            if(printMessages && size(p.messages) > 0){
               println(f);
               for(msg <- p.messages){
        	      println("\t<msg>");
        	   }
        	} 
           
            for(decl <- p.declarations){
                if(decl is FUNCTION){
                    nfunctions += 1;
                    locals[decl.name] = decl.nlocals;
                } else {
                    ncoroutines += 1;
                }
                ninstructions += size(decl.instructions);
            }
        } catch: 
            missing += f;
    }
    
    nfatal = 0;
    nerrors = 0;
    nwarnings = 0;
    
    fatal = {};
    
    
    for(msg <- messages){
        if(msg is error){
            if(findFirst(msg.msg, "Fatal compilation error") >= 0){
                fatal += msg.at;
            } else {
                nerrors += 1;
            }
         } else {
            nwarnings += 1;
         }
    }
    
    println("\nStatistics:
            '
            'files:        <size(allFiles)>
            'functions:    <nfunctions>
            'coroutines:   <ncoroutines>
            'instructions: <ninstructions>
            'errors:       <nerrors>
            'warnings:     <nwarnings>
            'missing:      <size(missing)>, <missing>
            'success:      <nsuccess>
            'fatal:        <size(fatal)>, <fatal>
            '");
}

set[loc] getFunctionLocations(
						   loc src,                  // location of Rascal source file
   							loc bin = |home:///bin|   // location where binaries are stored
							){
   rvmLoc = RVMModuleLocation(src, bin);
   try {
        p = readBinaryValueFile(#RVMModule, rvmLoc);
        
        return {d.src | d <- p.declarations};
   } catch e: {
        println("Reading: <rvmLoc>: <e>");
   }
} 

str config(str qualifiedModuleName,                // name of Rascal source module
            PathConfig pcfg = pathConfig(),
            Query select = none()){
            
   if(<true, cloc> := cachedConfigReadLoc(qualifiedModuleName,pcfg)){
      return config(cloc,  select=select);
   } else {
       return "Config file does not exist for: <qualifiedModuleName>";
    }
}
            
str config(loc cloc,  Query select = none()){
   Configuration c = readBinaryValueFile(#Configuration, cloc);
   
   res = "";
   
   if(hasMatches(c.messages, select)) { res += "messages:\n"; for(msg <- c.messages) res += "\t<msg>\n"; }
   
   if(hasMatches(c.locationTypes, select)) { res += "locationTypes:\n"; for(l <- c.locationTypes) res += getSelected(l, c.locationTypes[l], select); }
   if(containsSelected(c.expectedReturnType, select)) { res += "expectedReturnType:\n"; res += getSelected(c.expectedReturnType, select); }
   if(hasMatches(c.labelEnv, select)) { res += "labelEnv:\n"; for(nm <- c.labelEnv) res += getSelected(nm, c.labelEnv[nm], select); }
   
   if(hasMatches(c.fcvEnv, select)) { res += "fcvEnv:\n"; for(nm <- c.fcvEnv) res += getSelected(prettyPrintName(nm), c.fcvEnv[nm], select); }
   
   if(hasMatches(c.typeEnv, select)) { res += "typeEnv:\n"; for(nm <- c.typeEnv) res += getSelected(nm, c.typeEnv[nm], select); }
   if(hasMatches(c.modEnv, select)) {  res += "modEnv:\n"; for(nm <- c.modEnv)getSelected(nm, c.modEnv[nm], select); }
   if(hasMatches(c.annotationEnv, select)) {  res += "annotationEnv:\n"; for(nm <- c.annotationEnv) res += getSelected(nm, c.annotationEnv[nm], select); }
   if(hasMatches(c.tagEnv, select)) {  res += "tagEnv:\n"; for(nm <- c.tagEnv) res += getSelected(nm, c.tagEnv[nm], select); }
   if(hasMatches(c.visibilities, select)) {  res += "visibilities:\n"; for(uid <- c.visibilities)  res += getSelected(uid, c.visibilities[uid], select); }
   if(hasMatches(c.store, select)) {  res += "store:\n"; for(uid <- sort(domain(c.store)))  res += getSelected(uid, c.store[uid], select); }
   if(hasMatches(c.grammar, select)) {  res += "grammar:\n"; for(uid <- sort(domain(c.grammar)))  res += getSelected(uid, c.grammar[uid], select); }
   if(hasMatches(c.starts, select)) {  res += "starts:\n";  res += getSelected(c.starts, select); }
   if(hasMatches(c.adtFields, select)) {  res += "adtFields:\n"; for(is <- sort(domain(c.adtFields)))  res += getSelected(is, c.adtFields[is], select); }
   if(hasMatches(c.nonterminalFields, select)) {  res += "nonterminalFields:\n"; for(is <- sort(domain(c.nonterminalFields)))  res += getSelected(is, c.nonterminalFields[is], select); }
   if(hasMatches(c.functionModifiers, select)) {  res += "functionModifiers:\n"; for(uid <- sort(domain(c.functionModifiers)))  res += getSelected(uid, c.functionModifiers[uid], select); }
   if(hasMatches(c.definitions, select)) {  res += "definitions:\n"; for(uid <- sort(domain(c.definitions)))  res += getSelected(uid, c.definitions[uid], select); }
   if(hasMatches(c.uses, select)) {  res += "uses:\n"; for(uid <- sort(domain(c.uses)))  res += getSelected(uid, c.uses[uid], select); }
   if(hasMatches(c.narrowedUses, select)) {  res += "narrowedUses:\n"; for(uid <- sort(domain(c.narrowedUses)))  res += getSelected(uid, c.narrowedUses[uid], select); }
   if(hasMatches(c.usedIn, select)) {  res += "usedIn:\n"; for(uid <- sort(domain(c.usedIn)))  res += getSelected(uid, c.usedIn[uid], select); }
   if(hasMatches(c.adtConstructors, select)) {  res += "adtConstructors:\n"; for(uid <- sort(domain(c.adtConstructors)))  res += getSelected(uid, c.adtConstructors[uid], select); }
   if(hasMatches(c.nonterminalConstructors, select)) {  res += "nonterminalConstructors:\n"; for(uid <- sort(domain(c.nonterminalConstructors)))  res += getSelected(uid,c.nonterminalConstructors[uid], select); }
   res += "stack: <c.stack>\n";
   res += "labelStack: <c.labelStack>\n";
   if(hasMatches(c.keywordDefaults, select)) {  res += "keywordDefaults:\n"; for(uid <- sort(domain(c.keywordDefaults)))  res += getSelected(uid, c.keywordDefaults[uid], select); }
   if(hasMatches(c.dataKeywordDefaults, select)) {  res += "dataKeywordDefaults:\n"; for(uid <- sort(domain(c.dataKeywordDefaults)))  res += getSelected(uid, c.dataKeywordDefaults[uid], select); }
   if(hasMatches(c.tvarBounds, select)) {  res += "tvarBounds:\n"; for(uid <- sort(domain(c.tvarBounds)))  res += getSelected(uid, c.tvarBounds[uid], select); }
   if(hasMatches(c.moduleInfo, select)) {  res += "moduleInfo:\n"; for(uid <- sort(domain(c.moduleInfo)))  res += getSelected(uid, c.moduleInfo[uid], select); }
   if(hasMatches(c.globalAdtMap, select)) {  res += "globalAdtMap:\n"; for(uid <- sort(domain(c.globalAdtMap)))  res += getSelected(prettyPrintName(uid), c.globalAdtMap[uid], select); }
   
   if(hasMatches(c.globalSortMap, select)) {  res += "globalSortMap:\n"; for(uid <- sort(domain(c.globalSortMap)))  res += getSelected(prettyPrintName(uid), c.globalSortMap[uid], select); }
   
   if(hasMatches(c.deferredSignatures, select)) {  res += "deferredSignatures:\n"; for(uid <- sort(domain(c.deferredSignatures)))  res += getSelected(uid, c.deferredSignatures[uid], select); }
   if(hasMatches(c.unimportedNames, select)) {  res += "unimportedNames:\n"; for(uid <- sort(c.unimportedNames))  res += getSelected(uid, select); }
   if(c.importGraph != {}) {  res += "importGraph:\n"; for(<nm1, nm2> <- c.importGraph) res += "\t\<<prettyPrintName(nm1)>, <prettyPrintName(nm2)>\>\n"; }
   if(c.dirtyModules != {}) { res += "dirtyModules:\n"; for(dirty <- c.dirtyModules) res += "\t<prettyPrintName(dirty)>\n"; }
   return res;
}

void importGraph(str qualifiedModuleName,  // name of Rascal source module
            PathConfig pcfg = pathConfig()){
    
    //config(qualifiedModuleName);
    if(<true, cloc> := cachedConfigReadLoc(qualifiedModuleName,pcfg)){
       Configuration c = readBinaryValueFile(#Configuration, cloc); 
        
  
	    if(c.importGraph != {}) {
	        modules = [<prettyPrintName(nm), box(fig=text(getSimpleName(nm), fontSize=12), tooltip=prettyPrintName(nm))> | nm <- carrier(c.importGraph)];
	        edges = [edge(prettyPrintName(nm1), prettyPrintName(nm2)) | <nm1, nm2> <- c.importGraph];
	        g = box(fig=graph(modules, edges, width = 3000, height = 1000, lineWidth=1, graphOptions=graphOptions(nodeSep=50,layerSep=50, edgeSep=50)));
	        println(g);
	        render(g);
	    } else {
	        println("Import graph is empty");
	    }
	} else {
	  println("Config file does not exist");
	}    
 }