@license{
  Copyright (c) 2009-2015 CWI
  All rights reserved. This program and the accompanying materials
  are made available under the terms of the Eclipse Public License v1.0
  which accompanies this distribution, and is available at
  http://www.eclipse.org/legal/epl-v10.html
}
@contributor{Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI}
@contributor{Tijs van der Storm - Tijs.van.der.Storm@cwi.nl}
@contributor{Mark Hills - Mark.Hills@cwi.nl (CWI)}
@contributor{Paul Klint - Paul.Klint@cwi.nl (CWI)}

module util::Reflective

import Exception;
import Message;
import ParseTree;
import IO;
import String;
import util::SystemAPI;
import lang::rascal::\syntax::Rascal;
import lang::manifest::IO;

@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses Evaluator to evaluate}
public java lrel[str result, str out, str err] evalCommands(list[str] command, loc org);

@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses Evaluator to get back the parse tree for the given command}
public java Tree parseCommand(str command, loc location);

@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses Evaluator to get back the parse tree for the given commands}
public java Tree parseCommands(str commands, loc location);

@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses Evaluator to access the Rascal module parser}
@doc{This parses a module from a string, in its own evaluator context}
public java Tree parseModuleAndFragments(str moduleContent, loc location);


@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses Evaluator to access the Rascal module parser}
@doc{This parses a module on the search path, and loads it into the current evaluator including all of its imported modules}
public java Tree parseModuleAndFragments(loc location);

@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses Evaluator to access the Rascal module parser}
public java Tree parseModuleAndFragments(loc location, list[loc] searchPath);

@javaClass{org.rascalmpl.library.util.Reflective}
@doc{Just parse a module at a given location without any furter processing (i.e., fragment parsing) or side-effects (e.g. module loading) }
public java lang::rascal::\syntax::Rascal::Module parseModule(loc location);

@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses RascalExecutionContext to resolve modulePath}
public java start[Module] parseNamedModuleWithSpaces(str modulePath) ;
//{
//    return parseModuleWithSpaces(getModuleLocation(modulePath));
//}

public start[Module] parseNamedModuleWithSpaces(str modulePath, PathConfig pcfg) {
    return parseModuleWithSpaces(getModuleLocation(modulePath, pcfg));
}

@javaClass{org.rascalmpl.library.util.Reflective}
@doc{Parse a module (including surounding spaces) at a given location without any furter processing (i.e., fragment parsing) or side-effects (e.g. module loading) }
public java start[Module] parseModuleWithSpaces(loc location);

@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses Evaluator to resolve a module name in the Rascal search path}
public java loc getModuleLocation(str modulePath);

@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses Evaluator to resolve a path name in the Rascal search path}
public java loc getSearchPathLocation(str filePath);

data PathConfig 
    // Defaults should be in sync with org.rascalmpl.library.util.PathConfig
  = pathConfig(list[loc] srcs = [|std:///|],        // List of directories to search for source files
               list[loc] courses = [|courses:///|], // List of locations to search for course source files
               loc bin = |home:///bin/|,            // Global directory for derived files outside projects
               loc boot = |boot:///| ,          // Directory with Rascal boot files
               loc repo = |home:///.r2d2|,      // Directory for installed Rascal jar packages                                                 
               list[loc] libs = [|home:///bin/|],          // List of directories to search source for derived files
               list[loc] javaCompilerPath = [], // TODO: must generate the same defaults as in PathConfig 
               list[loc] classloaders = []      // TODO: must generate the same defaults as in PathConfig
              );

data RascalManifest
  = rascalManifest(
      str \Main-Module = "Plugin",
      str \Main-Function = "main", 
      list[str] Source = ["src"],
      str Bin = "bin",
      list[str] \Required-Libraries = [],
      list[str] \Required-Dependencies = []
    ); 

data JavaBundleManifest
  = javaManifest(
      str \Manifest-Version = "",
      str \Bundle-SymbolicName = "",
      str \Bundle-RequiredExecutionEnvironment = "JavaSE-1.8",
      list[str] \Require-Bundle = [],
      str \Bundle-Version = "0.0.0.qualifier",
      list[str] \Export-Package = [],
      str \Bundle-Vendor = "",
      str \Bundle-Name = "",
      list[str] \Bundle-ClassPath = [],
      list[str] \Import-Package = [] 
    );        
            
loc metafile(loc l) = l + "META-INF/RASCAL.MF";

@doc{
  Converts a PathConfig and replaces all references to roots of projects or bundles
  by the folders which are nested under these roots as configured in their respective
  META-INF/RASCAL.MF files.
}
PathConfig applyManifests(PathConfig cfg) {
   mf = (l:readManifest(#RascalManifest, metafile(l)) | l <- cfg.srcs + cfg.libs + [cfg.bin], exists(metafile(l)));

   list[loc] expandSrcs(loc p) = [ p + s | s <- mf[p].Source] when mf[p]?;
   default list[loc] expandSrcs(loc p, str _) = [p];
   
   list[loc] expandlibs(loc p) = [ p + s | s <- mf[p].\Required-Libraries] when mf[p]?;
   default list[loc] expandlibs(loc p, str _) = [p];
    
   loc expandBin(loc p) = p + mf[p].Bin when mf[p]?;
   default loc expandBin(loc p) = p;
   
   cfg.srcs = [*expandSrcs(p) | p <- cfg.srcs];
   cfg.libs = [*expandlibs(p) | p <- cfg.libs];
   cfg.bin  = expandBin(cfg.bin);
   
   // TODO: here we add features for Required-Libraries by searching in a repository of installed
   // jars. This has to be resolved recursively.
   
   return cfg;
}

str makeFileName(str qualifiedModuleName, str extension = "rsc") = replaceAll(qualifiedModuleName, "::", "/") + "." + extension;

loc getSearchPathLoc(str filePath, PathConfig pcfg){
    for(loc dir <- pcfg.srcs + pcfg.libs){
        fileLoc = dir + filePath;
        if(exists(fileLoc)){
            //println("getModuleLocation <qualifiedModuleName> =\> <fileLoc>");
            return fileLoc;
        }
    }
    throw "Module with path <filePath> not found";
}

loc getModuleLocation(str qualifiedModuleName,  PathConfig pcfg){
    fileName = makeFileName(qualifiedModuleName);
    for(loc dir <- pcfg.srcs){
        fileLoc = dir + fileName;
        if(exists(fileLoc)){
            //println("getModuleLocation <qualifiedModuleName> =\> <fileLoc>");
            return fileLoc;
        }
    }
    throw "Module <qualifiedModuleName> not found";
}

@reflect{need to get the configuration from the evaluation context}
@javaClass{org.rascalmpl.library.util.Reflective}
java str getRascalClasspath();

str getModuleName(loc moduleLoc,  PathConfig pcfg){
    modulePath = moduleLoc.path;
    
    if(!endsWith(modulePath, "rsc")){
        throw "Not a Rascal source file: <moduleLoc>";
    }
   
    for(loc dir <- pcfg.srcs){
        if(startsWith(modulePath, dir.path) && moduleLoc.scheme == dir.scheme){
           moduleName = replaceFirst(modulePath, dir.path, "");
           moduleName = replaceLast(moduleName, ".rsc", "");
           if(moduleName[0] == "/"){
              moduleName = moduleName[1..];
           }
           moduleName = replaceAll(moduleName, "/", "::");
           return moduleName;
        }
    }
    
     for(loc dir <- pcfg.libs){
        if(startsWith(modulePath, dir.path) && moduleLoc.scheme == dir.scheme){
           moduleName = replaceFirst(modulePath, dir.path, "");
           moduleName = replaceLast(moduleName, ".tc", "");
           if(moduleName[0] == "/"){
              moduleName = moduleName[1..];
           }
           moduleName = replaceAll(moduleName, "/", "::");
           return moduleName;
        }
    }
    
    
    throw "No module name found for <moduleLoc>";
}

@doc{   
.Synopsis
Derive a location from a given module name for reading

.Description
Given a module name, a file name extension, and a PathConfig,
a path name is constructed from the module name + extension.

If a file F with this path exists in one of the directories in the PathConfig,
then the pair <true, F> is returned. Otherwise <false, some error location> is returned.

For a source extension (typically "rsc" or "mu" but this can be configured) srcs is searched, otherwise binPath + libs.

.Examples
[source,rascal-shell]
----
import util::Reflective;
getDerivedReadLoc("List", "rsc", pathConfig());
getDerivedReadLoc("experiments::Compiler::Compile", "rvm", pathConfig());
getDerivedReadLoc("experiments::Compiler::muRascal2RVM::Library", "mu", pathConfig());
----

.Benefits
This function is useful for type checking and compilation tasks, when derived information related to source modules has to be read
from locations in different, configurable, directories.
}

tuple[bool, loc] getDerivedReadLoc(str qualifiedModuleName, str extension, PathConfig pcfg, set[str] srcExtensions = {"rsc", "mu"}){
    fileName = makeFileName(qualifiedModuleName, extension=extension);
    //println("getDerivedReadLoc: <fileName>");
   
    if(extension in srcExtensions){
       for(loc dir <- pcfg.srcs){        // In a source directory?
           fileLoc = dir + fileName;
           if(exists(fileLoc)){
             //println("getDerivedReadLoc: <qualifiedModuleName>, <extension> =\> <fileLoc");
             return <true, fileLoc>;
           }
       }
    } else {
      for(loc dir <- pcfg.bin + pcfg.libs){   // In a bin or lib directory?
       
        fileLoc = dir + fileName;
        if(exists(fileLoc)){
           //println("getDerivedReadLoc: <qualifiedModuleName>, <extension> =\> <fileLoc>");
           return <true, fileLoc>;
        }
      }
    }
    //println("getDerivedReadLoc: <qualifiedModuleName>, <extension> =\> |error:///|");
    return <false, |error:///|>;
}

@doc{   
.Synopsis
Derive a location from a given module name for writing

.Description
Given a module name, a file name extension, and a PathConfig,
a path name is constructed from the module name + extension.

For source modules, a writable location cannot be derived.
For other modules, a location for this path in bin will be returned.

.Examples
[source,rascal-shell]
----
import util::Reflective;
getDerivedWriteLoc("List", "rvm", pathConfig());
getDerivedWriteLoc("experiments::Compiler::Compile", "rvm", pathConfig());
getDerivedWriteLoc("experiments::Compiler::muRascal2RVM::Library", "mu", pathConfig());
----

.Benefits
This function is useful for type checking and compilation tasks, when derived information related to source modules has to be written
to locations in separate, configurable, directories.
}
loc getDerivedWriteLoc(str qualifiedModuleName, str extension, PathConfig pcfg, set[str] srcExtensions = {"rsc", "mu"}){
    if(extension in srcExtensions){
        throw "Cannot derive writable location for module <qualifiedModuleName> with extension <extension>";
    }
    fileNameSrc = makeFileName(qualifiedModuleName);
    fileNameBin = makeFileName(qualifiedModuleName, extension=extension);
    
    bin = pcfg.bin;
    fileLocBin = bin + fileNameBin;
    //println("getDerivedWriteLoc: <qualifiedModuleName>, <extension> =\> <fileLocBin>");
    return fileLocBin;
}

@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{looks in execution context}
public java PathConfig getCurrentPathConfig();

@doc{Is the current Rascal code executed by the compiler or the interpreter?}
@javaClass{org.rascalmpl.library.util.Reflective}
public java bool inCompiledMode();

@doc{Give a textual diff between two values.}
@javaClass{org.rascalmpl.library.util.Reflective}
public java str diff(value old, value new);

@doc{Watch value val: 
- running in interpreted mode: write val to a file, 
- running in compiled mode: compare val with previously written value}
@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses Evaluator to resolve a module name in the Rascal search path}
public java &T watch(type[&T] tp, &T val, str name);

@javaClass{org.rascalmpl.library.util.Reflective}
@reflect{Uses Evaluator to resolve a module name in the Rascal search path}
public java &T watch(type[&T] tp, &T val, str name, value suffix);

@doc{Compute a fingerprint of a value for the benefit of the compiler and the compiler runtime}
@javaClass{org.rascalmpl.library.util.Reflective}
public java int getFingerprint(value val, bool concretePatterns);

@doc{Compute a fingerprint of a value and arity modifier for the benefit of the compiler and the compiler runtime}
@javaClass{org.rascalmpl.library.util.Reflective}
public java int getFingerprint(value val, int arity, bool concretePatterns);

@doc{Compute a fingerprint of a complete node for the benefit of the compiler and the compiler runtime}
@javaClass{org.rascalmpl.library.util.Reflective}
public java int getFingerprintNode(node nd);
