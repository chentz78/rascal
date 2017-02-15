package org.rascalmpl.library.experiments.Compiler.RVM.Interpreter;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.rascalmpl.interpreter.TypeReifier;
import org.rascalmpl.value.IBool;
import org.rascalmpl.value.IConstructor;
import org.rascalmpl.value.IInteger;
import org.rascalmpl.value.IList;
import org.rascalmpl.value.IMap;
import org.rascalmpl.value.ISet;
import org.rascalmpl.value.ISetWriter;
import org.rascalmpl.value.ISourceLocation;
import org.rascalmpl.value.IString;
import org.rascalmpl.value.ITuple;
import org.rascalmpl.value.IValue;
import org.rascalmpl.value.IValueFactory;
import org.rascalmpl.value.type.Type;
import org.rascalmpl.value.type.TypeFactory;

/**
 * The RVMLinker takes an RVMProgram (and its imports) and creates a single RVMExecutable for it, with
 * - all overloaded resolved
 * - all unused functions removed
 */
public class RVMLinker {

	private IValueFactory vf;
	private TypeFactory tf;
	
	// Functions
	private ArrayList<Function> functionStore;
	private Map<String, Integer> functionMap;
	
	// Constructors
	private ArrayList<Type> constructorStore;
	private Map<String, Integer> constructorMap;
	
	// Function overloading
	private Map<String, Integer> resolver;
	private ArrayList<OverloadedFunction> overloadedStore;
	
	private final Types types;

	public RVMLinker(IValueFactory vf) {
		this.vf = vf;
		this.types = new Types(this.vf);
		this.tf = TypeFactory.getInstance();
	}
	
	String moduleInit(String moduleName){
		return  "/#" + moduleName + "_init(list(value());)#0";
	}
	
	String muModuleInit(String moduleName){
		return   "/#" + moduleName + "_init";
	}
	
	private void validateInstructionAdressingLimits(){
		int nfs = functionStore.size();
		//System.out.println("size functionStore: " + nfs);
		if(nfs >= CodeBlock.maxArg){
			throw new CompilerError("functionStore size " + nfs + " exceeds limit " + CodeBlock.maxArg1);
		}
		int ncs = constructorStore.size();
		//System.out.println("size constructorStore: " + ncs);
		if(ncs >= CodeBlock.maxArg){
			throw new CompilerError("constructorStore size " + ncs + " exceeds limit " + CodeBlock.maxArg1);
		}
		int nov = overloadedStore.size();
		//System.out.println("size overloadedStore: " + nov);
		if(nov >= CodeBlock.maxArg){
			throw new CompilerError("overloadedStore size " + nov + " exceeds limit " + CodeBlock.maxArg1);
		}
	}
	
	private HashSet<String> usedFunctions = new HashSet<>();
	
	private Integer useFunctionName(String fname){
		Integer index = functionMap.get(fname);
		
		if(index == null){
			index = functionStore.size();
			functionMap.put(fname, index);
			functionStore.add(null);
			//System.out.println("useFunctionName (undef): " + index + "  => " + fname);
			usedFunctions.add(fname);
		}
		//System.out.println("useFunctionName: " + index + "  => " + fname);
		return index;
	}
	
	private void declareFunction(Function f){
		Integer index = functionMap.get(f.getName());
		if(index == null){
			index = functionStore.size();
			functionMap.put(f.getName(), index);
			functionStore.add(f);
			f.funId = index ;
		} else {
			functionStore.set(index, f);
			f.funId = index ;
		}
		
		//System.out.println("declareFunction: " + index + "  => " + f.getName());
	}
	
	private Integer useConstructorName(String cname) {
		Integer index = constructorMap.get(cname) ;
		if(index == null) {
			index = constructorStore.size();
			constructorMap.put(cname, index);
			constructorStore.add(null);
		}
		//System.out.println("useConstructorName: " + index + "  => " + cname);
		return index;
	}
	
	public Type loadProduction(IConstructor symbol) {
	    return types.loadProduction(symbol);
	}
	
	private void declareConstructor(String cname, IConstructor symbol) {
		Type constr = loadProduction(symbol);

		Integer index = constructorMap.get(cname);
		if(index == null) {
			index = constructorStore.size();
			constructorMap.put(cname, index);
			constructorStore.add(constr);
		} else {
			constructorStore.set(index, constr);
		}
	}
	
	private void addResolver(IMap resolver) {
		for(IValue fuid : resolver) {
			String of = ((IString) fuid).getValue();
			int index = ((IInteger) resolver.get(fuid)).intValue();
			this.resolver.put(of, index);
		}
	}
	
	private void fillOverloadedStore(IList overloadedStore) {
		
		for(IValue of : overloadedStore) {
			
			ITuple ofTuple = (ITuple) of;
			
			String funName = ((IString) ofTuple.get(0)).getValue();
			
			IConstructor funType = (IConstructor) ofTuple.get(1);
			
			String scopeIn = ((IString) ofTuple.get(2)).getValue();

			IList fuids = (IList) ofTuple.get(3);		// function alternatives
			int[] funs = new int[fuids.length()];
			int alt = 0;
			for(IValue fuid : fuids) {
				String name = ((IString) fuid).getValue();
				//System.out.println("fillOverloadedStore: add function " + name);
				
				Integer index = useFunctionName(name);
				funs[alt++] = index;
			}
			
			fuids = (IList) ofTuple.get(4);				// constructor alternatives
			int[] constrs = new int[fuids.length()];
			alt = 0;
			for(IValue fuid : fuids) {
				Integer index = useConstructorName(((IString) fuid).getValue());
				constrs[alt++] = index;
			}
			
			OverloadedFunction res = new OverloadedFunction(funName, new TypeReifier(vf).symbolToType(funType, vf.mapWriter().done()), funs, constrs, scopeIn);
			this.overloadedStore.add(res);
		}
	}
	
	private void validateExecutable(){
		int nfun = functionStore.size();
		if(nfun != functionMap.size()){
			System.err.println("functionStore and functionMap have different size: " + nfun + " vs " + functionMap.size());
		}
		for(String fname : functionMap.keySet()){
			int n = functionMap.get(fname);
			if(functionStore.get(n) == null){
				System.err.println("FunctionStore has null entry for: "+ fname + " at index " + n);
			}
		}
		
		int ncon = constructorStore.size();
		if(ncon != constructorMap.size()){
			System.err.println("constructorStore and constructorMap have different size: " + ncon + " vs " + constructorMap.size());
		}
		for(String cname : constructorMap.keySet()){
			int n = constructorMap.get(cname);
			if(constructorStore.get(n) == null){
				System.err.println("ConstructorStore has null entry for: "+ cname + " at index " + n);
			}
		}
	}
	
	private void validateOverloading(){
		for(String oname : resolver.keySet()){
			int n = resolver.get(oname);
			if(overloadedStore.get(n) == null){
				System.err.println("OverloadedStore has null entry for: "+ oname + " at index " + n);
			}
		}
	}
	
	/*
	 * Remove all unused functions from this RVM, given a set of used functions:
	 * - Remove unused functions from functionMap and shift function indices
	 * - The finalize in overloaded functions uses the resulting indexMap to shift and prune
	 *   function indices in overloading vectors
	 */
	private HashMap<Integer,Integer> removeUnusedFunctions(ISet used){
		int newSize = used.size();
		ArrayList<Function> newFunctionStore = new ArrayList<Function>(newSize);
		
		HashMap<Integer,Integer> functionIndexMap = new HashMap<>(newSize);
		
		// Prune and shift functionMap and functionStore
		
		int shift = 0;
		for(int i = 0; i < functionStore.size(); i++){
			Function fn = functionStore.get(i);
			if(!used.contains(vf.string(fn.name))){
				functionMap.remove(fn.name);
				shift++;
			} else {
				int ishifted = i - shift;
				fn.funId = ishifted;
				functionMap.put(fn.name, ishifted);
				newFunctionStore.add(fn);
				functionIndexMap.put(i, ishifted);
				//System.err.println("Shift " + fn.name + " from " + i + " to " + ishifted);
			}
		}
		
		functionStore = newFunctionStore;

		return functionIndexMap;
	}
	
	/*
	 * After collecting the basic use relation for all functions we have
	 * to expand each overlaoded function to all its possible alternatives.
	 * This can only be done in a second pass over the use relation
	 *  when all function overloading information is available.
	 */
	
	private ISet expandOverloadedFunctionUses(ISet initial){
		ISetWriter w = vf.setWriter();
		for(IValue v : initial){
			ITuple tup = (ITuple) v;
			IString left = (IString) tup.get(0);
			IString right = (IString) tup.get(1);
			Integer uresolver = resolver.get(right.getValue());
		
			if(uresolver != null){
				// right is an overloaded function, replace by its alternatives
				OverloadedFunction of = overloadedStore.get(uresolver);
				for (int uuid : of.functions){
					Function function = functionStore.get(uuid);
					if (function == null) {
					    throw new CompilerError("No function for uuid " + uuid + " in store, part of overloaded function:" + of);
					}
					w.insert(vf.tuple(left, vf.string(function.name)));
				}
			} else {
				// otherwise, keep original tuple
				w.insert(tup);
			}
		}
		return w.done();
	}
	
	private void finalizeInstructions(Map<Integer, Integer> indexMap){
		int i = 0;
		for(String fname : functionMap.keySet()){
			if(functionMap.get(fname) == null){
				System.out.println("finalizeInstructions, null for function : " + fname);
			}
		}
		for(String fname : functionMap.keySet()) {
			Function f = functionStore.get(functionMap.get(fname));
			if(f == null){
				String nameAtIndex = "**unknown**";
				for(String fname2 : functionMap.keySet()){
					if(functionMap.get(fname2) == i){
						nameAtIndex = fname2;
						break;
					}
				}
				System.out.println("finalizeInstructions, null at index: " + i + ", " + nameAtIndex);
			} else {
				//System.out.println("finalizeInstructions: " + f.name);
			}
			f.finalize(functionMap, constructorMap, resolver);
			i++;
		}
		for(OverloadedFunction of : overloadedStore) {
			of.finalize(functionMap, functionStore, indexMap);
		}
	}
	
	private void addFunctionUses(ISetWriter w, IString fname, ISet uses){
		for(IValue use : uses){
			w.insert(vf.tuple(fname, use));
		}
	}
	
	private void addOverloadedFunctionUses(ISetWriter w, IString fname, ISet uses){
		//System.err.println("addOverloadedFunctionUses:" + fname + ", " + uses);
		for(IValue use : uses){
			w.insert(vf.tuple(fname, use));
		}
	}
	
	public ISet getErrors(IConstructor program){
		ISetWriter w = vf.setWriter();
		IConstructor main_module = (IConstructor) program.get("main_module");
		ISet messages = (ISet) main_module.get("messages");
		for(IValue v : messages){
			IConstructor msg = (IConstructor) v;
			if(msg.getName().equals("error")){
				w.insert(msg);
			}
		}
		return w.done();
	}
	
	public RVMExecutable link(IConstructor program, boolean jvm) throws IOException {
		
		ISet errors = getErrors(program);
		
		if(errors.size() > 0){
			return new RVMExecutable(errors);
		}
		
		boolean eliminateDeadCode = true;
		
		functionStore = new ArrayList<Function>();
		constructorStore = new ArrayList<Type>();

		functionMap = new HashMap<String, Integer>();
		constructorMap = new HashMap<String, Integer>();
		
		resolver = new HashMap<String,Integer>();
		overloadedStore = new ArrayList<OverloadedFunction>();
		
		IMap imported_module_tags = (IMap) program.get("imported_module_tags");
		IConstructor main_module = (IConstructor) program.get("main_module");
		IMap moduleTags = imported_module_tags.put(main_module.get("name"), main_module.get("module_tags"));
		
		String moduleName = ((IString) main_module.get("name")).getValue();
		
		IList extendedModules = (IList) main_module.get("extends");
		HashSet<String> extendedModuleSet = new HashSet<>();
		for(IValue v : extendedModules){
			extendedModuleSet.add(((IString) v).getValue());
		}
		
		boolean hasExtends = !extendedModuleSet.isEmpty();

		/** Imported types */

		IMap imported_types = (IMap) program.get("imported_types");
		Iterator<Entry<IValue, IValue>> entries = imported_types.entryIterator();
		while(entries.hasNext()) {
			Entry<IValue, IValue> entry = entries.next();
			declareConstructor(((IString) entry.getKey()).getValue(), (IConstructor) entry.getValue());
		}
		
		/** Overloading resolution */

		// Overloading resolution of imported functions
		IMap imported_overloading_resolvers = (IMap) program.get("imported_overloading_resolvers");
		addResolver(imported_overloading_resolvers);
		
		IList imported_overloaded_functions = (IList) program.get("imported_overloaded_functions");
		fillOverloadedStore(imported_overloaded_functions);

		// Overloading resolution of functions in main module
		addResolver((IMap) main_module.get("resolver"));
		fillOverloadedStore((IList) main_module.get("overloaded_functions"));

		ISetWriter usesWriter = vf.setWriter();
		ISetWriter rootWriter = vf.setWriter();
		
		/** Imported functions */
		
		ArrayList<String> initializers = new ArrayList<String>();  	// initializers of imported modules

		IList imported_declarations = (IList) program.get("imported_declarations");
		for(IValue imp : imported_declarations){
			IConstructor declaration = (IConstructor) imp;
			IString iname = (IString) declaration.get("qname");
			String name = iname.getValue();
			if (declaration.getName().contentEquals("FUNCTION")) {
				//System.out.println("IMPORTED FUNCTION: " + name);

				if(name.endsWith("_init(list(value());)#0")){
					rootWriter.insert(iname);
					initializers.add(name);
				}
				
				loadInstructions(name, declaration, false);

				if(eliminateDeadCode){

					addOverloadedFunctionUses(usesWriter, iname, (ISet) declaration.get("usedOverloadedFunctions"));
					addFunctionUses(usesWriter, iname, (ISet) declaration.get("usedFunctions"));

					if(name.contains("companion")){	// always preserve generated companion and companion-defaults functions
						rootWriter.insert(iname);
						loadInstructions(name, declaration, false);
					}

					if(name.contains("closure#")){	// preserve generated closure functions and their enclosing function
						// (this is an overapproximation and may result in preserving some unused functions)
						IString scopeIn = (IString) declaration.get("scopeIn");
						rootWriter.insert(iname);
						rootWriter.insert(scopeIn);
					}

					if(hasExtends){
						int n = name.indexOf("/");
						if(n >=0 && extendedModuleSet.contains(name.substring(0, n))){
							rootWriter.insert(iname);
						}
					}
				}
			}
			if (declaration.getName().contentEquals("COROUTINE")) {
				loadInstructions(name, declaration, true);

				if(eliminateDeadCode){
					addOverloadedFunctionUses(usesWriter, iname, (ISet) declaration.get("usedOverloadedFunctions"));
					addFunctionUses(usesWriter, iname, (ISet) declaration.get("usedFunctions"));
				}

			}
		}

		/** Types from main module */

		IMap types = (IMap) main_module.get("types");
		entries = types.entryIterator();
		while(entries.hasNext()) {
			Entry<IValue, IValue> entry = entries.next();
			declareConstructor(((IString) entry.getKey()).getValue(), (IConstructor) entry.getValue());
		}

		/** Declarations for main module */
		
		String main = "/main()#0";
		
		String mu_main = "/MAIN";	
		
		String module_init = moduleInit(moduleName);
		String mu_module_init = muModuleInit(moduleName);

		String uid_module_main = "";
		String uid_module_init = "";

		IList declarations = (IList) main_module.get("declarations");
		for (IValue ideclaration : declarations) {
			IConstructor declaration = (IConstructor) ideclaration;
			IString iname = (IString) declaration.get("qname");
			String name = iname.getValue();

			if (declaration.getName().contentEquals("FUNCTION")) {
				
				//System.out.println("FUNCTION: " + name);
				
				if(name.endsWith(main) || name.endsWith(mu_main)) {
					uid_module_main = name;					// Get main's uid in current module
				}
				
				if(name.endsWith(module_init) || name.endsWith(mu_module_init)) {
					uid_module_init = name;					// Get module_init's uid in current module
				}
				
				loadInstructions(name, declaration, false);
				
				if(eliminateDeadCode){
					rootWriter.insert(iname);
					addOverloadedFunctionUses(usesWriter, iname, (ISet) declaration.get("usedOverloadedFunctions"));
					addFunctionUses(usesWriter, iname, (ISet) declaration.get("usedFunctions"));
				}
			}
				
			if(declaration.getName().contentEquals("COROUTINE")) {
				loadInstructions(name, declaration, true);
				if(eliminateDeadCode){
					addOverloadedFunctionUses(usesWriter, iname, (ISet) declaration.get("usedOverloadedFunctions"));
					addFunctionUses(usesWriter, iname, (ISet) declaration.get("usedFunctions"));
				}
			}
		}
		
		HashMap<Integer, Integer> indexMap = null;
		
		if(eliminateDeadCode){
			ISet uses =  expandOverloadedFunctionUses(usesWriter.done());

			ISet roots = rootWriter.done();

			ISetWriter usedWriter = vf.setWriter();

			for(IValue v : roots){
				usedWriter.insert(v);
			}

			for(IValue v : uses.asRelation().closure()){
				ITuple tup = (ITuple) v;
				if(roots.contains(tup.get(0))){
					usedWriter.insert(tup.get(1));
				}
			}

			ISet used = usedWriter.done();

			indexMap = removeUnusedFunctions(used);
		}
		
		/** Finalize & validate */

		finalizeInstructions(indexMap);

		validateInstructionAdressingLimits();

		validateExecutable();

		validateOverloading();

		//System.out.println("Linking: " +  (Timing.getCpuTime() - start)/1000000 + " ms");

		return new RVMExecutable(((IString) main_module.get("name")).getValue(),
							     moduleTags,
								 (IMap) main_module.get("symbol_definitions"),
								 functionMap, 
								 functionStore.toArray(new Function[functionStore.size()]), 
								 constructorMap,
								 constructorStore,	
								 resolver, 
								 overloadedStore.toArray(new OverloadedFunction[overloadedStore.size()]),  
								 initializers,
								 uid_module_init, 
								 uid_module_main, 
								 vf, 
								 jvm);
	}
	
	/*
	 * Utility function for instruction loading
	 */
	
	// Get Boolean field from an instruction

	private boolean getBooleanField(IConstructor instruction, String field) {
		return ((IBool) instruction.get(field)).getValue();
	}

	// Get integer field from an instruction

	private int getIntField(IConstructor instruction, String field) {
		return ((IInteger) instruction.get(field)).intValue();
	}

	// Get String field from an instruction

	private String getStrField(IConstructor instruction, String field) {
		return ((IString) instruction.get(field)).getValue();
	}
	
	// Get Location field from an instruction

	private ISourceLocation getLocField(IConstructor instruction, String field) {
		return ((ISourceLocation) instruction.get(field));
	}
	private IList getListField(IConstructor instruction, String field) {
		return ((IList) instruction.get(field));
	}

	/**
	 * Load the instructions of a function in a RVM.
	 * 
	 * @param name of the function to be loaded
	 * @param declaration the declaration of that function
	 * @param rvm in which function will be loaded
	 */
	private void loadInstructions(String name, IConstructor declaration, boolean isCoroutine){
		int continuationPoints = 0 ;
		Type ftype = isCoroutine ? tf.voidType() : types.symbolToType((IConstructor) declaration.get("ftype"));
		
		Type kwType = null;   // transitional for boot
		if(!isCoroutine && declaration.has("kwType")){
		  kwType = types.symbolToType((IConstructor) declaration.get("kwType"));
		}
		
		String scopeIn = ((IString) declaration.get("scopeIn")).getValue();
//		if(scopeIn.equals("")) {
//			scopeIn = null;
//		}
		
		Integer nlocals = ((IInteger) declaration.get("nlocals")).intValue();
		IMap localNames = ((IMap) declaration.get("localNames"));
		Integer nformals = ((IInteger) declaration.get("nformals")).intValue();
		
		Integer maxstack = ((IInteger) declaration.get("maxStack")).intValue();
		IList code = (IList) declaration.get("instructions");
		ISourceLocation src = (ISourceLocation) declaration.get("src");
		boolean isDefault = false;
		boolean isTest = false;
		IMap tags = null;
		boolean isConcreteArg = false;
		int abstractFingerprint = 0;
		int concreteFingerprint = 0;
		if(!isCoroutine){
			isDefault = ((IBool) declaration.get("isDefault")).getValue();
			if(declaration.has("isTest")){   // Transitional for boot
			  isTest = ((IBool) declaration.get("isTest")).getValue();
			  tags = ((IMap) declaration.get("tags"));
			} else {
			  tags = vf.mapWriter().done();
			}
			isConcreteArg = ((IBool) declaration.get("isConcreteArg")).getValue();
			abstractFingerprint = ((IInteger) declaration.get("abstractFingerprint")).intValue();
			concreteFingerprint = ((IInteger) declaration.get("concreteFingerprint")).intValue();
		}
		CodeBlock codeblock = new CodeBlock(name, vf);
		// Loading instructions
		try {
		for (int i = 0; i < code.length(); i++) {
			IConstructor instruction = (IConstructor) code.get(i);
			String opcode = instruction.getName();

			// TODO: let's do this with Java reflection; the names all match up and this code
			// is brittle to extension and renaming
			switch (opcode) {
			case "LOADCON":
				codeblock.LOADCON(instruction.get("val"));
				break;
				
			case "PUSHCON":
				codeblock.PUSHCON(instruction.get("val"));
				break;

			case "LOADVAR":
				codeblock.LOADVAR(getStrField(instruction, "fuid"), 
								  getIntField(instruction, "pos"));
				break;
				
			case "PUSHVAR":
				codeblock.PUSHVAR(getStrField(instruction, "fuid"), 
								  getIntField(instruction, "pos"));
				break;

			case "LOADLOC":
				codeblock.LOADLOC(getIntField(instruction, "pos"));
				break;
				
			case "PUSHLOC":
				codeblock.PUSHLOC(getIntField(instruction, "pos"));
				break;

			case "STOREVAR":
				codeblock.STOREVAR(getStrField(instruction, "fuid"), 
								   getIntField(instruction, "pos"));
				break;
				
			case "RESETVAR":
				codeblock.RESETVAR(getStrField(instruction, "fuid"), 
								   getIntField(instruction, "pos"));
				break;

			case "STORELOC":
				codeblock.STORELOC(getIntField(instruction, "pos"));
				break;
				
			case "RESETLOC":
				codeblock.RESETLOC(getIntField(instruction, "pos"));
				break;

			case "LABEL":
				codeblock = codeblock.LABEL(getStrField(instruction, "label"));
				break;

			case "CALL":
				codeblock.CALL(getStrField(instruction, "fuid"), getIntField(instruction, "arity"),++continuationPoints);
				break;

			case "CALLDYN":
				codeblock.CALLDYN( getIntField(instruction, "arity"), ++continuationPoints);
				break;
				
			case "APPLY":
				codeblock.APPLY(getStrField(instruction, "fuid"), 
								getIntField(instruction, "arity"));
				break;
				
			case "APPLYDYN":
				codeblock.APPLYDYN(getIntField(instruction, "arity"));
				break;

			case "PUSH_ROOT_FUN":
				codeblock.PUSH_ROOT_FUN(getStrField(instruction, "fuid"));
				break;

			case "RETURN0":
				codeblock.RETURN0();
				break;

			case "RETURN1":
				codeblock.RETURN1();
				break;
				
			case "CORETURN0":
				codeblock.CORETURN0();
				break;

			case "CORETURN1":
				codeblock.CORETURN1(getIntField(instruction, "arity"));
				break;

			case "JMP":
				codeblock.JMP(getStrField(instruction, "label"));
				break;

			case "JMPTRUE":
				codeblock.JMPTRUE(getStrField(instruction, "label"));
				break;

			case "JMPFALSE":
				codeblock.JMPFALSE(getStrField(instruction, "label"));
				break;

			case "HALT":
				codeblock.HALT();
				break;
				
			case "CREATE":
				codeblock.CREATE(getStrField(instruction, "fuid"), 
								 getIntField(instruction, "arity"));
				break;

			case "CREATEDYN":
				codeblock.CREATEDYN(getIntField(instruction, "arity"));
				break;

			case "NEXT0":
				codeblock.NEXT0();
				break;

			case "NEXT1":
				codeblock.NEXT1();
				break;

			case "YIELD0":
				codeblock.YIELD0(++continuationPoints);
				break;

			case "YIELD1":
				codeblock.YIELD1(getIntField(instruction, "arity"),++continuationPoints);
				break;

			case "PRINTLN":
				codeblock.PRINTLN(getIntField(instruction, "arity"));
				break;

			case "POP":
				codeblock.POP();
				break;

			case "LOADLOCREF":
				codeblock.LOADLOCREF(getIntField(instruction, "pos"));
				break;
				
			case "PUSHLOCREF":
				codeblock.PUSHLOCREF(getIntField(instruction, "pos"));
				break;

			case "LOADVARREF":
				codeblock.LOADVARREF(getStrField(instruction, "fuid"), 
									 getIntField(instruction, "pos"));
				break;
				
			case "PUSHVARREF":
				codeblock.PUSHVARREF(getStrField(instruction, "fuid"), 
									 getIntField(instruction, "pos"));
				break;

			case "LOADLOCDEREF":
				codeblock.LOADLOCDEREF(getIntField(instruction, "pos"));
				break;
				
			case "PUSHLOCDEREF":
				codeblock.PUSHLOCDEREF(getIntField(instruction, "pos"));
				break;

			case "LOADVARDEREF":
				codeblock.LOADVARDEREF(getStrField(instruction, "fuid"), 
									   getIntField(instruction, "pos"));
				break;
				
			case "PUSHVARDEREF":
				codeblock.PUSHVARDEREF(getStrField(instruction, "fuid"), 
									   getIntField(instruction, "pos"));
				break;

			case "STORELOCDEREF":
				codeblock.STORELOCDEREF(getIntField(instruction, "pos"));
				break;

			case "STOREVARDEREF":
				codeblock.STOREVARDEREF(getStrField(instruction, "fuid"), 
										getIntField(instruction, "pos"));
				break;

			case "PUSH_NESTED_FUN":
				codeblock.PUSHNESTEDFUN(getStrField(instruction, "fuid"), 
										getStrField(instruction, "scopeIn"));
				break;

			case "PUSHCONSTR":
				codeblock.PUSHCONSTR(getStrField(instruction, "fuid"));
				break;

			case "CALLCONSTR":
				codeblock.CALLCONSTR(getStrField(instruction, "fuid"), 
									 getIntField(instruction, "arity")/*, getLocField(instruction, "src")*/);
				break;

			case "LOADTYPE":
				codeblock.LOADTYPE(getTypeField(instruction, "type"));
				break;
				
			case "PUSHTYPE":
				codeblock.PUSHTYPE(getTypeField(instruction, "type"));
				break;
				
			case "LOADBOOL":
				codeblock.LOADBOOL(getBooleanField(instruction, "bval"));
				break;

			case "LOADINT":
				codeblock.LOADINT(getIntField(instruction, "nval"));
				break;

			case "FAILRETURN":
				codeblock.FAILRETURN();
				break;

			case "PUSHOFUN" :
				codeblock.PUSHOFUN(getStrField(instruction, "fuid"));
				break;

			case "OCALL" :
				codeblock.OCALL(getStrField(instruction, "fuid"), 
								getIntField(instruction, "arity"), 
								getLocField(instruction, "src"));
				break;

			case "OCALLDYN" :
				codeblock.OCALLDYN(getTypeField(instruction, "types"), 
								   getIntField(instruction, "arity"), 
								   getLocField(instruction, "src"));
				break;

			case "CALLJAVA":
				codeblock.CALLJAVA(getStrField(instruction, "name"), 
						           getStrField(instruction, "class"), 
						 		   getTypeField(instruction, "parameterTypes"), 
						 		   getTypeField(instruction, "keywordTypes"), 
						 		   getIntField(instruction, "reflect"));
				break;

			case "THROW":
				codeblock.THROW(getLocField(instruction, "src"));
				break;
			
			case "TYPESWITCH":
				codeblock.TYPESWITCH((IList)instruction.get("labels"));
				break;
				
			case "UNWRAPTHROWNLOC":
				codeblock.UNWRAPTHROWNLOC(getIntField(instruction, "pos"));
				break;
				
			case "FILTERRETURN":
				codeblock.FILTERRETURN();
				break;
				
			case "EXHAUST":
				codeblock.EXHAUST();
				break;
				
			case "GUARD":
				codeblock.GUARD(++continuationPoints);
				break;
				
			case "SUBSCRIPTARRAY":
				codeblock.SUBSCRIPTARRAY();
				break;
				
			case "SUBSCRIPTLIST":
				codeblock.SUBSCRIPTLIST();
				break;
				
			case "LESSINT":
				codeblock.LESSINT();
				break;
				
			case "GREATEREQUALINT":
				codeblock.GREATEREQUALINT();
				break;
				
			case "ADDINT":
				codeblock.ADDINT();
				break;
				
			case "SUBTRACTINT":
				codeblock.SUBTRACTINT();
				break;
				
			case "ANDBOOL":
				codeblock.ANDBOOL();
				break;
				
			case "TYPEOF":
				codeblock.TYPEOF();
				break;
				
			case "SUBTYPE":
				codeblock.SUBTYPE();
				break;
				
			case "CHECKARGTYPEANDCOPY":
				codeblock.CHECKARGTYPEANDCOPY(getIntField(instruction, "pos1"),
				        getTypeField(instruction, "type"),
				        getIntField(instruction, "pos2"));
				break;
				
			case "LOADLOCKWP":
				codeblock.LOADLOCKWP(getStrField(instruction, "name"));
				break;
				
			case "PUSHLOCKWP":
				codeblock.PUSHLOCKWP(getStrField(instruction, "name"));
				break;
				
			case "LOADVARKWP":
				codeblock.LOADVARKWP(getStrField(instruction, "fuid"), 
									 getStrField(instruction, "name"));
				break;
				
			case "PUSHVARKWP":
				codeblock.PUSHVARKWP(getStrField(instruction, "fuid"), 
									 getStrField(instruction, "name"));
				break;
				
			case "STORELOCKWP":
				codeblock.STORELOCKWP(getStrField(instruction, "name"));
				break;
				
			case "STOREVARKWP":
				codeblock.STOREVARKWP(getStrField(instruction, "fuid"), 
									  getStrField(instruction, "name"));
				break;
				
			case "UNWRAPTHROWNVAR":
				codeblock.UNWRAPTHROWNVAR(getStrField(instruction, "fuid"), 
									      getIntField(instruction, "pos"));
				break;
			
			case "SWITCH":
				codeblock.SWITCH((IMap)instruction.get("caseLabels"),
								 getStrField(instruction, "caseDefault"),
								 getBooleanField(instruction, "useConcreteFingerprint"));
				break;
				
			case "RESETLOCS":
				codeblock.RESETLOCS(getListField(instruction, "positions"));
				break;
				
			case "VISIT":
				codeblock.VISIT(getBooleanField(instruction, "direction"),
								getBooleanField(instruction, "fixedpoint"),
								getBooleanField(instruction, "progress"),
								getBooleanField(instruction, "rebuild"));
				break;
				
			case "CHECKMEMO":
				codeblock.CHECKMEMO();
				break;
				
			case "PUSHEMPTYKWMAP":
				codeblock.PUSHEMPTYKWMAP();
				break;
				
			case "VALUESUBTYPE":
				codeblock.VALUESUBTYPE(getTypeField(instruction, "type"));
				break;
				
			case "CALLMUPRIM0":
				codeblock.CALLMUPRIM0(MuPrimitive.valueOf(getStrField(instruction, "name")));
				break;
				
			case "PUSHCALLMUPRIM0":
				codeblock.PUSHCALLMUPRIM0(MuPrimitive.valueOf(getStrField(instruction, "name")));
				break;
				
			case "CALLMUPRIM1":
				codeblock.CALLMUPRIM1(MuPrimitive.valueOf(getStrField(instruction, "name")));
				break;
				
			case "PUSHCALLMUPRIM1":
				codeblock.PUSHCALLMUPRIM1(MuPrimitive.valueOf(getStrField(instruction, "name")));
				break;
				
			case "CALLMUPRIM2":
				codeblock.CALLMUPRIM2(MuPrimitive.valueOf(getStrField(instruction, "name")));
				break;
				
			case "PUSHCALLMUPRIM2":
				codeblock.PUSHCALLMUPRIM2(MuPrimitive.valueOf(getStrField(instruction, "name")));
				break;
				
			case "CALLMUPRIMN":
				codeblock.CALLMUPRIMN(MuPrimitive.valueOf(getStrField(instruction, "name")), 
									 getIntField(instruction, "arity"));
				break;
				
			case "PUSHCALLMUPRIMN":
				codeblock.PUSHCALLMUPRIMN(MuPrimitive.valueOf(getStrField(instruction, "name")), 
									 getIntField(instruction, "arity"));
				break;
			
			case "CALLPRIM0":
				codeblock.CALLPRIM0(RascalPrimitive.valueOf(getStrField(instruction, "name")), 
								   getLocField(instruction, "src"));
				break;
				
			case "PUSHCALLPRIM0":
				codeblock.PUSHCALLPRIM0(RascalPrimitive.valueOf(getStrField(instruction, "name")), 
								   getLocField(instruction, "src"));
				break;
				
			case "CALLPRIM1":
				codeblock.CALLPRIM1(RascalPrimitive.valueOf(getStrField(instruction, "name")), 
								   getLocField(instruction, "src"));
				break;
				
			case "PUSHCALLPRIM1":
				codeblock.PUSHCALLPRIM1(RascalPrimitive.valueOf(getStrField(instruction, "name")), 
								   getLocField(instruction, "src"));
				break;
				
			case "CALLPRIM2":
				codeblock.CALLPRIM2(RascalPrimitive.valueOf(getStrField(instruction, "name")), 
								   getLocField(instruction, "src"));
				break;
				
			case "PUSHCALLPRIM2":
				codeblock.PUSHCALLPRIM2(RascalPrimitive.valueOf(getStrField(instruction, "name")), 
								   getLocField(instruction, "src"));
				break;
				
			case "CALLPRIMN":
				codeblock.CALLPRIMN(RascalPrimitive.valueOf(getStrField(instruction, "name")), 
								   getIntField(instruction, "arity"), 
								   getLocField(instruction, "src"));
				break;
				
			case "PUSHCALLPRIMN":
				codeblock.PUSHCALLPRIMN(RascalPrimitive.valueOf(getStrField(instruction, "name")), 
								   getIntField(instruction, "arity"), 
								   getLocField(instruction, "src"));
				break;
			
			case "POPACCU":
				codeblock.POPACCU();
				break;
				
			case "PUSHACCU":
				codeblock.PUSHACCU();
				break;
				
			default:
				throw new CompilerError("In function " + name + ", unknown instruction: " + opcode);
			}

		}
		} catch (Exception e){
			throw new CompilerError("In function " + name + " : " + e.getMessage(), e);
		}
		
		
		Function function = new Function(name, 
										 ftype, 
										 kwType,
										 scopeIn, 
										 nformals, 
										 nlocals, 
										 isDefault, 
										 isTest, 
										 tags,
										 localNames,
										 maxstack,
										 isConcreteArg, 
										 abstractFingerprint, concreteFingerprint, codeblock, src, continuationPoints);
		
		function.attachExceptionTable((IList) declaration.get("exceptions"));
		
		if(isCoroutine) {
			function.isCoroutine = true;
			IList refList = (IList) declaration.get("refs");
			int[] refs = new int[refList.length()];
			int i = 0;
			for(IValue ref : refList) {
				refs[i++] = ((IInteger) ref).intValue();
			}
			function.refs = refs;
		} else {
			
			boolean isVarArgs = ((IBool) declaration.get("isVarArgs")).getValue();
			function.isVarArgs = isVarArgs;
		}
		declareFunction(function);
	}

    private Type getTypeField(IConstructor instruction, String field) {
        return types.symbolToType((IConstructor) instruction.get(field));
    }
}
