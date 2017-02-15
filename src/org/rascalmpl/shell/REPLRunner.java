package org.rascalmpl.shell;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URISyntaxException;

import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.repl.RascalInterpreterREPL;

import jline.Terminal;
import jline.TerminalFactory;

public class REPLRunner extends RascalInterpreterREPL  implements ShellRunner {

  private static File getHistoryFile() throws IOException {
    File home = new File(System.getProperty("user.home"));
    File rascal = new File(home, ".rascal");
    if (!rascal.exists()) {
      rascal.mkdirs();
    }
    File historyFile = new File(rascal, ".repl-history-rascal-terminal");
    if (!historyFile.exists()) {
      historyFile.createNewFile();
    }
    return historyFile;
  }

  public REPLRunner(InputStream stdin, OutputStream stdout, Terminal term)  throws IOException, URISyntaxException{
    super(stdin, stdout, true, true, getHistoryFile(), term);
    setMeasureCommandTime(false);
    
  }

  @Override
  protected Evaluator constructEvaluator(Writer stdout, Writer stderr) {
    return ShellEvaluatorFactory.getDefaultEvaluator(new PrintWriter(stdout), new PrintWriter(stderr));
  }

  @Override
  public void run(String[] args) throws IOException {
    // there are no args for now
    run();
  }
}