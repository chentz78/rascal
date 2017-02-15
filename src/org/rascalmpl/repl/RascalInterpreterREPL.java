package org.rascalmpl.repl;

import static org.rascalmpl.interpreter.utils.ReadEvalPrintDialogMessages.parseErrorMessage;
import static org.rascalmpl.interpreter.utils.ReadEvalPrintDialogMessages.staticErrorMessage;
import static org.rascalmpl.interpreter.utils.ReadEvalPrintDialogMessages.throwMessage;
import static org.rascalmpl.interpreter.utils.ReadEvalPrintDialogMessages.throwableMessage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.rascalmpl.interpreter.Configuration;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.StackTrace;
import org.rascalmpl.interpreter.control_exceptions.InterruptException;
import org.rascalmpl.interpreter.control_exceptions.QuitException;
import org.rascalmpl.interpreter.control_exceptions.Throw;
import org.rascalmpl.interpreter.result.IRascalResult;
import org.rascalmpl.interpreter.result.Result;
import org.rascalmpl.interpreter.staticErrors.StaticError;
import org.rascalmpl.interpreter.utils.Timing;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.ideservices.IDEServices;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.value.IValue;

import jline.Terminal;

public abstract class RascalInterpreterREPL extends BaseRascalREPL {

    protected Evaluator eval;
    private boolean measureCommandTime;
    private final OutputStream originalOutput;

    public RascalInterpreterREPL(InputStream stdin, OutputStream stdout, boolean prettyPrompt, boolean allowColors, File persistentHistory, Terminal terminal)
                    throws IOException, URISyntaxException {
        super(null, stdin, stdout, prettyPrompt, allowColors, persistentHistory, terminal, null);
        originalOutput = stdout;
    }

    public void setMeasureCommandTime(boolean measureCommandTime) {
        this.measureCommandTime = measureCommandTime;
    }

    public boolean getMeasureCommandTime() {
        return measureCommandTime;
    }

    @Override
    protected void initialize(PathConfig pcfg, Writer stdout, Writer stderr, IDEServices ideServices) {
        eval = constructEvaluator(stdout, stderr);
        eval.setREPL(this);
    }

    protected abstract Evaluator constructEvaluator(Writer stdout, Writer stderr);

    @Override
    protected PrintWriter getErrorWriter() {
        return eval.getStdErr();
    }

    @Override
    protected PrintWriter getOutputWriter() {
        return eval.getStdOut();
    }

    @Override
    public void stop() {
        eval.interrupt();
        super.stop();
    }

    @Override
    protected void cancelRunningCommandRequested() {
        eval.interrupt();
    }

    @Override
    protected void terminateRequested() {
        eval.interrupt();
    }

    @Override
    protected void stackTraceRequested() {
        StackTrace trace = eval.getStackTrace();
        Writer err = getErrorWriter();
        try {
            err.write("Current stack trace:\n");
            err.write(trace.toLinkedString());
            err.flush();
        }
        catch (IOException e) {
        }
    }

    @Override
    protected IRascalResult evalStatement(String statement, String lastLine) throws InterruptedException {
        try {
            Result<IValue> value;
            long duration;

            synchronized(eval) {
                Timing tm = new Timing();
                tm.start();
                value = eval.eval(null, statement, URIUtil.rootLocation("prompt"));
                duration = tm.duration();
            }
            if (measureCommandTime) {
                eval.getStdErr().println("\nTime: " + duration + "ms");
            }
            return value;
        }
        catch (InterruptException ie) {
            eval.getStdErr().println("Interrupted");
            eval.getStdErr().println(ie.getRascalStackTrace().toLinkedString());
            return null;
        }
        catch (ParseError pe) {
            eval.getStdErr().println(parseErrorMessage(lastLine, "prompt", pe));
            return null;
        }
        catch (StaticError e) {
            eval.getStdErr().println(staticErrorMessage(e));
            return null;
        }
        catch (Throw e) {
            eval.getStdErr().println(throwMessage(e));
            return null;
        }
        catch (QuitException q) {
            eval.getStdErr().println("Quiting REPL");
            throw new InterruptedException();
        }
        catch (Throwable e) {
            eval.getStdErr().println(throwableMessage(e, eval.getStackTrace()));
            return null;
        }
    }

    @Override
    protected boolean isStatementComplete(String command) {
        try {
            eval.parseCommand(null, command, URIUtil.rootLocation("prompt"));
        }
        catch (ParseError pe) {
            String[] commandLines = command.split("\n");
            int lastLine = commandLines.length;
            int lastColumn = commandLines[lastLine - 1].length();

            if (pe.getEndLine() + 1 == lastLine && lastColumn <= pe.getEndColumn()) { 
                return false;
            }
        }
        return true;
    }

    @Override
    protected Collection<String> completePartialIdentifier(String line, int cursor, String qualifier, String term) {
        return eval.completePartialIdentifier(qualifier, term);
    }

    @Override
    protected Collection<String> completeModule(String qualifier, String partialModuleName) {
        List<String> entries = eval.getRascalResolver().listModuleEntries(qualifier);
        if (entries != null && entries.size() > 0) {
            if (entries.contains(partialModuleName)) {
                // we have a full directory name (at least the option)
                List<String> subEntries = eval.getRascalResolver().listModuleEntries(qualifier + "::" + partialModuleName);
                if (subEntries != null) {
                    entries.remove(partialModuleName);
                    subEntries.forEach(e -> entries.add(partialModuleName + "::" + e));
                }
            }
            return entries.stream()
                            .filter(m -> m.startsWith(partialModuleName))
                            .map(s -> qualifier.isEmpty() ? s : qualifier + "::" + s)
                            .sorted()
                            .collect(Collectors.toList());

        }
        return null;
    }

    private static final SortedSet<String> commandLineOptions = new TreeSet<>();
    static {
        commandLineOptions.add(Configuration.GENERATOR_PROFILING_PROPERTY.substring("rascal.".length()));
        commandLineOptions.add(Configuration.PROFILING_PROPERTY.substring("rascal.".length()));
        commandLineOptions.add(Configuration.ERRORS_PROPERTY.substring("rascal.".length()));
        commandLineOptions.add(Configuration.TRACING_PROPERTY.substring("rascal.".length()));
    }
    @Override
    protected SortedSet<String> getCommandLineOptions() {
        return commandLineOptions;
    }

    public Terminal getTerminal() {
        return reader.getTerminal();
    }

    public InputStream getInput() {
        return reader.getInput();
    }
    public OutputStream getOutput() {
        return originalOutput;
    }
}
