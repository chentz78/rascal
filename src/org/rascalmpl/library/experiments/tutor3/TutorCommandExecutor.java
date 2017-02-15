package org.rascalmpl.library.experiments.tutor3;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;

import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.NoSuchRascalFunction;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.ideservices.IDEServices;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.repl.CommandExecutor;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.repl.CompiledRascalREPL;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.repl.RascalShellExecutionException;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.repl.debug.DebugREPLFrameObserver;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.value.ISourceLocation;
import org.rascalmpl.value.IValue;
import org.rascalmpl.value.IValueFactory;
import org.rascalmpl.values.ValueFactoryFactory;

public class TutorCommandExecutor {
	ISourceLocation screenInputLocation;
	CommandExecutor executor;
	StringWriter shellStringWriter;
	PrintWriter shellPrintWriter;
	PrintWriter err;
	String consoleInputPath = "/ConsoleInput.rsc";

	TutorCommandExecutor(PathConfig pcfg, PrintWriter err, IDEServices ideServices) throws IOException, NoSuchRascalFunction, URISyntaxException{
		try {
			IValueFactory vf = ValueFactoryFactory.getValueFactory();
			screenInputLocation = vf.sourceLocation("home", "", consoleInputPath);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Cannot initialize: " + e.getMessage());
		}
		this.err = err;
		shellStringWriter = new StringWriter();
		shellPrintWriter = new PrintWriter(shellStringWriter);
		executor = new CommandExecutor(pcfg, shellPrintWriter, shellPrintWriter, ideServices, null);
	
		try {
			executor.setDebugObserver(new DebugREPLFrameObserver(null, System.in, System.out, true, true, null, null, ideServices));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	void flush(){
		shellPrintWriter.flush();
	}
	
	void resetOutput(){
		shellStringWriter.getBuffer().setLength(0);
	}
	
	void reset(){
		executor.reset();
	}
	
	IValue eval(String line) throws RascalShellExecutionException, IOException{
	  String[] words = line.split(" ");
	  if(words.length > 0 && CompiledRascalREPL.SHELL_VERBS.contains(words[0])){
	    return executor.evalShellCommand(words);
	  } else {
	    return executor.eval(line, screenInputLocation);
	  }
	}
	
	String evalPrint(String line) throws IOException, RascalShellExecutionException{
	  String[] words = line.trim().split(" ");
	  IValue result;
      if(words.length > 0 && CompiledRascalREPL.SHELL_VERBS.contains(words[0])){
        result = executor.evalShellCommand(words);
      } else {
        result = executor.eval(line, screenInputLocation);
      }
		return executor.resultToString(result);
	}
	
	String getMessages(){
		flush();
		return shellStringWriter.toString();
	}
	
	void error(String msg){
		err.println(msg);
	}
	
	boolean isStatementComplete(String line){
		return executor.isStatementComplete(line);
	}
}
