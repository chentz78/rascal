package org.rascalmpl.library.experiments.Compiler.RascalExtraction;

import java.io.IOException;
import java.util.Map;

import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.OverloadedFunction;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.RVMCore;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.value.ISourceLocation;
import org.rascalmpl.value.IString;
import org.rascalmpl.value.ITuple;
import org.rascalmpl.value.IValue;
import org.rascalmpl.value.IValueFactory;

public class RascalExtraction {
	IValueFactory vf;
	private OverloadedFunction extractDoc;
	
	private RVMCore rvm;
	
	public RascalExtraction(IValueFactory vf, PathConfig pcfg) throws IOException{
//		this.vf = vf;
//		if(rvm == null){
//			RascalExecutionContext rex = 
//					RascalExecutionContextBuilder.normalContext(vf, pcfg.getBoot() /* TODO needs a kernel location */, System.out, System.err)
//						.setJVM(true)					// options for complete repl
//						.setTrace(false)
//						.build();
//			rvm = ExecutionTools.initializedRVM(rex.getRascalExtraction(), rex);
//		}
//		try {
//			extractDoc = rvm.getOverloadedFunction("tuple[str moduleDoc, list[DeclarationInfo] declarationInfo] extractDoc(str parent, loc moduleLoc)");
//		} catch (NoSuchRascalFunction e) {
//			e.printStackTrace();
//		}
	}
	
	/**
	 * Extract concepts from a "remote" Rascal files, i.e. outside a documentation hierarchy
	 * @param moduleLoc	Location of the Rascal source file
	 * @param kwArgs	Keyword arguments
	 * @return A tuple consisting of 1. string with extracted documentation, 2. a list of extracted declaration info
	 */
	public ITuple extractDoc(IString parent, ISourceLocation moduleLoc, Map<String,IValue> kwArgs){
		try {
			return (ITuple) rvm.executeRVMFunction(extractDoc, new IValue[] { parent, moduleLoc }, kwArgs);
		} catch (Exception e){
			e.printStackTrace(System.err);
		}
		return vf.tuple(vf.string(""), vf.list());
	}
}
