package org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.Instructions;

import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.BytecodeGenerator;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.CodeBlock;

public class ResetLoc extends Instruction {

	int pos;
	
	public ResetLoc(CodeBlock ins, int pos){
		super(ins, Opcode.RESETLOC);
		this.pos = pos;
	}
	
	public String toString() { return "RESETLOC " + pos; }
	
	public void generate(){
		codeblock.addCode1(opcode.getOpcode(), pos);
	}
	public void generateByteCode(BytecodeGenerator codeEmittor, boolean debug){
		if ( debug ) 
			codeEmittor.emitDebugCall1(opcode.name(), pos);
		
		codeEmittor.emitInlineResetLoc(pos,debug);
	}
}
