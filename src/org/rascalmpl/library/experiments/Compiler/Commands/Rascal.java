package org.rascalmpl.library.experiments.Compiler.Commands;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;

import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.RVMCore;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.RascalExecutionContext;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.RascalExecutionContextBuilder;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.value.ISourceLocation;
import org.rascalmpl.value.IValueFactory;
import org.rascalmpl.values.ValueFactoryFactory;

public class Rascal {

    static IValueFactory vf = ValueFactoryFactory.getValueFactory();

    public static ISourceLocation findBinary(ISourceLocation bin, String moduleName) throws IOException {
        StringWriter sw = new StringWriter();
        sw.append(bin.getPath())
        .append("/")
        .append(moduleName.replaceAll("::", "/"))
        .append(".rvmx");
        
        try {
            return vf.sourceLocation(bin.getScheme(), bin.getAuthority(), sw.toString());
        }
        catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }
 
    /**
     * Main function for execute command: rascal
     * 
     * @param args	list of command-line arguments
     */
    public static void main(String[] args) {
        try {

            CommandOptions cmdOpts = new CommandOptions("rascal");
            cmdOpts.pathConfigOptions()
            
            .boolOption("verbose")		
            .help("Print compilation steps")

            .boolOption("help")			
            .help("Print help message for this command")

            .boolOption("trace")		
            .help("Print Rascal functions during execution")

            .boolOption("profile")		
            .help("Profile execution of Rascal program")

            .module("RascalModule::main() to be executed")

            .handleArgs(args);

            PathConfig pcfg = cmdOpts.getPathConfig();
            RascalExecutionContext rex = RascalExecutionContextBuilder.normalContext(pcfg)
                    .trace(cmdOpts.getCommandBoolOption("trace"))
                    .profile(cmdOpts.getCommandBoolOption("profile"))
                    .forModule(cmdOpts.getModule().getValue())
                    .verbose(cmdOpts.getCommandBoolOption("verbose"))
                    .build();

            ISourceLocation binary = findBinary(cmdOpts.getCommandLocOption("bin"), cmdOpts.getModule().getValue());
            System.out.println(RVMCore.readFromFileAndExecuteProgram(binary, cmdOpts.getModuleOptionsAsMap(), rex));
        } catch (Throwable e) {
            e.printStackTrace();
            System.err.println("rascal: cannot execute program: " + e.getMessage());
            System.exit(1);
        }
    }
}
