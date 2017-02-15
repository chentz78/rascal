module lang::rascal::boot::Kernel
 
/*
 * The Kernel module collects all Rascal modules that are needed
 * to compile and execute Rascal programs.
 *
 * The Kernel is self-contained and comprehensive: a compiled version of the Kernel
 * is all that is needed for a full bootstrap of Rascal.
 *
 * An up-to-date, compiled version of the Kernel should always reside in the /boot directory 
 * of the Rascal project.
 */
 
extend experiments::Compiler::Compile;
extend experiments::Compiler::Execute;
extend experiments::Compiler::CompileMuLibrary;
extend lang::rascal::grammar::Bootstrap;
extend experiments::Compiler::Summary;